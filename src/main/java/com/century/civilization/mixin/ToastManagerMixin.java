package com.century.civilization.mixin;

import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.client.gui.components.toasts.SystemToast;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ToastManager.class)
public class ToastManagerMixin {
    @Inject(method = "addToast", at = @At("HEAD"), cancellable = true)
    private void onAddToast(Toast toast, CallbackInfo ci) {
        if (toast instanceof SystemToast systemToast) {
            if (systemToast.getToken() == SystemToast.SystemToastId.UNSECURE_SERVER_WARNING) {
                ci.cancel();
            }
        }
    }
}
