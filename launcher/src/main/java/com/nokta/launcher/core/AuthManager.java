package com.nokta.launcher.core;

import com.google.gson.*;
import okhttp3.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

public class AuthManager {

    private static final String CLIENT_ID    = "00000000402b5328";
    private static final String REDIRECT_URI = "https://login.live.com/oauth20_desktop.srf";
    private static final String AUTH_URL     =
        "https://login.live.com/oauth20_authorize.srf" +
        "?client_id=" + CLIENT_ID +
        "&response_type=code" +
        "&redirect_uri=https%3A%2F%2Flogin.live.com%2Foauth20_desktop.srf" +
        "&scope=XboxLive.signin%20offline_access";

    private final OkHttpClient http = new OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build();
    private final Gson  gson     = new Gson();
    private final Path  saveFile;
    private Account     currentAccount;

    public AuthManager(Path gameDir) {
        this.saveFile = gameDir.resolve("accounts.json");
        loadAccounts();
    }

    // ── Offline ──────────────────────────────────────────────────────
    public Account loginOffline(String username) {
        String uuid = UUID.nameUUIDFromBytes(
            ("OfflinePlayer:" + username).getBytes()).toString();
        currentAccount = new Account(username, uuid, "offline", "0", false);
        saveAccounts();
        return currentAccount;
    }

    // ── Microsoft — otomatik kod yakalama ───────────────────────────
    public String getMicrosoftAuthUrl() { return AUTH_URL; }

    /**
     * Tarayıcıyı aç, localhost:9876'da auth code'u bekle.
     * Callback: code yakalandığında loginMicrosoft() çağır.
     */
    /**
     * JavaFX WebView ile OAuth — redirect URI'yi intercept eder.
     * JavaFX Application Thread'den çağrılmalı.
     */
    public void startMicrosoftAuthFlow(ProgressCallback cb,
                                       AuthSuccessCallback onSuccess,
                                       ErrorCallback onError) {
        // JavaFX thread'de WebView aç
        javafx.application.Platform.runLater(() -> {
            javafx.scene.web.WebView webView = new javafx.scene.web.WebView();
            javafx.scene.web.WebEngine engine = webView.getEngine();

            javafx.stage.Stage dialog = new javafx.stage.Stage();
            dialog.setTitle("Microsoft ile Giriş Yap");
            dialog.setWidth(520);
            dialog.setHeight(680);
            dialog.initModality(javafx.stage.Modality.APPLICATION_MODAL);

            webView.setPrefSize(520, 640);
            engine.load(AUTH_URL);
            cb.update("Microsoft giriş sayfası yükleniyor...", 10);

            // URL değişimini dinle — redirect_uri'ye yönlenince code'u yakala
            engine.locationProperty().addListener((obs, oldUrl, newUrl) -> {
                if (newUrl != null && newUrl.startsWith(REDIRECT_URI)) {
                    dialog.close();
                    if (newUrl.contains("code=")) {
                        try {
                            String code = newUrl.split("code=")[1].split("[&]")[0];
                            cb.update("Kod alındı, giriş yapılıyor...", 25);
                            new Thread(() ->
                                loginMicrosoft(code, cb, onSuccess, onError),
                                "ms-auth").start();
                        } catch (Exception e) {
                            onError.onError("Kod ayrıştırılamadı: " + e.getMessage());
                        }
                    } else if (newUrl.contains("error=")) {
                        onError.onError("Giriş iptal edildi.");
                    }
                }
            });

            javafx.scene.Scene scene = new javafx.scene.Scene(
                new javafx.scene.layout.StackPane(webView));
            dialog.setScene(scene);
            dialog.show();
        });
    }

    private void loginMicrosoft(String code, ProgressCallback cb,
                                 AuthSuccessCallback onSuccess,
                                 ErrorCallback onError) {
        try {
            cb.update("Microsoft token alınıyor...", 30);
            String msToken = getMsAccessToken(code);

            cb.update("Xbox Live girişi...", 50);
            String[] xbox = getXboxToken(msToken);

            cb.update("XSTS token...", 65);
            String xsts = getXstsToken(xbox[0]);

            cb.update("Minecraft girişi...", 80);
            String mcToken = getMinecraftToken(xsts, xbox[1]);

            cb.update("Profil yükleniyor...", 90);
            String[] profile = getMinecraftProfile(mcToken);

            currentAccount = new Account(profile[0], profile[1], "msa", mcToken, true);
            saveAccounts();

            cb.update("Giriş başarılı! ✅", 100);
            onSuccess.onSuccess(currentAccount);
        } catch (Exception e) {
            onError.onError(e.getMessage());
        }
    }

