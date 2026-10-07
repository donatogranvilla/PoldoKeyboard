// Collegamento JNI tra WhisperNative (Kotlin) e whisper.cpp.

#include <jni.h>
#include <android/log.h>

#include <mutex>
#include <string>
#include <vector>

#include "ggml-backend.h"
#include "whisper.h"

namespace {

constexpr const char * TAG = "PoldoWhisper";

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

std::once_flag caricamentoCpu;
const char * varianteCpu = nullptr;

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
 * Carica, una volta sola, la variante del backend CPU più veloce tra quelle che il processore supporta.
 * ggml_backend_load scarta da sé le varianti con istruzioni che il processore non ha, controllandole con codice
 * compilato senza quelle istruzioni: basta provarle in ordine e fermarsi alla prima che si carica.
 * @return Il nome della variante caricata, o nullptr se nessuna si può usare.
 */
const char * caricaBackendCpu() {
    std::call_once(caricamentoCpu, [] {
        whisper_log_set(scriviLog, nullptr);
        for (const char * variante : VARIANTI_CPU) {
            if (ggml_backend_load(variante) != nullptr) {
                varianteCpu = variante;
                break;
            }
        }
        if (varianteCpu != nullptr) {
            __android_log_print(ANDROID_LOG_INFO, TAG, "Backend CPU: %s", varianteCpu);
        } else {
            __android_log_write(ANDROID_LOG_ERROR, TAG, "Nessuna variante del backend CPU si può caricare");
        }
    });
    return varianteCpu;
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
 * Carica la variante del backend CPU adatta al processore.
 * @return Il nome della libreria caricata, come "libggml-cpu-android_armv8.2_2.so", o null se nessuna si può usare.
 */
extern "C" JNIEXPORT jstring JNICALL
Java_org_florisboard_lib_localai_whisper_WhisperNative_caricaVarianteCpu(
        JNIEnv * env, jobject /* thiz */) {
    const char * variante = caricaBackendCpu();
    return variante != nullptr ? env->NewStringUTF(variante) : nullptr;
}

/**
 * Carica il modello dal file indicato.
 * @return Il puntatore al contesto di whisper.cpp, o 0 se il file non è un modello valido o manca il backend CPU.
 */
extern "C" JNIEXPORT jlong JNICALL
Java_org_florisboard_lib_localai_whisper_WhisperNative_initContext(
        JNIEnv * env, jobject /* thiz */, jstring percorsoModello) {
    whisper_log_set(scriviLog, nullptr);
    if (caricaBackendCpu() == nullptr) {
        return 0;
    }

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
 * Riconosce la lingua parlata nei primi 30 s dell'audio indicato.
 * @return Il codice della lingua, come "it", o null se il modello è solo inglese o il riconoscimento fallisce.
 */
extern "C" JNIEXPORT jstring JNICALL
Java_org_florisboard_lib_localai_whisper_WhisperNative_rilevaLingua(
        JNIEnv * env, jobject /* thiz */, jlong handle, jfloatArray campioni, jint thread) {
    auto * contesto = reinterpret_cast<whisper_context *>(handle);
    if (contesto == nullptr || !whisper_is_multilingual(contesto)) {
        return nullptr;
    }
    const jsize numeroCampioni = env->GetArrayLength(campioni);
    jfloat * dati = env->GetFloatArrayElements(campioni, nullptr);
    const int esitoMel = whisper_pcm_to_mel(contesto, dati, numeroCampioni, thread);
    env->ReleaseFloatArrayElements(campioni, dati, JNI_ABORT);
    if (esitoMel != 0) {
        return nullptr;
    }
    const int idLingua = whisper_lang_auto_detect(contesto, 0, thread, nullptr);
    const char * codice = idLingua >= 0 ? whisper_lang_str(idLingua) : nullptr;
    return codice != nullptr ? env->NewStringUTF(codice) : nullptr;
}

/**
 * Carica il modello Silero del rilevatore di parlato (VAD) dal file indicato.
 * @return Il puntatore al contesto del VAD, o 0 se il file non è valido o manca il backend CPU.
 */
extern "C" JNIEXPORT jlong JNICALL
Java_org_florisboard_lib_localai_whisper_WhisperNative_initVad(
        JNIEnv * env, jobject /* thiz */, jstring percorsoModello) {
    if (caricaBackendCpu() == nullptr) {
        return 0;
    }
    const char * percorso = env->GetStringUTFChars(percorsoModello, nullptr);
    whisper_vad_context_params parametri = whisper_vad_default_context_params();
    // La rete è minuscola e ricorrente: un thread basta e lascia liberi gli altri
    parametri.n_threads = 1;
    parametri.use_gpu = false;
    whisper_vad_context * contesto = whisper_vad_init_from_file_with_params(percorso, parametri);
    env->ReleaseStringUTFChars(percorsoModello, percorso);
    return reinterpret_cast<jlong>(contesto);
}

/**
 * Libera la memoria del VAD.
 */
extern "C" JNIEXPORT void JNICALL
Java_org_florisboard_lib_localai_whisper_WhisperNative_freeVad(
        JNIEnv * /* env */, jobject /* thiz */, jlong handle) {
    whisper_vad_free(reinterpret_cast<whisper_vad_context *>(handle));
}

/**
 * Trova i tratti di parlato nell'audio a 16 kHz mono, già allargati del margine richiesto.
 * @return Inizio e fine di ogni tratto in secondi, in coppie consecutive; vuoto se non c'è parlato,
 *   null se il rilevamento non riesce.
 */
extern "C" JNIEXPORT jfloatArray JNICALL
Java_org_florisboard_lib_localai_whisper_WhisperNative_trovaParlato(
        JNIEnv * env, jobject /* thiz */, jlong handle, jfloatArray campioni, jfloat soglia,
        jint parlatoMinimoMs, jint silenzioMinimoMs, jfloat parlatoMassimoS, jint margineMs) {
    auto * contesto = reinterpret_cast<whisper_vad_context *>(handle);
    if (contesto == nullptr) {
        return nullptr;
    }
    whisper_vad_params parametri = whisper_vad_default_params();
    parametri.threshold = soglia;
    parametri.min_speech_duration_ms = parlatoMinimoMs;
    parametri.min_silence_duration_ms = silenzioMinimoMs;
    parametri.max_speech_duration_s = parlatoMassimoS;
    parametri.speech_pad_ms = margineMs;

    const jsize numeroCampioni = env->GetArrayLength(campioni);
    jfloat * dati = env->GetFloatArrayElements(campioni, nullptr);
    whisper_vad_segments * tratti = whisper_vad_segments_from_samples(contesto, parametri, dati, numeroCampioni);
    env->ReleaseFloatArrayElements(campioni, dati, JNI_ABORT);
    if (tratti == nullptr) {
        return nullptr;
    }

    const int numeroTratti = whisper_vad_segments_n_segments(tratti);
    std::vector<jfloat> secondi(2 * numeroTratti);
    for (int i = 0; i < numeroTratti; i++) {
        // whisper.cpp li dà in centesimi di secondo
        secondi[2 * i] = whisper_vad_segments_get_segment_t0(tratti, i) / 100.0f;
        secondi[2 * i + 1] = whisper_vad_segments_get_segment_t1(tratti, i) / 100.0f;
    }
    whisper_vad_free_segments(tratti);

    jfloatArray risultato = env->NewFloatArray(static_cast<jsize>(secondi.size()));
    if (risultato != nullptr && !secondi.empty()) {
        env->SetFloatArrayRegion(risultato, 0, static_cast<jsize>(secondi.size()), secondi.data());
    }
    return risultato;
}

/**
 * Trascrive un segmento di audio a 16 kHz mono.
 * @param prompt Il testo da dare al modello come contesto precedente, o null per nessuno.
 * @return Il testo trascritto, o null se whisper.cpp non è riuscito a elaborare l'audio.
 */
extern "C" JNIEXPORT jstring JNICALL
Java_org_florisboard_lib_localai_whisper_WhisperNative_fullTranscribe(
        JNIEnv * env, jobject /* thiz */, jlong handle, jfloatArray campioni, jstring lingua,
        jint thread, jboolean traduci, jstring prompt) {
    auto * contesto = reinterpret_cast<whisper_context *>(handle);
    if (contesto == nullptr) {
        return nullptr;
    }

    // Qualità prima della velocità: ricerca a fascio con 5 ipotesi, come la riga di comando di whisper.cpp
    whisper_full_params parametri = whisper_full_default_params(WHISPER_SAMPLING_BEAM_SEARCH);
    parametri.beam_search.beam_size = 5;
    // Quando una finestra fallisce, whisper.cpp la ritenta a temperature più alte con greedy.best_of campioni,
    // che con la beam search predefinita vale -1, cioè uno solo: come OpenAI e la CLI se ne tengono 5
    parametri.greedy.best_of = 5;
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

    std::string testoPrompt;
    if (prompt != nullptr) {
        const char * caratteri = env->GetStringUTFChars(prompt, nullptr);
        testoPrompt = caratteri;
        env->ReleaseStringUTFChars(prompt, caratteri);
        parametri.initial_prompt = testoPrompt.c_str();
    }

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
