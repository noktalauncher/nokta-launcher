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

    private SocketChannel socket;
    private boolean connected  = false;
    private boolean authorized = false;
    private String  accessToken  = null;
    private String  refreshToken = null;
    private String  currentChannel   = null;
    private String  currentChannelId = null;
    private String  myUserId = null;

    // Speaking set — event ile doluyor
    private final Set<String> speakingUsers =
        Collections.synchronizedSet(new HashSet<>());

    // Nonce -> response bekleyen thread
    private final ConcurrentHashMap<String, CompletableFuture<JsonObject>> pending =
        new ConcurrentHashMap<>();

    private Consumer<List<VoiceUser>> onVoiceUsersChanged;

    private final ScheduledExecutorService scheduler =
        Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "discord-scheduler");
            t.setDaemon(true); return t;
        });

    private long startTime = System.currentTimeMillis() / 1000;

    public void setOnVoiceUsersChanged(Consumer<List<VoiceUser>> cb) {
        this.onVoiceUsersChanged = cb;
    }

    // ── Bağlantı ────────────────────────────────────────────────────

    public void connect() {
        new Thread(() -> {
            for (int i = 0; i < 10; i++) {
                try {
                    tryConnect(i);
                    System.out.println("✅ Discord IPC bağlandı!");
                    sendHandshake();
                    connected = true; // reader için
                    startReader(); // okuyucu thread başlat

                    if (loadSavedToken()) {
                        // Önce access token dene
                        if (authenticate(accessToken)) {
                            System.out.println("✅ Otomatik Discord girişi! (ID: " + myUserId + ")");
                            connected = true; authorized = true;
                            setInLauncher(); subscribeAll(); startPolling(); return;
                        }
                        // Access token süresi dolmuş - refresh token ile yenile
                        System.out.println("🔄 Token yenileniyor...");
                        if (refreshToken != null && refreshAccessToken()) {
                            System.out.println("✅ Token yenilendi! (ID: " + myUserId + ")");
                            connected = true; authorized = true;
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

    // ── Okuyucu thread — tüm mesajları işler ────────────────────────

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
                        // 3 saniye bekle yeniden bağlan
                        new Thread(() -> {
                            try { Thread.sleep(3000); } catch (InterruptedException ignored) {}
                            connect();
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

            // Handshake cevabı (nonce yok)
            CompletableFuture<JsonObject> hsFut = pending.get("__handshake__");
            if (hsFut != null && !hsFut.isDone()) {
                hsFut.complete(obj);
            }

            // Nonce varsa bekleyen komutu tamamla
            if (obj.has("nonce") && !obj.get("nonce").isJsonNull()) {
                String nonce = obj.get("nonce").getAsString();
                CompletableFuture<JsonObject> fut = pending.remove(nonce);
                if (fut != null) { fut.complete(obj); return; }
            }

            // Event işle
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

    // ── Komut gönder + cevap bekle ───────────────────────────────────

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

            System.out.println("⏳ Discord yetkilendirme bekleniyor (Discord uygulamasını kontrol et)...");
            JsonObject resp;
            try { resp = sendCmd("AUTHORIZE", args, 120); }
            catch (Exception e) { System.out.println("⚠ Yetkilendirme iptal edildi."); fallback(); return; }
            if (resp == null || !resp.has("data") || resp.get("data").isJsonNull()) {
                fallback(); return;
            }
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
            setInLauncher();
            subscribeAll();
            startPolling();
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
                // Kanal değişimi
                subscribe("VOICE_CHANNEL_SELECT", null);
                System.out.println("✅ VOICE_CHANNEL_SELECT subscribe edildi");

                // İlk kanalı al
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
            }

            JsonObject data = resp.getAsJsonObject("data");
            currentChannel = data.has("name") ? data.get("name").getAsString() : "Ses Kanalı";
            String channelId = data.has("id") ? data.get("id").getAsString() : null;

            // Yeni kanala subscribe
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

                    String  uid  = user.get("id").getAsString();
                    boolean muted = vs2.has("self_mute") && vs2.get("self_mute").getAsBoolean();
                    boolean deaf  = vs2.has("self_deaf") && vs2.get("self_deaf").getAsBoolean();
                    boolean speaking = speakingUsers.contains(uid);

                    users.add(new VoiceUser(uid,
                        user.get("username").getAsString(),
                        user.has("avatar") && !user.get("avatar").isJsonNull()
                            ? user.get("avatar").getAsString() : null,
                        muted, deaf, speaking));
                }
            }

            if (onVoiceUsersChanged != null) onVoiceUsersChanged.accept(users);

        } catch (Exception e) {
            // Sessiz geç - bağlantı kesilmişse reader thread halleder
        }
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
                    String  uid  = user.get("id").getAsString();
                    boolean muted = vs2.has("self_mute") && vs2.get("self_mute").getAsBoolean();
                    boolean deaf  = vs2.has("self_deaf") && vs2.get("self_deaf").getAsBoolean();
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

    // ── Polling ──────────────────────────────────────────────────────

    private void startPolling() {
        scheduler.scheduleAtFixedRate(this::fetchVoiceChannel, 3, 5, TimeUnit.SECONDS);
    }

    private void fallback() {
        connected = true;
        setInLauncher(); startPolling();
    }

    // ── RPC ──────────────────────────────────────────────────────────

    private String currentServerIp = "";
    private String currentMcVersion = "";
    public void setPresence(String details, String state) {
        setPresenceWithServer(details, state, currentServerIp);
    }
    public void setPresenceWithServer(String details, String state, String serverIp) {
        if (!connected) return;
        currentServerIp = serverIp != null ? serverIp : "";
        scheduler.execute(() -> {
            try {
                JsonObject activity = new JsonObject();
                activity.addProperty("details", details);
                activity.addProperty("state", state);
                JsonObject ts = new JsonObject();
                ts.addProperty("start", startTime);
                activity.add("timestamps", ts);
                JsonObject assets = new JsonObject();
                // MC açıkken Minecraft'ın kendi ikonunu kullan
                String largeImg = currentMcVersion != null && !currentMcVersion.isEmpty()
                    ? "minecraft" : "nokta_logo";
                String largeText = currentMcVersion != null && !currentMcVersion.isEmpty()
                    ? "Minecraft " + currentMcVersion : "Nokta Launcher";
                assets.addProperty("large_image", largeImg);
                assets.addProperty("large_text", largeText);
                assets.addProperty("small_image", "nokta_logo");
                assets.addProperty("small_text", "Nokta Client");
                activity.add("assets", assets);
                // Sunucuya katıl butonu
                if (!currentServerIp.isEmpty() && !currentServerIp.equals("Singleplayer")) {
                    JsonArray buttons = new JsonArray();
                    JsonObject joinBtn = new JsonObject();
                    joinBtn.addProperty("label", "Sunucuya Katıl");
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

    public void setPlayingMinecraft(String v) {
        startTime = System.currentTimeMillis() / 1000;
        setPresence("Nokta Client", "Minecraft " + v + " oynuyor");
    }
    public void setPlayingMinecraft(String mcVersion, String loader) {
        currentMcVersion = mcVersion != null ? mcVersion : "";
        startTime = System.currentTimeMillis() / 1000;
        String loaderStr = (loader != null && !loader.equals("Vanilla")) ? loader : "Vanilla";
        setPresence("Nokta Client", "Minecraft " + mcVersion + " · " + loaderStr);
    }
    public void setInLauncher()   { currentMcVersion = ""; setPresence("Nokta Launcher", "Oyun seçiyor..."); }
    public void setPlayingOnServer(String serverIp, String mcVersion, String loader) {
        String loaderStr = (loader != null && !loader.equals("Vanilla")) ? loader : "Vanilla";
        setPresenceWithServer("Minecraft " + mcVersion + " · " + loaderStr,
                    serverIp + " sunucusunda oynuyor", serverIp);
    }
    public void setPlayingSingleplayer(String mcVersion, String loader) {
        String loaderStr = (loader != null && !loader.equals("Vanilla")) ? loader : "Vanilla";
        setPresence("Minecraft " + mcVersion + " · " + loaderStr, "Tek oyunculu oynuyor");
    }
    public void setBrowsingMods() { setPresence("Mod arıyor", "Modrinth / CurseForge"); }

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

    public void disconnect() {
        connected = false; scheduler.shutdownNow();
        try { if (socket != null) socket.close(); } catch (Exception ignored) {}
    }

    public boolean isConnected()      { return connected; }
    public boolean isAuthorized()     { return authorized; }
    public String getCurrentChannel() { return currentChannel; }

    private void tryConnect(int pipe) throws Exception {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            socket = SocketChannel.open();
            socket.connect(new InetSocketAddress("127.0.0.1", 6463 + pipe));
        } else {
            String[] paths = {System.getenv("XDG_RUNTIME_DIR"), "/tmp", "/var/tmp",
                System.getProperty("user.home") + "/.config/discord"};
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
        // Handshake cevabını reader thread okuyacak
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
        // Blocking mode - sadece reader thread çağırır
        socket.configureBlocking(true);
        ByteBuffer h = ByteBuffer.allocate(8);
        h.order(ByteOrder.LITTLE_ENDIAN);
        // SO_TIMEOUT benzeri - non-blocking ile poll
        socket.configureBlocking(false);
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

    public static class VoiceUser {
        public final String id, username, avatarHash;
        public final boolean muted, deafened, speaking;
        public VoiceUser(String id, String u, String av, boolean m, boolean d, boolean s) {
            this.id=id; username=u; avatarHash=av; muted=m; deafened=d; speaking=s;
        }
    }
}
