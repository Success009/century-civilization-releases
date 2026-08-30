package com.century.civilization.mixin;

import com.century.civilization.feature.NetherStarSacrificeCutscene;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {

    @Inject(method = "handleAccumulatedMovement", at = @At("HEAD"), cancellable = true)
    private void onHandleAccumulatedMovement(CallbackInfo ci) {
        if (NetherStarSacrificeCutscene.isInputLocked()) {
            ci.cancel();
        }
    }
}
