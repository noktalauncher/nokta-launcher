package com.nokta.launcher.core;

import com.google.gson.*;
import java.io.*;
import java.io.InputStream;
import java.net.*;
import java.nio.file.*;
import java.util.function.BiConsumer;

public class FabricInstaller {

    private final Path gameDir;

    public FabricInstaller(Path gameDir) {
        this.gameDir = gameDir;
    }

    public String install(String mcVersion, BiConsumer<String, Integer> progress) throws Exception {
        // En son Fabric loader versiyonunu al
        progress.accept("Fabric loader listesi alınıyor...", 5);
        String loaderVersion = getLatestLoader();
        String fabricVersionId = "fabric-loader-" + loaderVersion + "-" + mcVersion;

        Path versionDir = gameDir.resolve("versions").resolve(fabricVersionId);
        Path versionJson = versionDir.resolve(fabricVersionId + ".json");

        if (Files.exists(versionJson)) {
            progress.accept("Fabric zaten kurulu: " + fabricVersionId, 100);
            return fabricVersionId;
        }

        Files.createDirectories(versionDir);

        // Fabric version JSON'u indir
        progress.accept("Fabric profil indiriliyor...", 20);
        String profileUrl = "https://meta.fabricmc.net/v2/versions/loader/" 
            + mcVersion + "/" + loaderVersion + "/profile/json";
        String json = downloadString(profileUrl);
        Files.writeString(versionJson, json);

        // Fabric JAR (client jar olarak vanilla kullan)
        Path vanillaJar = gameDir.resolve("versions").resolve(mcVersion)
                                 .resolve(mcVersion + ".jar");
        Path fabricJar = versionDir.resolve(fabricVersionId + ".jar");
        if (Files.exists(vanillaJar) && !Files.exists(fabricJar)) {
            Files.copy(vanillaJar, fabricJar);
        }

        // Fabric loader JAR'ını indir
        progress.accept("Fabric loader JAR indiriliyor...", 35);
        String loaderJarPath = "net/fabricmc/fabric-loader/" + loaderVersion 
            + "/fabric-loader-" + loaderVersion + ".jar";
        Path loaderJarFile = gameDir.resolve("libraries").resolve(loaderJarPath);
        if (!Files.exists(loaderJarFile)) {
            Files.createDirectories(loaderJarFile.getParent());
            try (InputStream in = new URL("https://maven.fabricmc.net/" + loaderJarPath).openStream()) {
                Files.copy(in, loaderJarFile, StandardCopyOption.REPLACE_EXISTING);
            }
        }

        // Fabric kütüphanelerini indir
        progress.accept("Fabric kütüphaneleri indiriliyor...", 40);
        JsonObject profile = JsonParser.parseString(json).getAsJsonObject();
        if (profile.has("libraries")) {
            JsonArray libs = profile.getAsJsonArray("libraries");
            int total = libs.size(); int i = 0;
            for (JsonElement el : libs) {
                JsonObject lib = el.getAsJsonObject();
                downloadLibrary(lib);
                int pct = 40 + (int)((++i / (double) total) * 50);
                progress.accept("Kütüphane: " + lib.get("name").getAsString(), pct);
            }
        }

        // Fabric API otomatik indir
        progress.accept("Fabric API kontrol ediliyor...", 95);
        downloadFabricApi(mcVersion);

        progress.accept("Fabric kuruldu: " + fabricVersionId, 100);
        return fabricVersionId;
    }

    private void downloadFabricApi(String mcVersion) {
        try {
            Path modsDir = gameDir.resolve("mods").resolve(mcVersion);
            Files.createDirectories(modsDir);

            // Zaten fabric-api var mı?
            boolean exists = Files.list(modsDir)
                .anyMatch(p -> p.getFileName().toString().startsWith("fabric-api-"));
            if (exists) return;

            // Modrinth API'den en son sürümü bul
            String apiUrl = "https://api.modrinth.com/v2/project/P7dR8mSH/version"
                + "?game_versions=%5B%22" + mcVersion + "%22%5D&loaders=%5B%22fabric%22%5D";
            JsonArray versions = JsonParser.parseString(downloadString(apiUrl)).getAsJsonArray();
            if (versions.size() == 0) return;

            JsonArray files = versions.get(0).getAsJsonObject().getAsJsonArray("files");
            for (JsonElement fe : files) {
                JsonObject file = fe.getAsJsonObject();
                if (file.has("primary") && file.get("primary").getAsBoolean()) {
                    String url = file.get("url").getAsString();
                    String filename = file.get("filename").getAsString();
                    Path dest = modsDir.resolve(filename);
                    try (InputStream in = new URL(url).openStream()) {
                        Files.copy(in, dest, StandardCopyOption.REPLACE_EXISTING);
                    }
                    System.out.println("✅ Fabric API indirildi: " + filename);
                    break;
                }
            }
        } catch (Exception e) {
            System.out.println("⚠ Fabric API indirilemedi: " + e.getMessage());
        }
    }

    private String getLatestLoader() throws Exception {
        String url = "https://meta.fabricmc.net/v2/versions/loader";
        JsonArray arr = JsonParser.parseString(downloadString(url)).getAsJsonArray();
        return arr.get(0).getAsJsonObject().get("version").getAsString();
    }

    private void downloadLibrary(JsonObject lib) {
        try {
            String name = lib.get("name").getAsString();
            String[] parts = name.split(":");
            if (parts.length < 3) return;
            String group = parts[0].replace('.', '/');
            String artifact = parts[1];
            String version = parts[2];
            String path = group + "/" + artifact + "/" + version + "/" 
                        + artifact + "-" + version + ".jar";

            Path libFile = gameDir.resolve("libraries").resolve(path);
            if (Files.exists(libFile)) return;
            Files.createDirectories(libFile.getParent());

            String url = lib.has("url") 
                ? lib.get("url").getAsString() + path
                : "https://maven.fabricmc.net/" + path;

            try (InputStream in = new URL(url).openStream()) {
                Files.copy(in, libFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception ignored) {}
    }

    private String downloadString(String url) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(15000); conn.setReadTimeout(15000);
        conn.setRequestProperty("User-Agent", "NoktalLauncher/1.0");
        try (InputStream in = conn.getInputStream()) {
            return new String(in.readAllBytes());
        }
    }
}
