package com.century.civilization.mixin;

import com.century.civilization.feature.NetherStarSacrificeCutscene;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientLevel.class)
public abstract class ClientLevelMixin {
    @Shadow
    public abstract Entity getEntity(int id);

    @Inject(method = "addEntity", at = @At("HEAD"))
    private void onAddEntity(Entity entity, CallbackInfo ci) {
        if (entity instanceof ItemEntity itemEntity) {
            ItemStack stack = itemEntity.getItem();
            if (stack != null && stack.is(Items.NETHER_STAR)) {
                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null && mc.player.distanceToSqr(itemEntity) < 36.0) {
                    NetherStarSacrificeCutscene.recordToss();
                }
            }
        }
    }

    @Inject(method = "removeEntity", at = @At("HEAD"))
    private void onRemoveEntity(int id, Entity.RemovalReason reason, CallbackInfo ci) {
        Entity entity = this.getEntity(id);
        if (entity instanceof ItemEntity itemEntity) {
            ItemStack stack = itemEntity.getItem();
            if (stack != null && stack.is(Items.NETHER_STAR)) {
                BlockState state = entity.level().getBlockState(entity.blockPosition());
                boolean isBurning = entity.isInLava() || entity.isOnFire() || 
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
