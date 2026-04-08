@echo off
:: Nokta Launcher — Windows başlatıcı
set SCRIPT_DIR=%~dp0

java --add-opens java.base/java.lang=ALL-UNNAMED ^
     --add-opens java.base/java.io=ALL-UNNAMED ^
     --add-opens java.base/java.util=ALL-UNNAMED ^
     -jar "%SCRIPT_DIR%nokta-launcher.jar"

if errorlevel 1 (
    echo Java bulunamadi veya hata olustu!
    echo Java 21+ kurun: https://adoptium.net
    pause
)
