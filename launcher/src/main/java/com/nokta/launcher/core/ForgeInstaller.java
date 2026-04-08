package com.nokta.launcher.core;

import com.google.gson.*;
import okhttp3.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * Forge ve NeoForge installer.
 * Forge Maven'dan installer jar'ı indirir ve çalıştırır.
 */
public class ForgeInstaller {

    private static final String FORGE_META =
        "https://files.minecraftforge.net/net/minecraftforge/forge/promotions_slim.json";
    private static final String NEOFORGE_META =
        "https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml";

    private final Path gameDir;
    private final OkHttpClient http = new OkHttpClient.Builder()
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(300, java.util.concurrent.TimeUnit.SECONDS)
        .build();

    public ForgeInstaller(Path gameDir) {
        this.gameDir = gameDir;
    }

    // ── Forge ────────────────────────────────────────────────────────────────

    /**
     * Forge'u kur. Önce önerilen versiyonu bul, sonra indir ve çalıştır.
     * @return Forge version ID (örn: "1.20.1-forge-47.3.0")
     */
    public String installForge(String mcVersion, ProgressCallback cb) throws Exception {
        cb.update("Forge sürümü aranıyor...", 5);

        // Önerilen Forge sürümünü bul
        String forgeVersion = getRecommendedForgeVersion(mcVersion);
        if (forgeVersion == null) {
            throw new Exception("Forge " + mcVersion + " için bulunamadı!");
        }

        String forgeFullVersion = mcVersion + "-" + forgeVersion;
        String forgeId = mcVersion + "-forge-" + forgeVersion;

        // Zaten kurulu mu?
        Path versionDir = gameDir.resolve("versions").resolve(forgeId);
        if (Files.exists(versionDir.resolve(forgeId + ".json"))) {
            cb.update("Forge zaten kurulu: " + forgeId, 100);
            return forgeId;
        }

        cb.update("Forge indiriliyor: " + forgeFullVersion, 10);

        // Forge installer jar'ını indir
        String installerUrl = "https://maven.minecraftforge.net/net/minecraftforge/forge/"
            + forgeFullVersion + "/forge-" + forgeFullVersion + "-installer.jar";

        Path installerJar = gameDir.resolve("downloads")
            .resolve("forge-" + forgeFullVersion + "-installer.jar");
        Files.createDirectories(installerJar.getParent());

        downloadFile(installerUrl, installerJar, cb, 10, 60);

        cb.update("Forge kuruluyor...", 60);

        // Installer'ı çalıştır
        String javaPath = ProcessHandle.current().info().command().orElse("java");
        ProcessBuilder pb = new ProcessBuilder(
            javaPath, "-jar", installerJar.toAbsolutePath().toString(),
            "--installClient", gameDir.toAbsolutePath().toString()
        );
        pb.redirectErrorStream(true);
        Process proc = pb.start();

        // Output'u oku
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(proc.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println("[Forge] " + line);
                cb.update(line.length() > 50 ? line.substring(0, 50) + "..." : line, 70);
            }
        }

        int exit = proc.waitFor();
        if (exit != 0) throw new Exception("Forge installer hata kodu: " + exit);

