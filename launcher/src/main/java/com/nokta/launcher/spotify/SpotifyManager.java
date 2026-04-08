package com.nokta.launcher.spotify;

import java.util.concurrent.*;
import java.util.function.Consumer;

public class SpotifyManager {

    private boolean connected = false;
    private Consumer<TrackInfo> onTrackChanged;
    private TrackInfo lastTrack = null;
    private static SpotifyManager instance;
    private static final String OS = System.getProperty("os.name").toLowerCase();
    private final SpotifyWebAPI webAPI = new SpotifyWebAPI();

    public static SpotifyManager get() {
        if (instance == null) instance = new SpotifyManager();
        return instance;
    }

    public void setOnTrackChanged(Consumer<TrackInfo> cb) { this.onTrackChanged = cb; }

    public void connect() {
        ScheduledExecutorService retry = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "spotify-retry"); t.setDaemon(true); return t;
        });
        retry.scheduleAtFixedRate(() -> {
            if (!connected && isSpotifyRunning()) {
                connected = true;
                System.out.println("✅ Spotify bağlandı!");
                retry.shutdown();
                startPolling();
            }
        }, 0, 5, TimeUnit.SECONDS);
    }

    private boolean isSpotifyRunning() {
        try {
            // WebAPI bağlıysa her zaman çalışıyor say
            if (webAPI.hasToken()) return true;
            Process p;
            if (isLinux()) {
                p = Runtime.getRuntime().exec(new String[]{
                    "dbus-send", "--print-reply",
                    "--dest=org.mpris.MediaPlayer2.spotify",
                    "/org/mpris/MediaPlayer2",
                    "org.freedesktop.DBus.Peer.Ping"
                });
            } else if (isWindows()) {
                p = Runtime.getRuntime().exec(new String[]{"tasklist","/FI","IMAGENAME eq Spotify.exe"});
                String out = new String(p.getInputStream().readAllBytes());
                return out.contains("Spotify.exe");
            } else {
                p = Runtime.getRuntime().exec(new String[]{"pgrep","-x","Spotify"});
            }
            p.waitFor(2, TimeUnit.SECONDS);
            return p.exitValue() == 0;
        } catch (Exception e) { return false; }
    }

    private static final java.nio.file.Path CMD_FILE = java.nio.file.Paths.get(
        System.getProperty("user.home"), ".nokta-launcher", "spotify_cmd.json");
    private long lastCmdTime = 0;

    private void checkCommands() {
        try {
            if (!java.nio.file.Files.exists(CMD_FILE)) return;
            com.google.gson.JsonObject j = com.google.gson.JsonParser.parseString(
                java.nio.file.Files.readString(CMD_FILE)).getAsJsonObject();
            long t = j.has("time") ? j.get("time").getAsLong() : 0;
            if (t <= lastCmdTime) return;
            lastCmdTime = t;
            java.nio.file.Files.deleteIfExists(CMD_FILE);
            switch (j.has("cmd") ? j.get("cmd").getAsString() : "") {
                case "playpause" -> playPause();
                case "next"      -> nextTrack();
                case "prev"      -> prevTrack();
                case "seek" -> {
                    if (j.has("position")) seek((int) j.get("position").getAsLong());
                }
            }
        } catch (Exception ignored) {}
    }

    private void startPolling() {
        ScheduledExecutorService s = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "spotify-poll"); t.setDaemon(true); return t;
        });
        s.scheduleAtFixedRate(this::fetchCurrentTrack, 0, 1, TimeUnit.SECONDS);
        s.scheduleAtFixedRate(this::checkCommands, 0, 300, TimeUnit.MILLISECONDS);
    }

    public void fetchCurrentTrack() {
        try {
            TrackInfo track;
            if (webAPI.hasToken()) {
                try {
                    track = webAPI.fetchCurrentTrack();
                } catch (Exception webEx) {
                    track = isLinux() ? fetchLinux() :
                            isWindows() ? fetchWindows() : fetchMac();
                }
            } else {
                track = isLinux() ? fetchLinux() :
                        isWindows() ? fetchWindows() : fetchMac();
            }
            if (track == null) return;
            // Progress için her zaman yaz
            SpotifyFileWriter.write(track);
            if (lastTrack == null || !lastTrack.title().equals(track.title())
                || lastTrack.playing() != track.playing()) {
                lastTrack = track;
                if (onTrackChanged != null) onTrackChanged.accept(track);
                System.out.println("🎵 " + (track.playing() ? "▶" : "⏸")
                    + " " + track.artist() + " - " + track.title());
            }
        } catch (Exception e) {
            if (connected) {
                connected = false; lastTrack = null;
                SpotifyFileWriter.clear();
                System.out.println("⚠ Spotify kapandı.");
                connect();
            }
        }
    }

    private TrackInfo fetchLinux() throws Exception {
        String meta   = dbus("Metadata");
        String status = dbus("PlaybackStatus");
        String pos    = dbus("Position");
        if (meta == null) return null;
        String title    = parseDbusString(meta, "xesam:title");
        String artist   = parseDbusString(meta, "xesam:artist");
        String albumArt = parseDbusString(meta, "mpris:artUrl");
        String album    = parseDbusString(meta, "xesam:album");
        long   length   = parseDbusLong(meta, "uint64");
        boolean playing = status != null && status.contains("\"Playing\"");
        long position   = pos != null ? parseDbusLong(pos, "int64") : 0;
        if (title == null || title.isEmpty()) return null;
        return new TrackInfo(title,
            artist   != null ? artist   : "Bilinmiyor",
            albumArt != null ? albumArt : "",
            album    != null ? album    : "",
            playing, (int)(position/1000), (int)(length/1000));
    }

    private TrackInfo fetchWindows() throws Exception {
        Process p = Runtime.getRuntime().exec(new String[]{"powershell","-Command",
            "Get-Process Spotify | Where-Object {$_.MainWindowTitle -ne ''} | Select-Object -ExpandProperty MainWindowTitle"});
        p.waitFor(3, TimeUnit.SECONDS);
        String title = new String(p.getInputStream().readAllBytes()).trim();
        if (title.isEmpty() || title.equals("Spotify")) return null;
        String artist = "Bilinmiyor", song = title;
        if (title.contains(" - ")) {
            String[] parts = title.split(" - ", 2);
            artist = parts[0].trim(); song = parts[1].trim();
        }
        return new TrackInfo(song, artist, "", "", true, 0, 1);
    }

    private TrackInfo fetchMac() throws Exception {
        Process p = Runtime.getRuntime().exec(new String[]{"osascript","-e",
            "tell application \"Spotify\" to return (name of current track) & \"|\" & (artist of current track)"});
        p.waitFor(3, TimeUnit.SECONDS);
        String out = new String(p.getInputStream().readAllBytes()).trim();
        if (out.isEmpty()) return null;
        String[] parts = out.split("\\|");
        return new TrackInfo(parts[0].trim(), parts.length>1?parts[1].trim():"", "", "", true, 0, 1);
    }

    private String dbus(String property) throws Exception {
        Process p = Runtime.getRuntime().exec(new String[]{
            "dbus-send","--print-reply",
            "--dest=org.mpris.MediaPlayer2.spotify",
            "/org/mpris/MediaPlayer2",
            "org.freedesktop.DBus.Properties.Get",
            "string:org.mpris.MediaPlayer2.Player",
            "string:" + property
        });
        p.waitFor(2, TimeUnit.SECONDS);
        return new String(p.getInputStream().readAllBytes());
    }

    public void playPause() {
        if (webAPI.hasToken()) {
            try { webAPI.playPause(lastTrack != null && lastTrack.playing()); return; } catch (Exception ignored) {}
        }
        if (isLinux())   dbusCmd("PlayPause");
        else if (isWindows()) spotifyKey(new String[]{"powershell","-Command",
            "(New-Object -ComObject WScript.Shell).SendKeys(' ')"});
        else if (isMac()) osascript("tell application \"Spotify\" to playpause");
    }
    public void nextTrack() {
        if (webAPI.hasToken()) {
            try { webAPI.next(); return; } catch (Exception ignored) {}
        }
        if (isLinux())   dbusCmd("Next");
        else if (isWindows()) spotifyKey(new String[]{"powershell","-Command",
            "(New-Object -ComObject WScript.Shell).SendKeys('%{RIGHT}')"});
        else if (isMac()) osascript("tell application \"Spotify\" to next track");
    }
    public void seek(int positionMs) {
        if (webAPI.hasToken()) {
            try { webAPI.seek(positionMs); return; } catch (Exception ignored) {}
        }
        if (isLinux()) {
            try {
                double secs = positionMs / 1000.0;
                String pos = String.format(java.util.Locale.US, "%.1f", secs);
                System.out.println("🎵 playerctl seek: " + pos);
                Process p = Runtime.getRuntime().exec(new String[]{"playerctl", "-p", "spotify", "position", pos});
                p.waitFor();
                System.out.println("🎵 playerctl done: " + p.exitValue());
            } catch (Exception e) { System.out.println("🎵 playerctl hata: " + e.getMessage()); }
        }
    }
    public void seekDbus(int positionMs) { seek(positionMs); }
    public void prevTrack() {
        if (webAPI.hasToken()) {
            try { webAPI.previous(); return; } catch (Exception ignored) {}
        }
        if (isLinux())   dbusCmd("Previous");
        else if (isWindows()) spotifyKey(new String[]{"powershell","-Command",
            "(New-Object -ComObject WScript.Shell).SendKeys('%{LEFT}')"});
        else if (isMac()) osascript("tell application \"Spotify\" to previous track");
    }
    private void spotifyKey(String[] cmd) {
        try { Runtime.getRuntime().exec(cmd); } catch (Exception ignored) {}
    }
    private void osascript(String script) {
        try { Runtime.getRuntime().exec(new String[]{"osascript", "-e", script}); } catch (Exception ignored) {}
    }

    private void dbusCmd(String cmd) {
        try { Runtime.getRuntime().exec(new String[]{
            "dbus-send","--print-reply",
            "--dest=org.mpris.MediaPlayer2.spotify",
            "/org/mpris/MediaPlayer2",
            "org.mpris.MediaPlayer2.Player." + cmd
        }); } catch (Exception ignored) {}
    }

    private String parseDbusString(String output, String key) {
        try {
            int idx = output.indexOf("\"" + key + "\"");
            if (idx < 0) return null;
            int start = output.indexOf("string \"", idx) + 8;
            int end   = output.indexOf("\"", start);
            return output.substring(start, end);
        } catch (Exception e) { return null; }
    }

    private long parseDbusLong(String output, String type) {
        try {
            String[] tokens = output.split("\\s+");
            for (int i = 0; i < tokens.length-1; i++) {
                if (tokens[i].contains(type)) {
                    String num = tokens[i+1].replaceAll("[^0-9]","");
                    if (!num.isEmpty()) return Long.parseLong(num);
                }
            }
        } catch (Exception ignored) {}
        return 0;
    }

    private boolean isLinux()   { return OS.contains("nux") || OS.contains("nix"); }
    private boolean isWindows() { return OS.contains("win"); }
    private boolean isMac()     { return OS.contains("mac"); }

    public boolean isConnected()    { return connected; }
    public TrackInfo getLastTrack() { return lastTrack; }

    public record TrackInfo(String title, String artist, String albumArt,
                             String album, boolean playing,
                             int progressMs, int durationMs) {}
}
