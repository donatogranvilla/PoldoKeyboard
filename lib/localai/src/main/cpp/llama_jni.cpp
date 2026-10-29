// Collegamento JNI tra LlamaNative (Kotlin) e llama.cpp: genera testo da un prompt già composto, in modo deterministico
// e con lo speculative decoding a n-grammi, che accelera i testi che ripetono parole del prompt come le correzioni.

#include <jni.h>
#include <android/log.h>

#include <algorithm>
#include <atomic>
#include <chrono>
#include <functional>
#include <string>
#include <vector>

#include "backend_cpu.h"
#include "llama.h"

namespace {

constexpr const char * TAG = "PoldoLlama";

/** Token elaborati insieme nella lettura del prompt: abbastanza per sfruttare i thread senza sprecare memoria. */
constexpr uint32_t TOKEN_PER_BLOCCO = 512;

/**
 * Lunghezza della sequenza da ritrovare nel testo già scritto per proporre una bozza, e lunghezza massima della
 * bozza: gli stessi valori di "--spec-type ngram-simple" di llama.cpp, provati sul telefono con Gemma 4 E2B.
 */
constexpr size_t LUNGHEZZA_CHIAVE = 12;
constexpr size_t LUNGHEZZA_BOZZA = 48;

/** Un modello caricato, con il suo contesto e la richiesta di interrompere la generazione in corso. */
struct Motore {
    llama_model * modello = nullptr;
    llama_context * contesto = nullptr;
    llama_sampler * campionatore = nullptr;
    std::atomic<bool> fermato{false};
};

/** Chiamata da llama.cpp durante ogni calcolo: true lo interrompe. */
bool daFermare(void * dati) {
    return static_cast<Motore *>(dati)->fermato.load();
}

/** Carica il backend CPU e intanto manda a logcat i messaggi di llama.cpp e ggml. */
const char * preparaBackend() {
    llama_log_set(scriviLog, const_cast<char *>(TAG));
    const char * variante = caricaBackendCpu(TAG);
    if (variante != nullptr) {
        llama_backend_init();
    }
    return variante;
}

/** I token del testo, con il token di inizio se il modello lo vuole e riconoscendo i marcatori dei turni di chat. */
std::vector<llama_token> tokenizza(const llama_vocab * vocabolario, const std::string & testo) {
    const auto lunghezza = static_cast<int32_t>(testo.size());
    const int32_t quanti = -llama_tokenize(vocabolario, testo.data(), lunghezza, nullptr, 0, true, true);
    std::vector<llama_token> token(quanti);
    llama_tokenize(vocabolario, testo.data(), lunghezza, token.data(), quanti, true, true);
    return token;
}

/** Il testo di un token, senza i marcatori speciali. */
std::string testoToken(const llama_vocab * vocabolario, llama_token token) {
    char buffer[256];
    const int32_t lunghezza = llama_token_to_piece(vocabolario, token, buffer, sizeof(buffer), 0, false);
    return lunghezza > 0 ? std::string(buffer, lunghezza) : std::string();
}

/**
 * Una bozza dei prossimi token presa dal testo già visto: si cerca all'indietro l'ultima volta in cui comparivano gli
 * ultimi LUNGHEZZA_CHIAVE token, e si propongono quelli che li seguivano. Nelle correzioni il testo da scrivere è quasi
 * tutto nel prompt, quindi la bozza è spesso giusta e il modello ne conferma molti token in un passo solo.
 * @param storia I token già nel contesto, prompt compreso.
 * @param ultimo L'ultimo token scelto, non ancora nel contesto.
 * @return I token proposti; vuoto se la sequenza non compare o la bozza sarebbe troppo corta per convenire.
 */
std::vector<llama_token> bozzaDaStoria(const std::vector<llama_token> & storia, llama_token ultimo) {
    const size_t n = storia.size();
    if (n <= LUNGHEZZA_CHIAVE + LUNGHEZZA_BOZZA + 1) {
        return {};
    }
    std::vector<llama_token> chiave(storia.end() - (LUNGHEZZA_CHIAVE - 1), storia.end());
    chiave.push_back(ultimo);

    // La posizione 0 vale "non trovata", come in llama.cpp
    size_t trovata = 0;
    for (size_t j = n - LUNGHEZZA_CHIAVE - 1; j > 0; --j) {
        if (std::equal(chiave.begin(), chiave.end(), storia.begin() + j)) {
            trovata = j;
            break;
        }
    }
    if (trovata == 0) {
        return {};
    }
    const size_t inizio = trovata + LUNGHEZZA_CHIAVE;
    const size_t quanti = std::min(LUNGHEZZA_BOZZA, n - inizio);
    if (quanti < LUNGHEZZA_CHIAVE) {
        return {};
    }
    return {storia.begin() + inizio, storia.begin() + inizio + quanti};
}

/**
 * La parte del testo che finisce con un carattere UTF-8 completo: un token può contenere solo una parte dei byte di
 * una lettera accentata o di un'emoji, e il resto arriva col token dopo.
 */
size_t lunghezzaUtf8Completa(const std::string & testo) {
    size_t fine = testo.size();
    // Al più 3 byte di continuazione dopo il byte iniziale
    for (size_t indietro = 1; indietro <= 4 && indietro <= fine; ++indietro) {
        const auto byte = static_cast<unsigned char>(testo[fine - indietro]);
        if ((byte & 0xC0) == 0x80) {
            continue;
        }
        size_t attesi = 1;
        if ((byte & 0xE0) == 0xC0) attesi = 2;
        else if ((byte & 0xF0) == 0xE0) attesi = 3;
        else if ((byte & 0xF8) == 0xF0) attesi = 4;
        return indietro >= attesi ? fine : fine - indietro;
    }
    return fine;
}


/**
 * Carica il modello GGUF e prepara un contesto della lunghezza indicata.
 * @param token La lunghezza del contesto: prompt e risposta insieme non possono superarla.
 * @return Il motore, o nullptr se il file non è un modello valido o manca la memoria.
 */
Motore * caricaMotore(const char * percorso, int token, int thread) {
    if (preparaBackend() == nullptr) {
        return nullptr;
    }
    auto * motore = new Motore();
    llama_model_params parametriModello = llama_model_default_params();
    // Solo CPU: nessun backend GPU è compilato
    parametriModello.n_gpu_layers = 0;
    motore->modello = llama_model_load_from_file(percorso, parametriModello);
    if (motore->modello == nullptr) {
        delete motore;
        return nullptr;
    }

    llama_context_params parametri = llama_context_default_params();
    parametri.n_ctx = static_cast<uint32_t>(token);
    parametri.n_batch = TOKEN_PER_BLOCCO;
    parametri.n_ubatch = TOKEN_PER_BLOCCO;
    parametri.n_threads = thread;
    parametri.n_threads_batch = thread;
    // Gli strati a finestra scorrevole, molti in Gemma, tengono in cache solo la loro finestra, come in llama-server:
    // la cache intera, predefinita nella libreria, occupa più memoria e sul telefono scrive un po' più lentamente.
    // La finestra basta comunque a scartare le bozze rifiutate, che sono gli ultimi token
    parametri.swa_full = false;
    parametri.abort_callback = daFermare;
    parametri.abort_callback_data = motore;
    motore->contesto = llama_init_from_model(motore->modello, parametri);
    if (motore->contesto == nullptr) {
        llama_model_free(motore->modello);
        delete motore;
        return nullptr;
    }
    // Sempre il token più probabile: la stessa risposta a ogni richiesta, come la temperatura 0 delle prove
    motore->campionatore = llama_sampler_init_greedy();
    return motore;
}

/** Libera il modello, il contesto e il motore. */
void liberaMotore(Motore * motore) {
    if (motore == nullptr) {
        return;
    }
    llama_sampler_free(motore->campionatore);
    llama_free(motore->contesto);
    llama_model_free(motore->modello);
    delete motore;
}

/**
 * Genera la risposta al prompt, già composto con il formato di chat del modello.
 * @param massimoToken Quanti token scrivere al più.
 * @param avanzamento Riceve il testo scritto finora dopo ogni passo e risponde false per fermare la generazione.
 * @param testo Riceve il testo generato, anche parziale se la generazione è stata fermata.
 * @return false se il prompt non entra nel contesto o llama.cpp non riesce a elaborarlo.
 */
bool generaTesto(Motore & motore, const std::string & prompt, size_t massimoToken,
        const std::function<bool(const std::string &)> & avanzamento, std::string & testo) {
    motore.fermato.store(false);
    llama_context * contesto = motore.contesto;
    const llama_vocab * vocabolario = llama_model_get_vocab(motore.modello);
    llama_memory_t memoria = llama_get_memory(contesto);
    llama_memory_clear(memoria, true);
    testo.clear();

    std::vector<llama_token> storia = tokenizza(vocabolario, prompt);
    const size_t tokenPrompt = storia.size();
    const auto lunghezzaContesto = static_cast<size_t>(llama_n_ctx(contesto));
    if (tokenPrompt + LUNGHEZZA_BOZZA + 1 >= lunghezzaContesto) {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "Prompt di %zu token, contesto di %zu", tokenPrompt,
                lunghezzaContesto);
        return false;
    }
    // Anche la risposta deve entrare nel contesto, con lo spazio per l'ultima bozza
    const size_t massimo = std::min(massimoToken, lunghezzaContesto - tokenPrompt - LUNGHEZZA_BOZZA - 1);

