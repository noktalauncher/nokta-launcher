package com.nokta.launcher.core;

import com.google.gson.*;
import okhttp3.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;

public class AssetDownloader {

    private static final String RESOURCE_BASE = "https://resources.download.minecraft.net/";
    private final OkHttpClient http = new OkHttpClient.Builder()
        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .build();
    private final Path gameDir;

    public AssetDownloader(Path gameDir) {
        this.gameDir = gameDir;
    }

    public void downloadAssets(String versionId,
                                VersionManager.ProgressCallback cb) throws Exception {
        Path versionJson = gameDir.resolve("versions")
                                  .resolve(versionId)
                                  .resolve(versionId + ".json");

        JsonObject versionObj = JsonParser.parseString(
            Files.readString(versionJson)).getAsJsonObject();

        String assetIndexId = versionObj.getAsJsonObject("assetIndex")
                                        .get("id").getAsString();

        Path indexFile = gameDir.resolve("assets")
                                .resolve("indexes")
                                .resolve(assetIndexId + ".json");

        if (!Files.exists(indexFile))
            throw new Exception("Asset index bulunamadi, once surumu kurun!");

        JsonObject indexObj = JsonParser.parseString(
            Files.readString(indexFile)).getAsJsonObject();
        JsonObject objects = indexObj.getAsJsonObject("objects");

        Set<Map.Entry<String, JsonElement>> entries = objects.entrySet();
        int total = entries.size();

        cb.update("Asset indirme basliyor (" + total + " dosya)...", 0);

        Path objectsDir = gameDir.resolve("assets").resolve("objects");
        Files.createDirectories(objectsDir);

        java.util.concurrent.ExecutorService pool =
            java.util.concurrent.Executors.newFixedThreadPool(32);
        java.util.concurrent.atomic.AtomicInteger done =
            new java.util.concurrent.atomic.AtomicInteger(0);

        for (Map.Entry<String, JsonElement> entry : entries) {
            JsonObject asset = entry.getValue().getAsJsonObject();
            String hash = asset.get("hash").getAsString();
            String prefix = hash.substring(0, 2);
            Path assetDir  = objectsDir.resolve(prefix);
            Path assetFile = assetDir.resolve(hash);

            if (Files.exists(assetFile)) {
                int n = done.incrementAndGet();
                if (n % 200 == 0)
                    cb.update("Assets: " + n + "/" + total, (int)(n * 100.0 / total));
                continue;
            }

            pool.submit(() -> {
                try {
                    Files.createDirectories(assetDir);
                    String url = RESOURCE_BASE + prefix + "/" + hash;
                    downloadFile(url, assetFile);
                } catch (Exception ignored) {
                } finally {
                    int n = done.incrementAndGet();
                    if (n % 200 == 0)
                        cb.update("Assets: " + n + "/" + total, (int)(n * 100.0 / total));
                }
            });
        }

        pool.shutdown();
        pool.awaitTermination(30, java.util.concurrent.TimeUnit.MINUTES);
        cb.update("Tum assets hazir! (" + total + " dosya)", 100);
    }

    public boolean isAssetsReady(String versionId) {
        try {
            Path versionJson = gameDir.resolve("versions")
                                      .resolve(versionId)
                                      .resolve(versionId + ".json");
            if (!Files.exists(versionJson)) return false;

            JsonObject obj = JsonParser.parseString(
                Files.readString(versionJson)).getAsJsonObject();
            String assetIndexId = obj.getAsJsonObject("assetIndex")
                                     .get("id").getAsString();
            Path indexFile = gameDir.resolve("assets").resolve("indexes")
                                    .resolve(assetIndexId + ".json");
            if (!Files.exists(indexFile)) return false;

            JsonObject indexObj = JsonParser.parseString(
                Files.readString(indexFile)).getAsJsonObject();
            JsonObject objects = indexObj.getAsJsonObject("objects");

            int total = objects.size(), exists = 0;
            Path objectsDir = gameDir.resolve("assets").resolve("objects");
            for (Map.Entry<String, JsonElement> e : objects.entrySet()) {
                String hash = e.getValue().getAsJsonObject()
                               .get("hash").getAsString();
                if (Files.exists(objectsDir.resolve(hash.substring(0, 2))
                                           .resolve(hash))) exists++;
            }
            return exists >= total * 0.9;
        } catch (Exception e) { return false; }
    }

    private void downloadFile(String url, Path dest) throws Exception {
        Request req = new Request.Builder().url(url).build();
        try (Response res = http.newCall(req).execute()) {
            if (!res.isSuccessful()) throw new IOException("HTTP " + res.code());
            try (InputStream in  = res.body().byteStream();
                 OutputStream out = Files.newOutputStream(dest)) {
                byte[] buf = new byte[8192]; int n;
                while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
            }
        }
    }
}
