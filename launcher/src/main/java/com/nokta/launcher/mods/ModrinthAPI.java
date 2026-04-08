package com.nokta.launcher.mods;

import com.google.gson.*;
import okhttp3.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;

public class ModrinthAPI {

    private static final String BASE = "https://api.modrinth.com/v2";
    private final OkHttpClient http = new OkHttpClient();

    public List<Mod> search(String query, String mcVersion,
                             String loader, int limit) throws Exception {
        // Facets doğru JSON formatında
        String facets;
        if (mcVersion != null && !mcVersion.isEmpty()) {
            facets = "[[\"project_type:mod\"],[\"versions:" + mcVersion + "\"]]";
        } else {
            facets = "[[\"project_type:mod\"]]";
        }

        String encodedQuery   = query.replace(" ", "+");
        String encodedFacets  = facets.replace("\"", "%22")
                                      .replace("[", "%5B")
                                      .replace("]", "%5D")
                                      .replace(":", "%3A")
                                      .replace(",", "%2C");

        String url = BASE + "/search?query=" + encodedQuery
            + "&facets=" + encodedFacets
            + "&limit=" + limit
            + "&index=relevance";

        Request req = new Request.Builder()
            .url(url)
            .addHeader("User-Agent", "NoktaLauncher/1.0 (contact@nokta.dev)")
            .build();

        try (Response res = http.newCall(req).execute()) {
            String body = res.body().string();
            JsonObject json = JsonParser.parseString(body).getAsJsonObject();

            if (!json.has("hits") || json.get("hits").isJsonNull())
                return new ArrayList<>();

            JsonArray hits = json.getAsJsonArray("hits");
            List<Mod> mods = new ArrayList<>();
            for (JsonElement el : hits) {
                JsonObject m = el.getAsJsonObject();
                mods.add(new Mod(
                    m.get("project_id").getAsString(),
                    m.get("title").getAsString(),
                    m.get("description").getAsString(),
                    m.has("icon_url") && !m.get("icon_url").isJsonNull() ? m.get("icon_url").getAsString() : null,
                    m.get("downloads").getAsLong(),
                    m.get("follows").getAsInt(),
                    getList(m, "versions"),
                    getList(m, "categories")
                ));
            }
            return mods;
        }
    }

    public List<ModVersion> getVersions(String projectId,
                                         String mcVersion,
                                         String loader) throws Exception {
        String url = BASE + "/project/" + projectId + "/version"
            + "?game_versions=%5B%22" + mcVersion + "%22%5D"
            + "&loaders=%5B%22" + loader.toLowerCase() + "%22%5D";

        Request req = new Request.Builder()
            .url(url)
            .addHeader("User-Agent", "NoktaLauncher/1.0")
            .build();

        try (Response res = http.newCall(req).execute()) {
            String body = res.body().string();
            JsonArray arr = JsonParser.parseString(body).getAsJsonArray();
            List<ModVersion> versions = new ArrayList<>();
            for (JsonElement el : arr) {
                JsonObject v = el.getAsJsonObject();
                if (!v.has("files") || v.getAsJsonArray("files").isEmpty()) continue;
                JsonObject primaryFile = v.getAsJsonArray("files").get(0).getAsJsonObject();
                versions.add(new ModVersion(
                    v.get("id").getAsString(),
                    v.get("name").getAsString(),
                    v.get("version_number").getAsString(),
                    primaryFile.get("url").getAsString(),
                    primaryFile.get("filename").getAsString(),
                    primaryFile.get("size").getAsLong()
                ));
            }
            return versions;
        }
    }

    public void downloadMod(ModVersion version, Path modsDir,
                             DownloadCallback cb) throws Exception {
        Files.createDirectories(modsDir);
        Path dest = modsDir.resolve(version.filename);
        if (Files.exists(dest)) { cb.done(dest); return; }

        Request req = new Request.Builder()
            .url(version.downloadUrl)
            .addHeader("User-Agent", "NoktaLauncher/1.0")
            .build();

        try (Response res = http.newCall(req).execute()) {
            if (!res.isSuccessful())
                throw new Exception("İndirme başarısız: " + version.filename);
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

    private List<String> getList(JsonObject obj, String key) {
        List<String> list = new ArrayList<>();
        if (obj.has(key) && obj.get(key).isJsonArray())
            for (JsonElement el : obj.getAsJsonArray(key))
                list.add(el.getAsString());
        return list;
    }

    public static class Mod {
        public final String id, title, description, iconUrl;
        public final long downloads;
        public final int follows;
        public final List<String> versions, categories;

        public Mod(String id, String title, String description, String iconUrl,
                   long downloads, int follows,
                   List<String> versions, List<String> categories) {
            this.id = id; this.title = title;
            this.description = description; this.iconUrl = iconUrl;
            this.downloads = downloads; this.follows = follows;
            this.versions = versions; this.categories = categories;
        }
    }

    public static class ModVersion {
        public final String id, name, versionNumber, downloadUrl, filename;
        public final long size;

        public ModVersion(String id, String name, String versionNumber,
                          String downloadUrl, String filename, long size) {
            this.id = id; this.name = name;
            this.versionNumber = versionNumber;
            this.downloadUrl = downloadUrl;
            this.filename = filename; this.size = size;
        }
    }

    public interface DownloadCallback {
        void progress(int percent);
        void done(Path file);
    }
}