    const auto inizio = std::chrono::steady_clock::now();
    for (size_t i = 0; i < tokenPrompt; i += TOKEN_PER_BLOCCO) {
        const auto quanti = static_cast<int32_t>(std::min<size_t>(TOKEN_PER_BLOCCO, tokenPrompt - i));
        if (llama_decode(contesto, llama_batch_get_one(storia.data() + i, quanti)) != 0) {
            __android_log_write(ANDROID_LOG_ERROR, TAG, "Lettura del prompt non riuscita o fermata");
            return false;
        }
    }
    const auto lettoPrompt = std::chrono::steady_clock::now();

    llama_batch blocco = llama_batch_init(static_cast<int32_t>(LUNGHEZZA_BOZZA + 1), 0, 1);
    auto posizione = static_cast<llama_pos>(tokenPrompt);
    llama_token ultimo = llama_sampler_sample(motore.campionatore, contesto, -1);
    size_t scritti = 0;
    size_t proposti = 0;
    size_t accettati = 0;
    bool finito = false;

    while (!finito && !llama_vocab_is_eog(vocabolario, ultimo)) {
        testo += testoToken(vocabolario, ultimo);
        if (++scritti >= massimo || !avanzamento(testo.substr(0, lunghezzaUtf8Completa(testo)))) {
            break;
        }

        // L'ultimo token più la bozza, in un passo solo: il modello dà il suo token dopo ciascuno di essi
        std::vector<llama_token> bozza = bozzaDaStoria(storia, ultimo);
        bozza.resize(std::min(bozza.size(), massimo - scritti));
        blocco.n_tokens = 0;
        for (size_t i = 0; i <= bozza.size(); ++i) {
            const int32_t k = blocco.n_tokens++;
            blocco.token[k] = i == 0 ? ultimo : bozza[i - 1];
            blocco.pos[k] = posizione + static_cast<llama_pos>(i);
            blocco.n_seq_id[k] = 1;
            blocco.seq_id[k][0] = 0;
            blocco.logits[k] = true;
        }
        if (llama_decode(contesto, blocco) != 0) {
            break;
        }
        storia.push_back(ultimo);
        ++posizione;
        proposti += bozza.size();

        // I token della bozza valgono finché coincidono con quelli che il modello avrebbe scelto da sé
        llama_token prossimo = ultimo;
        for (size_t i = 0; i <= bozza.size(); ++i) {
            const llama_token scelto = llama_sampler_sample(motore.campionatore, contesto, static_cast<int32_t>(i));
            if (i == bozza.size() || scelto != bozza[i]) {
                prossimo = scelto;
                break;
            }
            ++accettati;
            if (llama_vocab_is_eog(vocabolario, scelto)) {
                finito = true;
                break;
            }
            testo += testoToken(vocabolario, scelto);
            storia.push_back(scelto);
            ++posizione;
            if (++scritti >= massimo) {
                finito = true;
                break;
            }
        }
        // Dal contesto escono i token della bozza scartati
        llama_memory_seq_rm(memoria, 0, posizione, -1);
        ultimo = prossimo;
    }
    llama_batch_free(blocco);
    testo.resize(lunghezzaUtf8Completa(testo));

