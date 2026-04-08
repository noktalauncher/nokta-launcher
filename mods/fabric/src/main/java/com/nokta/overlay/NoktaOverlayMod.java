package com.nokta.overlay;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.gui.screens.ChatScreen;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;

public class NoktaOverlayMod implements ClientModInitializer {
    public static OverlayRenderer renderer;

    @Override
    public void onInitializeClient() {
        renderer = new OverlayRenderer();
        ScreenshotNotifier.start();
        // Screenshot chat mesajına [Copy] butonu ekle
        // Screenshot watcher - chat'e [Copy] butonu ekle
        final String[] lastSsPath = {""};
        ClientTickEvents.START_CLIENT_TICK.register(mc -> {
            if (mc.level == null) return;
            try {
                java.nio.file.Path dir = java.nio.file.Path.of(
                    System.getProperty("user.home"), ".nokta-launcher", "screenshots");
                if (!java.nio.file.Files.exists(dir)) return;
                java.util.Optional<java.nio.file.Path> last = java.nio.file.Files.list(dir)
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
        // Vanilla screenshot mesajına [Copy] ekle — tick watcher ile
        // Tick watcher zaten lastSsPath'i guncelliyor
        // MODIFY_GAME yerine ALLOW_RECEIVE kullan
        net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents.ALLOW_GAME.register((msg, overlay) -> {
            String txt = msg.getString();
            if (txt.contains("olarak kaydedildi") || txt.contains("screenshot was saved")) {
                System.out.println("[Nokta] Screenshot mesaji alindi: " + txt);
                // Son screenshot dosyasini bul
                try {
                    java.nio.file.Path ssDir = java.nio.file.Path.of(
                        System.getProperty("user.home"), ".nokta-launcher", "screenshots");
                    if (java.nio.file.Files.exists(ssDir)) {
                        java.util.Optional<java.nio.file.Path> latest = java.nio.file.Files.list(ssDir)
                            .filter(p -> p.toString().endsWith(".png"))
                            .max(java.util.Comparator.comparingLong(p -> {
                                try { return java.nio.file.Files.getLastModifiedTime(p).toMillis(); }
                                catch (Exception e) { return 0L; }
                            }));
                        if (latest.isPresent()) {
                            lastSsPath[0] = latest.get().toString();
                            System.out.println("[Nokta] Son screenshot: " + lastSsPath[0]);
                        }
                    }
                } catch (Exception ignored) {}
            }
            return true;
        });
        // MODIFY_GAME ile [Copy] butonu ekle
        net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents.MODIFY_GAME.register((msg, overlay) -> {
            String txt = msg.getString();
            if ((txt.contains("olarak kaydedildi") || txt.contains("screenshot was saved"))
                    && !lastSsPath[0].isEmpty()) {
                System.out.println("[Nokta] [Copy] ekleniyor: " + lastSsPath[0]);
                return ((net.minecraft.network.chat.MutableComponent) msg)
                    .append(net.minecraft.network.chat.Component.literal(" [Copy]")
                        .setStyle(net.minecraft.network.chat.Style.EMPTY
                            .withColor(0x55FFFF)
                            .withUnderlined(true)
                            .withClickEvent(new net.minecraft.network.chat.ClickEvent(
                                net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND,
                                "/noktacopy " + lastSsPath[0]))));
            }
            return msg;
        });
        // /noktacopy client komutu
        net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback.EVENT.register((dispatcher, ctx) -> {
            dispatcher.register(net.fabricmc.fabric.api.client.command.v2.ClientCommandManager
                .literal("noktacopy")
                .then(net.fabricmc.fabric.api.client.command.v2.ClientCommandManager
                    .argument("file", com.mojang.brigadier.arguments.StringArgumentType.greedyString())
                    .executes(c -> {
                        String file = com.mojang.brigadier.arguments.StringArgumentType.getString(c, "file");
                        try {
                            Runtime.getRuntime().exec(new String[]{"xclip","-selection","clipboard","-t","image/png","-i",file});
                            c.getSource().sendFeedback(net.minecraft.network.chat.Component.literal("Kopyalandı!").setStyle(net.minecraft.network.chat.Style.EMPTY.withColor(0xAAFFAA)));
                        } catch (Exception e) {}
                        return 1;
                    })));
        });
        // Screenshot kopyala butonu click
        net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents.afterMouseClick(screen).register(
                (scr, mx, my, btn) -> ScreenshotNotifier.onClick((int)mx, (int)my, scr.width, scr.height)
            );
        });
        HudRenderCallback.EVENT.register(renderer);

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (renderer != null) {
                renderer.onTick(client);
                // Her 2 saniyede dosyaları oku
                if (System.currentTimeMillis() % 2000 < 50) renderer.readFiles();
            }
        });

        // Chat açılınca ilk tick'te input'u temizle
        final boolean[] chatWasOpen = {false};
        final boolean[] cleared = {false};
        ClientTickEvents.START_CLIENT_TICK.register(mc -> {
            boolean chatOpen = mc.screen instanceof ChatScreen;
            if (chatOpen && !chatWasOpen[0]) { cleared[0] = false; }
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
        // /nokta_copy komutu - ss panoya kopyala
        net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback.EVENT.register((dispatcher, ctx) -> {
            dispatcher.register(net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal("nokta_copy")
                .then(net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument("path",
                    com.mojang.brigadier.arguments.StringArgumentType.greedyString())
                    .executes(c -> {
                        String p = com.mojang.brigadier.arguments.StringArgumentType.getString(c, "path");
                        try {
                            Runtime.getRuntime().exec(new String[]{"xclip","-selection","clipboard","-t","image/png","-i",p});
                        } catch (Exception e) {
                            c.getSource().sendFeedback(net.minecraft.network.chat.Component.literal("Hata: " + e.getMessage()));
                        }
                        return 1;
                    })));
        });
        // Sunucu join/disconnect        // Sunucu join/disconnect        // Sunucu join/disconnect olayları → server_info.json
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            try {
                String server = "";
                var addr = handler.getConnection().getRemoteAddress();
                if (addr == null || addr.toString().contains("127.0.0.1") || addr.toString().contains("localhost")) {
                    server = "Singleplayer";
                } else {
                    server = addr.toString().replaceAll("/", "").replaceAll(":.*", "");
                }
                java.nio.file.Path f = java.nio.file.Paths.get(
                    System.getProperty("user.home"), ".nokta-launcher", "screenshots", "server_info.json");
                com.google.gson.JsonObject obj = new com.google.gson.JsonObject();
                obj.addProperty("server", server);
                java.nio.file.Files.writeString(f, obj.toString());
                System.out.println("[Nokta] Sunucu: " + server);
            } catch (Exception ignored) {}
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            try {
                java.nio.file.Path f = java.nio.file.Paths.get(
                    System.getProperty("user.home"), ".nokta-launcher", "screenshots", "server_info.json");
                com.google.gson.JsonObject obj = new com.google.gson.JsonObject();
                obj.addProperty("server", "");
                java.nio.file.Files.writeString(f, obj.toString());
            } catch (Exception ignored) {}
        });
        // Spotify butonunu menülere ekle
        SpotifyButtonInjector.register();

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

        System.out.println("[Nokta] Overlay + Spotify butonları yüklendi!");
    }
}
