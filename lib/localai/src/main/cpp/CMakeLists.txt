# Libreria nativa del motore di trascrizione: whisper.cpp (submodule) più il collegamento JNI di WhisperNative.
cmake_minimum_required(VERSION 3.22)
project(poldo_whisper LANGUAGES C CXX)

# Solo la libreria di whisper.cpp, senza esempi, test o server, collegata in modo statico dentro libwhisper_jni.so
set(WHISPER_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)

# Senza OpenMP ggml usa il proprio pool di thread, e non serve impacchettare libomp.so
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)

# Istruzioni dei processori ARM recenti (dotprod e fp16, dai Cortex-A55 e A75 in su), che accelerano molto
# i modelli quantizzati. Il telefono di test, uno Snapdragon 7s Gen 2 con Cortex-A78 e A55, le ha entrambe.
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_CPU_ARM_ARCH "armv8.2-a+dotprod+fp16" CACHE STRING "" FORCE)

# whisper.cpp senza ottimizzazioni è decine di volte più lento: anche le build di debug compilano in -O3
if (CMAKE_BUILD_TYPE STREQUAL "Debug")
    add_compile_options(-O3 -DNDEBUG)
endif ()

add_subdirectory(whisper.cpp)

add_library(whisper_jni SHARED whisper_jni.cpp)
target_link_libraries(whisper_jni PRIVATE whisper log)
# Allineamento a pagine da 16 KB, richiesto dai dispositivi Android 15 che le usano
target_link_options(whisper_jni PRIVATE "-Wl,-z,max-page-size=16384")
