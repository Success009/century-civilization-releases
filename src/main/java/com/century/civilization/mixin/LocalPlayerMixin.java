package com.century.civilization.mixin;

import com.century.civilization.feature.NetherStarSacrificeCutscene;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMixin extends AbstractClientPlayer {
    public LocalPlayerMixin(net.minecraft.client.multiplayer.ClientLevel level, com.mojang.authlib.GameProfile profile) {
        super(level, profile);
    }

    @Inject(method = "drop", at = @At("HEAD"))
    private void onDrop(boolean fullStack, CallbackInfoReturnable<Boolean> cir) {
        ItemStack held = this.getMainHandItem();
        ItemStack offhand = this.getOffhandItem();
        if ((held != null && held.is(Items.NETHER_STAR)) || (offhand != null && offhand.is(Items.NETHER_STAR))) {
            NetherStarSacrificeCutscene.recordToss();
        }
    }

    @Inject(method = "applyInput", at = @At("HEAD"), cancellable = true)
    private void onApplyInput(CallbackInfo ci) {
        if (NetherStarSacrificeCutscene.isInputLocked()) {
            this.xxa = 0.0f;
            this.zza = 0.0f;
            this.jumping = false;
            this.setSprinting(false);
            ci.cancel();
        }
    }

    @Inject(method = "aiStep", at = @At("HEAD"))
    private void onAiStepHead(CallbackInfo ci) {
        if (NetherStarSacrificeCutscene.isInputLocked()) {
            this.xxa = 0.0f;
            this.zza = 0.0f;
            this.jumping = false;
            this.setSprinting(false);
            Vec3 cur = this.getDeltaMovement();
            this.setDeltaMovement(0.0, cur.y > 0 ? 0.0 : cur.y, 0.0);
        }
    }

    @Inject(method = "aiStep", at = @At("TAIL"))
    private void onAiStepTail(CallbackInfo ci) {
        if (NetherStarSacrificeCutscene.isInputLocked()) {
            this.xxa = 0.0f;
            this.zza = 0.0f;
            this.jumping = false;
            this.setSprinting(false);
            Vec3 cur = this.getDeltaMovement();
            this.setDeltaMovement(0.0, cur.y > 0 ? 0.0 : cur.y, 0.0);
        }
    }
}
