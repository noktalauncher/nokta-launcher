package com.nokta.overlay.mixin;

import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import java.nio.file.*;
import java.util.Optional;

@Mixin(ChatComponent.class)
public class ChatComponentMixin {

    // Chat geçmişini 100 → 1000 satıra çıkar
    @ModifyConstant(method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/message/MessageSignature;Lnet/minecraft/client/GuiMessageTag;)V",
                    constant = @Constant(intValue = 100))
    private int increaseHistory(int original) {
        return 1000;
    }

    // Screenshot [Copy] butonu ekle
    @ModifyVariable(method = "addMessage(Lnet/minecraft/network/chat/Component;)V",
                    at = @At("HEAD"), argsOnly = true)
    private Component onAddMessage(Component msg) {
        String txt = msg.getString();
        if (txt.contains("olarak kaydedildi") || txt.contains("screenshot was saved")) {
            try {
                String home = System.getProperty("user.home");
                Path ssDir = Path.of(home, ".nokta-launcher", "screenshots");
                if (Files.exists(ssDir)) {
                    Optional<Path> latest = Files.list(ssDir)
                        .filter(p -> p.toString().endsWith(".png"))
                        .max((a, b) -> {
                            try { return Files.getLastModifiedTime(a).compareTo(Files.getLastModifiedTime(b)); }
                            catch (Exception e) { return 0; }
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
