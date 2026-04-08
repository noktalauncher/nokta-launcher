package com.nokta.forgemod.mixin;

import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import java.nio.file.*;
import java.util.Optional;

@Mixin(ChatComponent.class)
public class ChatComponentMixin {

    @ModifyVariable(method = "addMessage(Lnet/minecraft/network/chat/Component;)V",
                    at = @At("HEAD"), argsOnly = true)
    private Component onAddMessage(Component msg) {
        String txt = msg.getString();
        if (txt.contains("olarak kaydedildi") || txt.contains("screenshot was saved")) {
            try {
                String home = System.getProperty("user.home");
                String os = System.getProperty("os.name").toLowerCase();
                Path ssDir;
                if (os.contains("win")) {
                    ssDir = Path.of(System.getenv("APPDATA"), ".nokta-launcher", "screenshots");
                } else if (os.contains("mac")) {
                    ssDir = Path.of(home, "Library/Application Support/nokta-launcher/screenshots");
                } else {
                    ssDir = Path.of(home, ".nokta-launcher", "screenshots");
                }
                if (Files.exists(ssDir)) {
                    Optional<Path> latest = Files.list(ssDir)
                        .filter(p -> p.toString().endsWith(".png"))
                        .max((a, b) -> {
                            try {
                                return Files.getLastModifiedTime(a)
                                    .compareTo(Files.getLastModifiedTime(b));
                            } catch (Exception e) { return 0; }
                        });
                    if (latest.isPresent()) {
                        String ssPath = latest.get().toString();
                        return ((MutableComponent) msg)
                            .append(Component.literal(" [Copy]")
                                .setStyle(Style.EMPTY
                                    .withColor(0x55FFFF)
                                    .withUnderlined(true)
                                    .withClickEvent(new ClickEvent(
                                        ClickEvent.Action.RUN_COMMAND,
                                        "/noktacopy " + ssPath))));
                    }
                }
            } catch (Exception ignored) {}
        }
        return msg;
    }
}
