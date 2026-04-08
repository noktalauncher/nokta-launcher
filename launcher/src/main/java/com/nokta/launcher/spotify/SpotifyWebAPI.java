package com.nokta.launcher.spotify;

import com.google.gson.*;
import com.nokta.launcher.utils.PathManager;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

public class SpotifyWebAPI {

    private static final String CLIENT_ID     = "8cb3d71242be4abfade2bc1fbcf5c9d6";
    private static final String CLIENT_SECRET = "a6c13d4b594a42239e6a5ecd896728aa";
    private static final String REDIRECT_URI  = "http://127.0.0.1:8888/callback";
    private static final String SCOPE         = "user-read-playback-state user-modify-playback-state";
    private static final Path   TOKEN_FILE    = PathManager.getGameDir().resolve("spotify_token.json");

    private final HttpClient http = HttpClient.newHttpClient();
    private String accessToken;
    private String refreshToken;
    private Instant tokenExpiry = Instant.EPOCH;

    // ─── Token yönetimi ───────────────────────────────────────
    public boolean hasToken() {
        if (accessToken != null && Instant.now().isBefore(tokenExpiry)) return true;
        return loadToken();
    }

    private boolean loadToken() {
        try {
            if (!Files.exists(TOKEN_FILE)) return false;
            JsonObject j = JsonParser.parseString(Files.readString(TOKEN_FILE)).getAsJsonObject();
            accessToken  = j.get("access_token").getAsString();
            refreshToken = j.get("refresh_token").getAsString();
            tokenExpiry  = Instant.ofEpochMilli(j.get("expiry_ms").getAsLong());
            if (Instant.now().isAfter(tokenExpiry)) return refreshAccessToken();
            return true;
        } catch (Exception e) { return false; }
    }

    private void saveToken() {
        try {
            JsonObject j = new JsonObject();
            j.addProperty("access_token",  accessToken);
            j.addProperty("refresh_token", refreshToken);
            j.addProperty("expiry_ms",     tokenExpiry.toEpochMilli());
            Files.writeString(TOKEN_FILE, j.toString());
        } catch (Exception ignored) {}
    }

