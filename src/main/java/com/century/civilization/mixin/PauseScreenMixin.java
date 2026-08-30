package com.century.civilization.mixin;

import com.century.civilization.BridgeManager;
import com.century.civilization.JanitorPreLaunch;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PauseScreen.class)
public class PauseScreenMixin extends Screen {
    private static final Identifier LOGO_TEXTURE = Identifier.fromNamespaceAndPath("century", "textures/gui/logo.png");

    protected PauseScreenMixin(Component title) {
        super(title);
    }

    @Override
    public Component getTitle() {
        return Component.literal("Century Civilization");
    }

    @Inject(at = @At("RETURN"), method = "init")
    private void onInitReturn(CallbackInfo ci) {
        if (JanitorPreLaunch.isDisabled()) {
            return;
        }
        this.addRenderableOnly((context, mouseX, mouseY, delta) -> {
            int centerX = this.width / 2;

            // Display sleek Century Civilization subtitle
            context.centeredText(this.font, "§e§lCentury Civilization", centerX, this.height / 4 - 6, 0xFFFFFFFF);

                        // Fetch and draw clean player-focused connection status at the bottom of the screen
            String status = BridgeManager.getStatus();
            boolean clean = JanitorPreLaunch.isClean();

            String connectionDisplay;
                        if (!clean) {
                connectionDisplay = "§cDisabled (Locked)";
            } else if (com.century.civilization.client.CenturyModClient.isUseDirectLink()) {
                connectionDisplay = "§6Proxy";
            } else if (status != null && status.startsWith("FORWARDED:")) {
                String relay = status.substring("FORWARDED:".length());
                connectionDisplay = "§6Relay (" + relay + ")";
            } else {
                connectionDisplay = "§aDirect Connection";
            }

            context.centeredText(this.font, "§7Connection: " + connectionDisplay, centerX, this.height - 18, 0xFFFFFFFF);
            // Sized and scaled properly for the 4:1 rectangular aspect ratio
            int logoWidth = 48;
            int logoHeight = 12;
            int buttonsCenterY = this.height / 4 + 24 + 58;
            int logoY = buttonsCenterY - (logoHeight / 2);

            int gap = 15; 
            int leftX = centerX - 100 - gap - logoWidth;
            int rightX = centerX + 100 + gap;

            if (leftX > 10) {
                context.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, LOGO_TEXTURE, leftX, logoY, 0.0f, 0.0f, logoWidth, logoHeight, 1024, 256, 1024, 256, 0xFFFFFFFF);
                context.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, LOGO_TEXTURE, rightX, logoY, 0.0f, 0.0f, logoWidth, logoHeight, 1024, 256, 1024, 256, 0xFFFFFFFF);
            }
        });
    }
}
