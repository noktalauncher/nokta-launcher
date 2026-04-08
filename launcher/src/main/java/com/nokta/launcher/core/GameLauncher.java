package com.nokta.launcher.core;

import com.google.gson.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;

public class GameLauncher {

    private final Path gameDir;
    private final NativesManager nativesManager;

    public GameLauncher(Path gameDir) {
        this.gameDir = gameDir;
        this.nativesManager = new NativesManager(gameDir);
    }

    public Process launch(LaunchConfig config) throws Exception {
        Path versionDir  = gameDir.resolve("versions").resolve(config.versionId);
        Path versionJson = versionDir.resolve(config.versionId + ".json");

        if (!Files.exists(versionJson))
            throw new Exception("Sürüm kurulu değil: " + config.versionId);

        JsonObject versionObj = JsonParser.parseString(
            Files.readString(versionJson)).getAsJsonObject();

        // Natives dizini - Fabric için parent versiyonunu kullan
        String nativesVersionId = config.versionId;
        if (versionObj.has("inheritsFrom"))
            nativesVersionId = versionObj.get("inheritsFrom").getAsString();
        Path nativesDir = nativesManager.getNativesDir(nativesVersionId);

        // Classpath
        List<String> classpath = buildClasspath(versionObj, config.versionId);

        // Main class
        String mainClass = versionObj.get("mainClass").getAsString();

        // Komut oluştur
        List<String> cmd = new ArrayList<>();
        cmd.add(config.javaPath);

        // JVM argümanları
        cmd.addAll(buildJvmArgs(config, nativesDir));

        // Classpath
        cmd.add("-cp");
        cmd.add(String.join(File.pathSeparator, classpath));

        // Main class
        cmd.add(mainClass);

        // Minecraft argümanları
        cmd.addAll(buildMCArgs(versionObj, config));

        System.out.println("🚀 Başlatılıyor: " + config.versionId);
        System.out.println("📋 Tam komut: " + String.join(" ", cmd));
        System.out.println("📁 Natives: " + nativesDir);
        System.out.println("👤 Oyuncu: " + config.username);

        // Mod yönetimi — sürüme özel klasörden doğrudan yükle
        try {
            // MC sürümünü çıkar: "fabric-loader-X-1.21.4" → "1.21.4"
            String mcVersion = config.versionId;
            if (mcVersion.contains("-")) {
                String[] parts = mcVersion.split("-");
                mcVersion = parts[parts.length - 1];
            }

            // Sürüme özel mod klasörü: mods/1.21.4/
            Path versionMods = com.nokta.launcher.utils.PathManager.getModsDir(mcVersion);

            // Ana mods/ klasörünü temizle — sadece aktif sürümün modları olacak
            Path mainMods = gameDir.resolve("mods");
            if (Files.exists(mainMods)) {
                Files.list(mainMods)
                    .filter(p -> p.toString().endsWith(".jar") || p.toString().endsWith(".jar.disabled"))
                    .forEach(p -> { try { Files.delete(p); } catch (Exception ignored) {} });
            }

            // Sürüme özel modları kopyala
            if (Files.exists(versionMods)) {
                Files.createDirectories(mainMods);
                long count = 0;
                for (Path p : Files.list(versionMods).toList()) {
                    if (p.toString().endsWith(".jar") || p.toString().endsWith(".jar.disabled")) {
                        Files.copy(p, mainMods.resolve(p.getFileName()),
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                        if (p.toString().endsWith(".jar")) count++;
                    }
                }
                System.out.println("📦 " + count + " mod yuklendi → mods/ (" + mcVersion + ")");
            }

            // Nokta overlay mod jar'ını doğru sürüm için ekle
            // Loader tipini belirle
            String loaderType = "fabric";
            if (config.versionId.toLowerCase().contains("forge")) loaderType = "forge";
            else if (config.versionId.toLowerCase().contains("neoforge")) loaderType = "neoforge";

            Path overlayJar = com.nokta.launcher.utils.PathManager.getOverlayModJar(loaderType, mcVersion);
            if (Files.exists(overlayJar)) {
                Files.copy(overlayJar, mainMods.resolve("nokta-overlay.jar"),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                System.out.println("📦 Nokta overlay mod eklendi: " + overlayJar.getFileName());
            }
        } catch (Exception e) {
            System.out.println("⚠ Mod yuklenemedi: " + e.getMessage());
        }

        
        // Son oynanan oyunu kaydet
        try {
            java.nio.file.Path lastFile = java.nio.file.Paths.get(
                System.getProperty("user.home"), ".nokta-launcher", "last_played.json");
            com.google.gson.JsonObject j = new com.google.gson.JsonObject();
            j.addProperty("version", config.versionId);
            j.addProperty("time", System.currentTimeMillis());
            java.nio.file.Files.writeString(lastFile, j.toString());
        } catch (Exception ignored) {}
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(gameDir.toFile());
        pb.redirectErrorStream(true);
        return pb.start();
    }

    private List<String> buildJvmArgs(LaunchConfig config, Path nativesDir) {
        List<String> args = new ArrayList<>();

        // Nokta Agent — tum loader ve versiyonlarda calisir
        String agentJar = com.nokta.launcher.utils.PathManager.getGameDir()
            .resolve("nokta-agent.jar").toAbsolutePath().toString();
        if (new java.io.File(agentJar).exists()) {
            args.add(0, "-javaagent:" + agentJar);
            System.out.println("[Launcher] nokta-agent aktif: " + agentJar);
        }
        // Locale fix - SoupAPI Türkçe locale crash workaround
        args.add("-Duser.language=en");
        args.add("-Duser.country=US");
        // RAM
        args.add("-Xms512M");
        args.add("-Xmx" + config.ramMB + "M");

        // Aikar's Flags (performans optimizasyonu)
        if (config.useAikarsFlags) {
            args.add("-XX:+UseG1GC");
            args.add("-XX:+ParallelRefProcEnabled");
            args.add("-XX:MaxGCPauseMillis=200");
            args.add("-XX:+UnlockExperimentalVMOptions");
            args.add("-XX:+DisableExplicitGC");
            args.add("-XX:+AlwaysPreTouch");
            args.add("-XX:G1NewSizePercent=30");
            args.add("-XX:G1MaxNewSizePercent=40");
            args.add("-XX:G1HeapRegionSize=8M");
            args.add("-XX:G1ReservePercent=20");
            args.add("-XX:G1HeapWastePercent=5");
            args.add("-XX:G1MixedGCCountTarget=4");
            args.add("-XX:InitiatingHeapOccupancyPercent=15");
            args.add("-XX:G1MixedGCLiveThresholdPercent=90");
            args.add("-XX:G1RSetUpdatingPauseTimePercent=5");
            args.add("-XX:SurvivorRatio=32");
            args.add("-XX:+PerfDisableSharedMem");
            args.add("-XX:MaxTenuringThreshold=1");
        }

        // Natives yolu (çok önemli!)
        args.add("-Djava.library.path=" + nativesDir.toAbsolutePath());
        args.add("-Dorg.lwjgl.librarypath=" + nativesDir.toAbsolutePath());

        // Log4j güvenlik yaması
        args.add("-Dlog4j2.formatMsgNoLookups=true");

        // Module erişimi
        args.add("--add-opens"); args.add("java.base/java.lang=ALL-UNNAMED");
        args.add("--add-opens"); args.add("java.base/java.io=ALL-UNNAMED");
        args.add("--add-opens"); args.add("java.base/java.util=ALL-UNNAMED");

        // OS'a özel
        String os = NativesManager.detectOS();
        if (os.equals("osx")) {
            args.add("-XstartOnFirstThread");
            args.add("-Dapple.awt.UIElement=true");
        }

        return args;
    }

    private List<String> buildClasspath(JsonObject versionObj, String versionId) {
        List<String> cp = new ArrayList<>();
        Path librariesDir = gameDir.resolve("libraries");
        String os = NativesManager.detectOS();

        JsonArray libraries = versionObj.getAsJsonArray("libraries");
        for (JsonElement el : libraries) {
            JsonObject lib = el.getAsJsonObject();
            if (!isAllowedOnOS(lib, os)) continue;

            if (lib.has("downloads")) {
                // Vanilla/standart format
                JsonObject downloads = lib.getAsJsonObject("downloads");
                if (!downloads.has("artifact")) continue;
                String path = downloads.getAsJsonObject("artifact").get("path").getAsString();
                Path libFile = librariesDir.resolve(path);
                if (Files.exists(libFile)) cp.add(libFile.toAbsolutePath().toString());
            } else if (lib.has("name")) {
                // Fabric/Maven format: "group:artifact:version"
                String name = lib.get("name").getAsString();
                String[] parts = name.split(":");
                if (parts.length < 3) continue;
                String group    = parts[0].replace('.', '/');
                String artifact = parts[1];
                String version  = parts[2];
                String path = group + "/" + artifact + "/" + version
                            + "/" + artifact + "-" + version + ".jar";
                Path libFile = librariesDir.resolve(path);
                if (Files.exists(libFile)) cp.add(libFile.toAbsolutePath().toString());
            }
        }

        // Client JAR - Fabric için parent JAR'ı da ekle
        Path clientJar = gameDir.resolve("versions")
                                .resolve(versionId)
                                .resolve(versionId + ".jar");
        if (Files.exists(clientJar)) cp.add(clientJar.toAbsolutePath().toString());

        // inheritsFrom varsa parent JAR'ı da ekle
        if (versionObj.has("inheritsFrom")) {
            String parentId = versionObj.get("inheritsFrom").getAsString();
            Path parentJar = gameDir.resolve("versions").resolve(parentId).resolve(parentId + ".jar");
            if (Files.exists(parentJar)) cp.add(parentJar.toAbsolutePath().toString());

            // Fabric JSON'daki TUM artifact adlarini topla (dosya var/yok farketmez)
            java.util.Set<String> fabricArtifacts = new java.util.HashSet<>();
            for (JsonElement el2 : versionObj.getAsJsonArray("libraries")) {
                JsonObject lib2 = el2.getAsJsonObject();
                if (lib2.has("name")) {
                    String[] mvn = lib2.get("name").getAsString().split(":");
                    if (mvn.length >= 2) fabricArtifacts.add(mvn[1]);
                }
                if (lib2.has("downloads") && lib2.getAsJsonObject("downloads").has("artifact")) {
                    String p2 = lib2.getAsJsonObject("downloads").getAsJsonObject("artifact").get("path").getAsString();
                    String[] pp2 = p2.replace('\\', '/').split("/");
                    if (pp2.length >= 3) fabricArtifacts.add(pp2[pp2.length - 3]);
                }
            }

            // Parent kutuphanelerini ekle — Fabric'in bildirdigi artifact'lari atla
            Path parentJsonPath = gameDir.resolve("versions").resolve(parentId).resolve(parentId + ".json");
            if (Files.exists(parentJsonPath)) {
                try {
                    JsonObject parentObj = JsonParser.parseString(Files.readString(parentJsonPath)).getAsJsonObject();
                    if (parentObj.has("libraries")) {
                        for (JsonElement el2 : parentObj.getAsJsonArray("libraries")) {
                            JsonObject lib2 = el2.getAsJsonObject();
                            if (!isAllowedOnOS(lib2, os)) continue;
                            if (!lib2.has("downloads")) continue;
                            JsonObject downloads2 = lib2.getAsJsonObject("downloads");
                            if (!downloads2.has("artifact")) continue;
                            String path2 = downloads2.getAsJsonObject("artifact").get("path").getAsString();
                            String[] pp = path2.replace('\\', '/').split("/");
                            String artifactName = pp.length >= 3 ? pp[pp.length - 3] : "";
                            if (fabricArtifacts.contains(artifactName)) {
                                System.out.println("Dedup: vanilla '" + artifactName + "' atlandi (Fabric surumu kullaniliyor)");
                                continue;
                            }
                            Path libFile = librariesDir.resolve(path2);
                            if (Files.exists(libFile) && !cp.contains(libFile.toAbsolutePath().toString()))
                                cp.add(libFile.toAbsolutePath().toString());
                        }
                    }
                } catch (Exception e) {
                    System.out.println("Parent JSON okunamadi: " + e.getMessage());
                }
            }
        }

        return cp;
    }

    private List<String> buildMCArgs(JsonObject versionObj, LaunchConfig config) {
        List<String> args = new ArrayList<>();

        // Parent JSON'u yükle (inheritsFrom varsa)
        JsonObject parentObj = null;
        if (versionObj.has("inheritsFrom")) {
            String parentId = versionObj.get("inheritsFrom").getAsString();
            Path parentJsonPath = gameDir.resolve("versions").resolve(parentId).resolve(parentId + ".json");
            if (Files.exists(parentJsonPath)) {
                try { parentObj = JsonParser.parseString(Files.readString(parentJsonPath)).getAsJsonObject(); }
                catch (Exception ignored) {}
            }
        }

        // assetIndex: önce kendi JSON'da, yoksa parent'tan al
        JsonObject resolveObj = versionObj;
        if (!versionObj.has("assetIndex") && parentObj != null && parentObj.has("assetIndex"))
            resolveObj = parentObj;
        final JsonObject finalResolveObj = resolveObj;

        // Argümanları topla: önce parent, sonra child (child override eder)
        java.util.LinkedHashMap<String, String> argMap = new java.util.LinkedHashMap<>();

        // Parent argümanlarını ekle
        if (parentObj != null) {
            if (parentObj.has("arguments")) {
                JsonArray gameArgs = parentObj.getAsJsonObject("arguments").getAsJsonArray("game");
                String lastKey = null;
                for (JsonElement el : gameArgs) {
                    if (el.isJsonObject()) { lastKey = null; continue; }
                    String val = resolveMCArg(el.getAsString(), finalResolveObj, config);
                    if (val.startsWith("--")) { lastKey = val; argMap.put(lastKey, null); }
                    else if (lastKey != null) { argMap.put(lastKey, val); lastKey = null; }
                    else { argMap.put(val, null); }
                }
            } else if (parentObj.has("minecraftArguments")) {
                String[] parts = parentObj.get("minecraftArguments").getAsString().split(" ");
                String lastKey = null;
                for (String p : parts) {
                    String val = resolveMCArg(p, finalResolveObj, config);
                    if (val.startsWith("--")) { lastKey = val; argMap.put(lastKey, null); }
                    else if (lastKey != null) { argMap.put(lastKey, val); lastKey = null; }
                    else { argMap.put(val, null); }
                }
            }
        }

        // Child (Fabric) argümanlarını ekle/override et
        if (versionObj.has("arguments")) {
            JsonArray gameArgs = versionObj.getAsJsonObject("arguments").getAsJsonArray("game");
            String lastKey = null;
            for (JsonElement el : gameArgs) {
                if (el.isJsonObject()) { lastKey = null; continue; }
                String val = resolveMCArg(el.getAsString(), finalResolveObj, config);
                if (val.startsWith("--")) { lastKey = val; argMap.put(lastKey, null); }
                else if (lastKey != null) { argMap.put(lastKey, val); lastKey = null; }
                else { argMap.put(val, null); }
            }
        }

        // Map'i listeye çevir
        for (java.util.Map.Entry<String, String> e : argMap.entrySet()) {
            args.add(e.getKey());
            if (e.getValue() != null) args.add(e.getValue());
        }

        return args;
    }

    private String resolveMCArg(String arg, JsonObject ver, LaunchConfig cfg) {
        return arg
            .replace("${auth_player_name}",  cfg.username)
            .replace("${version_name}",      cfg.versionId)
            .replace("${game_directory}",    gameDir.toAbsolutePath().toString())
            .replace("${assets_root}",       gameDir.resolve("assets").toAbsolutePath().toString())
            .replace("${assets_index_name}", ver.has("assetIndex")
                ? ver.getAsJsonObject("assetIndex").get("id").getAsString() : "legacy")
            .replace("${auth_uuid}",         cfg.uuid)
            .replace("${auth_access_token}", cfg.accessToken)
            .replace("${user_type}",         cfg.userType)
            .replace("${version_type}",      "release")
            .replace("${resolution_width}",  "854")
            .replace("${resolution_height}", "480")
            .replace("${clientid}",          "")
            .replace("${auth_xuid}",         "")
            .replace("${user_properties}",   "{}")
            .replace("${game_assets}",       gameDir.resolve("assets").toAbsolutePath().toString())
            .replace("${profile_name}",      cfg.username)
            .replace("${session}",           "token:" + cfg.accessToken + ":" + cfg.uuid);
    }

    private boolean isAllowedOnOS(JsonObject lib, String os) {
        if (!lib.has("rules")) return true;
        boolean allowed = false;
        for (JsonElement ruleEl : lib.getAsJsonArray("rules")) {
            JsonObject rule = ruleEl.getAsJsonObject();
            String action = rule.get("action").getAsString();
            if (rule.has("os")) {
                String ruleOs = rule.getAsJsonObject("os").get("name").getAsString();
                if (ruleOs.equals(os)) allowed = action.equals("allow");
            } else {
                allowed = action.equals("allow");
            }
        }
        return allowed;
    }

    public static class LaunchConfig {
        public String  versionId    = "1.21.4";
        public String  username     = "Oyuncu";
        public String  uuid         = UUID.randomUUID().toString();
        public String  accessToken  = "0";
        public String  userType     = "legacy";
        public int     ramMB        = 4096;
        public String  javaPath     = "java";
        public boolean useAikarsFlags = true;
    }
}
