// Caricamento del backend CPU di ggml, comune ai collegamenti JNI di whisper.cpp e llama.cpp.

#include "backend_cpu.h"

#include <android/log.h>

#include <mutex>

#include "ggml-backend.h"

namespace {

/**
 * Le varianti del backend CPU di ggml, dalla più ricca di istruzioni alla più semplice. Il nome indica la prima
 * versione di ARM che le offre: armv8.2_2 usa dotprod e fp16, armv8.6_1 aggiunge i8mm, le armv9 SVE2 e SME.
 */
constexpr const char * VARIANTI_CPU[] = {
    "libggml-cpu-android_armv9.2_2.so",
    "libggml-cpu-android_armv9.2_1.so",
    "libggml-cpu-android_armv9.0_1.so",
    "libggml-cpu-android_armv8.6_1.so",
    "libggml-cpu-android_armv8.2_2.so",
    "libggml-cpu-android_armv8.2_1.so",
    "libggml-cpu-android_armv8.0_1.so",
};

/** È una libreria condivisa: i due collegamenti JNI vedono lo stesso stato e la variante si carica una volta sola. */
std::once_flag caricamento;
const char * varianteCpu = nullptr;

} // namespace

void scriviLog(enum ggml_log_level livello, const char * testo, void * datiUtente) {
    int priorita = ANDROID_LOG_DEBUG;
    switch (livello) {
        case GGML_LOG_LEVEL_ERROR: priorita = ANDROID_LOG_ERROR; break;
        case GGML_LOG_LEVEL_WARN:  priorita = ANDROID_LOG_WARN;  break;
        case GGML_LOG_LEVEL_INFO:  priorita = ANDROID_LOG_INFO;  break;
        default: break;
    }
    __android_log_write(priorita, static_cast<const char *>(datiUtente), testo);
}

/*
 * ggml_backend_load scarta da sé le varianti con istruzioni che il processore non ha, controllandole con codice
 * compilato senza quelle istruzioni: basta provarle in ordine e fermarsi alla prima che si carica.
 */
const char * caricaBackendCpu(const char * tag) {
    std::call_once(caricamento, [tag] {
        for (const char * variante : VARIANTI_CPU) {
            if (ggml_backend_load(variante) != nullptr) {
                varianteCpu = variante;
                break;
            }
        }
        if (varianteCpu != nullptr) {
            __android_log_print(ANDROID_LOG_INFO, tag, "Backend CPU: %s", varianteCpu);
        } else {
            __android_log_write(ANDROID_LOG_ERROR, tag, "Nessuna variante del backend CPU si può caricare");
        }
    });
    return varianteCpu;
}

jstring nuovaStringaUtf8(JNIEnv * env, const std::string & testo) {
    const auto lunghezza = static_cast<jsize>(testo.size());
    jbyteArray byte = env->NewByteArray(lunghezza);
    env->SetByteArrayRegion(byte, 0, lunghezza, reinterpret_cast<const jbyte *>(testo.data()));
    jclass classeStringa = env->FindClass("java/lang/String");
    jmethodID costruttore = env->GetMethodID(classeStringa, "<init>", "([BLjava/lang/String;)V");
    jstring codifica = env->NewStringUTF("UTF-8");
    auto risultato = static_cast<jstring>(env->NewObject(classeStringa, costruttore, byte, codifica));
    env->DeleteLocalRef(codifica);
    env->DeleteLocalRef(classeStringa);
    env->DeleteLocalRef(byte);
    return risultato;
}
