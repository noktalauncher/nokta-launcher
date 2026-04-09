package com.nokta.forgemod;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

public class ForgeNokHud {

    private boolean dragging         = false;
    private boolean resizing         = false;
    private int     dragOffsetX      = 0;
    private int     dragOffsetY      = 0;
    private double  resizeStartX     = 0;
    private float   resizeStartScale = 1f;
    private boolean editMode         = false;
    private String  playtime         = "00:00:00";
    private long    lastPlaytimeRead = 0;

    public void render(GuiGraphics ctx, Minecraft mc) {
        ForgeHudConfig cfg = ForgeHudConfig.get();
        if (!cfg.visible) return;

        long now = System.currentTimeMillis();
        if (now - lastPlaytimeRead > 5000) { lastPlaytimeRead = now; readPlaytime(); }

        int    fps    = mc.getFps();
        int    ping   = getPing(mc);
        String server = getServer(mc);

        String[] lines = {
            "\u00a7a" + fps    + " \u00a77fps",
            "\u00a7b" + (ping >= 0 ? ping + " \u00a77ms" : "\u00a77-"),
            "\u00a7f" + server,
            "\u00a7e" + playtime
        };

        float sc   = cfg.scale;
        int   px   = cfg.x;
        int   py   = cfg.y;
        int   lineH = (int)(10 * sc);
        int   padX  = (int)(6  * sc);
        int   padY  = (int)(5  * sc);
        int   boxW  = (int)(90 * sc);
        int   boxH  = padY * 2 + lines.length * lineH + (lines.length - 1) * (int)(2 * sc);

        int bgCol = editMode ? 0xcc1a1a3a : 0x99000000;
        ctx.fill(px, py, px + boxW, py + boxH, bgCol);

        if (editMode) {
            ctx.fill(px,          py,          px + boxW,     py + 1,        0xcc6c63ff);
            ctx.fill(px,          py + boxH-1, px + boxW,     py + boxH,     0xcc6c63ff);
            ctx.fill(px,          py,          px + 1,        py + boxH,     0xcc6c63ff);
            ctx.fill(px + boxW-1, py,          px + boxW,     py + boxH,     0xcc6c63ff);
            int rs = (int)(8 * sc);
            ctx.fill(px + boxW - rs, py + boxH - rs, px + boxW, py + boxH, 0xcc9d98ff);
            ctx.drawString(mc.font, "\u00a77surukle / koseden boyutlandir",
                px + boxW + 4, py + 2, 0x99aaaacc, false);
        }

        for (int i = 0; i < lines.length; i++) {
            int lx = px + padX;
            int ly = py + padY + i * (lineH + (int)(2 * sc));
            ctx.pose().pushPose();
            ctx.pose().translate(lx, ly, 0);
            ctx.pose().scale(sc, sc, 1f);
            ctx.drawString(mc.font, lines[i], 0, 0, 0xffffffff, true);
            ctx.pose().popPose();
        }
    }

    public void setEditMode(boolean on) { this.editMode = on; }
    public boolean isEditMode()         { return editMode; }

    public void onMousePress(double mx, double my) {
        if (!editMode) return;
        ForgeHudConfig cfg = ForgeHudConfig.get();
        float sc  = cfg.scale;
        int   bw  = (int)(90 * sc);
        int   bh  = getBoxH(cfg);
        int   rs  = (int)(8  * sc);

        if (mx >= cfg.x + bw - rs && mx <= cfg.x + bw &&
            my >= cfg.y + bh - rs && my <= cfg.y + bh) {
            resizing         = true;
            resizeStartX     = mx;
            resizeStartScale = cfg.scale;
        } else if (mx >= cfg.x && mx <= cfg.x + bw &&
                   my >= cfg.y && my <= cfg.y + bh) {
            dragging    = true;
            dragOffsetX = (int)(mx - cfg.x);
            dragOffsetY = (int)(my - cfg.y);
        }
    }

    public void onMouseMove(double mx, double my, int screenW, int screenH) {
        if (!editMode || (!dragging && !resizing)) return;
        ForgeHudConfig cfg = ForgeHudConfig.get();
        if (dragging) {
            cfg.x = (int) Math.max(0, Math.min(screenW - 90, mx - dragOffsetX));
            cfg.y = (int) Math.max(0, Math.min(screenH - 60, my - dragOffsetY));
        } else {
            double delta = mx - resizeStartX;
            float  newSc = resizeStartScale + (float)(delta / 200.0);
            cfg.scale = Math.max(0.75f, Math.min(2.0f, newSc));
        }
    }

    public void onMouseRelease() {
        if (dragging || resizing) ForgeHudConfig.get().save();
        dragging = false;
        resizing = false;
    }

    private int getBoxH(ForgeHudConfig cfg) {
        float sc  = cfg.scale;
        int lineH = (int)(10 * sc);
        int padY  = (int)(5  * sc);
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
        return "-";
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
