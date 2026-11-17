package com.century.civilization.mixin;

import com.century.civilization.client.skin.SkinOverride;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.player.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** World model, first-person arm, map hand and spectated players: swaps in the Century skin. Never throws. */
@Mixin(AbstractClientPlayer.class)
public abstract class AbstractClientPlayerSkinMixin {
    @Inject(method = "getSkin()Lnet/minecraft/world/entity/player/PlayerSkin;", at = @At("RETURN"), cancellable = true)
    private void century$overrideSkin(CallbackInfoReturnable<PlayerSkin> cir) {
        try {
            AbstractClientPlayer self = (AbstractClientPlayer) (Object) this;
            String name = self.getGameProfile().name();
            PlayerSkin out = SkinOverride.apply(cir.getReturnValue(), name);
            if (out != cir.getReturnValue()) cir.setReturnValue(out);
        } catch (Throwable ignored) {
        }
    }
}
