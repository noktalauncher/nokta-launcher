package com.nokta.overlay;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

public class NoktaOverlayMod implements ClientModInitializer {

    public static OverlayRenderer renderer;

    @Override
    public void onInitializeClient() {
        renderer = new OverlayRenderer();
        ScreenshotNotifier.start();

        // ── Screenshot watcher ───────────────────────────────────────
        final String[] lastSsPath = {""};
        ClientTickEvents.START_CLIENT_TICK.register(mc -> {
            if (mc.level == null) return;
            try {
                java.nio.file.Path dir = java.nio.file.Path.of(
                    System.getProperty("user.home"), ".nokta-launcher", "screenshots");
                if (!java.nio.file.Files.exists(dir)) return;
                java.util.Optional<java.nio.file.Path> last =
                    java.nio.file.Files.list(dir)
                        .filter(p -> p.toString().endsWith(".png"))
                        .max(java.util.Comparator.comparingLong(p -> {
                            try { return java.nio.file.Files.getLastModifiedTime(p).toMillis(); }
                            catch (Exception e) { return 0L; }
                        }));
                if (last.isPresent()) {
                    String path2 = last.get().toString();
                    long mod = java.nio.file.Files.getLastModifiedTime(last.get()).toMillis();
                    if (!path2.equals(lastSsPath[0]) && System.currentTimeMillis() - mod < 3000) {
                        lastSsPath[0] = path2;
                        ScreenshotNotifier.setLastPath(path2);
                    }
                }
            } catch (Exception ignored) {}
        });

        // ── Screenshot [Copy] butonu ─────────────────────────────────
        net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents.ALLOW_GAME.register((msg, overlay) -> {
            String txt = msg.getString();
            if (txt.contains("olarak kaydedildi") || txt.contains("screenshot was saved")) {
                try {
                    java.nio.file.Path ssDir = java.nio.file.Path.of(
                        System.getProperty("user.home"), ".nokta-launcher", "screenshots");
                    if (java.nio.file.Files.exists(ssDir)) {
                        java.util.Optional<java.nio.file.Path> latest =
                            java.nio.file.Files.list(ssDir)
                                .filter(p -> p.toString().endsWith(".png"))
                                .max(java.util.Comparator.comparingLong(p -> {
                                    try { return java.nio.file.Files.getLastModifiedTime(p).toMillis(); }
                                    catch (Exception e) { return 0L; }
                                }));
                        if (latest.isPresent()) lastSsPath[0] = latest.get().toString();
                    }
                } catch (Exception ignored) {}
            }
            return true;
        });

        net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents.MODIFY_GAME.register((msg, overlay) -> {
            String txt = msg.getString();
            if ((txt.contains("olarak kaydedildi") || txt.contains("screenshot was saved"))
                    && !lastSsPath[0].isEmpty()) {
                return ((net.minecraft.network.chat.MutableComponent) msg)
                    .append(Component.literal(" [Copy]")
                        .setStyle(Style.EMPTY
                            .withColor(0x55FFFF)
                            .withUnderlined(true)
                            .withClickEvent(new ClickEvent(
                                ClickEvent.Action.RUN_COMMAND,
                                "/noktacopy " + lastSsPath[0]))));
            }
            return msg;
        });

        // ── /noktacopy komutu ────────────────────────────────────────
        net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback.EVENT.register((dispatcher, ctx) -> {
            dispatcher.register(
                net.fabricmc.fabric.api.client.command.v2.ClientCommandManager
                    .literal("noktacopy")
                    .then(net.fabricmc.fabric.api.client.command.v2.ClientCommandManager
                        .argument("file", com.mojang.brigadier.arguments.StringArgumentType.greedyString())
                        .executes(c -> {
                            String file = com.mojang.brigadier.arguments.StringArgumentType.getString(c, "file");
                            try {
                                Runtime.getRuntime().exec(new String[]{
                                    "xclip","-selection","clipboard","-t","image/png","-i", file});
                                c.getSource().sendFeedback(Component.literal("✅ Kopyalandı!")
                                    .setStyle(Style.EMPTY.withColor(0xAAFFAA)));
                            } catch (Exception e) {
                                c.getSource().sendFeedback(Component.literal("❌ Hata: " + e.getMessage()));
                            }
                            return 1;
                        })));
        });

        // ── Screenshot onClick ───────────────────────────────────────
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            ScreenMouseEvents.afterMouseClick(screen).register(
                (scr, mx, my, btn) -> ScreenshotNotifier.onClick((int)mx, (int)my, scr.width, scr.height));
        });

        // ── HUD render ───────────────────────────────────────────────
        HudRenderCallback.EVENT.register(renderer);

        // ── Chat açılınca HUD edit modu + T fix ─────────────────────
        final boolean[] chatWasOpen = {false};
        final boolean[] cleared     = {false};

        ClientTickEvents.START_CLIENT_TICK.register(mc -> {
            boolean chatOpen = mc.screen instanceof ChatScreen;

            // Chat yeni açıldı
            if (chatOpen && !chatWasOpen[0]) {
                cleared[0] = false;
                OverlayRenderer.hud.setEditMode(true);  // HUD edit modu aç
            }
            // Chat kapandı
            if (!chatOpen && chatWasOpen[0]) {
                OverlayRenderer.hud.setEditMode(false); // HUD edit modu kapat
                OverlayRenderer.hud.onMouseRelease();   // drag/resize bitir
            }

            // T harfi fix — input kutusunu temizle
            if (chatOpen && !cleared[0]) {
                cleared[0] = true;
                try {
                    for (java.lang.reflect.Field fi : mc.screen.getClass().getDeclaredFields()) {
                        fi.setAccessible(true);
                        Object val = fi.get(mc.screen);
                        if (val instanceof net.minecraft.client.gui.components.EditBox eb) {
                            eb.setValue("");
                            break;
                        }
                    }
                } catch (Exception ignored) {}
            }

            chatWasOpen[0] = chatOpen;
        });

        // ── Chat açıkken mouse olayları → HUD drag/resize ────────────
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (!(screen instanceof ChatScreen)) return;

            ScreenMouseEvents.beforeMouseClick(screen).register((scr, mx, my, btn) -> {
                if (btn == 0) OverlayRenderer.hud.onMousePress(mx, my);
            });

            // Drag: tick ile mouse pozisyonu takip et
            // (beforeMouseDrag bu Fabric versiyonunda yok)

            ScreenMouseEvents.beforeMouseRelease(screen).register((scr, mx, my, btn) -> {
                if (btn == 0) OverlayRenderer.hud.onMouseRelease();
            });
        });

        // ── Tick: FPS/Server güncelle ────────────────────────────────
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (renderer != null) {
                renderer.onTick(client);
                if (System.currentTimeMillis() % 2000 < 50) renderer.readFiles();
            }
        });

        // ── Sunucu join/disconnect → server_info.json ────────────────
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            try {
                String server = "";
                var addr = handler.getConnection().getRemoteAddress();
                if (addr == null
                        || addr.toString().contains("127.0.0.1")
                        || addr.toString().contains("localhost")) {
                    server = "Singleplayer";
                } else {
                    server = addr.toString().replaceAll("/","").replaceAll(":.*","");
                }
                java.nio.file.Path f = java.nio.file.Paths.get(
                    System.getProperty("user.home"), ".nokta-launcher", "server_info.json");
                com.google.gson.JsonObject obj = new com.google.gson.JsonObject();
                obj.addProperty("server", server);
                java.nio.file.Files.writeString(f, obj.toString());
                System.out.println("[Nokta] Sunucu: " + server);
            } catch (Exception ignored) {}
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            try {
                java.nio.file.Path f = java.nio.file.Paths.get(
                    System.getProperty("user.home"), ".nokta-launcher", "server_info.json");
                com.google.gson.JsonObject obj = new com.google.gson.JsonObject();
                obj.addProperty("server", "");
                java.nio.file.Files.writeString(f, obj.toString());
            } catch (Exception ignored) {}
        });

        // ── Spotify ──────────────────────────────────────────────────
        SpotifyButtonInjector.register();

        // ── Launcher durdurulunca PID temizle ────────────────────────
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            try {
                java.nio.file.Path pidFile = java.nio.file.Paths.get(
                    System.getProperty("user.home"), ".nokta-launcher", "screenshots", "overlay.pid");
                if (java.nio.file.Files.exists(pidFile)) {
                    long pid = Long.parseLong(java.nio.file.Files.readString(pidFile).trim());
                    ProcessHandle.of(pid).ifPresent(ProcessHandle::destroy);
                    java.nio.file.Files.deleteIfExists(pidFile);
                }
            } catch (Exception ignored) {}
        });

        System.out.println("[Nokta] HUD + Overlay + Spotify yüklendi!");
    }
}
