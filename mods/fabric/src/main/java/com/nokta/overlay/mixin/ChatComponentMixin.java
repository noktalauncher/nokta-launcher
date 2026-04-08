package com.nokta.overlay.mixin;

import com.nokta.overlay.ScreenshotNotifier;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ChatComponent.class)
public class ChatComponentMixin {

    @ModifyVariable(method = "addMessage(Lnet/minecraft/network/chat/Component;)V",
                    at = @At("HEAD"), argsOnly = true)
    private Component onAddMessage(Component msg) {
        String txt = msg.getString();
        if (txt.contains("olarak kaydedildi") || txt.contains("screenshot was saved")) {
            ScreenshotNotifier.LastPath lp = ScreenshotNotifier.getLastPath();
            String ssPath = lp != null ? lp.path : null;
            if (ssPath != null && !ssPath.isEmpty()) {
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
        return msg;
    }
}
