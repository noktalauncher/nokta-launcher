package com.nokta.launcher.core;

import com.google.gson.*;
import java.nio.file.*;

public class ProfileManager {
    private static final Path CONFIG = Paths.get(
        System.getProperty("user.home"), ".nokta-launcher", "profile.json");

    private String username     = "Oyuncu";
    private String avatarPath   = null; // null = varsayılan
    private String bgPath       = null; // null = varsayılan renk
    private String bgColor      = "#0f0f17";
    private String accentColor  = "#6c63ff";
    private double bgOpacity    = 0.85;

    private static ProfileManager instance;
    public static ProfileManager get() {
        if (instance == null) { instance = new ProfileManager(); instance.load(); }
        return instance;
    }

    public void load() {
        try {
            if (!Files.exists(CONFIG)) return;
            JsonObject j = JsonParser.parseString(Files.readString(CONFIG)).getAsJsonObject();
            if (j.has("username"))    username    = j.get("username").getAsString();
            if (j.has("avatarPath"))  avatarPath  = j.get("avatarPath").getAsString();
            if (j.has("bgPath"))      bgPath      = j.get("bgPath").getAsString();
            if (j.has("bgColor"))     bgColor     = j.get("bgColor").getAsString();
            if (j.has("accentColor")) accentColor = j.get("accentColor").getAsString();
            if (j.has("bgOpacity"))   bgOpacity   = j.get("bgOpacity").getAsDouble();
        } catch (Exception e) { System.out.println("⚠ Profil yüklenemedi: " + e.getMessage()); }
    }

    public void save() {
        try {
            JsonObject j = new JsonObject();
            j.addProperty("username", username);
            if (avatarPath  != null) j.addProperty("avatarPath", avatarPath);
            if (bgPath      != null) j.addProperty("bgPath", bgPath);
            j.addProperty("bgColor",     bgColor);
            j.addProperty("accentColor", accentColor);
            j.addProperty("bgOpacity",   bgOpacity);
            Files.writeString(CONFIG, j.toString());
        } catch (Exception e) { System.out.println("⚠ Profil kaydedilemedi: " + e.getMessage()); }
    }

    public String getUsername()    { return username; }
    public String getAvatarPath()  { return avatarPath; }
    public String getBgPath()      { return bgPath; }
    public String getBgColor()     { return bgColor; }
    public String getAccentColor() { return accentColor; }
    public double getBgOpacity()   { return bgOpacity; }

    public void setUsername(String v)    { username = v;    save(); }
    public void setAvatarPath(String v)  { avatarPath = v;  save(); }
    public void setBgPath(String v)      { bgPath = v;      save(); }
    public void setBgColor(String v)     { bgColor = v;     save(); }
    public void setAccentColor(String v) { accentColor = v; save(); }
    public void setBgOpacity(double v)   { bgOpacity = v;   save(); }
}
