package com.nokta.overlay;

import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

public class DataReader {
    private static final Path VOICE_FILE   = Paths.get(
        System.getProperty("user.home"), ".nokta-launcher", "voice_channel.json");
    private static final Path SPOTIFY_FILE = Paths.get(
        System.getProperty("user.home"), ".nokta-launcher", "spotify_current.json");

    public static final List<VoiceEntry>  VOICE_USERS = new CopyOnWriteArrayList<>();
    public static String channelName = "";
    public static SpotifyEntry currentTrack = null;

    public static void start() {
        ScheduledExecutorService s = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "nokta-reader"); t.setDaemon(true); return t;
        });
        s.scheduleAtFixedRate(() -> {
            readVoice();
            readSpotify();
        }, 1, 2, TimeUnit.SECONDS);
    }

    private static void readVoice() {
        try {
            if (!Files.exists(VOICE_FILE)) return;
            JsonObject j = JsonParser.parseString(Files.readString(VOICE_FILE)).getAsJsonObject();
            channelName = j.has("channel") ? j.get("channel").getAsString() : "";
            VOICE_USERS.clear();
            if (j.has("users"))
                for (JsonElement el : j.getAsJsonArray("users")) {
                    JsonObject u = el.getAsJsonObject();
                    VOICE_USERS.add(new VoiceEntry(
                        u.get("username").getAsString(),
                        u.has("speaking") && u.get("speaking").getAsBoolean(),
                        u.has("muted")    && u.get("muted").getAsBoolean()));
                }
        } catch (Exception ignored) {}
    }

    private static void readSpotify() {
        try {
            if (!Files.exists(SPOTIFY_FILE)) { currentTrack = null; return; }
            JsonObject j = JsonParser.parseString(Files.readString(SPOTIFY_FILE)).getAsJsonObject();
            currentTrack = new SpotifyEntry(
                j.has("title")   ? j.get("title").getAsString()   : "",
                j.has("artist")  ? j.get("artist").getAsString()  : "",
                j.has("playing") && j.get("playing").getAsBoolean(),
                j.has("progress") ? j.get("progress").getAsInt()  : 0,
                j.has("duration") ? j.get("duration").getAsInt()  : 1);
        } catch (Exception ignored) { currentTrack = null; }
    }

    public record VoiceEntry(String username, boolean speaking, boolean muted) {}
    public record SpotifyEntry(String title, String artist, boolean playing,
                                int progressMs, int durationMs) {}
}
