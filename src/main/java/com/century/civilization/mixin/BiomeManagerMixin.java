package com.century.civilization.mixin;

import com.century.civilization.duck.BiomeManagerDuck;
import net.minecraft.world.level.biome.BiomeManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mutable;

@Mixin(BiomeManager.class)
public abstract class BiomeManagerMixin implements BiomeManagerDuck {
    @Shadow @Final @Mutable private long biomeZoomSeed;

    @Override
    public void century$setBiomeZoomSeed(long seed) {
        this.biomeZoomSeed = seed;
    }
}