    const auto fine = std::chrono::steady_clock::now();
    const double secondiPrompt = std::chrono::duration<double>(lettoPrompt - inizio).count();
    const double secondiRisposta = std::chrono::duration<double>(fine - lettoPrompt).count();
    __android_log_print(ANDROID_LOG_INFO, TAG,
            "Prompt: %zu token in %.1f s (%.1f/s). Risposta: %zu token in %.1f s (%.1f/s), bozze accettate %zu su %zu",
            tokenPrompt, secondiPrompt, tokenPrompt / secondiPrompt,
            scritti, secondiRisposta, scritti / secondiRisposta, accettati, proposti);
    return true;
}

} // namespace

/**
 * Carica la variante del backend CPU adatta al processore.
 * @return Il nome della libreria caricata, o null se nessuna si può usare.
 */
extern "C" JNIEXPORT jstring JNICALL
Java_io_github_donatogranvilla_poldokeyboard_localai_llama_LlamaNative_caricaVarianteCpu(
        JNIEnv * env, jobject /* thiz */) {
    const char * variante = preparaBackend();
    return variante != nullptr ? env->NewStringUTF(variante) : nullptr;
}

/**
 * Carica il modello GGUF e prepara un contesto della lunghezza indicata.
 * @return Il puntatore al motore, o 0 se il file non è un modello valido o manca la memoria.
 */
