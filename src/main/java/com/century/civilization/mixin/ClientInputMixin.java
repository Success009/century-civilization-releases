package com.century.civilization.mixin;

import com.century.civilization.feature.NetherStarSacrificeCutscene;
import net.minecraft.client.player.ClientInput;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientInput.class)
public abstract class ClientInputMixin {
    @Shadow
    public Input keyPresses;

    @Shadow
    protected Vec2 moveVector;

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void onTickHead(CallbackInfo ci) {
        if (NetherStarSacrificeCutscene.isInputLocked()) {
            this.keyPresses = Input.EMPTY;
            this.moveVector = Vec2.ZERO;
            ci.cancel();
        }
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void onTickTail(CallbackInfo ci) {
        if (NetherStarSacrificeCutscene.isInputLocked()) {
            this.keyPresses = Input.EMPTY;
            this.moveVector = Vec2.ZERO;
        }
    }

    @Inject(method = "getMoveVector", at = @At("HEAD"), cancellable = true)
    private void onGetMoveVector(CallbackInfoReturnable<Vec2> cir) {
        if (NetherStarSacrificeCutscene.isInputLocked()) {
            cir.setReturnValue(Vec2.ZERO);
        }
    }
}
