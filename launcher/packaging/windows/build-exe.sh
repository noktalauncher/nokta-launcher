#!/bin/bash
set -e

echo "🔨 Nokta Launcher Windows paketi oluşturuluyor..."

cd ~/nokta-launcher
./gradlew jar

JAR="build/libs/nokta-launcher.jar"
VERSION="1.0.0"

# Launch4j ile Windows .exe oluştur (Linux'tan cross-build)
if ! command -v launch4j &> /dev/null; then
    echo "📥 Launch4j indiriliyor..."
    wget -q "https://sourceforge.net/projects/launch4j/files/launch4j-3/3.50/launch4j-3.50-linux-x64.tgz" \
        -O /tmp/launch4j.tgz
    tar -xzf /tmp/launch4j.tgz -C ~/
fi

mkdir -p build/windows

# Launch4j config
cat > /tmp/launch4j-config.xml << XML
<launch4jConfig>
  <dontWrapJar>false</dontWrapJar>
  <headerType>gui</headerType>
  <jar>$(pwd)/$JAR</jar>
  <outfile>$(pwd)/build/windows/NektaLauncher.exe</outfile>
  <errTitle>Nokta Launcher</errTitle>
  <cmdLine></cmdLine>
  <chdir>.</chdir>
  <priority>normal</priority>
  <downloadUrl>https://www.java.com/download/</downloadUrl>
  <supportUrl></supportUrl>
  <jre>
    <path></path>
    <minVersion>21</minVersion>
    <jdkPreference>preferJre</jdkPreference>
    <runtimeBits>64</runtimeBits>
    <opt>--add-opens java.base/java.lang=ALL-UNNAMED
--add-opens java.base/java.io=ALL-UNNAMED</opt>
  </jre>
  <versionInfo>
    <fileVersion>1.0.0.0</fileVersion>
    <txtFileVersion>$VERSION</txtFileVersion>
    <fileDescription>Nokta Minecraft Launcher</fileDescription>
    <copyright>2024 Nokta Dev</copyright>
    <productVersion>1.0.0.0</productVersion>
    <txtProductVersion>$VERSION</txtProductVersion>
    <productName>Nokta Launcher</productName>
    <internalName>nokta-launcher</internalName>
    <originalFilename>NektaLauncher.exe</originalFilename>
  </versionInfo>
</launch4jConfig>
XML

~/launch4j/launch4j /tmp/launch4j-config.xml
echo "✅ Windows .exe hazır: build/windows/NektaLauncher.exe"
