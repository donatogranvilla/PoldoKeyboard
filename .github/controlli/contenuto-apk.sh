#!/usr/bin/env bash
# Le librerie dei due motori, whisper.cpp e llama.cpp, devono esserci tutte. Le varianti del backend CPU si caricano a
# runtime: se una manca i motori non partono su quei processori, quindi la run si ferma prima di pubblicare l'APK. Controlla anche il modello del VAD e che le librerie native siano senza i
# simboli di debug (il passo di strip non ha trovato la NDK e l'APK cresce di 10 MB).
# Uso: .github/controlli/contenuto-apk.sh percorso/dell.apk
set -euo pipefail
apk=$1
unzip -l "$apk" 'lib/arm64-v8a/*'
for lib in whisper_jni whisper llama_jni llama backend_cpu ggml ggml-base \
    ggml-cpu-android_armv8.0_1 ggml-cpu-android_armv8.2_1 ggml-cpu-android_armv8.2_2 \
    ggml-cpu-android_armv8.6_1 ggml-cpu-android_armv9.0_1 ggml-cpu-android_armv9.2_1 \
    ggml-cpu-android_armv9.2_2 sherpa-onnx-jni; do
  unzip -Z1 "$apk" | grep -Fxq "lib/arm64-v8a/lib$lib.so" || { echo "Manca lib$lib.so nell'APK"; exit 1; }
done
unzip -Z1 "$apk" | grep -Fxq "assets/vad/ggml-silero-v6.2.0.bin" || { echo "Manca il modello del VAD nell'APK"; exit 1; }
libreria=$(mktemp)
unzip -p "$apk" lib/arm64-v8a/libwhisper.so > "$libreria"
if readelf -S "$libreria" | grep -q '.debug_info'; then echo "Librerie native con i simboli di debug"; exit 1; fi
