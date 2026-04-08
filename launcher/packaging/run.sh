#!/bin/bash
# Nokta Launcher — Evrensel başlatıcı (Linux/macOS)
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JAR="$SCRIPT_DIR/nokta-launcher.jar"

# Java kontrolü
if ! command -v java &> /dev/null; then
    echo "❌ Java bulunamadı! Lütfen Java 21+ kurun."
    echo "   Ubuntu: sudo apt install openjdk-21-jre"
    echo "   Windows: https://adoptium.net"
    exit 1
fi

JAVA_VER=$(java -version 2>&1 | head -1 | cut -d'"' -f2 | cut -d'.' -f1)
if [ "$JAVA_VER" -lt 21 ] 2>/dev/null; then
    echo "⚠ Java 21+ gerekli! Mevcut: $JAVA_VER"
fi

java --add-opens java.base/java.lang=ALL-UNNAMED \
     --add-opens java.base/java.io=ALL-UNNAMED \
     --add-opens java.base/java.util=ALL-UNNAMED \
     -jar "$JAR" "$@"
