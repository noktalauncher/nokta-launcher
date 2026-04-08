package com.nokta.launcher.core;

import com.google.gson.*;
import okhttp3.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;

public class AuthManager {

    // Microsoft Azure App Client ID
    // (Gerçek yayında kendi Azure app'ini oluşturman gerekir)
    private static final String CLIENT_ID    = "00000000402b5328";
    private static final String REDIRECT_URI = "https://login.live.com/oauth20_desktop.srf";
    private static final String AUTH_URL     =
        "https://login.live.com/oauth20_authorize.srf" +
        "?client_id=" + CLIENT_ID +
        "&response_type=code" +
        "&redirect_uri=" + REDIRECT_URI +
        "&scope=XboxLive.signin%20offline_access";

    private final OkHttpClient http  = new OkHttpClient();
    private final Gson         gson  = new Gson();
    private final Path         saveFile;

    private Account currentAccount;

    public AuthManager(Path gameDir) {
        this.saveFile = gameDir.resolve("accounts.json");
        loadAccounts();
    }

    // ── Offline Giriş ────────────────────────────────────────────────
    public Account loginOffline(String username) {
        String uuid = UUID.nameUUIDFromBytes(
            ("OfflinePlayer:" + username).getBytes()).toString();
        currentAccount = new Account(username, uuid, "offline", "0", false);
        saveAccounts();
        return currentAccount;
    }

    // ── Microsoft Giriş URL'si döndür ───────────────────────────────
    public String getMicrosoftAuthUrl() {
        return AUTH_URL;
    }

    // ── Microsoft Auth Code ile giriş yap ───────────────────────────
    public Account loginMicrosoft(String authCode,
                                   ProgressCallback cb) throws Exception {
        cb.update("Microsoft token alınıyor...", 10);

        // 1) Auth code → Access token
        String msToken = getMsAccessToken(authCode);
        cb.update("Xbox Live girişi yapılıyor...", 30);

        // 2) Xbox Live token
        String[] xboxResult = getXboxToken(msToken);
        String xblToken = xboxResult[0];
        String userHash = xboxResult[1];
        cb.update("XSTS token alınıyor...", 50);

        // 3) XSTS token
        String xstsToken = getXstsToken(xblToken);
        cb.update("Minecraft girişi yapılıyor...", 70);

        // 4) Minecraft token
        String mcToken = getMinecraftToken(xstsToken, userHash);
        cb.update("Profil bilgileri alınıyor...", 85);

        // 5) Profil (isim + UUID)
        String[] profile = getMinecraftProfile(mcToken);
        String username = profile[0];
        String uuid     = profile[1];
        cb.update("Giriş başarılı! ✅", 100);

        currentAccount = new Account(username, uuid, "msa", mcToken, true);
        saveAccounts();
        return currentAccount;
    }

    // ── Mevcut hesabı döndür ─────────────────────────────────────────
    public Account getCurrentAccount() { return currentAccount; }
    public boolean isLoggedIn() { return currentAccount != null; }

    // ── Çıkış yap ────────────────────────────────────────────────────
    public void logout() {
        currentAccount = null;
        saveAccounts();
    }

