package com.nokta.overlay;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ServerData;

public class NokHud {

    // ── Drag/resize state ────────────────────────────────────────────
    private boolean dragging    = false;
    private boolean resizing    = false;
    private int     dragOffsetX = 0;
    private int     dragOffsetY = 0;
    private double  resizeStartX = 0;
    private float   resizeStartScale = 1f;

    // Editmode: chat açıkken true
    private boolean editMode = false;
    private boolean wasPressed        = false;

    // Playtime (launcher'dan IPC ile gelir — saniye cinsinden)
    private String playtime = "00:00:00";
    private long   lastPlaytimeRead = 0;

    // ── Render ───────────────────────────────────────────────────────
    public void render(GuiGraphics ctx, Minecraft mc) {
        HudConfig cfg = HudConfig.get();
        if (!cfg.visible) return;

        // ── GLFW tabanlı drag — render her frame çalışır, tick'ten daha güvenilir
        if (editMode) {
            try {
                long win = mc.getWindow().getWindow();
                int state = org.lwjgl.glfw.GLFW.glfwGetMouseButton(win, org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT);
                double[] xArr = new double[1], yArr = new double[1];
                org.lwjgl.glfw.GLFW.glfwGetCursorPos(win, xArr, yArr);
                double scaleX = mc.getWindow().getGuiScaledWidth()  / (double) mc.getWindow().getWidth();
                double scaleY = mc.getWindow().getGuiScaledHeight() / (double) mc.getWindow().getHeight();
                double mx = xArr[0] * scaleX, my = yArr[0] * scaleY;
                if (state == org.lwjgl.glfw.GLFW.GLFW_PRESS && !wasPressed) {
                    onMousePress(mx, my);
                    wasPressed = true;
                } else if (state == org.lwjgl.glfw.GLFW.GLFW_PRESS) {
                    onMouseMove(mx, my,
                        mc.getWindow().getGuiScaledWidth(),
                        mc.getWindow().getGuiScaledHeight());
                } else if (wasPressed) {
                    onMouseRelease();
                    wasPressed = false;
                }
            } catch (Exception ignored) {}
        }

        // Playtime oku (her 5s)
        long now = System.currentTimeMillis();
        if (now - lastPlaytimeRead > 5000) {
            lastPlaytimeRead = now;
            readPlaytime();
        }

        // Veri topla
        int    fps    = mc.getFps();
        int    ping   = getPing(mc);
        String server = getServer(mc);

        // Satırlar
        String[] lines = {
            "§a" + fps + " §7fps",
            "§b" + (ping >= 0 ? ping + " §7ms" : "§7—"),
            "§f" + server,
            "§e" + playtime
        };

        float  sc  = cfg.scale;
        int    px  = cfg.x;
        int    py  = cfg.y;

        // Satır boyutları
        int lineH  = (int)(10 * sc);
        int padX   = (int)(6  * sc);
        int padY   = (int)(5  * sc);
        int boxW   = (int)(90 * sc);
        int boxH   = padY * 2 + lines.length * lineH + (lines.length - 1) * (int)(2 * sc);

        // Arka plan
        int bgCol = editMode ? 0x881a1a3a : 0x00000000;
        ctx.fill(px, py, px + boxW, py + boxH, bgCol);

        // Edit mode: kenar çizgisi
        if (editMode) {
            ctx.fill(px,          py,          px + boxW,     py + 1,        0xcc6c63ff);
            ctx.fill(px,          py + boxH-1, px + boxW,     py + boxH,     0xcc6c63ff);
            ctx.fill(px,          py,          px + 1,        py + boxH,     0xcc6c63ff);
            ctx.fill(px + boxW-1, py,          px + boxW,     py + boxH,     0xcc6c63ff);

            // Resize köşesi (sağ alt) — küçük kare
            int rs = (int)(8 * sc);
            ctx.fill(px + boxW - rs, py + boxH - rs,
                     px + boxW,     py + boxH,     0xcc9d98ff);

            // "Sürükle" yazısı
            ctx.drawString(mc.font, "§7≡ sürükle / köşeden boyutlandır",
                px + boxW + 4, py + 2, 0x99aaaacc, false);
        }

        // Satırları çiz
        for (int i = 0; i < lines.length; i++) {
            int lx = px + padX;
            int ly = py + padY + i * (lineH + (int)(2 * sc));
            // Scale için matrix push
            ctx.pose().pushPose();
            ctx.pose().translate(lx, ly, 0);
            ctx.pose().scale(sc, sc, 1f);
            ctx.drawString(mc.font, lines[i], 0, 0, 0xffffffff, true);
            ctx.pose().popPose();
        }
    }

