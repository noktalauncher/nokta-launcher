package com.nokta.overlay;

import com.google.gson.*;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.DeltaTracker;
import java.nio.file.*;
import java.util.*;

public class OverlayRenderer implements HudRenderCallback {

    private static final Path VOICE_FILE   = Paths.get(System.getProperty("user.home"), ".nokta-launcher", "voice_channel.json");
    private static final Path SERVER_FILE  = Paths.get(System.getProperty("user.home"), ".nokta-launcher", "server_info.json");
    private static final Path SPOTIFY_FILE = Paths.get(System.getProperty("user.home"), ".nokta-launcher", "spotify_current.json");
    private static final Path SPOTIFY_CMD  = Paths.get(System.getProperty("user.home"), ".nokta-launcher", "spotify_cmd.json");

    // Voice
    private String channelName = null;
    private final List<String>  voiceUsers = new ArrayList<>();
    private final List<Boolean> speaking   = new ArrayList<>();
    private final List<Boolean> muted      = new ArrayList<>();

    // Spotify
    public static String  spotifyTitle    = null;
    public static String  spotifyArtist   = "";
    public static String  spotifyAlbumArt = "";
    public static boolean spotifyPlaying  = false;
    public static int     spotifyProgress = 0;  // ms
    public static int     spotifyDuration = 0;  // ms

    private long   lastRead   = 0;
    private String lastServer = "";

    private static final int[] AVATAR_COLORS = {
        0xff5865F2, 0xff57F287, 0xffFEE75C, 0xffEB459E,
        0xffED4245, 0xff1DB954, 0xff00B0F4, 0xffFAA61A
    };

    @Override
    public void onHudRender(GuiGraphics ctx, DeltaTracker ticker) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getDebugOverlay().showDebugScreen()) return;

        long now = System.currentTimeMillis();
        if (now - lastRead > 1000) { lastRead = now; readFiles(); }

        if (channelName != null && !voiceUsers.isEmpty()) {
            drawVoiceOverlay(ctx, mc);
        }
        
    }

    private void drawVoiceOverlay(GuiGraphics ctx, Minecraft mc) {
        int x = 5, y = 8;
        int rowH = 22, avatarSize = 16, boxW = 155;

        ctx.fill(x, y, x + boxW, y + 13, 0x22000000);
        ctx.fill(x, y, x + 2, y + 13, 0xaa5865F2);
        ctx.drawString(mc.font, "§7" + channelName, x + 6, y + 3, 0x99ffffff, false);
        y += 16;

        for (int i = 0; i < voiceUsers.size(); i++) {
            String  name = voiceUsers.get(i);
            boolean spk  = i < speaking.size() && speaking.get(i);
            boolean mut  = i < muted.size()    && muted.get(i);
            int rowY = y + i * rowH;
            int rowB = rowY + rowH - 2;

            if (spk) ctx.fill(x, rowY, x + boxW, rowB, 0x2257f287);
            ctx.fill(x, rowY, x + 2, rowB, spk ? 0xcc57f287 : 0x33ffffff);

            int col = AVATAR_COLORS[Math.abs(name.hashCode()) % AVATAR_COLORS.length];
            int ax = x + 5, ay = rowY + 3;
            ctx.fill(ax, ay, ax + avatarSize, ay + avatarSize, col);
            ctx.drawString(mc.font,
                name.isEmpty() ? "?" : String.valueOf(name.charAt(0)).toUpperCase(),
                ax + 4, ay + 4, 0xffffff, false);

            String dn = name.length() > 13 ? name.substring(0, 11) + ".." : name;
            ctx.drawString(mc.font, dn, ax + avatarSize + 5, rowY + 7,
                spk ? 0xff57f287 : (mut ? 0x77888888 : 0xbbdddddd), false);

            if (mut) ctx.drawString(mc.font, "§c✖", x + boxW - 14, rowY + 5, 0x99ff4444, false);
        }
    }

    public void onTick(Minecraft mc) {
        if (mc == null) return;
        try {
            String server = "";
            if (mc.getCurrentServer() != null) server = mc.getCurrentServer().ip;
            else if (mc.hasSingleplayerServer()) server = "Singleplayer";
            if (!server.equals(lastServer)) {
                lastServer = server;
                JsonObject obj = new JsonObject();
                obj.addProperty("server", server);
                obj.addProperty("online", !server.isEmpty());
                Files.writeString(SERVER_FILE, obj.toString());
            }
        } catch (Exception ignored) {}
    }

    public static void writeCmd(String cmd) {
        try {
            JsonObject j = new JsonObject();
            j.addProperty("cmd", cmd);
            j.addProperty("time", System.currentTimeMillis());
            Files.writeString(SPOTIFY_CMD, j.toString());
        } catch (Exception ignored) {}
    }

    public void readFiles() {
        try {
            if (Files.exists(VOICE_FILE)) {
                JsonObject j = JsonParser.parseString(Files.readString(VOICE_FILE)).getAsJsonObject();
                channelName = j.has("channel") && !j.get("channel").getAsString().isEmpty()
                    ? j.get("channel").getAsString() : null;
                voiceUsers.clear(); speaking.clear(); muted.clear();
                if (j.has("users")) {
                    for (JsonElement el : j.getAsJsonArray("users")) {
                        JsonObject u = el.getAsJsonObject();
                        voiceUsers.add(u.has("username") ? u.get("username").getAsString() : "?");
                        speaking.add(u.has("speaking") && u.get("speaking").getAsBoolean());
                        muted.add(u.has("muted") && u.get("muted").getAsBoolean());
                    }
                }
            } else { channelName = null; voiceUsers.clear(); }
        } catch (Exception ignored) {}

        try {
            if (Files.exists(SPOTIFY_FILE)) {
                JsonObject j = JsonParser.parseString(Files.readString(SPOTIFY_FILE)).getAsJsonObject();
                spotifyTitle    = j.has("title")    ? j.get("title").getAsString()    : null;
                spotifyArtist   = j.has("artist")   ? j.get("artist").getAsString()   : "";
                spotifyAlbumArt = j.has("albumArt") ? j.get("albumArt").getAsString() : "";
                spotifyPlaying  = j.has("playing")  && j.get("playing").getAsBoolean();
                spotifyProgress = j.has("progress") ? j.get("progress").getAsInt()    : 0;
                spotifyDuration = j.has("duration") ? j.get("duration").getAsInt()    : 0;
            }
        } catch (Exception ignored) {}
    }
}
