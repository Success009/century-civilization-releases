package com.century.civilization.mixin;

import com.century.civilization.feature.NetherStarSacrificeCutscene;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MultiPlayerGameMode.class)
public class MultiPlayerGameModeMixin {
    @Inject(method = "dropItem", at = @At("HEAD"))
    private void onDropItem(LocalPlayer player, boolean entireStack, CallbackInfo ci) {
        if (player != null) {
            ItemStack held = player.getMainHandItem();
            ItemStack offhand = player.getOffhandItem();
            if ((held != null && held.is(Items.NETHER_STAR)) || (offhand != null && offhand.is(Items.NETHER_STAR))) {
                NetherStarSacrificeCutscene.recordToss();
            }
        }
    }
}
