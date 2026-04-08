package com.nokta.launcher.core;

import com.google.gson.*;
import okhttp3.*;
import java.io.*;
import java.nio.file.*;
import java.util.zip.*;

public class NativesManager {

    private final Path gameDir;
    private final OkHttpClient http = new OkHttpClient();

    public NativesManager(Path gameDir) {
        this.gameDir = gameDir;
    }

    // İşletim sistemini tespit et
    public static String detectOS() {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win"))   return "windows";
        if (os.contains("mac"))   return "osx";
        if (os.contains("linux")) return "linux";
        return "linux";
    }

    // CPU mimarisini tespit et
    public static String detectArch() {
        String arch = System.getProperty("os.arch").toLowerCase();
        if (arch.contains("aarch64") || arch.contains("arm64")) return "arm64";
        if (arch.contains("arm"))  return "arm32";
        if (arch.contains("x86_64") || arch.contains("amd64")) return "x64";
        return "x64";
    }

    // Natives klasörü yolu
    public Path getNativesDir(String versionId) {
        return gameDir.resolve("versions")
                      .resolve(versionId)
                      .resolve("natives-" + detectOS() + "-" + detectArch());
    }

    // Natives'leri version.json'dan çıkart ve indir
    public void extractNatives(String versionId,
                                VersionManager.ProgressCallback cb) throws Exception {
        Path versionJson = gameDir.resolve("versions")
                                  .resolve(versionId)
                                  .resolve(versionId + ".json");

        if (!Files.exists(versionJson))
            throw new Exception("Önce sürümü indirin!");

        JsonObject versionObj = JsonParser.parseString(
            Files.readString(versionJson)).getAsJsonObject();

        Path nativesDir = getNativesDir(versionId);
        Files.createDirectories(nativesDir);

        String os = detectOS();
        JsonArray libraries = versionObj.getAsJsonArray("libraries");
        int total = libraries.size(), i = 0;

        for (JsonElement el : libraries) {
            i++;
            JsonObject lib = el.getAsJsonObject();

            // OS kurallarını kontrol et
            if (!isAllowedOnOS(lib, os)) continue;
            if (!lib.has("downloads")) continue;

            JsonObject downloads = lib.getAsJsonObject("downloads");

            // Classifiers (natives jar'ları)
            if (downloads.has("classifiers")) {
                JsonObject classifiers = downloads.getAsJsonObject("classifiers");
                String nativeKey = getNativeKey(lib, os);
                if (nativeKey != null && classifiers.has(nativeKey)) {
                    JsonObject nativeArt = classifiers.getAsJsonObject(nativeKey);
                    String url  = nativeArt.get("url").getAsString();
                    String path = nativeArt.get("path").getAsString();
                    Path libFile = gameDir.resolve("libraries").resolve(path);

                    if (!Files.exists(libFile)) {
                        Files.createDirectories(libFile.getParent());
                        cb.update("Native indiriliyor: " + path, (i * 80 / total));
                        downloadFile(url, libFile);
                    }
                    // JAR içinden native dosyaları çıkart
                    cb.update("Native çıkartılıyor: " + libFile.getFileName(), (i * 80 / total));
                    extractNativesFromJar(libFile, nativesDir);
                }
            }

            int pct = 80 + (i * 20 / total);
            cb.update("Natives hazırlanıyor...", pct);
        }

        cb.update("✅ Natives hazır: " + nativesDir, 100);
    }

    // JAR içindeki native dosyaları çıkart (.so, .dll, .dylib)
    private void extractNativesFromJar(Path jar, Path dest) throws Exception {
        try (ZipInputStream zis = new ZipInputStream(
                Files.newInputStream(jar))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();
                // Sadece native dosyaları al
                if (name.endsWith(".so")    ||
                    name.endsWith(".dll")   ||
                    name.endsWith(".dylib") ||
                    name.endsWith(".jnilib")) {

                    // Alt klasörleri yoksay
                    String fileName = Paths.get(name).getFileName().toString();
                    Path outFile = dest.resolve(fileName);

                    if (!Files.exists(outFile)) {
                        try (OutputStream os2 = Files.newOutputStream(outFile)) {
                            byte[] buf = new byte[8192]; int n;
                            while ((n = zis.read(buf)) != -1) os2.write(buf, 0, n);
                        }
                    }
                }
                zis.closeEntry();
            }
        }
    }

    // OS'a göre native key belirle
    private String getNativeKey(JsonObject lib, String os) {
        if (!lib.has("natives")) return null;
        JsonObject natives = lib.getAsJsonObject("natives");
        if (!natives.has(os)) return null;
        String key = natives.get(os).getAsString();
        // ${arch} varsa değiştir
        return key.replace("${arch}", detectArch().equals("arm64") ? "arm64" : "64");
    }

    // OS kurallarını kontrol et
    private boolean isAllowedOnOS(JsonObject lib, String os) {
        if (!lib.has("rules")) return true;
        JsonArray rules = lib.getAsJsonArray("rules");
        boolean allowed = false;
        for (JsonElement ruleEl : rules) {
            JsonObject rule = ruleEl.getAsJsonObject();
            String action = rule.get("action").getAsString();
            if (rule.has("os")) {
                String ruleOs = rule.getAsJsonObject("os").get("name").getAsString();
                if (ruleOs.equals(os)) {
                    allowed = action.equals("allow");
                }
            } else {
                allowed = action.equals("allow");
            }
        }
        return allowed;
    }

    // Natives hazır mı?
    public boolean isNativesReady(String versionId) {
        Path nativesDir = getNativesDir(versionId);
        if (!Files.exists(nativesDir)) return false;
        try {
            return Files.list(nativesDir).findAny().isPresent();
        } catch (Exception e) { return false; }
    }

    private void downloadFile(String url, Path dest) throws Exception {
        Request req = new Request.Builder().url(url).build();
        try (Response res = http.newCall(req).execute()) {
            if (!res.isSuccessful()) throw new Exception("İndirme hatası: " + url);
            try (InputStream in  = res.body().byteStream();
                 OutputStream out = Files.newOutputStream(dest)) {
                byte[] buf = new byte[8192]; int n;
                while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
            }
        }
    }
}
