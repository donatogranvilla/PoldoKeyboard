# Libreria nativa del motore di trascrizione: whisper.cpp (submodule) più il collegamento JNI di WhisperNative.
cmake_minimum_required(VERSION 3.22)
project(poldo_whisper LANGUAGES C CXX)

# Solo la libreria di whisper.cpp, senza esempi, test o server
set(WHISPER_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_SERVER OFF CACHE BOOL "" FORCE)

# Senza OpenMP ggml usa il proprio pool di thread, e non serve impacchettare libomp.so
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)

# Una variante del backend CPU per ogni generazione di processori ARM, da ARMv8.0 ad ARMv9.2 con SME:
# all'avvio whisper_jni carica la più veloce che il telefono supporta. Le varianti sono librerie caricate
# a runtime, quindi anche whisper e ggml diventano librerie condivise.
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

# whisper.cpp senza ottimizzazioni è decine di volte più lento: anche le build di debug compilano in -O3
if (CMAKE_BUILD_TYPE STREQUAL "Debug")
    add_compile_options(-O3 -DNDEBUG)
endif ()

add_subdirectory(whisper.cpp)

add_library(whisper_jni SHARED whisper_jni.cpp)
target_link_libraries(whisper_jni PRIVATE whisper ggml log)
