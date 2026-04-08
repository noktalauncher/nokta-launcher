package com.nokta.overlay;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public class SpotifyButtonInjector {

    private static final ResourceLocation SPOTIFY =
        ResourceLocation.fromNamespaceAndPath("nokta_overlay", "textures/spotify.png");

    public static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (!(screen instanceof TitleScreen) && !(screen instanceof PauseScreen)) return;

            AbstractWidget btn = new AbstractWidget(
                w - 27, 7, 20, 20, Component.literal("Spotify")) {

                @Override
                public void renderWidget(GuiGraphics ctx, int mx, int my, float delta) {
                    if (isHovered()) {
                        ctx.fill(getX()-2, getY()-2, getX()+getWidth()+2, getY()+getHeight()+2,
                            0x331db954);
                    }
                    ctx.blit(
                        net.minecraft.client.renderer.RenderType::guiTextured,
                        SPOTIFY,
                        getX(), getY(), 0, 0,
                        getWidth(), getHeight(),
                        getWidth(), getHeight()
                    );
                }

                @Override
                public void onClick(double x, double y) {
                    client.setScreen(new SpotifyScreen(screen));
                }

                @Override
                protected void updateWidgetNarration(NarrationElementOutput n) {
                    defaultButtonNarrationText(n);
                }
            };

            btn.setTooltip(Tooltip.create(Component.literal("Spotify")));
            Screens.getButtons(screen).add(btn);
        });
    }
}