extern "C" JNIEXPORT jlong JNICALL
Java_io_github_donatogranvilla_poldokeyboard_localai_llama_LlamaNative_carica(
        JNIEnv * env, jobject /* thiz */, jstring percorsoModello, jint token, jint thread) {
    const char * percorso = env->GetStringUTFChars(percorsoModello, nullptr);
    Motore * motore = caricaMotore(percorso, token, thread);
    env->ReleaseStringUTFChars(percorsoModello, percorso);
    return reinterpret_cast<jlong>(motore);
}

/**
 * Libera il modello e il contesto.
 */
extern "C" JNIEXPORT void JNICALL
Java_io_github_donatogranvilla_poldokeyboard_localai_llama_LlamaNative_libera(
        JNIEnv * /* env */, jobject /* thiz */, jlong handle) {
    liberaMotore(reinterpret_cast<Motore *>(handle));
}

/**
 * Chiede di interrompere la generazione in corso; si può chiamare da un altro thread.
 */
extern "C" JNIEXPORT void JNICALL
Java_io_github_donatogranvilla_poldokeyboard_localai_llama_LlamaNative_ferma(
        JNIEnv * /* env */, jobject /* thiz */, jlong handle) {
    auto * motore = reinterpret_cast<Motore *>(handle);
    if (motore != nullptr) {
        motore->fermato.store(true);
    }
}

/**
 * Genera la risposta al prompt, già composto con il formato di chat del modello.
 * @param ascoltatore Riceve il testo scritto finora dopo ogni passo e risponde false per fermare la generazione;
 *   può essere null.
 * @return Il testo generato, anche parziale se la generazione è stata fermata; null se il prompt non entra nel
 *   contesto o llama.cpp non riesce a elaborarlo.
 */
extern "C" JNIEXPORT jstring JNICALL
Java_io_github_donatogranvilla_poldokeyboard_localai_llama_LlamaNative_genera(
        JNIEnv * env, jobject /* thiz */, jlong handle, jstring prompt, jint massimoToken, jobject ascoltatore) {
    auto * motore = reinterpret_cast<Motore *>(handle);
    if (motore == nullptr) {
        return nullptr;
    }
    jmethodID metodo = nullptr;
    if (ascoltatore != nullptr) {
        jclass classe = env->GetObjectClass(ascoltatore);
        metodo = env->GetMethodID(classe, "avanzamento", "(Ljava/lang/String;)Z");
        env->DeleteLocalRef(classe);
    }
    auto avanzamento = [env, ascoltatore, metodo](const std::string & parziale) {
        if (metodo == nullptr) {
            return true;
        }
        jstring testo = nuovaStringaUtf8(env, parziale);
        const bool continua = env->CallBooleanMethod(ascoltatore, metodo, testo);
        env->DeleteLocalRef(testo);
        return continua && !env->ExceptionCheck();
    };

    const char * caratteri = env->GetStringUTFChars(prompt, nullptr);
    const std::string testoPrompt = caratteri;
    env->ReleaseStringUTFChars(prompt, caratteri);
    std::string testo;
    const bool riuscito = generaTesto(*motore, testoPrompt, static_cast<size_t>(massimoToken), avanzamento, testo);
    if (!riuscito || env->ExceptionCheck()) {
        return nullptr;
    }
    return nuovaStringaUtf8(env, testo);
}
