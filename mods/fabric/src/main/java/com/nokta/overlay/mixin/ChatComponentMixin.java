package com.nokta.overlay.mixin;

import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

@Mixin(value = ChatComponent.class, priority = 900)
public abstract class ChatComponentMixin {

    private static final DateTimeFormatter TIME_FMT =
        DateTimeFormatter.ofPattern("HH:mm");

    // Inject yerine @Inject HEAD + modify kullan — daha güvenli
    @Inject(method = "addMessage(Lnet/minecraft/network/chat/Component;)V",
            at = @At("HEAD"), cancellable = true)
    private void onAddMessage(Component msg, CallbackInfo ci) {
        try {
            // Sadece zaten timestamp yoksa ekle
            String plain = msg.getString();
            if (plain.startsWith("[") && plain.length() > 5 && plain.charAt(3) == ':') {
                return; // zaten timestamp var
            }
            String t = LocalTime.now().format(TIME_FMT);
            MutableComponent stamped = ((MutableComponent)
                Component.literal("§8[" + t + "] §r")).append(msg);
            // Kendi kendini tekrar çağır — sonsuz döngü yok çünkü timestamp kontrolü var
            ((ChatComponent)(Object)this).addMessage(stamped);
            ci.cancel();
        } catch (Exception e) {
            // Hata olursa orijinal mesajı göster
        }
    }
}
