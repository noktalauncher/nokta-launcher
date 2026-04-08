package com.nokta.launcher.mods;

import com.google.gson.*;
import okhttp3.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;

public class CurseForgeAPI {

    // CurseForge public API anahtarı (ücretsiz)
   private static final String API_KEY  = "$2a$10$ryUP5GHXp061Um3OYCSDbuC8ZczC7cw7mI4wjAJ5j6.zru3QetWT.";
    private static final String BASE_URL = "https://api.curseforge.com/v1";
    private static final int MINECRAFT_GAME_ID = 432;
    private static final int MODS_CLASS_ID     = 6;

    private final OkHttpClient http = new OkHttpClient();
    private final Gson gson = new Gson();

    // Mod ara
    public List<CFMod> search(String query, String mcVersion,
                               String loader, int limit) throws Exception {
        String loaderType = getLoaderType(loader);
        String url = BASE_URL + "/mods/search" +
            "?gameId=" + MINECRAFT_GAME_ID +
            "&classId=" + MODS_CLASS_ID +
            "&searchFilter=" + query.replace(" ", "%20") +
            "&gameVersion=" + mcVersion +
            (loaderType != null ? "&modLoaderType=" + loaderType : "") +
            "&pageSize=" + limit +
            "&sortField=2&sortOrder=desc";

        Request req = new Request.Builder()
            .url(url)
            .addHeader("x-api-key", API_KEY)
            .addHeader("Accept", "application/json")
            .build();

        try (Response res = http.newCall(req).execute()) {
            if (!res.isSuccessful())
                throw new Exception("CurseForge API hatası: " + res.code());

            JsonObject json = JsonParser.parseString(res.body().string()).getAsJsonObject();
            JsonArray data  = json.getAsJsonArray("data");

            List<CFMod> mods = new ArrayList<>();
            for (JsonElement el : data) {
                JsonObject m = el.getAsJsonObject();
                mods.add(parseMod(m));
            }
            return mods;
        }
    }

    // Modun dosyalarını getir
    public List<CFFile> getFiles(int modId, String mcVersion,
                                  String loader) throws Exception {
        String loaderType = getLoaderType(loader);
        String url = BASE_URL + "/mods/" + modId + "/files" +
            "?gameVersion=" + mcVersion +
            (loaderType != null ? "&modLoaderType=" + loaderType : "") +
            "&pageSize=10";

        Request req = new Request.Builder()
            .url(url)
            .addHeader("x-api-key", API_KEY)
            .addHeader("Accept", "application/json")
            .build();

        try (Response res = http.newCall(req).execute()) {
            if (!res.isSuccessful())
                throw new Exception("Dosya listesi alınamadı: " + res.code());

            JsonObject json = JsonParser.parseString(res.body().string()).getAsJsonObject();
            JsonArray data  = json.getAsJsonArray("data");

            List<CFFile> files = new ArrayList<>();
            for (JsonElement el : data) {
                JsonObject f = el.getAsJsonObject();
                String downloadUrl = f.has("downloadUrl") && !f.get("downloadUrl").isJsonNull()
                    ? f.get("downloadUrl").getAsString() : null;
                if (downloadUrl == null) continue;

                files.add(new CFFile(
                    f.get("id").getAsInt(),
                    f.get("fileName").getAsString(),
                    f.get("displayName").getAsString(),
                    downloadUrl,
                    f.get("fileLength").getAsLong()
                ));
            }
            return files;
        }
    }

    // Mod indir
    public void downloadMod(CFFile file, Path modsDir,
                             DownloadCallback cb) throws Exception {
        Files.createDirectories(modsDir);
        Path dest = modsDir.resolve(file.fileName);

        if (Files.exists(dest)) { cb.done(dest); return; }

        Request req = new Request.Builder()
            .url(file.downloadUrl)
            .addHeader("x-api-key", API_KEY)
            .build();

        try (Response res = http.newCall(req).execute()) {
            if (!res.isSuccessful())
                throw new Exception("İndirme başarısız: " + file.fileName);
            long total = res.body().contentLength();
            try (InputStream in  = res.body().byteStream();
                 OutputStream out = Files.newOutputStream(dest)) {
                byte[] buf = new byte[8192];
                long downloaded = 0; int n;
                while ((n = in.read(buf)) != -1) {
                    out.write(buf, 0, n);
                    downloaded += n;
                    if (total > 0) cb.progress((int)(downloaded * 100 / total));
                }
            }
        }
        cb.done(dest);
    }

    // Loader tipini CurseForge formatına çevir
    private String getLoaderType(String loader) {
        if (loader == null) return null;
        return switch (loader.toLowerCase()) {
            case "forge"    -> "1";
            case "fabric"   -> "4";
            case "quilt"    -> "5";
            case "neoforge" -> "6";
            default         -> null;
        };
    }

    private CFMod parseMod(JsonObject m) {
        String thumbUrl = null;
        if (m.has("logo") && !m.get("logo").isJsonNull()) {
            JsonObject logo = m.getAsJsonObject("logo");
            if (logo.has("thumbnailUrl"))
                thumbUrl = logo.get("thumbnailUrl").getAsString();
        }

        List<String> categories = new ArrayList<>();
        if (m.has("categories")) {
            for (JsonElement c : m.getAsJsonArray("categories"))
                categories.add(c.getAsJsonObject().get("name").getAsString());
        }

        return new CFMod(
            m.get("id").getAsInt(),
            m.get("name").getAsString(),
            m.has("summary") ? m.get("summary").getAsString() : "",
            thumbUrl,
            m.get("downloadCount").getAsLong(),
            m.get("thumbsUpCount").getAsInt(),
            categories
        );
    }

    // ── Veri modelleri ────────────────────────────────────────────────

    public static class CFMod {
        public final int    id;
        public final String name, description, iconUrl;
        public final long   downloads;
        public final int    likes;
        public final List<String> categories;

        public CFMod(int id, String name, String description,
                     String iconUrl, long downloads, int likes,
                     List<String> categories) {
            this.id = id; this.name = name;
            this.description = description; this.iconUrl = iconUrl;
            this.downloads = downloads; this.likes = likes;
            this.categories = categories;
        }
    }

    public static class CFFile {
        public final int    id;
        public final String fileName, displayName, downloadUrl;
        public final long   size;

        public CFFile(int id, String fileName, String displayName,
                      String downloadUrl, long size) {
            this.id = id; this.fileName = fileName;
            this.displayName = displayName;
            this.downloadUrl = downloadUrl; this.size = size;
        }
    }

    public interface DownloadCallback {
        void progress(int percent);
        void done(Path file);
    }
}