    // ── Edit modu aç/kapat ───────────────────────────────────────────
    public void setEditMode(boolean on) {
        this.editMode = on;
    }
    public boolean isEditMode() { return editMode; }

    // ── Mouse olayları (sadece editMode'da aktif) ────────────────────
    public void onMousePress(double mx, double my) {
        if (!editMode) return;
        HudConfig cfg = HudConfig.get();
        float sc  = cfg.scale;
        int   bw  = (int)(90 * sc);
        int   bh  = getBoxH(cfg);
        int   rs  = (int)(8  * sc);

        // Resize köşesinde mi?
        if (mx >= cfg.x + bw - rs && mx <= cfg.x + bw &&
            my >= cfg.y + bh - rs && my <= cfg.y + bh) {
            resizing       = true;
            resizeStartX   = mx;
            resizeStartScale = cfg.scale;
        } else if (mx >= cfg.x && mx <= cfg.x + bw &&
                   my >= cfg.y && my <= cfg.y + bh) {
            dragging    = true;
            dragOffsetX = (int)(mx - cfg.x);
            dragOffsetY = (int)(my - cfg.y);
        }
    }

    // Tick'te çağrılır — drag aktifse mouse pozisyonunu takip et
    public void onMouseMove(double mx, double my, int screenW, int screenH) {
        if (!editMode) return;
        if (dragging || resizing) onMouseDrag(mx, my, screenW, screenH);
    }

    public void onMouseDrag(double mx, double my, int screenW, int screenH) {
        if (!editMode) return;
        HudConfig cfg = HudConfig.get();
        if (dragging) {
            cfg.x = (int) Math.max(0, Math.min(screenW - 90, mx - dragOffsetX));
            cfg.y = (int) Math.max(0, Math.min(screenH - 60, my - dragOffsetY));
        } else if (resizing) {
            double delta = mx - resizeStartX;
            float  newSc = resizeStartScale + (float)(delta / 200.0);
            cfg.scale = Math.max(0.75f, Math.min(2.0f, newSc));
        }
    }

    public void onMouseRelease() {
        if (dragging || resizing) {
            HudConfig.get().save();
        }
        dragging = false;
        resizing = false;
    }

    // ── Yardımcılar ──────────────────────────────────────────────────
    private int getBoxH(HudConfig cfg) {
        float sc   = cfg.scale;
        int lineH  = (int)(10 * sc);
        int padY   = (int)(5  * sc);
        return padY * 2 + 4 * lineH + 3 * (int)(2 * sc);
    }

    private int getPing(Minecraft mc) {
        try {
            if (mc.getConnection() == null) return -1;
            var info = mc.getConnection().getPlayerInfo(mc.getUser().getProfileId());
            return info != null ? info.getLatency() : -1;
        } catch (Exception e) { return -1; }
    }

    private String getServer(Minecraft mc) {
        if (mc.getCurrentServer() != null) return mc.getCurrentServer().ip;
        if (mc.hasSingleplayerServer()) return "Singleplayer";
        return "—";
    }

    private void readPlaytime() {
        try {
            java.nio.file.Path f = java.nio.file.Paths.get(
                System.getProperty("user.home"), ".nokta-launcher", "session_time.txt");
            if (java.nio.file.Files.exists(f))
                playtime = java.nio.file.Files.readString(f).trim();
        } catch (Exception ignored) {}
    }
}
