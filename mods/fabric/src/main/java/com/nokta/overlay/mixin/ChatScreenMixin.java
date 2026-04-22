package com.nokta.overlay.mixin;

import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChatScreen.class)
public class ChatScreenMixin {

    @Shadow protected EditBox input;

    private boolean justOpened = true;

    @Inject(method = "init", at = @At("TAIL"))
    private void onInit(CallbackInfo ci) {
        if (input != null) input.setValue("");
        justOpened = true;
    }

    @Inject(method = "charTyped", at = @At("HEAD"), cancellable = true)
    private void onCharTyped(char c, int mods, CallbackInfoReturnable<Boolean> cir) {
        // İlk karakter T veya t ise yut — chat açış tuşu
        if (justOpened && (c == 't' || c == 'T' || c == '/' )) {
            justOpened = false;
            if (c != '/') cir.setReturnValue(true); // / işaretini koru
        } else {
            justOpened = false;
        }
    }
}
