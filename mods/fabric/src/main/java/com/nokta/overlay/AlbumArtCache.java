package com.nokta.overlay;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.GlStateManager;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import javax.imageio.ImageIO;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.URI;
import java.util.concurrent.*;

public class AlbumArtCache {
    private static String           loadedUrl = "";
    private static ResourceLocation texture   = null;
    private static boolean          loading   = false;
    private static int              glId      = -1;

    private static final ExecutorService pool = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "album-art-dl"); t.setDaemon(true); return t;
    });

    public static ResourceLocation get(String url) {
        if (url == null || url.isEmpty()) return null;
        if (url.equals(loadedUrl) && texture != null) return texture;
        if (url.equals(loadedUrl) && loading) return null;

        loadedUrl = url;
        texture   = null;
        loading   = true;

        pool.submit(() -> {
            try {
                byte[] raw = URI.create(url).toURL().openStream().readAllBytes();
                BufferedImage bi = ImageIO.read(new ByteArrayInputStream(raw));
                if (bi == null) { loading = false; return; }

                // 512x512 yüksek çözünürlük
                int TARGET = 512;
                BufferedImage scaled = new BufferedImage(TARGET, TARGET, BufferedImage.TYPE_INT_ARGB);
                java.awt.Graphics2D g2 = scaled.createGraphics();
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                g2.setRenderingHint(RenderingHints.KEY_RENDERING,     RenderingHints.VALUE_RENDER_QUALITY);
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,  RenderingHints.VALUE_ANTIALIAS_ON);
                g2.drawImage(bi, 0, 0, TARGET, TARGET, null);
                g2.dispose();

                // NativeImage için RGBA byte array
                int[] rgbaPixels = new int[TARGET * TARGET];
                for (int y = 0; y < TARGET; y++) {
                    for (int x = 0; x < TARGET; x++) {
                        int argb = scaled.getRGB(x, y);
                        // ARGB → ABGR (NativeImage format)
                        int a = (argb >> 24) & 0xFF;
                        int r = (argb >> 16) & 0xFF;
                        int g = (argb >>  8) & 0xFF;
                        int b = (argb      ) & 0xFF;
                        rgbaPixels[y * TARGET + x] = (a << 24) | (r << 16) | (g << 8) | b;
                    }
                }

                Minecraft.getInstance().execute(() -> {
                    try {
                        NativeImage img = new NativeImage(NativeImage.Format.RGBA, TARGET, TARGET, false);
                        for (int y = 0; y < TARGET; y++)
                            for (int x = 0; x < TARGET; x++)
                                img.setPixel(x, y, rgbaPixels[y * TARGET + x]);

                        DynamicTexture dyn = new DynamicTexture(img);
                        ResourceLocation loc = ResourceLocation.fromNamespaceAndPath(
                            "nokta_overlay", "album_art_dyn");
                        Minecraft.getInstance().getTextureManager().register(loc, dyn);

                        // Linear filtering — pixel görüntüyü önle
                        dyn.bind();
                        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
                        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
                        // Mipmap oluştur
                        GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
                        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER,
                            GL11.GL_LINEAR_MIPMAP_LINEAR);

                        texture = loc;
                        loading = false;
                        System.out.println("[Nokta] Albüm art yüklendi ✓ " + TARGET + "px");
                    } catch (Exception e) {
                        System.out.println("[Nokta] Texture hata: " + e);
                        loading = false;
                    }
                });
            } catch (Exception e) {
                System.out.println("[Nokta] İndirme hata: " + e);
                loading = false;
            }
        });
        return null;
    }

    public static void invalidate() {
        loadedUrl = ""; texture = null; loading = false;
    }
}
