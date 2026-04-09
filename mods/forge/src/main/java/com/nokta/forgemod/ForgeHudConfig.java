package com.nokta.forgemod;

import com.google.gson.*;
import java.nio.file.*;

public class ForgeHudConfig {
    private static final Path FILE = Paths.get(
        System.getProperty("user.home"), ".nokta-launcher", "hud_config.json");

    public int   x       = 6;
    public int   y       = 6;
    public float scale   = 1.0f;
    public boolean visible = true;

    private static ForgeHudConfig instance;
    public static ForgeHudConfig get() {
        if (instance == null) { instance = new ForgeHudConfig(); instance.load(); }
        return instance;
    }

    public void load() {
        try {
            if (!Files.exists(FILE)) return;
            JsonObject j = JsonParser.parseString(Files.readString(FILE)).getAsJsonObject();
            if (j.has("x"))       x       = j.get("x").getAsInt();
            if (j.has("y"))       y       = j.get("y").getAsInt();
            if (j.has("scale"))   scale   = j.get("scale").getAsFloat();
            if (j.has("visible")) visible = j.get("visible").getAsBoolean();
        } catch (Exception ignored) {}
    }

    public void save() {
        try {
            Files.createDirectories(FILE.getParent());
            JsonObject j = new JsonObject();
            j.addProperty("x",       x);
            j.addProperty("y",       y);
            j.addProperty("scale",   scale);
            j.addProperty("visible", visible);
            Files.writeString(FILE, j.toString());
        } catch (Exception ignored) {}
    }
}
