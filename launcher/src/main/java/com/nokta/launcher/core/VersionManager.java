package com.nokta.launcher.core;

import com.google.gson.*;
import okhttp3.*;
import java.io.*;
import java.net.URL;
import java.nio.file.*;
import java.util.*;

public class VersionManager {

    private static final String VERSION_MANIFEST =
        "https://launchermeta.mojang.com/mc/game/version_manifest_v2.json";

    private final Path gameDir;
    private final OkHttpClient http = new OkHttpClient();

    public VersionManager(Path gameDir) {
        this.gameDir = gameDir;
    }

    // Mevcut sürüm listesini Mojang'dan çek
    public List<MCVersion> fetchVersionList() throws Exception {
        Request req = new Request.Builder().url(VERSION_MANIFEST).build();
        try (Response res = http.newCall(req).execute()) {
            String body = res.body().string();
            JsonObject json = JsonParser.parseString(body).getAsJsonObject();
            JsonArray versions = json.getAsJsonArray("versions");

            List<MCVersion> list = new ArrayList<>();
            for (JsonElement el : versions) {
                JsonObject v = el.getAsJsonObject();
                String id   = v.get("id").getAsString();
                String type = v.get("type").getAsString();
                String url  = v.get("url").getAsString();
                list.add(new MCVersion(id, type, url));
            }
            return list;
        }
    }

    // Sürümü indir
    public void downloadVersion(String versionId,
                                ProgressCallback progress) throws Exception {
        // 1) Sürüm manifest'ini indir
        List<MCVersion> versions = fetchVersionList();
        MCVersion target = versions.stream()
            .filter(v -> v.id.equals(versionId))
            .findFirst()
            .orElseThrow(() -> new Exception("Sürüm bulunamadı: " + versionId));

        progress.update("Sürüm bilgisi alınıyor...", 5);

        Request req = new Request.Builder().url(target.url).build();
        String versionJson;
        try (Response res = http.newCall(req).execute()) {
            versionJson = res.body().string();
        }

        // 2) Klasörleri oluştur
        Path versionDir = gameDir.resolve("versions").resolve(versionId);
        Path librariesDir = gameDir.resolve("libraries");
        Path assetsDir = gameDir.resolve("assets");
        Files.createDirectories(versionDir);
        Files.createDirectories(librariesDir);
        Files.createDirectories(assetsDir);

        // 3) version.json kaydet
        Path versionJsonPath = versionDir.resolve(versionId + ".json");
        Files.writeString(versionJsonPath, versionJson);
        progress.update("Version JSON kaydedildi.", 10);

        JsonObject versionObj = JsonParser.parseString(versionJson).getAsJsonObject();

        // 4) Client JAR indir
        JsonObject downloads = versionObj.getAsJsonObject("downloads");
        JsonObject client = downloads.getAsJsonObject("client");
        String clientUrl = client.get("url").getAsString();
        Path clientJar = versionDir.resolve(versionId + ".jar");
        if (!Files.exists(clientJar)) {
            progress.update("Client JAR indiriliyor...", 20);
            downloadFile(clientUrl, clientJar);
        }
        progress.update("Client JAR hazır.", 40);

        // 5) Kütüphaneleri indir
        JsonArray libraries = versionObj.getAsJsonArray("libraries");
        int libCount = libraries.size();
        int i = 0;
        for (JsonElement libEl : libraries) {
            i++;
            JsonObject lib = libEl.getAsJsonObject();
            if (!lib.has("downloads")) continue;
            JsonObject libDownloads = lib.getAsJsonObject("downloads");
            if (!libDownloads.has("artifact")) continue;
            JsonObject artifact = libDownloads.getAsJsonObject("artifact");
            String libPath = artifact.get("path").getAsString();
            String libUrl  = artifact.get("url").getAsString();
            Path libFile   = librariesDir.resolve(libPath);
            if (!Files.exists(libFile)) {
                Files.createDirectories(libFile.getParent());
                downloadFile(libUrl, libFile);
            }
            int pct = 40 + (int)((i / (double) libCount) * 40);
            progress.update("Kütüphane: " + lib.get("name").getAsString(), pct);
        }
        progress.update("Tüm kütüphaneler hazır.", 80);

        // 6) Asset index indir
        JsonObject assetIndex = versionObj.getAsJsonObject("assetIndex");
        String assetIndexUrl = assetIndex.get("url").getAsString();
        String assetIndexId  = assetIndex.get("id").getAsString();
        Path indexDir = assetsDir.resolve("indexes");
        Files.createDirectories(indexDir);
        Path indexFile = indexDir.resolve(assetIndexId + ".json");
        if (!Files.exists(indexFile)) {
            progress.update("Asset index indiriliyor...", 85);
            downloadFile(assetIndexUrl, indexFile);
        }

        progress.update("Kurulum tamamlandı! ✅", 100);
    }

    // Dosyanın kurulu olup olmadığını kontrol et
    public boolean isInstalled(String versionId) {
        Path jar = gameDir
            .resolve("versions")
            .resolve(versionId)
            .resolve(versionId + ".jar");
        return Files.exists(jar);
    }

    // Dosya indirme yardımcısı
    private void downloadFile(String url, Path dest) throws Exception {
        Request req = new Request.Builder().url(url).build();
        try (Response res = http.newCall(req).execute()) {
            if (!res.isSuccessful()) throw new Exception("İndirme hatası: " + url);
            try (InputStream in = res.body().byteStream();
                 OutputStream out = Files.newOutputStream(dest)) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
            }
        }
    }

    // Sürüm modeli
    public static class MCVersion {
        public final String id, type, url;
        public MCVersion(String id, String type, String url) {
            this.id = id; this.type = type; this.url = url;
        }
        @Override public String toString() { return id; }
    }

    // İlerleme geri çağırma arayüzü
    public interface ProgressCallback {
        void update(String message, int percent);
    }
}
