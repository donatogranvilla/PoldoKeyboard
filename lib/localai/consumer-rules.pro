# Proguard rules for lib:localai

# 1. Preserve JNI Whisper Native methods and classes
-keep class io.github.donatogranvilla.poldokeyboard.localai.whisper.WhisperNative {
    native <methods>;
    *;
}

# 1b. sherpa-onnx: la libreria nativa legge per nome i campi delle classi di configurazione via JNI
-keep class com.k2fsa.sherpa.onnx.** { *; }

# 2. Preserve Model serialization data classes
-keep class io.github.donatogranvilla.poldokeyboard.localai.model.ModelInfo { *; }
-keep class io.github.donatogranvilla.poldokeyboard.localai.engine.ModelloInstallato { *; }
-keep class io.github.donatogranvilla.poldokeyboard.localai.engine.SegmentoVoce { *; }
-keep class io.github.donatogranvilla.poldokeyboard.localai.engine.OpzioniTrascrizione { *; }
-keep class io.github.donatogranvilla.poldokeyboard.localai.engine.RisultatoTrascrizione { *; }

# 3. Preserve hardware profiling and audio decoding
-keep class io.github.donatogranvilla.poldokeyboard.localai.audio.AudioDecoder { *; }
-keep class io.github.donatogranvilla.poldokeyboard.localai.model.RilevatoreDispositivo { *; }
