package com.nokta.launcher.utils;

import java.nio.file.*;

/**
 * Nokta Launcher — Merkezi Dizin Yöneticisi
 * 
 * Tüm platformlarda tek bir ana dizin kullanılır:
 *   Windows : %APPDATA%\.nokta-launcher
 *   macOS   : ~/Library/Application Support/nokta-launcher
 *   Linux   : ~/.nokta-launcher
 *
 * Dizin Yapısı:
 *   versions/          → MC sürüm jar ve JSON dosyaları
 *   libraries/         → Tüm kütüphaneler (paylaşımlı)
 *   assets/            → Texture, ses, dil (paylaşımlı)
 *   screenshots/       → TÜM sürümlerden ekran görüntüleri (paylaşımlı)
 *   saves/             → TÜM sürümlerden kayıt dosyaları (paylaşımlı)
 *   resourcepacks/     → Kaynak paketleri (paylaşımlı)
 *   shaderpacks/       → Shader paketleri (paylaşımlı)
 *   mods/{version}/    → Sürüme özel modlar (1.20.1, 1.21.4 vb.)
 *   overlay-mods/      → Nokta overlay mod jar'ları (fabric, forge vb.)
 *   config/            → Mod config dosyaları (paylaşımlı)
 *   logs/              → Oyun log dosyaları
 *   crash-reports/     → Crash raporları
 *   profiles/          → Kullanıcı profilleri
 *   nokta-agent.jar    → Java agent (tüm sürümler)
 */
public class PathManager {

    // ── Ana dizin (OS'a göre) ─────────────────────────────────────────────────
    public static Path getGameDir() {
        String os = System.getProperty("os.name").toLowerCase();
        Path home = Path.of(System.getProperty("user.home"));
        if (os.contains("win")) {
            String appData = System.getenv("APPDATA");
            if (appData != null) return Path.of(appData, ".nokta-launcher");
            return home.resolve("AppData/Roaming/.nokta-launcher");
        } else if (os.contains("mac")) {
            return home.resolve("Library/Application Support/nokta-launcher");
        } else {
            return home.resolve(".nokta-launcher");
        }
    }

    // ── Sürüme özel modlar ────────────────────────────────────────────────────
    // Örnek: mods/1.21.4/ — sadece o sürüme ait modlar
    public static Path getModsDir(String mcVersion) {
        return getGameDir().resolve("mods").resolve(mcVersion);
    }

    // ── Nokta overlay mod jar'ları ────────────────────────────────────────────
    // Örnek: overlay-mods/fabric-1.21.4.jar
    public static Path getOverlayModsDir() {
        return getGameDir().resolve("overlay-mods");
    }

    // ── Paylaşımlı dizinler ───────────────────────────────────────────────────
    public static Path getScreenshotsDir()   { return getGameDir().resolve("screenshots"); }
    public static Path getSavesDir()         { return getGameDir().resolve("saves"); }
    public static Path getResourcePacksDir() { return getGameDir().resolve("resourcepacks"); }
    public static Path getShaderPacksDir()   { return getGameDir().resolve("shaderpacks"); }
    public static Path getConfigDir()        { return getGameDir().resolve("config"); }
    public static Path getLogsDir()          { return getGameDir().resolve("logs"); }

    // ── Agent jar yolu ────────────────────────────────────────────────────────
    public static Path getAgentJar() {
        return getGameDir().resolve("nokta-agent.jar");
    }

    // ── Overlay mod jar yolu (loader ve versiyon'a göre) ─────────────────────
    public static Path getOverlayModJar(String loader, String mcVersion) {
        // Örnek: overlay-mods/fabric-1.21.4.jar
        String fileName = loader.toLowerCase() + "-" + mcVersion + ".jar";
        return getOverlayModsDir().resolve(fileName);
    }

    // ── Tüm gerekli dizinleri oluştur ─────────────────────────────────────────
    public static void createDirs() throws Exception {
        Path base = getGameDir();

        // Temel dizinler
        Files.createDirectories(base.resolve("versions"));
        Files.createDirectories(base.resolve("libraries"));
        Files.createDirectories(base.resolve("assets/indexes"));
        Files.createDirectories(base.resolve("assets/objects"));
        Files.createDirectories(base.resolve("profiles"));
        Files.createDirectories(base.resolve("logs"));

        // Paylaşımlı dizinler
        Files.createDirectories(getScreenshotsDir());
        Files.createDirectories(getSavesDir());
        Files.createDirectories(getResourcePacksDir());
        Files.createDirectories(getShaderPacksDir());
        Files.createDirectories(getConfigDir());
        Files.createDirectories(getOverlayModsDir());

        // Mods ana dizini
        Files.createDirectories(base.resolve("mods"));

        System.out.println("📁 Oyun dizini: " + base);
    }

    // ── MC args için game_directory ───────────────────────────────────────────
    // MC'ye verilen --gameDir argümanı — paylaşımlı dizinler için ana dizin
    public static Path getMCGameDir() {
        return getGameDir();
    }
}