    public boolean refreshAccessToken() {
        try {
            String body = "grant_type=refresh_token&refresh_token=" + URLEncoder.encode(refreshToken, "UTF-8");
            String auth = Base64.getEncoder().encodeToString((CLIENT_ID + ":" + CLIENT_SECRET).getBytes());
            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("https://accounts.spotify.com/api/token"))
                .header("Authorization", "Basic " + auth)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            JsonObject j = JsonParser.parseString(res.body()).getAsJsonObject();
            if (!j.has("access_token")) return false;
            accessToken = j.get("access_token").getAsString();
            tokenExpiry = Instant.now().plusSeconds(j.get("expires_in").getAsInt() - 60);
            saveToken();
            return true;
        } catch (Exception e) { return false; }
    }

    // ─── OAuth login (tarayıcı açılır, token alınır) ──────────
    public boolean login() throws Exception {
        String state = UUID.randomUUID().toString().substring(0, 8);
        String authUrl = "https://accounts.spotify.com/authorize"
            + "?client_id=" + CLIENT_ID
            + "&response_type=code"
            + "&redirect_uri=" + URLEncoder.encode(REDIRECT_URI, "UTF-8")
            + "&scope=" + URLEncoder.encode(SCOPE, "UTF-8")
            + "&state=" + state;

        // Tarayıcı aç
        openBrowser(authUrl);

        // Localhost'ta callback dinle
        String code = listenForCode(state);
        if (code == null) return false;

        // Code → token
        return exchangeCode(code);
    }

    private void openBrowser(String url) {
        try {
            String os = System.getProperty("os.name").toLowerCase();
            if (os.contains("win"))       Runtime.getRuntime().exec(new String[]{"rundll32", "url.dll,FileProtocolHandler", url});
            else if (os.contains("mac"))  Runtime.getRuntime().exec(new String[]{"open", url});
            else                          Runtime.getRuntime().exec(new String[]{"xdg-open", url});
        } catch (Exception ignored) {}
    }

    private String listenForCode(String expectedState) throws Exception {
        ServerSocket server = new ServerSocket(8888);
        server.setSoTimeout(120000); // 2 dakika bekle
        try (Socket client = server.accept()) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(client.getInputStream()));
            String line = reader.readLine();
            System.out.println("🔑 Callback satırı: " + line);
            if (line == null) return null;
            String path2 = line.split(" ")[1]; // /?code=...&state=...
            int qIdx = path2.indexOf('?');
            if (qIdx < 0) return null;
            String query = path2.substring(qIdx + 1);
            Map<String, String> params = new HashMap<>();
            for (String p : query.split("&")) {
                String[] kv = p.split("=", 2);
                if (kv.length == 2) params.put(kv[0], URLDecoder.decode(kv[1], "UTF-8"));
            }
            System.out.println("🔑 Code: " + params.get("code"));
            // Başarılı sayfası göster
            String html = "<html><head><meta charset=\"UTF-8\"></head><body style='font-family:sans-serif;text-align:center;padding:60px;background:#111;color:#fff'>"
                + "<h2 style='color:#1db954'>Nokta Launcher'a baglandi!</h2>"
                + "<p>Bu pencereyi kapatabilirsiniz.</p></body></html>";
            PrintWriter writer = new PrintWriter(client.getOutputStream());
            writer.println("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=UTF-8\r\n\r\n" + html);
            writer.flush();
            if (!expectedState.equals(params.get("state"))) return null;
            return params.get("code");
        } finally {
            server.close();
        }
    }

    private boolean exchangeCode(String code) throws Exception {
        String body = "grant_type=authorization_code"
            + "&code=" + URLEncoder.encode(code, "UTF-8")
            + "&redirect_uri=" + URLEncoder.encode(REDIRECT_URI, "UTF-8");
        String auth = Base64.getEncoder().encodeToString((CLIENT_ID + ":" + CLIENT_SECRET).getBytes());
        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create("https://accounts.spotify.com/api/token"))
            .header("Authorization", "Basic " + auth)
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        System.out.println("🔑 Spotify token yanıtı: " + res.statusCode() + " " + res.body());
        JsonObject j = JsonParser.parseString(res.body()).getAsJsonObject();
        if (!j.has("access_token")) return false;
        accessToken  = j.get("access_token").getAsString();
        refreshToken = j.get("refresh_token").getAsString();
        tokenExpiry  = Instant.now().plusSeconds(j.get("expires_in").getAsInt() - 60);
        saveToken();
        return true;
    }

    // ─── Mevcut şarkı bilgisi ──────────────────────────────────
    public SpotifyManager.TrackInfo fetchCurrentTrack() throws Exception {
        ensureToken();
        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create("https://api.spotify.com/v1/me/player/currently-playing"))
            .header("Authorization", "Bearer " + accessToken)
            .GET().build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() == 204 || res.body().isEmpty()) return null;
        if (res.statusCode() == 401) { refreshAccessToken(); return fetchCurrentTrack(); }
        JsonObject j = JsonParser.parseString(res.body()).getAsJsonObject();
        if (!j.has("item") || j.get("item").isJsonNull()) return null;
        JsonObject item    = j.getAsJsonObject("item");
        String title       = item.get("name").getAsString();
        String artist      = item.getAsJsonArray("artists").get(0).getAsJsonObject().get("name").getAsString();
        String album       = item.getAsJsonObject("album").get("name").getAsString();
        String albumArt    = "";
        JsonArray images   = item.getAsJsonObject("album").getAsJsonArray("images");
        if (images.size() > 0) albumArt = images.get(0).getAsJsonObject().get("url").getAsString();
        boolean playing    = j.get("is_playing").getAsBoolean();
        int progressMs     = j.get("progress_ms").getAsInt();
        int durationMs     = item.get("duration_ms").getAsInt();
        return new SpotifyManager.TrackInfo(title, artist, albumArt, album, playing, progressMs, durationMs);
    }

    // ─── Kontroller ───────────────────────────────────────────
    public void playPause(boolean isPlaying) throws Exception {
        ensureToken();
        String endpoint = isPlaying ? "pause" : "play";
        sendControl("https://api.spotify.com/v1/me/player/" + endpoint, "PUT", "");
    }
    public void next()     throws Exception { ensureToken(); sendControl("https://api.spotify.com/v1/me/player/next", "POST", ""); }
    public void previous() throws Exception { ensureToken(); sendControl("https://api.spotify.com/v1/me/player/previous", "POST", ""); }
    public void seek(int positionMs) throws Exception {
        ensureToken();
        sendControl("https://api.spotify.com/v1/me/player/seek?position_ms=" + positionMs, "PUT", "");
    }

    private void sendControl(String url, String method, String body) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Authorization", "Bearer " + accessToken)
            .header("Content-Type", "application/json")
            .method(method, HttpRequest.BodyPublishers.ofString(body))
            .build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        System.out.println("🎵 Kontrol yaniti: " + res.statusCode() + " " + url);
        if (res.statusCode() == 401) { refreshAccessToken(); sendControl(url, method, body); return; }
        if (res.statusCode() == 403) throw new Exception("Premium gerekli, fallback kullanilacak");
        if (res.statusCode() >= 400) throw new Exception("Spotify API hata: " + res.statusCode());
    }

    private void ensureToken() throws Exception {
        if (accessToken == null || Instant.now().isAfter(tokenExpiry)) {
            if (!refreshAccessToken()) throw new Exception("Token yenilenemedi");
        }
    }
    public String[] fetchUserProfile() throws Exception {
        ensureToken();
        System.out.println("👤 Profil çekiliyor... token: " + (accessToken != null ? accessToken.substring(0,10) : "null"));
        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create("https://api.spotify.com/v1/me"))
            .timeout(java.time.Duration.ofSeconds(5))
            .header("Authorization", "Bearer " + accessToken)
            .GET().build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        System.out.println("👤 Profil yanıtı: " + res.statusCode() + " " + res.body().substring(0, Math.min(100, res.body().length())));
        if (res.statusCode() == 403 || res.statusCode() == 401) {
            System.out.println("👤 Profil erişimi yok (free hesap), atlanıyor.");
            return null;
        }
        JsonObject j = JsonParser.parseString(res.body()).getAsJsonObject();
        String name = j.has("display_name") ? j.get("display_name").getAsString() : "Kullanıcı";
        String imageUrl = "";
        if (j.has("images") && j.getAsJsonArray("images").size() > 0)
            imageUrl = j.getAsJsonArray("images").get(0).getAsJsonObject().get("url").getAsString();
        System.out.println("👤 Profil: " + name + " | PP: " + imageUrl);
        return new String[]{name, imageUrl};
    }

}
