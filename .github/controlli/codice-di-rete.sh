#!/usr/bin/env bash
# L'app usa la rete solo per scaricare il catalogo dei modelli, in un unico file: classi di rete nel codice Kotlin o
# Java di qualunque altro file fermano la run (la cartella cpp contiene il submodule di whisper.cpp).
# Uso, dalla radice del repository: .github/controlli/codice-di-rete.sh
set -euo pipefail
permesso=lib/localai/src/main/kotlin/io/github/donatogranvilla/poldokeyboard/localai/model/ScaricatoreCatalogo.kt
rete='java\.net\.(URL|HttpURLConnection|URLConnection|Socket|ServerSocket|DatagramSocket)\b|javax\.net\.ssl|okhttp3|io\.ktor|retrofit2|android\.net\.http|DownloadManager|WebView'
trovati=$(grep -rlE --include='*.kt' --include='*.java' --exclude-dir=cpp "$rete" app/src lib/localai/src \
    | grep -vFx "$permesso" || true)
if [ -n "$trovati" ]; then echo "Codice di rete fuori da $permesso:"; echo "$trovati"; exit 1; fi
