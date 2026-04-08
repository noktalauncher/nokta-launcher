package com.nokta.overlay;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import java.util.ArrayList;
import java.util.List;

public class SpotifyScreen extends Screen {

    private final Screen parent;
    private static final int W = 380, H = 160;
    private int tick = 0;
    private int pbX, pbY, pbW;

    public SpotifyScreen(Screen parent) {
        super(Component.literal("Spotify"));
        this.parent = parent;
        if (NoktaOverlayMod.renderer != null) NoktaOverlayMod.renderer.readFiles();
    }

    private static String fmtMs(int ms) {
        int s = ms / 1000;
        return String.format("%d:%02d", s / 60, s % 60);
    }

    private List<String> wrapText(String text, int maxWidth) {
        List<String> lines = new ArrayList<>();
        String[] words = text.split(" ");
        StringBuilder cur = new StringBuilder();
        for (String word : words) {
            String test = cur.length() == 0 ? word : cur + " " + word;
            if (font.width(test) <= maxWidth) { cur = new StringBuilder(test); }
            else { if (cur.length() > 0) lines.add(cur.toString()); cur = new StringBuilder(word); }
        }
        if (cur.length() > 0) lines.add(cur.toString());
        return lines;
    }

    @Override
    protected void init() {
        int cx = (width - W) / 2;
        int cy = (height - H) / 2;
        int btnY = cy + H - 30, btnCX = cx + W / 2;

        addRenderableWidget(Button.builder(Component.literal("⏮"),
            b -> { OverlayRenderer.writeCmd("prev"); rebuildWidgets(); })
            .bounds(btnCX - 66, btnY, 36, 20).build());

        addRenderableWidget(Button.builder(
            Component.literal(OverlayRenderer.spotifyPlaying ? "⏸" : "▶"),
            b -> { OverlayRenderer.writeCmd("playpause"); rebuildWidgets(); })
            .bounds(btnCX - 18, btnY - 3, 36, 26).build());

        addRenderableWidget(Button.builder(Component.literal("⏭"),
            b -> { OverlayRenderer.writeCmd("next"); rebuildWidgets(); })
            .bounds(btnCX + 30, btnY, 36, 20).build());
    }

    @Override
    public void renderBackground(GuiGraphics ctx, int mx, int my, float delta) {
        ctx.fill(0, 0, width, height, 0x88000000);
    }

