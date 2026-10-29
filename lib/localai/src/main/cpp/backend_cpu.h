// Caricamento del backend CPU di ggml, comune ai collegamenti JNI di whisper.cpp e llama.cpp.

#pragma once

#include <jni.h>

#include <string>

#include "ggml.h"

/**
 * Carica, una volta sola per tutta l'app, la variante del backend CPU più veloce tra quelle che il processore
 * supporta: ggml è uno solo per whisper.cpp e llama.cpp, e una variante caricata due volte comparirebbe due volte
 * tra i dispositivi.
 * @param tag L'etichetta di logcat della libreria che chiede il caricamento.
 * @return Il nome della variante caricata, come "libggml-cpu-android_armv8.2_2.so", o nullptr se nessuna si può usare.
 */
const char * caricaBackendCpu(const char * tag);

/**
 * Inoltra a logcat un messaggio di ggml, whisper.cpp o llama.cpp, che altrimenti finirebbe su stderr e andrebbe perso.
 * @param datiUtente L'etichetta di logcat, come const char *.
 */
void scriviLog(enum ggml_log_level livello, const char * testo, void * datiUtente);

/**
 * Crea una stringa Java dai byte UTF-8 prodotti dai motori. NewStringUTF accetta solo l'UTF-8 modificato di Java e
 * andrebbe in errore con i caratteri fuori dal piano base, come le emoji.
 */
jstring nuovaStringaUtf8(JNIEnv * env, const std::string & testo);
