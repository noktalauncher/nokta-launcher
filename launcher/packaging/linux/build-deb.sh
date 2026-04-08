#!/bin/bash
set -e
echo "🔨 Nokta Launcher .deb paketi oluşturuluyor..."
cd ~/nokta-launcher
./gradlew jar

JAR="build/libs/nokta-launcher.jar"
VERSION="1.0.0"
PKG_NAME="nokta-launcher"
DEB_DIR="build/deb"
JAVA_PATH="/home/enes/.sdkman/candidates/java/current/bin/java"

rm -rf "$DEB_DIR"
mkdir -p "$DEB_DIR/DEBIAN"
mkdir -p "$DEB_DIR/usr/local/bin"
mkdir -p "$DEB_DIR/usr/share/$PKG_NAME"
mkdir -p "$DEB_DIR/usr/share/applications"

cp "$JAR" "$DEB_DIR/usr/share/$PKG_NAME/"

cat > "$DEB_DIR/usr/local/bin/nokta-launcher" << SCRIPT
#!/bin/bash
# Java yollarını dene
for JAVA_BIN in \
    "$JAVA_PATH" \
    "\$HOME/.sdkman/candidates/java/current/bin/java" \
    "/usr/bin/java" \
    "\$(which java 2>/dev/null)"; do
    if [ -x "\$JAVA_BIN" ]; then
        exec "\$JAVA_BIN" \\
            --add-opens java.base/java.lang=ALL-UNNAMED \\
            --add-opens java.base/java.io=ALL-UNNAMED \\
            --add-opens java.base/java.util=ALL-UNNAMED \\
            -jar /usr/share/nokta-launcher/nokta-launcher.jar "\$@"
        break
    fi
done
echo "❌ Java 21 bulunamadı!"
SCRIPT
chmod +x "$DEB_DIR/usr/local/bin/nokta-launcher"

cat > "$DEB_DIR/usr/share/applications/nokta-launcher.desktop" << DESKTOP
[Desktop Entry]
Name=Nokta Launcher
Comment=Minecraft Launcher
Exec=nokta-launcher
Icon=application-games
Terminal=false
Type=Application
Categories=Game;
DESKTOP

cat > "$DEB_DIR/DEBIAN/control" << CONTROL
Package: $PKG_NAME
Version: $VERSION
Section: games
Priority: optional
Architecture: amd64
Maintainer: Nokta Dev <dev@nokta.dev>
Description: Nokta Minecraft Launcher
 Modern ve hızlı Minecraft launcher.
CONTROL

dpkg-deb --build "$DEB_DIR" "build/${PKG_NAME}_${VERSION}_amd64.deb"
echo "✅ Hazır: build/${PKG_NAME}_${VERSION}_amd64.deb"
