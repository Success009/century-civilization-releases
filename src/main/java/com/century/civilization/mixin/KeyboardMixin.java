package com.century.civilization.mixin;

import com.century.civilization.client.CenturyModClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
public class KeyboardMixin {
    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    private void onKeyPress(long window, int action, net.minecraft.client.input.KeyEvent event, CallbackInfo ci) {
        if (com.century.civilization.feature.NetherStarSacrificeCutscene.isInputLocked()) {
            if (event.key() != org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
                ci.cancel();
                return;
            }
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.gui != null && mc.gui.screen() instanceof TitleScreen && !CenturyModClient.isLoggedIn()) {
            if (mc.gui.screen() != null) {
                mc.gui.screen().keyPressed(event);
            }
            ci.cancel(); // Globally swallow to prevent any other mods from intercepting it
        }
    }

    @Inject(method = "charTyped", at = @At("HEAD"), cancellable = true)
    private void onCharTyped(long window, net.minecraft.client.input.CharacterEvent event, CallbackInfo ci) {
        if (com.century.civilization.feature.NetherStarSacrificeCutscene.isInputLocked()) {
            ci.cancel();
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.gui != null && mc.gui.screen() instanceof TitleScreen && !CenturyModClient.isLoggedIn()) {
            if (mc.gui.screen() != null) {
                mc.gui.screen().charTyped(event);
            }
            ci.cancel(); // Globally swallow
        }
    }
}
