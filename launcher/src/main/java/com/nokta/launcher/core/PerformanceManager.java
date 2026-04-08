package com.nokta.launcher.core;

import com.nokta.launcher.mods.ModrinthAPI;
import java.nio.file.*;
import java.util.*;

public class PerformanceManager {

    // Önerilen FPS boost mod paketleri (Fabric için)
    public static final List<FPSPack> FPS_PACKS = List.of(

        new FPSPack(
            "⚡ Ultra FPS Pack",
            "Maksimum FPS için en iyi kombinasyon",
            "#6c63ff",
            List.of(
                new ModEntry("sodium",           "Sodium",           "Ana render optimizasyonu — 3x FPS"),
                new ModEntry("lithium",          "Lithium",          "Oyun mantığı optimizasyonu"),
                new ModEntry("phosphor",         "Phosphor",         "Işık motoru optimizasyonu"),
                new ModEntry("iris",             "Iris Shaders",     "Shader desteği (opsiyonel)"),
                new ModEntry("entityculling",    "Entity Culling",   "Görünmeyen entity'leri gizler"),
                new ModEntry("ferrite-core",     "FerriteCore",      "RAM kullanımını azaltır"),
                new ModEntry("lazydfu",          "LazyDFU",          "Daha hızlı oyun açılışı"),
                new ModEntry("starlight",        "Starlight",        "Işık sistemi yeniden yazıldı"),
                new ModEntry("krypton",          "Krypton",          "Ağ stack optimizasyonu")
            ),
            4096, true
        ),

        new FPSPack(
            "🟢 Düşük Sistem Paketi",
            "2GB RAM ve zayıf GPU için optimize",
            "#10b981",
            List.of(
                new ModEntry("sodium",          "Sodium",           "Zorunlu — büyük FPS artışı"),
                new ModEntry("lithium",         "Lithium",          "CPU optimizasyonu"),
                new ModEntry("lazydfu",         "LazyDFU",          "Hızlı açılış"),
                new ModEntry("ferrite-core",    "FerriteCore",      "Düşük RAM kullanımı"),
                new ModEntry("entityculling",   "Entity Culling",   "FPS stabilitesi")
            ),
            2048, true
        ),

        new FPSPack(
            "🎨 Görsel Kalite Paketi",
            "Güzel görüntü + makul FPS",
            "#f59e0b",
            List.of(
                new ModEntry("sodium",          "Sodium",           "Temel optimizasyon"),
                new ModEntry("iris",            "Iris Shaders",     "Shader desteği"),
                new ModEntry("indium",          "Indium",           "Sodium + Iris uyumu"),
                new ModEntry("lambdynamiclights","LambDynLights",   "Dinamik ışıklar"),
                new ModEntry("continuity",      "Continuity",       "Bağlantılı dokular")
            ),
            6144, false
        )
    );

    // JVM optimizasyonu — sisteme göre otomatik ayarla
    public static List<String> getOptimalJVMFlags(int totalRamMB) {
        List<String> flags = new ArrayList<>();

        // RAM'in %60'ını Minecraft'a ver
        int mcRam = (int)(totalRamMB * 0.6);
        mcRam = Math.max(1024, Math.min(mcRam, 16384));

        flags.add("-Xms" + (mcRam / 2) + "M");
        flags.add("-Xmx" + mcRam + "M");

        // GC ayarları
        if (mcRam >= 4096) {
            // Yüksek RAM — G1GC (Aikar's flags)
            flags.addAll(List.of(
                "-XX:+UseG1GC",
                "-XX:+ParallelRefProcEnabled",
                "-XX:MaxGCPauseMillis=200",
                "-XX:+UnlockExperimentalVMOptions",
                "-XX:+DisableExplicitGC",
                "-XX:+AlwaysPreTouch",
                "-XX:G1NewSizePercent=30",
                "-XX:G1MaxNewSizePercent=40",
                "-XX:G1HeapRegionSize=8M",
                "-XX:G1ReservePercent=20",
                "-XX:G1HeapWastePercent=5",
                "-XX:G1MixedGCCountTarget=4",
                "-XX:InitiatingHeapOccupancyPercent=15",
                "-XX:G1MixedGCLiveThresholdPercent=90",
                "-XX:G1RSetUpdatingPauseTimePercent=5",
                "-XX:SurvivorRatio=32",
                "-XX:+PerfDisableSharedMem",
                "-XX:MaxTenuringThreshold=1"
            ));
        } else {
            // Düşük RAM — ZGC (daha az pause)
            flags.addAll(List.of(
                "-XX:+UseZGC",
                "-XX:+UnlockExperimentalVMOptions",
                "-XX:+DisableExplicitGC",
                "-XX:+AlwaysPreTouch"
            ));
        }

        // Genel optimizasyonlar
        flags.addAll(List.of(
            "-Dlog4j2.formatMsgNoLookups=true",
            "-Dfile.encoding=UTF-8",
            "-Djava.net.preferIPv4Stack=true"
        ));

        return flags;
    }

    // Toplam sistem RAM'ini al
    public static long getTotalSystemRAMMB() {
        try {
            com.sun.management.OperatingSystemMXBean os =
                (com.sun.management.OperatingSystemMXBean)
                java.lang.management.ManagementFactory.getOperatingSystemMXBean();
            return os.getTotalMemorySize() / (1024 * 1024);
        } catch (Exception e) {
            return 4096; // Varsayılan
        }
    }

    // Veri modelleri
    public static class FPSPack {
        public final String name, description, color;
        public final List<ModEntry> mods;
        public final int recommendedRAM;
        public final boolean recommended;

        public FPSPack(String name, String description, String color,
                       List<ModEntry> mods, int recommendedRAM, boolean recommended) {
            this.name = name; this.description = description;
            this.color = color; this.mods = mods;
            this.recommendedRAM = recommendedRAM;
            this.recommended = recommended;
        }
    }

    public static class ModEntry {
        public final String slug, displayName, description;
        public ModEntry(String slug, String displayName, String description) {
            this.slug = slug;
            this.displayName = displayName;
            this.description = description;
        }
    }
}
