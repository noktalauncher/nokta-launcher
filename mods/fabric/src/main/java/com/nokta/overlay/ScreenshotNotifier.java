package com.nokta.overlay;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import java.nio.file.*;
import java.util.concurrent.*;

public class ScreenshotNotifier {
    public static class LastPath { public String path; public LastPath(String p){this.path=p;} }
    private static LastPath lastPath = null;
    public static LastPath getLastPath() { return lastPath; }
    public static void setLastPath(String p) { lastPath = new LastPath(p); }
    private static String lastScreenshot = null;
    private static long showUntil = 0;
    private static final int W = 180, H = 36;

    public static void start() {
        Path dir = Path.of(System.getProperty("user.home"), ".nokta-launcher", "screenshots");
        ScheduledExecutorService s = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "ss-watcher"); t.setDaemon(true); return t;
        });
        s.scheduleAtFixedRate(() -> {
            try {
                if (!Files.exists(dir)) return;
                Files.list(dir)
                    .filter(p -> p.toString().endsWith(".png"))
                    .max(java.util.Comparator.comparingLong(p -> {
                        try { return Files.getLastModifiedTime(p).toMillis(); }
                        catch (Exception e) { return 0L; }
                    }))
                    .ifPresent(p -> {
                        String name = p.toString();
                        try {
                            long mod = Files.getLastModifiedTime(p).toMillis();
                            if (!name.equals(lastScreenshot) && System.currentTimeMillis() - mod < 3000) {
                                lastScreenshot = name;
                                showUntil = System.currentTimeMillis() + 5000;
                            }
                        } catch (Exception ignored) {}
                    });
            } catch (Exception ignored) {}
        }, 1, 1, TimeUnit.SECONDS);
    }

    public static void render(GuiGraphics ctx, int mx, int my, int screenW, int screenH) {
        if (lastScreenshot == null || System.currentTimeMillis() > showUntil) return;
        Minecraft mc = Minecraft.getInstance();
        String label = "[Copy]";
        int btnW = mc.font.width(label) + 6;
        int btnH = 10;
        int x = 4, y = screenH - 44;
        boolean hov = mx >= x && mx <= x+btnW && my >= y && my <= y+btnH;
        ctx.fill(x, y, x+btnW, y+btnH, hov ? 0xcc5555ff : 0xcc000000);
        ctx.drawString(mc.font, label, x+3, y+1, hov ? 0xffffff55 : 0xffaaaaff, false);
    }

    public static boolean onClick(int mx, int my, int screenW, int screenH) {
        if (lastScreenshot == null || System.currentTimeMillis() > showUntil) return false;
        int btnW = 52, btnH = 12;
        int x = 2, y = screenH - 38;
        if (mx >= x && mx <= x+btnW && my >= y && my <= y+btnH) {
            try {
                Runtime.getRuntime().exec(new String[]{
                    "xclip", "-selection", "clipboard", "-t", "image/png", "-i", lastScreenshot
                });
                showUntil = 0;
            } catch (Exception ignored) {}
            return true;
        }
        return false;
    }
}
