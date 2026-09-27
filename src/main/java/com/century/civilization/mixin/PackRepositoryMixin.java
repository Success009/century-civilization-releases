package com.century.civilization.mixin;

import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.PackSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import com.google.common.collect.ImmutableMap;

@Mixin(PackRepository.class)
public class PackRepositoryMixin {
    @Shadow private java.util.Map<String, Pack> available;

    private static boolean isAllowedPack(String id, Pack pack) {
        if (id == null) return false;

        // If it is not a user-supplied external pack from the resourcepacks directory (i.e. not DEFAULT), allow it
        if (pack != null && pack.getPackSource() != PackSource.DEFAULT) {
            return true;
        }

        // Always allow default vanilla assets and built-in accessibility packs
        if (id.equals("vanilla") || id.equals("programmer_art") || id.equals("high_contrast")) {
            return true;
        }

        // Built-in Century mod resource pack identifiers
        if (id.equals("century:recovered") || id.equals("century:re-covered")) {
            return true;
        }

        // Allowed external custom resource pack: Re-covered
        String lowerId = id.toLowerCase(Locale.ROOT);
        if (lowerId.contains("re-covered") || lowerId.contains("recovered")) {
            return true;
        }

        if (pack != null && pack.getTitle() != null) {
            String lowerTitle = pack.getTitle().getString().toLowerCase(Locale.ROOT);
            if (lowerTitle.contains("re-covered") || lowerTitle.contains("recovered")) {
                return true;
            }
        }

        return false;
    }

    @Inject(method = "discoverAvailable", at = @At("RETURN"), cancellable = true)
    private void onDiscoverAvailable(CallbackInfoReturnable<Map<String, Pack>> cir) {
        if (com.century.civilization.JanitorPreLaunch.isDisabled()) {
            return;
        }

        Map<String, Pack> original = cir.getReturnValue();
        if (original == null || original.isEmpty()) {
            return;
        }

        Map<String, Pack> filtered = new TreeMap<>();
        for (Map.Entry<String, Pack> entry : original.entrySet()) {
            if (isAllowedPack(entry.getKey(), entry.getValue())) {
                filtered.put(entry.getKey(), entry.getValue());
            }
        }
        cir.setReturnValue(ImmutableMap.copyOf(filtered));
    }

    @ModifyVariable(method = "setSelected", at = @At("HEAD"), argsOnly = true)
    private Collection<String> onSetSelected(Collection<String> selected) {
        if (com.century.civilization.JanitorPreLaunch.isDisabled()) {
            return selected;
        }

        List<String> filtered = new ArrayList<>();
        for (String id : selected) {
            Pack pack = (this.available != null) ? this.available.get(id) : null;
            if (isAllowedPack(id, pack)) {
                filtered.add(id);
            }
        }
        return filtered;
    }
}
