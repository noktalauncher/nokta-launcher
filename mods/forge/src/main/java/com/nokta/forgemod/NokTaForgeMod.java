package com.nokta.forgemod;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.network.NetworkEvent;

@Mod("nokta_overlay_forge")
public class NokTaForgeMod {

    public static final ForgeNokHud hud = new ForgeNokHud();

    public NokTaForgeMod() {
        FMLJavaModLoadingContext.get().getModEventBus()
            .addListener(this::onClientSetup);

        MinecraftForge.EVENT_BUS.addListener(this::onRegisterCommands);
        MinecraftForge.EVENT_BUS.addListener(this::onRenderHud);
        MinecraftForge.EVENT_BUS.addListener(this::onClientTick);
        MinecraftForge.EVENT_BUS.addListener(this::onMouseClick);
        MinecraftForge.EVENT_BUS.addListener(this::onScreenOpen);
        MinecraftForge.EVENT_BUS.addListener(this::onPlayerLoggedIn);
        MinecraftForge.EVENT_BUS.addListener(this::onPlayerLoggedOut);
    }

    private void onClientSetup(FMLClientSetupEvent event) {
        System.out.println("[Nokta Forge] HUD + Overlay yuklendi!");
    }

    // ── HUD render ───────────────────────────────────────────────────
    private void onRenderHud(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.renderDebug) return;
        hud.render(event.getGuiGraphics(), mc);
    }

    // ── Tick: drag takibi (GLFW mouse pos) ───────────────────────────
    private void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof ChatScreen) || !hud.isEditMode()) return;
        try {
            long win = mc.getWindow().getWindow();
            double[] xArr = new double[1], yArr = new double[1];
            org.lwjgl.glfw.GLFW.glfwGetCursorPos(win, xArr, yArr);
            double scaleX = mc.getWindow().getGuiScaledWidth()  / (double) mc.getWindow().getScreenWidth();
            double scaleY = mc.getWindow().getGuiScaledHeight() / (double) mc.getWindow().getScreenHeight();
            hud.onMouseMove(
                xArr[0] * scaleX, yArr[0] * scaleY,
                mc.getWindow().getGuiScaledWidth(),
                mc.getWindow().getGuiScaledHeight());
        } catch (Exception ignored) {}
    }

    // ── Mouse tıklama / bırakma ───────────────────────────────────────
    private void onMouseClick(InputEvent.MouseButton.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof ChatScreen)) return;
        double[] xArr = new double[1], yArr = new double[1];
        org.lwjgl.glfw.GLFW.glfwGetCursorPos(mc.getWindow().getWindow(), xArr, yArr);
        double scaleX = mc.getWindow().getGuiScaledWidth()  / (double) mc.getWindow().getScreenWidth();
        double scaleY = mc.getWindow().getGuiScaledHeight() / (double) mc.getWindow().getScreenHeight();
        double mx = xArr[0] * scaleX;
        double my = yArr[0] * scaleY;

        if (event.getButton() == 0) {
            if (event.getAction() == org.lwjgl.glfw.GLFW.GLFW_PRESS) {
                hud.onMousePress(mx, my);
            } else if (event.getAction() == org.lwjgl.glfw.GLFW.GLFW_RELEASE) {
                hud.onMouseRelease();
            }
        }
    }

    // ── Chat açılınca edit mode, kapanınca kapat ──────────────────────
    private void onScreenOpen(ScreenEvent.Opening event) {
        if (event.getScreen() instanceof ChatScreen) {
            hud.setEditMode(true);
        } else if (hud.isEditMode()) {
            hud.setEditMode(false);
            hud.onMouseRelease();
        }
    }

    // ── Sunucu join/disconnect → server_info.json ───────────────────────
    private void onPlayerLoggedIn(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event) {
        writeServerInfo();
    }
    private void onPlayerLoggedOut(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        try {
            java.nio.file.Path f = java.nio.file.Paths.get(
                System.getProperty("user.home"), ".nokta-launcher", "server_info.json");
            com.google.gson.JsonObject obj = new com.google.gson.JsonObject();
            obj.addProperty("server", "");
            java.nio.file.Files.writeString(f, obj.toString());
        } catch (Exception ignored) {}
    }
    private void writeServerInfo() {
        try {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            String server = "";
            if (mc.getCurrentServer() != null) {
                server = mc.getCurrentServer().ip;
            } else if (mc.hasSingleplayerServer()) {
                server = "Singleplayer";
            }
            java.nio.file.Path f = java.nio.file.Paths.get(
                System.getProperty("user.home"), ".nokta-launcher", "server_info.json");
            com.google.gson.JsonObject obj = new com.google.gson.JsonObject();
            obj.addProperty("server", server);
            java.nio.file.Files.writeString(f, obj.toString());
            System.out.println("[Nokta Forge] Sunucu: " + server);
        } catch (Exception ignored) {}
    }

    // ── /noktacopy komutu ─────────────────────────────────────────────
    private void onRegisterCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(
            Commands.literal("noktacopy")
                .then(Commands.argument("file", StringArgumentType.greedyString())
                    .executes(ctx -> {
                        String file = StringArgumentType.getString(ctx, "file");
                        try {
                            String os = System.getProperty("os.name").toLowerCase();
                            ProcessBuilder pb;
                            if (os.contains("win")) {
                                pb = new ProcessBuilder("powershell", "-Command",
                                    "Add-Type -Assembly PresentationCore; " +
                                    "[System.Windows.Clipboard]::SetImage(" +
                                    "[System.Windows.Media.Imaging.BitmapFrame]::Create(" +
                                    "[System.Uri]::new('" + file + "')))");
                            } else if (os.contains("mac")) {
                                pb = new ProcessBuilder("osascript", "-e",
                                    "set the clipboard to (read (POSIX file \"" + file + "\") as JPEG picture)");
                            } else {
                                pb = new ProcessBuilder("xclip", "-selection", "clipboard",
                                    "-t", "image/png", "-i", file);
                            }
                            pb.start();
                            ctx.getSource().sendSuccess(
                                () -> Component.literal("Kopyalandi!"), false);
                        } catch (Exception e) {
                            ctx.getSource().sendFailure(
                                Component.literal("Hata: " + e.getMessage()));
                        }
                        return 1;
                    })));
    }
}