        cb.update("Forge kuruldu: " + forgeId, 100);
        return forgeId;
    }

    private String getRecommendedForgeVersion(String mcVersion) {
        try {
            Request req = new Request.Builder().url(FORGE_META).build();
            try (Response res = http.newCall(req).execute()) {
                JsonObject json = JsonParser.parseString(res.body().string())
                    .getAsJsonObject();
                JsonObject promos = json.getAsJsonObject("promos");
                // Önce recommended, yoksa latest
                String key = mcVersion + "-recommended";
                if (promos.has(key)) return promos.get(key).getAsString();
                key = mcVersion + "-latest";
                if (promos.has(key)) return promos.get(key).getAsString();
            }
        } catch (Exception e) {
            System.out.println("Forge meta hatası: " + e.getMessage());
        }
        return null;
    }

    // ── NeoForge ─────────────────────────────────────────────────────────────

    public String installNeoForge(String mcVersion, ProgressCallback cb) throws Exception {
        cb.update("NeoForge sürümü aranıyor...", 5);

        String neoVersion = getLatestNeoForgeVersion(mcVersion);
        if (neoVersion == null) {
            throw new Exception("NeoForge " + mcVersion + " için bulunamadı!");
        }

        String neoId = "neoforge-" + neoVersion;

        Path versionDir = gameDir.resolve("versions").resolve(neoId);
        if (Files.exists(versionDir.resolve(neoId + ".json"))) {
            cb.update("NeoForge zaten kurulu: " + neoId, 100);
            return neoId;
        }

        cb.update("NeoForge indiriliyor: " + neoVersion, 10);

        String installerUrl = "https://maven.neoforged.net/releases/net/neoforged/neoforge/"
            + neoVersion + "/neoforge-" + neoVersion + "-installer.jar";

        Path installerJar = gameDir.resolve("downloads")
            .resolve("neoforge-" + neoVersion + "-installer.jar");
        Files.createDirectories(installerJar.getParent());

        downloadFile(installerUrl, installerJar, cb, 10, 60);

        cb.update("NeoForge kuruluyor...", 60);

        String javaPath = ProcessHandle.current().info().command().orElse("java");
        ProcessBuilder pb = new ProcessBuilder(
            javaPath, "-jar", installerJar.toAbsolutePath().toString(),
            "--installClient", gameDir.toAbsolutePath().toString()
        );
        pb.redirectErrorStream(true);
        Process proc = pb.start();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(proc.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println("[NeoForge] " + line);
                cb.update(line.length() > 50 ? line.substring(0, 50) + "..." : line, 70);
            }
        }

        int exit = proc.waitFor();
        if (exit != 0) throw new Exception("NeoForge installer hata kodu: " + exit);

        cb.update("NeoForge kuruldu: " + neoId, 100);
        return neoId;
    }

    private String getLatestNeoForgeVersion(String mcVersion) {
        try {
            // mcVersion: "1.21.1" → NeoForge: "21.1.x"
            String[] parts = mcVersion.split("\\.");
            if (parts.length < 2) return null;
            String neoPrefix = parts[1] + "." + (parts.length > 2 ? parts[2] : "1");

            Request req = new Request.Builder().url(NEOFORGE_META).build();
            try (Response res = http.newCall(req).execute()) {
                String xml = res.body().string();
                // En son versiyonu bul
                String latest = null;
                for (String line : xml.split("\\n")) {
                    if (line.contains("<version>") && line.contains(neoPrefix)) {
                        latest = line.trim()
                            .replace("<version>", "")
                            .replace("</version>", "");
                    }
                }
                return latest;
            }
        } catch (Exception e) {
            System.out.println("NeoForge meta hatası: " + e.getMessage());
        }
        return null;
    }

    // ── Yardımcı ─────────────────────────────────────────────────────────────

    private void downloadFile(String url, Path dest,
                              ProgressCallback cb, int startPct, int endPct)
            throws Exception {
        Request req = new Request.Builder().url(url).build();
        try (Response res = http.newCall(req).execute()) {
            if (!res.isSuccessful())
                throw new Exception("İndirme hatası: " + res.code() + " " + url);
            long total = res.body().contentLength();
            try (InputStream in = res.body().byteStream();
                 OutputStream out = Files.newOutputStream(dest)) {
                byte[] buf = new byte[8192];
                long downloaded = 0;
                int n;
                while ((n = in.read(buf)) != -1) {
                    out.write(buf, 0, n);
                    downloaded += n;
                    if (total > 0) {
                        int pct = startPct + (int)((downloaded * (endPct - startPct)) / total);
                        cb.update("İndiriliyor... " + (downloaded / 1024) + " KB", pct);
                    }
                }
            }
        }
    }

    public interface ProgressCallback {
        void update(String msg, int pct);
    }
}
