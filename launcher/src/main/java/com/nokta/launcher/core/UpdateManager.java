package com.nokta.launcher.core;

import com.nokta.launcher.utils.PathManager;
import com.google.gson.*;
import okhttp3.*;
import java.io.*;
import java.nio.file.*;
import java.util.function.Consumer;

public class UpdateManager {

    public record UpdateInfo(
        String version,
        String downloadUrl,
        String changelog,
        boolean available
    ) {}

    // ── Güncelleme kontrolü (GitHub Releases API) ─────────────────────────
    public static UpdateInfo checkForUpdates() {
        try {
            OkHttpClient http = new OkHttpClient.Builder()
                .connectTimeout(java.time.Duration.ofSeconds(5))
                .readTimeout(java.time.Duration.ofSeconds(5))
                .build();

            Request req = new Request.Builder()
                .url(NokTaConfig.UPDATE_API_URL)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "NoktaLauncher/" + NokTaConfig.LAUNCHER_VERSION)
                .build();

            try (Response res = http.newCall(req).execute()) {
                if (!res.isSuccessful())
                    return noUpdate();

                JsonObject j = JsonParser.parseString(
                    res.body().string()).getAsJsonObject();

                // tag_name: "v1.0.1" → "1.0.1"
                String tag     = j.get("tag_name").getAsString().replaceFirst("^v", "");
                String changes = j.has("body") ? j.get("body").getAsString() : "";

                // Assets içinden platforma uygun dosyayı bul
                String assetName = NokTaConfig.getReleaseAssetName(tag);
                String dlUrl = null;
                JsonArray assets = j.getAsJsonArray("assets");
                for (JsonElement el : assets) {
                    JsonObject asset = el.getAsJsonObject();
                    if (asset.get("name").getAsString().equals(assetName)) {
                        dlUrl = asset.get("browser_download_url").getAsString();
                        break;
                    }
                }

                boolean available = !tag.equals(NokTaConfig.LAUNCHER_VERSION)
                    && dlUrl != null;

                System.out.println(available
                    ? "🆕 Güncelleme var: " + tag + " (" + assetName + ")"
                    : "✅ Launcher güncel: " + NokTaConfig.LAUNCHER_VERSION);

                return new UpdateInfo(tag, dlUrl, changes, available);
            }
        } catch (Exception e) {
            System.out.println("⚠ Güncelleme kontrolü yapılamadı: " + e.getMessage());
            return noUpdate();
        }
    }

    // ── İndir ve kur ─────────────────────────────────────────────────────
    public static void downloadAndInstall(
        String downloadUrl,
        Runnable onComplete,
        Consumer<Integer> onProgress
    ) {
        new Thread(() -> {
            try {
                // İndirme dizini
                Path updateDir = PathManager.getGameDir().resolve("update");
                Files.createDirectories(updateDir);

                // Dosya adını URL'den al
                String fileName = downloadUrl.substring(downloadUrl.lastIndexOf('/') + 1);
                Path installerPath = updateDir.resolve(fileName);

                // İndir
                OkHttpClient http = new OkHttpClient.Builder()
                    .connectTimeout(java.time.Duration.ofSeconds(10))
                    .readTimeout(java.time.Duration.ofSeconds(120))
                    .build();

                Request req = new Request.Builder()
                    .url(downloadUrl)
                    .header("User-Agent", "NoktaLauncher/" + NokTaConfig.LAUNCHER_VERSION)
                    .build();

                try (Response res = http.newCall(req).execute()) {
                    if (!res.isSuccessful())
                        throw new Exception("İndirme hatası: " + res.code());

                    long total = res.body().contentLength();
                    try (InputStream in  = res.body().byteStream();
                         OutputStream out = Files.newOutputStream(installerPath)) {
                        byte[] buf = new byte[8192];
                        long downloaded = 0; int n;
                        while ((n = in.read(buf)) != -1) {
                            out.write(buf, 0, n);
                            downloaded += n;
                            if (total > 0 && onProgress != null)
                                onProgress.accept((int)(100.0 * downloaded / total));
                        }
                    }
                }

                System.out.println("✅ İndirildi: " + installerPath);

                // Platform'a göre kur
                launchInstaller(installerPath);

                if (onComplete != null) onComplete.run();

            } catch (Exception e) {
                System.out.println("⚠ Güncelleme hatası: " + e.getMessage());
            }
        }, "updater").start();
    }

    // ── Platform'a göre installer çalıştır ───────────────────────────────
    private static void launchInstaller(Path installer) throws Exception {
        String path = installer.toAbsolutePath().toString();
        ProcessBuilder pb;

        if (NokTaConfig.isWindows()) {
            // .exe — sessiz kurulum, mevcut launcher'ın üstüne yazar
            pb = new ProcessBuilder("cmd", "/c", "start", "/wait", path, "/S");
        } else if (NokTaConfig.isMac()) {
            // .dmg — aç, kullanıcı kendi sürükler (standart macOS flow)
            pb = new ProcessBuilder("open", path);
        } else {
            // .deb — dpkg ile kur
            pb = new ProcessBuilder("pkexec", "dpkg", "-i", path);
        }

        pb.inheritIO().start().waitFor();
        System.out.println("✅ Kurulum tamamlandı, launcher yeniden başlatılıyor...");

        // Launcher'ı yeniden başlat
        restartLauncher();
    }

    // ── Launcher'ı yeniden başlat ─────────────────────────────────────────
    private static void restartLauncher() throws Exception {
        if (NokTaConfig.isWindows()) {
            new ProcessBuilder("cmd", "/c",
                "timeout /t 2 && start noktalauncher").start();
        } else if (NokTaConfig.isMac()) {
            new ProcessBuilder("sh", "-c",
                "sleep 2 && open -a NoktaLauncher").start();
        } else {
            new ProcessBuilder("sh", "-c",
                "sleep 2 && noktalauncher").start();
        }
        System.exit(0);
    }

    private static UpdateInfo noUpdate() {
        return new UpdateInfo(NokTaConfig.LAUNCHER_VERSION, null, null, false);
    }

    public static String getCurrentVersion() {
        return NokTaConfig.LAUNCHER_VERSION;
    }
}
