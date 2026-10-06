// Collegamento JNI tra WhisperNative (Kotlin) e whisper.cpp.

#include <jni.h>
#include <android/log.h>

#include <string>

#include "whisper.h"

namespace {

constexpr const char * TAG = "PoldoWhisper";

/**
 * Inoltra a logcat i messaggi di whisper.cpp e ggml, che altrimenti finirebbero su stderr e andrebbero persi.
 */
void scriviLog(enum ggml_log_level livello, const char * testo, void * /* datiUtente */) {
    int priorita = ANDROID_LOG_DEBUG;
    switch (livello) {
        case GGML_LOG_LEVEL_ERROR: priorita = ANDROID_LOG_ERROR; break;
        case GGML_LOG_LEVEL_WARN:  priorita = ANDROID_LOG_WARN;  break;
        case GGML_LOG_LEVEL_INFO:  priorita = ANDROID_LOG_INFO;  break;
        default: break;
    }
    __android_log_write(priorita, TAG, testo);
}

/**
 * Crea una stringa Java dai byte UTF-8 prodotti da whisper.cpp. NewStringUTF accetta solo l'UTF-8 modificato
 * di Java e andrebbe in errore con i caratteri fuori dal piano base, come le emoji.
 */
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

} // namespace

/**
 * Carica il modello dal file indicato.
 * @return Il puntatore al contesto di whisper.cpp, o 0 se il file non è un modello valido.
 */
extern "C" JNIEXPORT jlong JNICALL
Java_org_florisboard_lib_localai_whisper_WhisperNative_initContext(
        JNIEnv * env, jobject /* thiz */, jstring percorsoModello) {
    whisper_log_set(scriviLog, nullptr);

    const char * percorso = env->GetStringUTFChars(percorsoModello, nullptr);
    whisper_context_params parametri = whisper_context_default_params();
    // Solo CPU: nessun backend GPU è compilato
    parametri.use_gpu = false;
    whisper_context * contesto = whisper_init_from_file_with_params(percorso, parametri);
    env->ReleaseStringUTFChars(percorsoModello, percorso);

    return reinterpret_cast<jlong>(contesto);
}

/**
 * Libera la memoria del modello.
 */
extern "C" JNIEXPORT void JNICALL
Java_org_florisboard_lib_localai_whisper_WhisperNative_freeContext(
        JNIEnv * /* env */, jobject /* thiz */, jlong handle) {
    whisper_free(reinterpret_cast<whisper_context *>(handle));
}

/**
 * Trascrive un segmento di audio a 16 kHz mono.
 * @return Il testo trascritto, o null se whisper.cpp non è riuscito a elaborare l'audio.
 */
extern "C" JNIEXPORT jstring JNICALL
Java_org_florisboard_lib_localai_whisper_WhisperNative_fullTranscribe(
        JNIEnv * env, jobject /* thiz */, jlong handle, jfloatArray campioni, jstring lingua,
        jint thread, jboolean traduci) {
    auto * contesto = reinterpret_cast<whisper_context *>(handle);
    if (contesto == nullptr) {
        return nullptr;
    }

    // Qualità prima della velocità: ricerca a fascio con 5 ipotesi, come la riga di comando di whisper.cpp
    whisper_full_params parametri = whisper_full_default_params(WHISPER_SAMPLING_BEAM_SEARCH);
    parametri.beam_search.beam_size = 5;
    parametri.n_threads = thread;
    parametri.translate = traduci == JNI_TRUE;
    parametri.no_context = true;
    parametri.print_progress = false;
    parametri.print_realtime = false;
    parametri.print_timestamps = false;
    parametri.print_special = false;
    // Niente token di non parlato come "(musica)": sui vocali sono quasi sempre inventati
    parametri.suppress_nst = true;

    // Senza lingua indicata whisper.cpp la riconosce da sé
    std::string codiceLingua = "auto";
    if (lingua != nullptr) {
        const char * caratteri = env->GetStringUTFChars(lingua, nullptr);
        codiceLingua = caratteri;
        env->ReleaseStringUTFChars(lingua, caratteri);
    }
    parametri.language = codiceLingua.c_str();

    const jsize numeroCampioni = env->GetArrayLength(campioni);
    jfloat * dati = env->GetFloatArrayElements(campioni, nullptr);
    const int esito = whisper_full(contesto, parametri, dati, numeroCampioni);
    // JNI_ABORT: i campioni non sono stati modificati, non serve ricopiarli nell'array Java
    env->ReleaseFloatArrayElements(campioni, dati, JNI_ABORT);
    if (esito != 0) {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "whisper_full non riuscito, codice %d", esito);
        return nullptr;
    }

    std::string testo;
    const int numeroSegmenti = whisper_full_n_segments(contesto);
    for (int i = 0; i < numeroSegmenti; ++i) {
        testo += whisper_full_get_segment_text(contesto, i);
    }
    return nuovaStringaUtf8(env, testo);
}
