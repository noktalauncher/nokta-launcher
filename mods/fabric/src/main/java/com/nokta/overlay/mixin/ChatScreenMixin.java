package com.nokta.overlay.mixin;

import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.client.gui.GuiGraphics;

@Mixin(ChatScreen.class)
public class ChatScreenMixin {

    @Shadow protected EditBox input;

    private EditBox searchBox = null;
    private String lastSearch = "";

    @Inject(method = "init", at = @At("TAIL"))
    private void onInit(CallbackInfo ci) {
        ChatScreen self = (ChatScreen)(Object)this;
        Minecraft mc = Minecraft.getInstance();

        // T harfi fix
        if (input != null) input.setValue("");

        // Arama kutusu — chat input'un hemen üstünde
        searchBox = new EditBox(mc.font,
            2, self.height - 44, self.width / 3, 16,
            net.minecraft.network.chat.Component.literal("Ara..."));
        searchBox.setMaxLength(256);
        searchBox.setHint(net.minecraft.network.chat.Component.literal("§7Chat ara..."));
        searchBox.setBordered(true);
        searchBox.setVisible(true);
        searchBox.setTextColor(0xFFFFFF);
        searchBox.setHintTextColor(0x888888);
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void onRender(GuiGraphics ctx, int mx, int my, float delta, CallbackInfo ci) {
        if (searchBox == null) return;
        ChatScreen self = (ChatScreen)(Object)this;
        Minecraft mc = Minecraft.getInstance();

        // Arama kutusu arka planı
        ctx.fill(0, self.height - 46, self.width / 3 + 4, self.height - 26,
            searchBox.isFocused() ? 0xcc1a1a3a : 0xaa000000);
        ctx.fill(0, self.height - 46, self.width / 3 + 4, self.height - 45, 0xff6c63ff);

        searchBox.render(ctx, mx, my, delta);

        // Arama sonuçlarını göster
        String query = searchBox.getValue().trim().toLowerCase();
        if (!query.isEmpty() && !query.equals(lastSearch)) {
            lastSearch = query;
        }
        if (!query.isEmpty()) {
            ctx.drawString(mc.font, "§7Aranan: §f" + query,
                4, self.height - 56, 0xFFFFFF, false);
        }
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = false)
    private void onMouseClick(double mx, double my, int btn,
                              CallbackInfoReturnable<Boolean> cir) {
        if (searchBox != null) searchBox.mouseClicked(mx, my, btn);
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = false)
    private void onKeyPress(int key, int scan, int mods,
                            CallbackInfoReturnable<Boolean> cir) {
        if (searchBox != null && searchBox.isFocused()) {
            searchBox.keyPressed(key, scan, mods);
        }
    }

    @Inject(method = "charTyped", at = @At("HEAD"), cancellable = false)
    private void onCharTyped(char c, int mods, CallbackInfoReturnable<Boolean> cir) {
        if (searchBox != null && searchBox.isFocused()) {
            searchBox.charTyped(c, mods);
        }
    }
}
