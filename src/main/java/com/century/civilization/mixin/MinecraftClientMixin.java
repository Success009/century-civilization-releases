package com.century.civilization.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import com.mojang.realmsclient.gui.screens.RealmsNotificationsScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Minecraft.class)
public abstract class MinecraftClientMixin {

    @ModifyVariable(method = "setScreenAndShow", at = @At("HEAD"), argsOnly = true)
    private Screen onModifyScreen(Screen screen) {
        if (screen != null) {
            String className = screen.getClass().getName();
            if (screen instanceof JoinMultiplayerScreen || 
                screen instanceof DisconnectedScreen || 
                className.contains("Realms") || 
                className.startsWith("com.mojang.realmsclient")) {
                return new TitleScreen();
            }
        }
        return screen;
    }

    @ModifyVariable(method = "disconnect(Lnet/minecraft/client/gui/screens/Screen;Z)V", at = @At("HEAD"), argsOnly = true)
    private Screen onModifyDisconnectScreen1(Screen screen) {
        com.century.civilization.client.VoxyCloudSyncManager.onSessionEndAsync();
        return new TitleScreen();
    }

    @ModifyVariable(method = "disconnect(Lnet/minecraft/client/gui/screens/Screen;ZZ)V", at = @At("HEAD"), argsOnly = true)
    private Screen onModifyDisconnectScreen2(Screen screen) {
        com.century.civilization.client.VoxyCloudSyncManager.onSessionEndAsync();
        return new TitleScreen();
    }

    @ModifyVariable(method = "clearClientLevel", at = @At("HEAD"), argsOnly = true)
    private Screen onModifyClearClientLevelScreen(Screen screen) {
        com.century.civilization.client.VoxyCloudSyncManager.onSessionEndAsync();
        return new TitleScreen();
    }
}
