package com.century.civilization.mixin;

import net.minecraft.client.gui.components.SplashRenderer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Font;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SplashRenderer.class)
public class SplashRendererMixin {
    @Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;ILnet/minecraft/client/gui/Font;F)V", at = @At("HEAD"), cancellable = true)
    private void onRender(GuiGraphicsExtractor context, int screenWidth, Font font, float alpha, CallbackInfo ci) {
        ci.cancel();
    }
}