    // ── Cross-platform tarayıcı aç ──────────────────────────────────
    public static void openBrowser(String url) throws Exception {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            Runtime.getRuntime().exec(new String[]{"cmd", "/c", "start", url});
        } else if (os.contains("mac")) {
            Runtime.getRuntime().exec(new String[]{"open", url});
        } else {
            // Linux
            for (String browser : new String[]{"xdg-open","gnome-open","kde-open","firefox","chromium-browser"}) {
                try {
                    Runtime.getRuntime().exec(new String[]{browser, url});
                    return;
                } catch (Exception ignored) {}
            }
        }
    }

    // ── Hesap bilgileri ──────────────────────────────────────────────
    public Account getCurrentAccount() { return currentAccount; }
    public boolean isLoggedIn()        { return currentAccount != null; }
    public void logout()               { currentAccount = null; saveAccounts(); }

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
            if (obj.has("username"))
                currentAccount = new Account(
                    obj.get("username").getAsString(),
                    obj.get("uuid").getAsString(),
                    obj.get("type").getAsString(),
                    obj.get("accessToken").getAsString(),
                    obj.get("microsoft").getAsBoolean());
        } catch (Exception e) { e.printStackTrace(); }
    }

    // ── OAuth adımları ───────────────────────────────────────────────
    private String getMsAccessToken(String code) throws Exception {
        RequestBody body = new FormBody.Builder()
            .add("client_id",    CLIENT_ID)
            .add("code",         code)
            .add("grant_type",   "authorization_code")
            .add("redirect_uri", REDIRECT_URI)
            .build();
        try (Response res = http.newCall(new Request.Builder()
                .url("https://login.live.com/oauth20_token.srf")
                .post(body).build()).execute()) {
            JsonObject j = JsonParser.parseString(res.body().string()).getAsJsonObject();
            if (!j.has("access_token")) throw new Exception("MS token alınamadı.");
            return j.get("access_token").getAsString();
        }
    }

    private String[] getXboxToken(String msToken) throws Exception {
        JsonObject props = new JsonObject();
        props.addProperty("AuthMethod", "RPS");
        props.addProperty("SiteName",   "user.auth.xboxlive.com");
        props.addProperty("RpsTicket",  "d=" + msToken);
        JsonObject payload = new JsonObject();
        payload.add("Properties",   props);
        payload.addProperty("RelyingParty", "http://auth.xboxlive.com");
        payload.addProperty("TokenType",    "JWT");
        try (Response res = http.newCall(new Request.Builder()
                .url("https://user.auth.xboxlive.com/user/authenticate")
                .post(RequestBody.create(gson.toJson(payload), MediaType.parse("application/json")))
                .addHeader("Accept", "application/json").build()).execute()) {
            JsonObject j = JsonParser.parseString(res.body().string()).getAsJsonObject();
            return new String[]{
                j.get("Token").getAsString(),
                j.getAsJsonObject("DisplayClaims").getAsJsonArray("xui")
                 .get(0).getAsJsonObject().get("uhs").getAsString()
            };
        }
    }

    private String getXstsToken(String xblToken) throws Exception {
        JsonObject props = new JsonObject();
        props.addProperty("SandboxId", "RETAIL");
        JsonArray tokens = new JsonArray(); tokens.add(xblToken);
        props.add("UserTokens", tokens);
        JsonObject payload = new JsonObject();
        payload.add("Properties",   props);
        payload.addProperty("RelyingParty", "rp://api.minecraftservices.com/");
        payload.addProperty("TokenType",    "JWT");
        try (Response res = http.newCall(new Request.Builder()
                .url("https://xsts.auth.xboxlive.com/xsts/authorize")
                .post(RequestBody.create(gson.toJson(payload), MediaType.parse("application/json")))
                .addHeader("Accept", "application/json").build()).execute()) {
            JsonObject j = JsonParser.parseString(res.body().string()).getAsJsonObject();
            if (!j.has("Token")) throw new Exception("XSTS hatası — hesapta Minecraft olmayabilir.");
            return j.get("Token").getAsString();
        }
    }

    private String getMinecraftToken(String xsts, String uhs) throws Exception {
        JsonObject payload = new JsonObject();
        payload.addProperty("identityToken", "XBL3.0 x=" + uhs + ";" + xsts);
        try (Response res = http.newCall(new Request.Builder()
                .url("https://api.minecraftservices.com/authentication/login_with_xbox")
                .post(RequestBody.create(gson.toJson(payload), MediaType.parse("application/json")))
                .build()).execute()) {
            return JsonParser.parseString(res.body().string())
                .getAsJsonObject().get("access_token").getAsString();
        }
    }

    private String[] getMinecraftProfile(String mcToken) throws Exception {
        try (Response res = http.newCall(new Request.Builder()
                .url("https://api.minecraftservices.com/minecraft/profile")
                .addHeader("Authorization", "Bearer " + mcToken).build()).execute()) {
            JsonObject j = JsonParser.parseString(res.body().string()).getAsJsonObject();
            if (!j.has("name")) throw new Exception("Bu hesapta Minecraft satın alınmamış!");
            return new String[]{j.get("name").getAsString(), j.get("id").getAsString()};
        }
    }

    // ── Model ────────────────────────────────────────────────────────
    public static class Account {
        public final String username, uuid, type, accessToken;
        public final boolean microsoft;
        public Account(String u, String id, String t, String tok, boolean ms) {
            username=u; uuid=id; type=t; accessToken=tok; microsoft=ms;
        }
    }

    public interface ProgressCallback  { void update(String msg, int pct); }
    public interface AuthSuccessCallback { void onSuccess(Account acc); }
    public interface ErrorCallback     { void onError(String msg); }
}
