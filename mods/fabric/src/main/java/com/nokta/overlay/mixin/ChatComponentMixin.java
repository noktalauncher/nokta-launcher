package com.nokta.overlay.mixin;

import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import java.nio.file.*;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

@Mixin(ChatComponent.class)
public class ChatComponentMixin {

    private static final DateTimeFormatter TIME_FMT =
        DateTimeFormatter.ofPattern("HH:mm");

    @ModifyVariable(method = "addMessage(Lnet/minecraft/network/chat/Component;)V",
                    at = @At("HEAD"), argsOnly = true)
    private Component onAddMessage(Component msg) {
        // Saat damgası ekle
        String timeStr = LocalTime.now().format(TIME_FMT);
        MutableComponent withTime = Component.literal("§8[" + timeStr + "] §r")
            .append(msg);

        // Screenshot [Copy] butonu
        String txt = msg.getString();
        if (txt.contains("olarak kaydedildi") || txt.contains("screenshot was saved")) {
            try {
                Path ssDir = Path.of(System.getProperty("user.home"),
                    ".nokta-launcher", "screenshots");
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
                        return withTime.append(
                            Component.literal(" [Copy]")
                                .setStyle(Style.EMPTY
                                    .withColor(0x55FFFF)
                                    .withUnderlined(true)
                                    .withClickEvent(new ClickEvent(
                                        ClickEvent.Action.RUN_COMMAND,
                                        "/noktacopy " + latest.get()))));
                    }
                }
            } catch (Exception ignored) {}
        }

        return withTime;
    }
}
