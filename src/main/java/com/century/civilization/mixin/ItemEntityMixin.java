package com.century.civilization.mixin;

import com.century.civilization.feature.NetherStarSacrificeCutscene;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemEntity.class)
public abstract class ItemEntityMixin extends Entity {
    @Shadow
    public abstract ItemStack getItem();

    protected ItemEntityMixin(EntityType<?> type, Level level) {
        super(type, level);
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void onTick(CallbackInfo ci) {
        if (this.level().isClientSide()) {
            ItemStack stack = this.getItem();
            if (stack != null && stack.is(Items.NETHER_STAR)) {
                BlockState state = this.level().getBlockState(this.blockPosition());
                boolean isBurning = this.isInLava() || this.isOnFire() || 
                    state.is(Blocks.LAVA) || state.is(Blocks.FIRE) || 
                    state.is(Blocks.SOUL_FIRE) || state.is(Blocks.CAMPFIRE) || 
                    state.is(Blocks.SOUL_CAMPFIRE) || state.is(Blocks.LAVA_CAULDRON);

                if (isBurning && NetherStarSacrificeCutscene.isWithinTossWindow()) {
                    NetherStarSacrificeCutscene.triggerCutscene();
                }
            }
        }
    }
}
