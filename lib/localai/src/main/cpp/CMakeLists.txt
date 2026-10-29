# Librerie native dei motori: whisper.cpp per la trascrizione e llama.cpp per i modelli di testo (submodule), sopra un
# solo ggml, più i collegamenti JNI di WhisperNative e LlamaNative.
cmake_minimum_required(VERSION 3.22)
project(poldo_whisper LANGUAGES C CXX)

# Solo la libreria di whisper.cpp, senza esempi, test o server
set(WHISPER_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_SERVER OFF CACHE BOOL "" FORCE)

# Solo la libreria di llama.cpp: common resta fuori perché porta con sé un client HTTP per scaricare i modelli, e
# l'app non deve contenere codice di rete oltre al catalogo
set(LLAMA_BUILD_COMMON OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TOOLS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_APP OFF CACHE BOOL "" FORCE)
set(LLAMA_OPENSSL OFF CACHE BOOL "" FORCE)

# Senza OpenMP ggml usa il proprio pool di thread, e non serve impacchettare libomp.so
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)

# Una variante del backend CPU per ogni generazione di processori ARM, da ARMv8.0 ad ARMv9.2 con SME:
# all'avvio i collegamenti JNI caricano la più veloce che il telefono supporta. Le varianti sono librerie caricate
# a runtime, quindi anche whisper, llama e ggml diventano librerie condivise.
set(BUILD_SHARED_LIBS ON CACHE BOOL "" FORCE)
set(GGML_BACKEND_DL ON CACHE BOOL "" FORCE)
set(GGML_CPU_ALL_VARIANTS ON CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
# Vuota: con le varianti ggml rifiuta un'architettura fissa, che la cache di una build precedente può avere
set(GGML_CPU_ARM_ARCH "" CACHE STRING "" FORCE)

# ggml scrive le varianti nella cartella degli eseguibili: deve essere quella delle altre librerie
if (NOT CMAKE_RUNTIME_OUTPUT_DIRECTORY)
    set(CMAKE_RUNTIME_OUTPUT_DIRECTORY ${CMAKE_LIBRARY_OUTPUT_DIRECTORY})
endif ()

# Allineamento a pagine da 16 KB, richiesto dai dispositivi Android 15 che le usano: vale per ogni libreria
string(APPEND CMAKE_SHARED_LINKER_FLAGS " -Wl,-z,max-page-size=16384")
string(APPEND CMAKE_MODULE_LINKER_FLAGS " -Wl,-z,max-page-size=16384")

# whisper.cpp e llama.cpp senza ottimizzazioni sono decine di volte più lenti: anche le build di debug compilano in -O3
if (CMAKE_BUILD_TYPE STREQUAL "Debug")
    add_compile_options(-O3 -DNDEBUG)
endif ()

# Prima llama.cpp, che crea il target ggml: whisper.cpp lo trova già definito e lo riusa invece di compilare il suo.
# Le due copie di ggml nei submodule devono quindi avere le stesse intestazioni pubbliche: si aggiornano insieme
add_subdirectory(llama.cpp)
add_subdirectory(whisper.cpp)

# Il caricamento delle varianti del backend CPU, comune ai due collegamenti JNI: condiviso, perché la variante si
# carichi una volta sola qualunque motore parta per primo
add_library(backend_cpu SHARED backend_cpu.cpp)
target_link_libraries(backend_cpu PUBLIC ggml log)

add_library(whisper_jni SHARED whisper_jni.cpp)
target_link_libraries(whisper_jni PRIVATE whisper backend_cpu)

add_library(llama_jni SHARED llama_jni.cpp)
target_link_libraries(llama_jni PRIVATE llama backend_cpu)
