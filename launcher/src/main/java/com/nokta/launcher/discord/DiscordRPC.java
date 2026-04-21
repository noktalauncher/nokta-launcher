package com.nokta.launcher.discord;

import com.google.gson.*;
import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

public class DiscordRPC {

    private static final String CLIENT_ID     = "1479493157723836558";
    private static final String CLIENT_SECRET = "gSJO1IYdBHUWE_-KcMFAOlUbBNZdfsi6";
    private static final Path   TOKEN_FILE    = Paths.get(
        System.getProperty("user.home"), ".nokta-launcher", "discord_token.json");

    // ── Durum enum'ları ──────────────────────────────────────────────
    public enum RpcState { LAUNCHER, PLAYING, ON_SERVER, SINGLEPLAYER, BROWSING_MODS }
    private RpcState currentState = RpcState.LAUNCHER;

    private SocketChannel socket;
    private boolean connected  = false;
    private boolean authorized = false;
    private String  accessToken  = null;
    private String  refreshToken = null;
    private String  currentChannel   = null;
    private String  currentChannelId = null;
    private String  myUserId = null;

    private final Set<String> speakingUsers =
        Collections.synchronizedSet(new HashSet<>());
    private final ConcurrentHashMap<String, CompletableFuture<JsonObject>> pending =
        new ConcurrentHashMap<>();