    @Override
    public void render(GuiGraphics ctx, int mx, int my, float delta) {
        tick++;
        if (tick % 20 == 0 && NoktaOverlayMod.renderer != null)
            NoktaOverlayMod.renderer.readFiles();

        renderBackground(ctx, mx, my, delta);

        int cx = (width - W) / 2;
        int cy = (height - H) / 2;

        // Panel
        ctx.fill(cx,     cy,     cx+W,   cy+H,   0xf2111318);
        ctx.fill(cx,     cy,     cx+W,   cy+2,   0xff1db954);
        ctx.fill(cx,     cy,     cx+2,   cy+H,   0x222a2f3a);
        ctx.fill(cx+W-2, cy,     cx+W,   cy+H,   0x222a2f3a);
        ctx.fill(cx,     cy+H-2, cx+W,   cy+H,   0x222a2f3a);

        // === ALBÜM ART ===
        int artX = cx+14, artY = cy+14, artS = 100;
        ctx.fill(artX-1, artY-1, artX+artS+1, artY+artS+1, 0x441db954);
        ctx.fill(artX, artY, artX+artS, artY+artS, 0xff0d1117);

        ResourceLocation artTex = AlbumArtCache.get(OverlayRenderer.spotifyAlbumArt);
        if (artTex != null) {
            // Pose matrix ile tam kare scale
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
            ctx.pose().pushPose();
            ctx.pose().translate(artX, artY, 0);
            float scale = (float) artS / 512f;
            ctx.pose().scale(scale, scale, 1f);
            ctx.blit(
                net.minecraft.client.renderer.RenderType::guiTextured,
                artTex, 0, 0, 0f, 0f, 512, 512, 512, 512
            );
            ctx.pose().popPose();
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        } else if (OverlayRenderer.spotifyPlaying) {
            for (int i = 0; i < 4; i++) {
                double ph = tick*0.14+i*0.9;
                int bh = 10+(int)(Math.abs(Math.sin(ph))*30);
                int bx = artX+10+i*22, mid = artY+artS/2;
                int a = 0x77+(int)(Math.abs(Math.sin(ph))*0x88);
                ctx.fill(bx, mid-bh/2, bx+14, mid+bh/2, (a<<24)|0x1db954);
            }
        }

        // === ŞARKI BİLGİSİ ===
        int tx = artX+artS+16, ty = cy+14;
        int textW = cx+W-tx-12;

        if (OverlayRenderer.spotifyTitle != null && !OverlayRenderer.spotifyTitle.isEmpty()) {
            List<String> titleLines = wrapText(OverlayRenderer.spotifyTitle, textW);
            int lineY = ty;
            for (int i = 0; i < Math.min(2, titleLines.size()); i++) {
                ctx.drawString(font, "§f§l" + titleLines.get(i), tx, lineY, 0xffffff, false);
                lineY += 11;
            }
            ctx.drawString(font, "§7" + OverlayRenderer.spotifyArtist, tx, lineY+2, 0xbbbbbb, false);
            ctx.drawString(font,
                OverlayRenderer.spotifyPlaying ? "§a▶  Çalıyor" : "§7⏸  Duraklatıldı",
                tx, lineY+14, 0x777777, false);
            ctx.drawString(font,
                "§f" + fmtMs(OverlayRenderer.spotifyProgress) +
                " §8/ §7" + fmtMs(OverlayRenderer.spotifyDuration),
                tx, lineY+28, 0xcccccc, false);
        } else {
            ctx.drawString(font, "§7Spotify açık değil", tx, ty+20, 0x555555, false);
        }

        // === SEEK BAR ===
        pbX = cx+14; pbY = cy+H-46; pbW = W-28;
        boolean barHov = mx >= pbX && mx <= pbX+pbW && my >= pbY-5 && my <= pbY+9;
        ctx.fill(pbX, pbY, pbX+pbW, pbY+4, 0x33ffffff);

        if (OverlayRenderer.spotifyDuration > 0) {
            float pct  = Math.min(1f, (float)OverlayRenderer.spotifyProgress/OverlayRenderer.spotifyDuration);
            int   prog = (int)(pct * pbW);
            ctx.fill(pbX, pbY, pbX+prog, pbY+4, 0xff1db954);
            int dotR = barHov ? 6 : 4;
            ctx.fill(pbX+prog-dotR, pbY+2-dotR, pbX+prog+dotR, pbY+2+dotR, 0xffffffff);
            if (barHov) {
                float hPct = (float)(mx-pbX)/pbW;
                ctx.drawString(font, "§7"+fmtMs((int)(hPct*OverlayRenderer.spotifyDuration)),
                    (int)mx-8, pbY-12, 0xaaaaaa, false);
            }
        }

        super.render(ctx, mx, my, delta);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (my >= pbY-5 && my <= pbY+9 && mx >= pbX && mx <= pbX+pbW) {
            seekTo((float)(mx-pbX)/pbW); return true;
        }
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int btn, double dx, double dy) {
        if (mx >= pbX-10 && mx <= pbX+pbW+10) {
            seekTo((float)(mx-pbX)/pbW); return true;
        }
        return super.mouseDragged(mx, my, btn, dx, dy);
    }

    private void seekTo(float pct) {
        pct = Math.max(0, Math.min(1, pct));
        int targetMs = (int)(pct * OverlayRenderer.spotifyDuration);
        try {
            com.google.gson.JsonObject j = new com.google.gson.JsonObject();
            j.addProperty("cmd", "seek");
            j.addProperty("position", targetMs);
            j.addProperty("time", System.currentTimeMillis());
            java.nio.file.Files.writeString(
                java.nio.file.Paths.get(System.getProperty("user.home"),
                    ".nokta-launcher", "spotify_cmd.json"), j.toString());
        } catch (Exception ignored) {}
        OverlayRenderer.spotifyProgress = targetMs;
    }

    @Override public boolean shouldCloseOnEsc() { return true; }
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) { onClose(); return true; }
        return true;
    }
    @Override
    public boolean charTyped(char chr, int modifiers) { return true; }
    @Override public void onClose() {
        // Bir tick bekle - T tuşunun chat'e geçmesini önle
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> mc.setScreen(parent));
    }
    @Override public boolean isPauseScreen() { return false; }
}
