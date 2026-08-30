package com.century.civilization.mixin;

import com.century.civilization.duck.RenderCrownDuck;
import net.minecraft.client.renderer.entity.state.PigRenderState;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(PigRenderState.class)
public class PigRenderStateMixin implements RenderCrownDuck {
    private boolean shouldRenderCrown;

    @Override
    public boolean technomodel$shouldRenderCrown() {
        return this.shouldRenderCrown;
    }

    @Override
    public void technomodel$setRenderCrown(boolean value) {
        this.shouldRenderCrown = value;
    }
}
