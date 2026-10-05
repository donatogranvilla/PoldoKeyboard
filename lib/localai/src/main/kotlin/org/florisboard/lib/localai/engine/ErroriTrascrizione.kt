package org.florisboard.lib.localai.engine

/**
 * Nessun modello è installato: l'utente deve importarne uno dalla gestione dei modelli.
 */
class NessunModelloException :
    IllegalStateException("Nessun modello installato: importane uno dalla gestione dei modelli.")

/**
 * La libreria nativa del motore non è inclusa in questa build dell'app.
 * @param nomeMotore Nome leggibile del motore, ad esempio "whisper.cpp".
 */
class MotoreNonDisponibileException(nomeMotore: String) :
    IllegalStateException("Il motore $nomeMotore non è disponibile in questa build: manca la libreria nativa.")
