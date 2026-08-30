package com.century.civilization.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "de.maxhenkel.voicechat.voice.client.InitializationData", remap = false)
public class InitializationDataMixin {
    @Inject(method = "getServerIP", at = @At("HEAD"), cancellable = true)
    private void onGetServerIP(CallbackInfoReturnable<String> cir) {
        if (com.century.civilization.JanitorPreLaunch.isDisabled()) {
            return;
        }
        cir.setReturnValue("127.0.0.1");
    }
}
