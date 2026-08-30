package com.century.civilization.mixin;

import net.minecraft.client.gui.components.LogoRenderer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LogoRenderer.class)
public class LogoRendererMixin {
    @Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IF)V", at = @At("HEAD"), cancellable = true)
    private void onRender1(GuiGraphicsExtractor context, int screenWidth, float alpha, CallbackInfo ci) {
        ci.cancel();
    }

    @Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IFI)V", at = @At("HEAD"), cancellable = true)
    private void onRender2(GuiGraphicsExtractor context, int screenWidth, float alpha, int heightOffset, CallbackInfo ci) {
        ci.cancel();
    }
}
