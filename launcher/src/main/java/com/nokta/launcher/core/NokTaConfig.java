package com.nokta.launcher.core;

/**
 * Nokta Launcher — Merkezi Konfigürasyon
 * Production'a geçerken bu dosyadaki URL'leri güncelle.
 */
public class NokTaConfig {

    // ── API ──────────────────────────────────────────────────────────────────
    // Geliştirme: http://localhost:3000
    // Production: https://api.noktalauncher.com (deploy edince değiştir)
    public static final String API_BASE = System.getProperty(
        "nokta.api.url",
        System.getenv("NOKTA_API_URL") != null
            ? System.getenv("NOKTA_API_URL")
            : "http://localhost:3000"
    );

    public static final String API_KEY = System.getProperty(
        "nokta.api.key",
        System.getenv("NOKTA_API_KEY") != null
            ? System.getenv("NOKTA_API_KEY")
            : "nokta-super-secret-2026"
    );

    // ── Launcher ─────────────────────────────────────────────────────────────
    public static final String LAUNCHER_VERSION = "1.0.0";
    public static final String LAUNCHER_NAME    = "Nokta Launcher";

    // ── Nokta Agent ──────────────────────────────────────────────────────────
    // Launcher kurulumunda ~/.nokta-launcher/nokta-agent.jar olarak gelir
    public static final String AGENT_JAR_NAME = "nokta-agent.jar";

    // ── GitHub Releases (Auto-Update) ─────────────────────────────────────
    // GitHub repo adın: https://github.com/KULLANICI_ADI/REPO_ADI
    // Örnek: "noktalauncher/nokta-launcher"
    public static final String GITHUB_REPO     = "noktalauncher/nokta-launcher";
    public static final String UPDATE_API_URL  =
        "https://api.github.com/repos/" + GITHUB_REPO + "/releases/latest";

    // ── Platform tespiti ─────────────────────────────────────────────────
    public static final String OS = System.getProperty("os.name").toLowerCase();
    public static boolean isWindows() { return OS.contains("win"); }
    public static boolean isMac()     { return OS.contains("mac"); }
    public static boolean isLinux()   { return OS.contains("nix") || OS.contains("nux"); }

    // Asset adı GitHub Release'deki dosya adıyla eşleşmeli
    // nokta-launcher-1.0.1-linux.deb / windows.exe / macos.dmg
    public static String getReleaseAssetName(String version) {
        if (isWindows()) return "nokta-launcher-" + version + "-windows.exe";
        if (isMac())     return "nokta-launcher-" + version + "-macos.dmg";
        return "nokta-launcher-" + version + "-linux.deb";
    }
}
