# Proguard rules for lib:localai

# 1. Preserve JNI Whisper Native methods and classes
-keep class org.florisboard.lib.localai.whisper.WhisperNative {
    native <methods>;
    *;
}

# 2. Preserve Model serialization data classes
-keep class org.florisboard.lib.localai.model.ModelInfo { *; }
-keep class org.florisboard.lib.localai.engine.ModelloInstallato { *; }
-keep class org.florisboard.lib.localai.engine.SegmentoVoce { *; }
-keep class org.florisboard.lib.localai.engine.OpzioniTrascrizione { *; }
-keep class org.florisboard.lib.localai.engine.RisultatoTrascrizione { *; }

# 3. Preserve hardware profiling and audio decoding
-keep class org.florisboard.lib.localai.audio.AudioDecoder { *; }
-keep class org.florisboard.lib.localai.model.RilevatoreDispositivo { *; }
