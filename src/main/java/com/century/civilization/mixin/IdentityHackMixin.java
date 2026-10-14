package com.century.civilization.mixin;

import com.century.civilization.client.CenturyModClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public abstract class IdentityHackMixin {
    @Shadow @Final @Mutable private User user;
    @Shadow public abstract User getUser();
    @Shadow public java.io.File gameDirectory;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void onInit(CallbackInfo ci) {
        System.out.println("[CenturyMod] Forcing Cracked Identity Mode...");
        CenturyModClient.loadSessionWithDirectory(this.gameDirectory);
        if (CenturyModClient.isLoggedIn()) {
            User authUser = CenturyModClient.getMinecraftUser();
            if (authUser != null) {
                this.user = authUser;
            }
        }
    }

    @Inject(method = "getUser", at = @At("HEAD"), cancellable = true)
    private void onGetUser(CallbackInfoReturnable<User> cir) {
        if (CenturyModClient.isLoggedIn()) {
            User authUser = CenturyModClient.getMinecraftUser();
            if (authUser != null) {
                this.user = authUser; // Keep the private field synchronized for direct field accesses
                cir.setReturnValue(authUser);
            }
        }
    }
}
