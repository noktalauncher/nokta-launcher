package com.nokta.overlay;

import com.google.gson.*;
import javax.swing.*;
import java.awt.*;
import java.nio.file.*;
import java.util.*;
import java.util.Timer;

public class NoktaOverlay {

    private static final Path VOICE_FILE   = Paths.get(
        System.getProperty("user.home"), ".nokta-launcher", "voice_channel.json");
    private static final Path SPOTIFY_FILE = Paths.get(
        System.getProperty("user.home"), ".nokta-launcher", "spotify_current.json");

    private String channelName = null;
    private final java.util.List<String>  voiceUsers = new ArrayList<>();
    private final java.util.List<Boolean> speaking   = new ArrayList<>();
    private String  spotifyTitle  = null;
    private String  spotifyArtist = "";
    private boolean spotifyPlaying = false;

    private JWindow voiceWindow;
    private JWindow spotifyWindow;
    private final Font font;
    private final Font fontBold;

    public NoktaOverlay() {
        font     = new Font("SansSerif", Font.PLAIN,  12);
        fontBold = new Font("SansSerif", Font.BOLD,   12);

        // Discord ses kanalı penceresi - sol üst, küçük
        voiceWindow = createOverlayWindow();
        voiceWindow.setSize(200, 10);
        voiceWindow.setLocation(10, 10);

        // Spotify penceresi - sol alt, küçük
        spotifyWindow = createOverlayWindow();
        spotifyWindow.setSize(220, 58);

        JPanel voicePanel = createPanel(true);
        JPanel spotifyPanel = createPanel(false);
        voiceWindow.add(voicePanel);
        spotifyWindow.add(spotifyPanel);

        new Timer(true).scheduleAtFixedRate(new TimerTask() {
            @Override public void run() {
                readFiles();
                SwingUtilities.invokeLater(() -> {
                    updateVoice(voicePanel);
                    updateSpotify(spotifyPanel);
                });
            }
        }, 0, 1000);
    }

    private JWindow createOverlayWindow() {
        JWindow w = new JWindow();
        w.setAlwaysOnTop(true);
        w.setBackground(new Color(0, 0, 0, 0));
        w.setFocusableWindowState(false);
        w.setEnabled(false);
        w.setVisible(true);
        return w;
    }

    private JPanel createPanel(boolean isVoice) {
        JPanel p = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                if (isVoice) drawVoice(g2);
                else         drawSpotify(g2);
                g2.dispose();
            }
        };
        p.setOpaque(false);
        p.setBackground(new Color(0,0,0,0));
        p.setEnabled(false);
        return p;
    }

    private void drawVoice(Graphics2D g) {
        if (channelName == null || voiceUsers.isEmpty()) return;
        int h = 18 + voiceUsers.size() * 16 + 6;
        g.setColor(new Color(0, 0, 0, 160));
        g.fillRoundRect(0, 0, 198, h, 8, 8);
        g.setColor(new Color(88, 101, 242, 150));
        g.drawRoundRect(0, 0, 197, h - 1, 8, 8);
        g.setFont(fontBold);
        g.setColor(new Color(88, 101, 242));
        g.drawString("🔊 " + channelName, 8, 14);
        int y = 28;
        g.setFont(font);
        for (int i = 0; i < voiceUsers.size(); i++) {
            boolean spk = i < speaking.size() && speaking.get(i);
            g.setColor(spk ? new Color(87, 242, 135) : new Color(180, 180, 180));
            g.drawString((spk ? "▶ " : "  ") + voiceUsers.get(i), 10, y);
            y += 16;
        }
    }

    private void drawSpotify(Graphics2D g) {
        if (spotifyTitle == null) return;
        g.setColor(new Color(0, 0, 0, 160));
        g.fillRoundRect(0, 0, 218, 56, 8, 8);
        g.setColor(new Color(29, 185, 84, 150));
        g.drawRoundRect(0, 0, 217, 55, 8, 8);
        g.setFont(fontBold);
        g.setColor(new Color(29, 185, 84));
        g.drawString("♫", 8, 16);
        g.setColor(spotifyPlaying ? new Color(87,242,135) : new Color(150,150,150));
        g.drawString(spotifyPlaying ? "▶" : "⏸", 24, 16);
        g.setFont(font);
        g.setColor(Color.WHITE);
        String title = spotifyTitle.length() > 24 ? spotifyTitle.substring(0,21)+"..." : spotifyTitle;
        g.drawString(title, 40, 16);
        g.setColor(new Color(150, 150, 150));
        String artist = spotifyArtist.length() > 28 ? spotifyArtist.substring(0,25)+"..." : spotifyArtist;
        g.drawString(artist, 10, 38);
    }

    private void updateVoice(JPanel p) {
        if (channelName == null || voiceUsers.isEmpty()) {
            voiceWindow.setSize(1, 1); return;
        }
        int h = 22 + voiceUsers.size() * 16;
        voiceWindow.setSize(200, h);
        p.setSize(200, h);
        p.repaint();
    }

    private void updateSpotify(JPanel p) {
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        Rectangle screen = ge.getDefaultScreenDevice().getDefaultConfiguration().getBounds();
        if (spotifyTitle == null) {
            spotifyWindow.setSize(1, 1); return;
        }
        spotifyWindow.setSize(222, 60);
        spotifyWindow.setLocation(screen.x + 10, screen.y + screen.height - 70);
        p.setSize(222, 60);
        p.repaint();
    }

    private void readFiles() {
        try {
            if (Files.exists(VOICE_FILE)) {
                JsonObject j = JsonParser.parseString(Files.readString(VOICE_FILE)).getAsJsonObject();
                channelName = j.has("channel") ? j.get("channel").getAsString() : null;
                voiceUsers.clear(); speaking.clear();
                if (j.has("users")) {
                    for (JsonElement el : j.getAsJsonArray("users")) {
                        JsonObject u = el.getAsJsonObject();
                        voiceUsers.add(u.has("username") ? u.get("username").getAsString() : "?");
                        speaking.add(u.has("speaking") && u.get("speaking").getAsBoolean());
                    }
                }
            } else { channelName = null; voiceUsers.clear(); }
        } catch (Exception ignored) {}
        try {
            if (Files.exists(SPOTIFY_FILE)) {
                JsonObject j = JsonParser.parseString(Files.readString(SPOTIFY_FILE)).getAsJsonObject();
                spotifyTitle   = j.has("title")   ? j.get("title").getAsString()   : null;
                spotifyArtist  = j.has("artist")  ? j.get("artist").getAsString()  : "";
                spotifyPlaying = j.has("playing") && j.get("playing").getAsBoolean();
            } else { spotifyTitle = null; }
        } catch (Exception ignored) {}
    }

    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeLater(NoktaOverlay::new);
        Thread.currentThread().join();
    }
}
