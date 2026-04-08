package com.nokta.launcher.core;

import com.google.gson.*;
import java.nio.file.*;

public class ThemeManager {

    public enum Theme {
        DARK_DEFAULT("Varsayılan Karanlık",
            "#0f0f17", "#13131e", "#6c63ff", "#1e1e30",
            "linear-gradient(to bottom, #0f0f17 0%, #13131e 100%)"),
        NETHER("Cehennem (Nether)",
            "#17080a", "#1e0d0f", "#ff4444", "#2e1010",
            "linear-gradient(to bottom, #17080a 0%, #2a0d0d 50%, #17080a 100%)"),
        THE_END("Son Boyut (The End)",
            "#0a0814", "#100c1e", "#aa44ff", "#1a1030",
            "linear-gradient(to bottom, #0a0814 0%, #1a0a2e 50%, #0a0814 100%)"),
        OCEAN("Okyanus (Ocean)",
            "#060e17", "#0a1520", "#00bcd4", "#0d1e2e",
            "linear-gradient(to bottom, #060e17 0%, #0a1a2e 50%, #060e17 100%)"),
        FOREST("Orman (Forest)",
            "#060f06", "#0a170a", "#44cc66", "#0d200d",
            "linear-gradient(to bottom, #060f06 0%, #0a1a0a 50%, #060f06 100%)"),
        SUNSET("Gün Batımı (Mesa)",
            "#17100a", "#1e1508", "#f59e0b", "#2a1e08",
            "linear-gradient(to bottom, #170e06 0%, #2a1408 50%, #17100a 100%)");

        public final String displayName;
        public final String bgColor;
        public final String cardColor;
        public final String accentColor;
        public final String borderColor;
        public final String gradient;

        Theme(String n, String bg, String card, String accent, String border, String gradient) {
            this.displayName = n;
            this.bgColor     = bg;
            this.cardColor   = card;
            this.accentColor = accent;
            this.borderColor = border;
            this.gradient    = gradient;
        }
    }

    private static ThemeManager instance;
    private Theme current = Theme.DARK_DEFAULT;
    private Runnable onThemeChanged;

    private static final Path PREFS = Paths.get(
        System.getProperty("user.home"), ".nokta-launcher", "theme.json");

    public static ThemeManager get() {
        if (instance == null) instance = new ThemeManager();
        return instance;
    }

    private ThemeManager() { load(); }

    public void setTheme(Theme t) {
        current = t;
        save();
        if (onThemeChanged != null) onThemeChanged.run();
    }

    public Theme getTheme()                      { return current; }
    public void  setOnThemeChanged(Runnable r)   { this.onThemeChanged = r; }

    public String bg()     { return current.bgColor;    }
    public String card()   { return current.cardColor;  }
    public String accent() { return current.accentColor;}
    public String border() { return current.borderColor;}

    private void save() {
        try {
            JsonObject j = new JsonObject();
            j.addProperty("theme", current.name());
            Files.writeString(PREFS, j.toString());
        } catch (Exception ignored) {}
    }

    private void load() {
        try {
            if (!Files.exists(PREFS)) return;
            JsonObject j = JsonParser.parseString(Files.readString(PREFS)).getAsJsonObject();
            if (j.has("theme")) current = Theme.valueOf(j.get("theme").getAsString());
        } catch (Exception ignored) {}
    }
}
