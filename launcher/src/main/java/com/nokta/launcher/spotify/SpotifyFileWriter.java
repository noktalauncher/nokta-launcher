package com.nokta.launcher.spotify;

import com.nokta.launcher.utils.PathManager;
import com.google.gson.*;
import java.nio.file.*;

public class SpotifyFileWriter {
    private static final Path FILE = PathManager.getGameDir().resolve("spotify_current.json");

    public static void write(SpotifyManager.TrackInfo t) {
        try {
            JsonObject j = new JsonObject();
            j.addProperty("title",    t.title());
            j.addProperty("artist",   t.artist());
            j.addProperty("album",    t.album());
            j.addProperty("albumArt", t.albumArt());
            j.addProperty("playing",  t.playing());
            j.addProperty("progress", t.progressMs());
            j.addProperty("duration", t.durationMs());
            Files.writeString(FILE, j.toString());
        } catch (Exception ignored) {}
    }

    public static void clear() {
        try { Files.deleteIfExists(FILE); } catch (Exception ignored) {}
    }
}
