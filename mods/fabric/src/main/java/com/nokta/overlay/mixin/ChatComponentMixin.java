package com.nokta.overlay.mixin;

import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

@Mixin(value = ChatComponent.class, priority = 900)
public class ChatComponentMixin {

    private static final DateTimeFormatter TIME_FMT =
        DateTimeFormatter.ofPattern("HH:mm");

    @ModifyVariable(
        method = "addMessage(Lnet/minecraft/network/chat/Component;)V",
        at = @At("HEAD"), argsOnly = true, require = 0, expect = 0)
    private Component onAddMessage(Component msg) {
        try {
            String t = LocalTime.now().format(TIME_FMT);
            return ((MutableComponent) Component.literal("§8[" + t + "] §r"))
                .append(msg);
        } catch (Exception e) {
            return msg;
        }
    }
}
