package com.century.civilization.client.skin;

import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;

/** Swaps in a skin uploaded to the Century web server. Fail-soft: returns the vanilla skin whenever anything is missing. */
public final class SkinOverride {
    private SkinOverride() {}

    public static PlayerSkin apply(PlayerSkin vanilla, String name) {
        try {
            if (vanilla == null || name == null || name.isEmpty()) return vanilla;
            SkinTextures.requestLazy(name);
            Identifier tex = SkinTextures.texture(name);
            if (tex == null) return vanilla;
            if (vanilla.body() != null && tex.equals(vanilla.body().texturePath())) return vanilla;
            PlayerModelType model = SkinTextures.isSlim(name) ? PlayerModelType.SLIM : PlayerModelType.WIDE;
            return new PlayerSkin(new ClientAsset.ResourceTexture(tex, tex), vanilla.cape(), vanilla.elytra(), model, vanilla.secure());
        } catch (Throwable t) {
            return vanilla;
        }
    }
}