    // ── Kayıt/Yükleme ────────────────────────────────────────────────
    private void saveAccounts() {
        try {
            JsonObject obj = new JsonObject();
            if (currentAccount != null) {
                obj.addProperty("username",    currentAccount.username);
                obj.addProperty("uuid",        currentAccount.uuid);
                obj.addProperty("type",        currentAccount.type);
                obj.addProperty("accessToken", currentAccount.accessToken);
                obj.addProperty("microsoft",   currentAccount.microsoft);
            }
            Files.writeString(saveFile, gson.toJson(obj));
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void loadAccounts() {
        try {
            if (!Files.exists(saveFile)) return;
            JsonObject obj = JsonParser.parseString(
                Files.readString(saveFile)).getAsJsonObject();
            if (obj.has("username")) {
                currentAccount = new Account(
                    obj.get("username").getAsString(),
                    obj.get("uuid").getAsString(),
                    obj.get("type").getAsString(),
                    obj.get("accessToken").getAsString(),
                    obj.get("microsoft").getAsBoolean()
                );
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    // ── Microsoft OAuth adımları ─────────────────────────────────────
    private String getMsAccessToken(String code) throws Exception {
        RequestBody body = new FormBody.Builder()
            .add("client_id",    CLIENT_ID)
            .add("code",         code)
            .add("grant_type",   "authorization_code")
            .add("redirect_uri", REDIRECT_URI)
            .build();
        Request req = new Request.Builder()
            .url("https://login.live.com/oauth20_token.srf")
            .post(body).build();
        try (Response res = http.newCall(req).execute()) {
            JsonObject json = JsonParser.parseString(
                res.body().string()).getAsJsonObject();
            if (!json.has("access_token"))
                throw new Exception("MS token alınamadı! Lütfen tekrar giriş yapın.");
            return json.get("access_token").getAsString();
        }
    }

    private String[] getXboxToken(String msToken) throws Exception {
        JsonObject props = new JsonObject();
        props.addProperty("AuthMethod", "RPS");
        props.addProperty("SiteName", "user.auth.xboxlive.com");
        props.addProperty("RpsTicket", "d=" + msToken);

        JsonObject payload = new JsonObject();
        payload.add("Properties", props);
        payload.addProperty("RelyingParty", "http://auth.xboxlive.com");
        payload.addProperty("TokenType", "JWT");

        Request req = new Request.Builder()
            .url("https://user.auth.xboxlive.com/user/authenticate")
            .post(RequestBody.create(gson.toJson(payload),
                MediaType.parse("application/json")))
            .addHeader("Accept", "application/json")
            .build();

        try (Response res = http.newCall(req).execute()) {
            JsonObject json = JsonParser.parseString(
                res.body().string()).getAsJsonObject();
            String token    = json.get("Token").getAsString();
            String userHash = json.getAsJsonObject("DisplayClaims")
                .getAsJsonArray("xui").get(0)
                .getAsJsonObject().get("uhs").getAsString();
            return new String[]{token, userHash};
        }
    }

    private String getXstsToken(String xblToken) throws Exception {
        JsonObject props = new JsonObject();
        props.addProperty("SandboxId", "RETAIL");
        JsonArray tokens = new JsonArray();
        tokens.add(xblToken);
        props.add("UserTokens", tokens);

        JsonObject payload = new JsonObject();
        payload.add("Properties", props);
        payload.addProperty("RelyingParty", "rp://api.minecraftservices.com/");
        payload.addProperty("TokenType", "JWT");

        Request req = new Request.Builder()
            .url("https://xsts.auth.xboxlive.com/xsts/authorize")
            .post(RequestBody.create(gson.toJson(payload),
                MediaType.parse("application/json")))
            .addHeader("Accept", "application/json")
            .build();

        try (Response res = http.newCall(req).execute()) {
            JsonObject json = JsonParser.parseString(
                res.body().string()).getAsJsonObject();
            if (!json.has("Token"))
                throw new Exception("XSTS hatası — hesapta Minecraft olmayabilir.");
            return json.get("Token").getAsString();
        }
    }

    private String getMinecraftToken(String xstsToken,
                                      String userHash) throws Exception {
        JsonObject payload = new JsonObject();
        payload.addProperty("identityToken",
            "XBL3.0 x=" + userHash + ";" + xstsToken);

        Request req = new Request.Builder()
            .url("https://api.minecraftservices.com/authentication/login_with_xbox")
            .post(RequestBody.create(gson.toJson(payload),
                MediaType.parse("application/json")))
            .build();

        try (Response res = http.newCall(req).execute()) {
            JsonObject json = JsonParser.parseString(
                res.body().string()).getAsJsonObject();
            return json.get("access_token").getAsString();
        }
    }

    private String[] getMinecraftProfile(String mcToken) throws Exception {
        Request req = new Request.Builder()
            .url("https://api.minecraftservices.com/minecraft/profile")
            .addHeader("Authorization", "Bearer " + mcToken)
            .build();

        try (Response res = http.newCall(req).execute()) {
            JsonObject json = JsonParser.parseString(
                res.body().string()).getAsJsonObject();
            if (!json.has("name"))
                throw new Exception("Bu hesapta Minecraft satın alınmamış!");
            return new String[]{
                json.get("name").getAsString(),
                json.get("id").getAsString()
            };
        }
    }

    // ── Veri modelleri ───────────────────────────────────────────────
    public static class Account {
        public final String  username, uuid, type, accessToken;
        public final boolean microsoft;

        public Account(String username, String uuid, String type,
                       String accessToken, boolean microsoft) {
            this.username    = username;
            this.uuid        = uuid;
            this.type        = type;
            this.accessToken = accessToken;
            this.microsoft   = microsoft;
        }
    }

    public interface ProgressCallback {
        void update(String message, int percent);
    }
}
