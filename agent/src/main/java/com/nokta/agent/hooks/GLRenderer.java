package com.nokta.agent.hooks;

import java.awt.image.BufferedImage;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;

public class GLRenderer {
    private static int textureId = -1;
    private static boolean initTried = false;
    private static boolean glReady = false;
    private static boolean modernGL = false;

    private static final int GL_TEXTURE_2D = 0x0DE1;
    private static final int GL_RGBA = 0x1908;
    private static final int GL_UNSIGNED_BYTE = 0x1401;
    private static final int GL_LINEAR = 0x2601;
    private static final int GL_TEXTURE_MIN_FILTER = 0x2801;
    private static final int GL_TEXTURE_MAG_FILTER = 0x2800;
    private static final int GL_QUADS = 0x0007;
    private static final int GL_SRC_ALPHA = 0x0302;
    private static final int GL_ONE_MINUS_SRC_ALPHA = 0x0303;
    private static final int GL_BLEND = 0x0BE2;

    private static Method glGenTextures, glBindTexture, glTexImage2D;
    private static Method glTexParameteri, glEnable, glDisable;
    private static Method glBegin, glEnd, glTexCoord2f, glVertex2f;
    private static Method glColor4f, glBlendFunc;
    private static Method glPushMatrix, glPopMatrix;

    public static void init(BufferedImage img) {
        if (initTried) return;
        initTried = true;
        try {
            if (!resolveGL()) {
                System.out.println("[NoftaAgent] GL metotlari bulunamadi!");
                return;
            }
            textureId = uploadTexture(img);
            glReady = textureId > 0;
            System.out.println("[NoftaAgent] GL hazir! id=" + textureId + " modern=" + modernGL);
        } catch (Exception e) {
            System.out.println("[NoftaAgent] GL init hatasi: " + e);
        }
    }

    private static boolean resolveGL() {
        Class<?> gl = null;
        for (String cn : new String[]{"org.lwjgl.opengl.GL11","org.lwjgl.opengl.GL11C"}) {
            try { gl = Class.forName(cn); break; }
            catch (Exception ignored) {}
        }
        if (gl == null) return false;

        glGenTextures   = findM(gl, "glGenTextures");
        glBindTexture   = findM(gl, "glBindTexture",   int.class, int.class);
        glTexParameteri = findM(gl, "glTexParameteri", int.class, int.class, int.class);
        glTexImage2D    = findM(gl, "glTexImage2D",    int.class,int.class,int.class,
                                int.class,int.class,int.class,int.class,int.class,
                                ByteBuffer.class);
        glEnable        = findM(gl, "glEnable",    int.class);
        glDisable       = findM(gl, "glDisable",   int.class);
        glColor4f       = findM(gl, "glColor4f",   float.class,float.class,float.class,float.class);
        glBlendFunc     = findM(gl, "glBlendFunc", int.class,int.class);
        glPushMatrix    = findM(gl, "glPushMatrix");
        glPopMatrix     = findM(gl, "glPopMatrix");
        glBegin         = findM(gl, "glBegin",      int.class);
        glEnd           = findM(gl, "glEnd");
        glTexCoord2f    = findM(gl, "glTexCoord2f", float.class, float.class);
        glVertex2f      = findM(gl, "glVertex2f",   float.class, float.class);

        if (glBegin != null) {
            modernGL = false;
            System.out.println("[NoftaAgent] Mod: LWJGL2/compat glBegin/glEnd");
        } else {
            modernGL = true;
            System.out.println("[NoftaAgent] Mod: LWJGL3/core Tesselator");
        }
        return glGenTextures != null && glBindTexture != null && glTexImage2D != null;
    }

    private static int uploadTexture(BufferedImage img) throws Exception {
        int w = img.getWidth(), h = img.getHeight();
        int[] px = img.getRGB(0, 0, w, h, null, 0, w);
        ByteBuffer buf = ByteBuffer.allocateDirect(w * h * 4);
        for (int p : px) {
            buf.put((byte)((p >> 16) & 0xFF));
            buf.put((byte)((p >>  8) & 0xFF));
            buf.put((byte)((p      ) & 0xFF));
            buf.put((byte)((p >> 24) & 0xFF));
        }
        buf.flip();

        int id;
        try {
            id = (Integer) glGenTextures.invoke(null);
        } catch (Exception e) {
            IntBuffer ib = IntBuffer.allocate(1);
            Method m2 = findM(glGenTextures.getDeclaringClass(),
                              "glGenTextures", IntBuffer.class);
            if (m2 != null) m2.invoke(null, ib);
            else             glGenTextures.invoke(null, ib);
            id = ib.get(0);
        }

        glBindTexture.invoke(null, GL_TEXTURE_2D, id);
        glTexParameteri.invoke(null, GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri.invoke(null, GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexImage2D.invoke(null,
            GL_TEXTURE_2D, 0, GL_RGBA, w, h, 0,
            GL_RGBA, GL_UNSIGNED_BYTE, buf);
        glBindTexture.invoke(null, GL_TEXTURE_2D, 0);
        return id;
    }

    public static void drawBackground(int x, int y, int w, int h, int sw, int sh) {
        if (!glReady || textureId < 0) return;
        try {
            if (modernGL) drawModern(x, y, w, h);
            else          drawLegacy(x, y, w, h, sw, sh);
        } catch (Exception e) {
            System.out.println("[NoftaAgent] draw hatasi: " + e);
            glReady = false;
        }
    }

    private static void drawLegacy(int x, int y, int w, int h, int sw, int sh) throws Exception {
        float x0 =  (2f * x)       / sw - 1f;
        float y0 = -(2f * y)       / sh + 1f;
        float x1 =  (2f * (x + w)) / sw - 1f;
        float y1 = -(2f * (y + h)) / sh + 1f;

        if (glPushMatrix != null) glPushMatrix.invoke(null);
        glEnable.invoke(null, GL_TEXTURE_2D);
        glEnable.invoke(null, GL_BLEND);
        glBlendFunc.invoke(null, GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glBindTexture.invoke(null, GL_TEXTURE_2D, textureId);
        glColor4f.invoke(null, 1f, 1f, 1f, 0.9f);
        glBegin.invoke(null, GL_QUADS);
        glTexCoord2f.invoke(null, 0f, 0f); glVertex2f.invoke(null, x0, y0);
        glTexCoord2f.invoke(null, 1f, 0f); glVertex2f.invoke(null, x1, y0);
        glTexCoord2f.invoke(null, 1f, 1f); glVertex2f.invoke(null, x1, y1);
        glTexCoord2f.invoke(null, 0f, 1f); glVertex2f.invoke(null, x0, y1);
        glEnd.invoke(null);
        glDisable.invoke(null, GL_BLEND);
        glDisable.invoke(null, GL_TEXTURE_2D);
        if (glPopMatrix != null) glPopMatrix.invoke(null);
    }

    private static void drawModern(int x, int y, int w, int h) {
        System.out.println("[NoftaAgent] Modern GL cizimi henuz desteklenmiyor, mixin kullanilmalidir.");
    }

    private static Method findM(Class<?> cls, String name, Class<?>... p) {
        try { return cls.getMethod(name, p); }
        catch (Exception ignored) {}
        for (Method m : cls.getDeclaredMethods()) {
            if (m.getName().equals(name) && m.getParameterCount() == p.length) {
                m.setAccessible(true);
                return m;
            }
        }
        return null;
    }
}