    private Consumer<List<VoiceUser>> onVoiceUsersChanged;
    private final ScheduledExecutorService scheduler =
        Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "discord-scheduler");
            t.setDaemon(true); return t;
        });

    private long   startTime        = System.currentTimeMillis() / 1000;
    private String currentMcVersion = "";
    private String currentLoader    = "";
    private String currentServerIp  = "";

    public void setOnVoiceUsersChanged(Consumer<List<VoiceUser>> cb) {
        this.onVoiceUsersChanged = cb;
    }

    // ── Sürüm → Discord asset key ────────────────────────────────────
    // Discord Developer Portal'a yüklenecek görsel isimleri:
    //   nokta_logo   → N logosu (her zaman yüklü olmalı)
    //   mc_1_21_4    → MC 1.21.4 ikonu
    //   mc_1_20_1    → MC 1.20.1 ikonu
    //   mc_default   → Bilinmeyen sürümler için yedek
    // Listeye yeni sürüm eklemek için sadece buraya ekle.
    private static final Set<String> KNOWN_MC_VERSIONS = Set.of(
        "1.21.4", "1.21.3", "1.21.2", "1.21.1", "1.21",
        "1.20.6", "1.20.4", "1.20.2", "1.20.1", "1.20",
        "1.19.4", "1.19.3", "1.19.2", "1.19",
        "1.18.2", "1.18.1", "1.18",
        "1.17.1", "1.17",
        "1.16.5", "1.16.4", "1.16.1", "1.16",
        "1.12.2", "1.8.9"
    );

    private String getMcVersionIcon(String version) {
        if (version == null || version.isBlank()) return "mc_default";
        String key = "mc_" + version.replace(".", "_");
        return KNOWN_MC_VERSIONS.contains(version) ? key : "mc_default";
    }

    // ── Bağlantı ────────────────────────────────────────────────────
    public void connect() {
        new Thread(() -> {
            for (int i = 0; i < 10; i++) {
                try {
                    tryConnect(i);
                    System.out.println("✅ Discord IPC bağlandı!");
                    sendHandshake();
                    connected = true;
                    startReader();
                    if (loadSavedToken()) {
                        if (authenticate(accessToken)) {
                            System.out.println("✅ Otomatik Discord girişi! (ID: " + myUserId + ")");
                            authorized = true;
                            setInLauncher(); subscribeAll(); startPolling(); return;
                        }
                        System.out.println("🔄 Token yenileniyor...");
                        if (refreshToken != null && refreshAccessToken()) {
                            System.out.println("✅ Token yenilendi! (ID: " + myUserId + ")");
                            authorized = true;
                            setInLauncher(); subscribeAll(); startPolling(); return;
                        }
                        System.out.println("⚠ Refresh başarısız, yeniden giriş gerekiyor.");
                    }
                    authorizeFlow();
                    return;
                } catch (Exception e) {
                    try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
                }
            }
            System.out.println("⚠ Discord bulunamadı.");
        }, "discord-connect").start();
    }

    // ── Okuyucu ─────────────────────────────────────────────────────
    private void startReader() {
        new Thread(() -> {
            while (socket != null && socket.isOpen()) {
                try {
                    String raw = readRaw(10000);
                    if (raw == null) continue;
                    dispatch(raw);
                } catch (Exception e) {
                    if (connected) {
                        System.out.println("⚠ Discord bağlantısı kesildi, yeniden bağlanılıyor...");
                        connected = false;
                        try { if (socket != null) socket.close(); } catch (Exception ignored) {}
                        socket = null;
                        new Thread(() -> {
                            try { Thread.sleep(3000); } catch (InterruptedException ignored) {}
                            connect();
                            // Bağlantı sonrası son state'i geri yükle
                            new Thread(() -> {
                                try { Thread.sleep(2000); } catch (InterruptedException ignored) {}
                                if (currentState == RpcState.LAUNCHER) setInLauncher();
                                else if (currentState == RpcState.PLAYING || currentState == RpcState.ON_SERVER
                                      || currentState == RpcState.SINGLEPLAYER)
                                    setPlayingMinecraft(currentMcVersion, currentLoader);
                            }, "discord-state-restore").start();
                        }, "discord-reconnect").start();
                    }
                    break;
                }
            }
        }, "discord-reader").start();
    }

    private void dispatch(String raw) {
        try {
            JsonObject obj = JsonParser.parseString(raw).getAsJsonObject();
            CompletableFuture<JsonObject> hsFut = pending.get("__handshake__");
            if (hsFut != null && !hsFut.isDone()) hsFut.complete(obj);
            if (obj.has("nonce") && !obj.get("nonce").isJsonNull()) {
                String nonce = obj.get("nonce").getAsString();
                CompletableFuture<JsonObject> fut = pending.remove(nonce);
                if (fut != null) { fut.complete(obj); return; }
            }
            if (!obj.has("evt") || obj.get("evt").isJsonNull()) return;
            String evt = obj.get("evt").getAsString();
            if (!obj.has("data") || obj.get("data").isJsonNull()) return;
            JsonObject data = obj.getAsJsonObject("data");
            switch (evt) {
                case "SPEAKING_START" -> {
                    if (data.has("user_id")) {
                        speakingUsers.add(data.get("user_id").getAsString());
                        scheduler.execute(this::pushVoiceUpdate);
                    }
                }
                case "SPEAKING_STOP" -> {
                    if (data.has("user_id")) {
                        speakingUsers.remove(data.get("user_id").getAsString());
                        scheduler.execute(this::pushVoiceUpdate);
                    }
                }
                case "VOICE_STATE_CREATE", "VOICE_STATE_DELETE", "VOICE_STATE_UPDATE" ->
                    scheduler.execute(this::fetchVoiceChannel);
                case "VOICE_CHANNEL_SELECT" ->
                    scheduler.execute(this::fetchVoiceChannel);
            }
        } catch (Exception ignored) {}
    }

    // ── Komut ────────────────────────────────────────────────────────
    private JsonObject sendCmd(String cmd, JsonObject args) throws Exception {
        return sendCmd(cmd, args, 5);
    }
    private JsonObject sendCmd(String cmd, JsonObject args, int timeoutSec) throws Exception {
        String nonce = UUID.randomUUID().toString();
        CompletableFuture<JsonObject> fut = new CompletableFuture<>();
        pending.put(nonce, fut);
        JsonObject p = new JsonObject();
        p.addProperty("cmd", cmd);
        p.addProperty("nonce", nonce);
        p.add("args", args != null ? args : new JsonObject());
        sendRaw(1, p.toString());
        try {
            return fut.get(timeoutSec, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            pending.remove(nonce);
            throw new Exception("Timeout: " + cmd);
        }
    }

    // ── Auth ─────────────────────────────────────────────────────────
    private boolean authenticate(String token) {
        try {
            JsonObject args = new JsonObject();
            args.addProperty("access_token", token);
            JsonObject resp = sendCmd("AUTHENTICATE", args);
            if (resp == null) return false;
            if (resp.has("data") && !resp.get("data").isJsonNull()) {
                JsonObject d = resp.getAsJsonObject("data");
                if (d.has("user") && !d.get("user").isJsonNull()) {
                    JsonObject u = d.getAsJsonObject("user");
                    if (u.has("id")) myUserId = u.get("id").getAsString();
                }
            }
            return !resp.has("evt") ||
                   resp.get("evt").isJsonNull() ||
                   !"ERROR".equals(resp.get("evt").getAsString());
        } catch (Exception e) { return false; }
    }

    private boolean refreshAccessToken() {
        try {
            URL url = new URL("https://discord.com/api/oauth2/token");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST"); conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type","application/x-www-form-urlencoded");
            conn.setConnectTimeout(10000); conn.setReadTimeout(10000);
            String body = "client_id=" + CLIENT_ID
                + "&client_secret=" + URLEncoder.encode(CLIENT_SECRET, StandardCharsets.UTF_8)
                + "&grant_type=refresh_token"
                + "&refresh_token=" + URLEncoder.encode(refreshToken, StandardCharsets.UTF_8);
            conn.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));
            if (conn.getResponseCode() != 200) return false;
            JsonObject json = JsonParser.parseString(
                new String(conn.getInputStream().readAllBytes())).getAsJsonObject();
            if (!json.has("access_token")) return false;
            accessToken  = json.get("access_token").getAsString();
            refreshToken = json.has("refresh_token")
                ? json.get("refresh_token").getAsString() : refreshToken;
            saveToken(accessToken, refreshToken);
            return authenticate(accessToken);
        } catch (Exception e) {
            System.out.println("⚠ Refresh hatası: " + e.getMessage());
            return false;
        }
    }

    private void authorizeFlow() {
        try {
            JsonObject args = new JsonObject();
            args.addProperty("client_id", CLIENT_ID);
            JsonArray scopes = new JsonArray();
            scopes.add("rpc"); scopes.add("rpc.voice.read");
            args.add("scopes", scopes);
            System.out.println("⏳ Discord yetkilendirme bekleniyor...");
            JsonObject resp;
            try { resp = sendCmd("AUTHORIZE", args, 120); }
            catch (Exception e) { System.out.println("⚠ Yetkilendirme iptal edildi."); fallback(); return; }
            if (resp == null || !resp.has("data") || resp.get("data").isJsonNull()) { fallback(); return; }
            JsonObject data = resp.getAsJsonObject("data");
            if (!data.has("code") || data.get("code").isJsonNull()) { fallback(); return; }
            String code = data.get("code").getAsString();
            URL url = new URL("https://discord.com/api/oauth2/token");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST"); conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type","application/x-www-form-urlencoded");
            conn.setConnectTimeout(10000); conn.setReadTimeout(10000);
            String body = "client_id=" + CLIENT_ID
                + "&client_secret=" + URLEncoder.encode(CLIENT_SECRET, StandardCharsets.UTF_8)
                + "&grant_type=authorization_code"
                + "&code=" + URLEncoder.encode(code, StandardCharsets.UTF_8)
                + "&redirect_uri=" + URLEncoder.encode("http://localhost", StandardCharsets.UTF_8);
            conn.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));
            if (conn.getResponseCode() != 200) { fallback(); return; }
            JsonObject tokenJson = JsonParser.parseString(
                new String(conn.getInputStream().readAllBytes())).getAsJsonObject();
            if (!tokenJson.has("access_token")) { fallback(); return; }
            accessToken  = tokenJson.get("access_token").getAsString();
            refreshToken = tokenJson.has("refresh_token") ?
                tokenJson.get("refresh_token").getAsString() : null;
            if (authenticate(accessToken)) {
                saveToken(accessToken, refreshToken);
                authorized = true;
                System.out.println("🎉 Discord yetki tamam! ID: " + myUserId);
            }
            connected = true;
            setInLauncher(); subscribeAll(); startPolling();
        } catch (Exception e) {
            System.out.println("⚠ Auth hatası: " + e.getMessage());
            fallback();
        }
    }

    // ── Subscribe ────────────────────────────────────────────────────
    private void subscribeAll() {
        new Thread(() -> {
            try {
                Thread.sleep(500);
                subscribe("VOICE_CHANNEL_SELECT", null);
                System.out.println("✅ VOICE_CHANNEL_SELECT subscribe edildi");
                fetchVoiceChannel();
            } catch (Exception e) {
                System.out.println("⚠ Subscribe hatası: " + e.getMessage());
            }
        }, "discord-subscribe").start();
    }

    private void subscribeToChannel(String channelId) {
        new Thread(() -> {
            try {
                for (String evt : new String[]{"SPEAKING_START","SPEAKING_STOP",
                    "VOICE_STATE_CREATE","VOICE_STATE_DELETE","VOICE_STATE_UPDATE"}) {
                    JsonObject args = new JsonObject();
                    args.addProperty("channel_id", channelId);
                    subscribe(evt, args);
                }
                System.out.println("✅ Kanal subscribe: " + currentChannel);
            } catch (Exception e) {
                System.out.println("⚠ Kanal subscribe hatası: " + e.getMessage());
            }
        }, "channel-subscribe").start();
    }

    private void subscribe(String evt, JsonObject args) throws Exception {
        String nonce = UUID.randomUUID().toString();
        JsonObject p = new JsonObject();
        p.addProperty("cmd", "SUBSCRIBE");
        p.addProperty("evt", evt);
        p.addProperty("nonce", nonce);
        if (args != null) p.add("args", args);
        sendRaw(1, p.toString());
        Thread.sleep(100);
    }

    // ── Ses kanalı ───────────────────────────────────────────────────
    public void fetchVoiceChannel() {
        if (!connected || socket == null || !socket.isOpen()) return;
        try {
            JsonObject resp = sendCmd("GET_SELECTED_VOICE_CHANNEL", null);
            if (resp == null) return;
            if (!resp.has("data") || resp.get("data").isJsonNull()) {
                currentChannel = null; currentChannelId = null;
                speakingUsers.clear();
                if (onVoiceUsersChanged != null) onVoiceUsersChanged.accept(new ArrayList<>());
                return;
            }
            JsonObject data = resp.getAsJsonObject("data");
            currentChannel = data.has("name") ? data.get("name").getAsString() : "Ses Kanalı";
            String channelId = data.has("id") ? data.get("id").getAsString() : null;
            if (channelId != null && !channelId.equals(currentChannelId)) {
                currentChannelId = channelId;
                speakingUsers.clear();
                subscribeToChannel(channelId);
            }
            List<VoiceUser> users = new ArrayList<>();
            if (data.has("voice_states")) {
                for (JsonElement el : data.getAsJsonArray("voice_states")) {
                    JsonObject vs   = el.getAsJsonObject();
                    if (!vs.has("user")) continue;
                    JsonObject user = vs.getAsJsonObject("user");
                    JsonObject vs2  = vs.has("voice_state")
                        ? vs.getAsJsonObject("voice_state") : new JsonObject();
                    String  uid     = user.get("id").getAsString();
                    boolean muted   = vs2.has("self_mute") && vs2.get("self_mute").getAsBoolean();
                    boolean deaf    = vs2.has("self_deaf") && vs2.get("self_deaf").getAsBoolean();
                    users.add(new VoiceUser(uid,
                        user.get("username").getAsString(),
                        user.has("avatar") && !user.get("avatar").isJsonNull()
                            ? user.get("avatar").getAsString() : null,
                        muted, deaf, speakingUsers.contains(uid)));
                }
            }
            if (onVoiceUsersChanged != null) onVoiceUsersChanged.accept(users);
        } catch (Exception ignored) {}
    }

    private void pushVoiceUpdate() {
        if (currentChannelId == null) return;
        try {
            JsonObject resp = sendCmd("GET_SELECTED_VOICE_CHANNEL", null);
            if (resp == null || !resp.has("data") || resp.get("data").isJsonNull()) return;
            JsonObject data = resp.getAsJsonObject("data");
            List<VoiceUser> users = new ArrayList<>();
            if (data.has("voice_states")) {
                for (JsonElement el : data.getAsJsonArray("voice_states")) {
                    JsonObject vs   = el.getAsJsonObject();
                    if (!vs.has("user")) continue;
                    JsonObject user = vs.getAsJsonObject("user");
                    JsonObject vs2  = vs.has("voice_state")
                        ? vs.getAsJsonObject("voice_state") : new JsonObject();
                    String uid = user.get("id").getAsString();
                    users.add(new VoiceUser(uid,
                        user.get("username").getAsString(),
                        user.has("avatar") && !user.get("avatar").isJsonNull()
                            ? user.get("avatar").getAsString() : null,
                        vs2.has("self_mute") && vs2.get("self_mute").getAsBoolean(),
                        vs2.has("self_deaf") && vs2.get("self_deaf").getAsBoolean(),
                        speakingUsers.contains(uid)));
                }
            }
            if (onVoiceUsersChanged != null) onVoiceUsersChanged.accept(users);
        } catch (Exception ignored) {}
    }

    // ── Polling ──────────────────────────────────────────────────────
    private void startPolling() {
        scheduler.scheduleAtFixedRate(this::fetchVoiceChannel, 3, 5, TimeUnit.SECONDS);
    }

    private void fallback() {
        connected = true;
        setInLauncher(); startPolling();
    }

    // ── RPC — Temel gönderici ────────────────────────────────────────
    /**
     * Tüm SET_ACTIVITY çağrıları buradan geçer.
     *
     * PP kuralları:
     *   LAUNCHER  → large = nokta_logo                    | small = YOK
     *   MC açık   → large = mc_X_Y_Z (sürüm ikonu)       | small = nokta_logo
     */
    private void sendActivity(String details, String state) {
        if (!connected) return;
        scheduler.execute(() -> {
            try {
                JsonObject activity = new JsonObject();
                activity.addProperty("details", details);
                activity.addProperty("state", state);

                JsonObject ts = new JsonObject();
                ts.addProperty("start", startTime);
                activity.add("timestamps", ts);

                JsonObject assets = new JsonObject();

                if (currentState == RpcState.LAUNCHER) {
                    // Launcher: sadece N logosu büyük, küçük resim yok
                    assets.addProperty("large_image", "nokta_logo");
                    assets.addProperty("large_text",  "Nokta Launcher");
                } else {
                    // MC açık: sürüm ikonu büyük, N logosu küçük
                    assets.addProperty("large_image", getMcVersionIcon(currentMcVersion));
                    assets.addProperty("large_text",
                        "Minecraft " + (currentMcVersion.isBlank() ? "" : currentMcVersion));
                    assets.addProperty("small_image", "nokta_logo");
                    assets.addProperty("small_text",  "Nokta Client");
                }

                activity.add("assets", assets);

                // Sunucuya katıl butonu (sadece multiplayer'da)
                if (currentState == RpcState.ON_SERVER && !currentServerIp.isEmpty()) {
                    JsonArray buttons = new JsonArray();
                    JsonObject joinBtn = new JsonObject();
                    joinBtn.addProperty("label", "Sunucuya Katıl");
                    joinBtn.addProperty("url", "https://nokta-api.onrender.com");
                    buttons.add(joinBtn);
                    activity.add("buttons", buttons);
                }

                JsonObject args = new JsonObject();
                args.add("activity", activity);
                args.addProperty("pid", ProcessHandle.current().pid());
                sendCmd("SET_ACTIVITY", args);

            } catch (Exception e) { connected = false; }
        });
    }

    // ── Halka açık RPC metodları ─────────────────────────────────────

    /** Launcher açık, MC kapalı */
    public void setInLauncher() {
        currentState     = RpcState.LAUNCHER;
        currentMcVersion = "";
        currentLoader    = "";
        currentServerIp  = "";
        startTime        = System.currentTimeMillis() / 1000;
        sendActivity("Nokta Launcher", "Oyun seçiyor...");
    }

    /** MC başlatıldı */
    public void setPlayingMinecraft(String mcVersion, String loader) {
        currentState     = RpcState.PLAYING;
        currentMcVersion = mcVersion != null ? mcVersion : "";
        currentLoader    = loader    != null ? loader    : "Vanilla";
        currentServerIp  = "";
        startTime        = System.currentTimeMillis() / 1000;
        String loaderStr = currentLoader.equals("Vanilla") ? "Vanilla" : currentLoader;
        sendActivity("Nokta Client", "Minecraft " + currentMcVersion + " · " + loaderStr);
    }

    /** Eski imza — geriye dönük uyumluluk */
    public void setPlayingMinecraft(String mcVersion) {
        setPlayingMinecraft(mcVersion, "Vanilla");
    }

    /** Çok oyunculu sunucuya bağlandı */
    public void setPlayingOnServer(String serverIp, String mcVersion, String loader) {
        currentState     = RpcState.ON_SERVER;
        currentMcVersion = mcVersion != null ? mcVersion : "";
        currentLoader    = loader    != null ? loader    : "Vanilla";
        currentServerIp  = serverIp  != null ? serverIp  : "";
        String loaderStr = currentLoader.equals("Vanilla") ? "Vanilla" : currentLoader;
        sendActivity(
            "Minecraft " + currentMcVersion + " · " + loaderStr,
            currentServerIp + " sunucusunda oynuyor"
        );
    }

    /** Tek oyunculu */
    public void setPlayingSingleplayer(String mcVersion, String loader) {
        currentState     = RpcState.SINGLEPLAYER;
        currentMcVersion = mcVersion != null ? mcVersion : "";
        currentLoader    = loader    != null ? loader    : "Vanilla";
        currentServerIp  = "Singleplayer";
        String loaderStr = currentLoader.equals("Vanilla") ? "Vanilla" : currentLoader;
        sendActivity(
            "Minecraft " + currentMcVersion + " · " + loaderStr,
            "Tek oyunculu oynuyor"
        );
    }

    /** Mod ekranı */
    public void setBrowsingMods() {
        currentState = RpcState.BROWSING_MODS;
        sendActivity("Mod arıyor", "Modrinth / CurseForge");
    }

    // ── Getterlar ────────────────────────────────────────────────────
    public boolean isConnected()      { return connected; }
    public boolean isAuthorized()     { return authorized; }
    public String getCurrentChannel() { return currentChannel; }
    public RpcState getCurrentState() { return currentState; }

    public void disconnect() {
        connected = false; scheduler.shutdownNow();
        try { if (socket != null) socket.close(); } catch (Exception ignored) {}
    }

    // ── Token ────────────────────────────────────────────────────────
    private boolean loadSavedToken() {
        try {
            if (!Files.exists(TOKEN_FILE)) return false;
            JsonObject j = JsonParser.parseString(Files.readString(TOKEN_FILE)).getAsJsonObject();
            accessToken  = j.has("access_token") ? j.get("access_token").getAsString() : null;
            refreshToken = j.has("refresh_token") ? j.get("refresh_token").getAsString() : null;
            return accessToken != null;
        } catch (Exception e) { return false; }
    }

    private void saveToken(String access, String refresh) {
        try {
            JsonObject j = new JsonObject();
            j.addProperty("access_token", access);
            if (refresh != null) j.addProperty("refresh_token", refresh);
            Files.writeString(TOKEN_FILE, j.toString());
        } catch (Exception ignored) {}
    }

    // ── Socket ───────────────────────────────────────────────────────
    private void tryConnect(int pipe) throws Exception {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            socket = SocketChannel.open();
            socket.connect(new InetSocketAddress("127.0.0.1", 6463 + pipe));
        } else {
            String[] paths = {
                System.getenv("XDG_RUNTIME_DIR"), "/tmp", "/var/tmp",
                System.getProperty("user.home") + "/.config/discord"
            };
            for (String dir : paths) {
                if (dir == null) continue;
                File sock = new File(dir, "discord-ipc-" + pipe);
                if (sock.exists()) {
                    socket = SocketChannel.open(UnixDomainSocketAddress.of(sock.toPath()));
                    return;
                }
            }
            throw new IOException("Discord socket bulunamadı");
        }
    }

    private void sendHandshake() throws Exception {
        JsonObject p = new JsonObject();
        p.addProperty("v", 1);
        p.addProperty("client_id", CLIENT_ID);
        CompletableFuture<JsonObject> hsFut = new CompletableFuture<>();
        pending.put("__handshake__", hsFut);
        sendRaw(0, p.toString());
        try { hsFut.get(5, TimeUnit.SECONDS); } catch (Exception ignored) {}
        pending.remove("__handshake__");
    }

    private synchronized void sendRaw(int op, String json) throws Exception {
        byte[] data = json.getBytes(StandardCharsets.UTF_8);
        ByteBuffer buf = ByteBuffer.allocate(8 + data.length);
        buf.order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt(op); buf.putInt(data.length); buf.put(data); buf.flip();
        socket.write(buf);
    }

    private String readRaw(int ms) throws Exception {
        socket.configureBlocking(false);
        ByteBuffer h = ByteBuffer.allocate(8);
        h.order(ByteOrder.LITTLE_ENDIAN);
        long deadline = System.currentTimeMillis() + ms;
        while (h.hasRemaining()) {
            if (System.currentTimeMillis() > deadline) return null;
            int r = socket.read(h);
            if (r < 0) throw new IOException("Socket kapandı");
            if (r == 0) Thread.sleep(5);
        }
        h.flip(); h.getInt();
        int len = h.getInt();
        if (len <= 0 || len > 131072) { socket.configureBlocking(true); return null; }
        ByteBuffer data = ByteBuffer.allocate(len);
        while (data.hasRemaining()) {
            if (System.currentTimeMillis() > deadline + 5000) break;
            int r = socket.read(data);
            if (r < 0) throw new IOException("Socket kapandı");
            if (r == 0) Thread.sleep(2);
        }
        socket.configureBlocking(true);
        return new String(data.array(), StandardCharsets.UTF_8);
    }

    // ── VoiceUser ────────────────────────────────────────────────────
    public static class VoiceUser {
        public final String id, username, avatarHash;
        public final boolean muted, deafened, speaking;
        public VoiceUser(String id, String u, String av, boolean m, boolean d, boolean s) {
            this.id=id; username=u; avatarHash=av; muted=m; deafened=d; speaking=s;
        }
    }
}
