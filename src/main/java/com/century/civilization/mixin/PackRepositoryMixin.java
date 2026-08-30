package com.century.civilization.mixin;

import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.PackSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

@Mixin(PackRepository.class)
public class PackRepositoryMixin {
    @Shadow private java.util.Map<String, Pack> available;

    @ModifyVariable(method = "setSelected", at = @At("HEAD"), argsOnly = true)
    private Collection<String> onSetSelected(Collection<String> selected) {
        if (com.century.civilization.JanitorPreLaunch.isDisabled()) {
            return selected;
        }

        List<String> filtered = new ArrayList<>();
        for (String id : selected) {
            // Always allow default vanilla assets and built-in accessibility packs
            if (id.equals("vanilla") || id.equals("programmer_art") || id.equals("high_contrast")) {
                filtered.add(id);
                continue;
            }

            Pack pack = this.available.get(id);
            if (pack != null) {
                // If it is an external custom resource pack from the user's directory, its source is DEFAULT
                if (pack.getPackSource() == PackSource.DEFAULT) {
                    continue; // Block/ignore this custom pack
                }
            }
            filtered.add(id);
        }
        return filtered;
    }
}
