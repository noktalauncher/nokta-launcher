package com.nokta.forgemod;

import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import com.mojang.brigadier.arguments.StringArgumentType;

@Mod("nokta_overlay_forge")
public class NokTaForgeMod {

    public NokTaForgeMod() {
        FMLJavaModLoadingContext.get().getModEventBus()
            .addListener(this::onClientSetup);
        MinecraftForge.EVENT_BUS.addListener(this::onRegisterCommands);
    }

    private void onClientSetup(FMLClientSetupEvent event) {
        System.out.println("[Nokta Forge] Overlay mod yuklendi!");
    }

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
                                () -> Component.literal("✅ Kopyalandi!"), false);
                        } catch (Exception e) {
                            ctx.getSource().sendFailure(
                                Component.literal("❌ Hata: " + e.getMessage()));
                        }
                        return 1;
                    })));
    }
}
