package com.century.civilization.mixin;

import com.century.civilization.client.CenturyModClient;
import com.century.civilization.JanitorPreLaunch;
import com.century.civilization.BridgeManager;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(TitleScreen.class)
public class TitleScreenMixin extends Screen {
    private Button joinButton;
    private Button optionsButton;
    private Button configButton;
    private Button quitButton;
    private Button networkModeButton;
    private EditBox usernameField;
    private EditBox passwordField;
    private Button loginButton;
    private Button loginQuitButton;

    private Button logoutButton;

    private String loginStatus = "";

    private static final Identifier CENTURY_LOGO = Identifier.fromNamespaceAndPath("century", "textures/gui/logo.png");
    private static final Identifier GEAR_TEXTURE = Identifier.fromNamespaceAndPath("century", "textures/gui/gear.png");

    protected TitleScreenMixin(Component title) {
        super(title);
    }

    @Inject(at = @At("RETURN"), method = "init")
    private void onInit(CallbackInfo ci) {
        // Load session if not loaded yet
        CenturyModClient.loadSessionIfNeeded();

        for (Renderable drawable : ((ScreenAccessor)this).getDrawables()) {
            if (drawable instanceof AbstractWidget widget) {
                widget.visible = false;
                widget.active = false;
            }
        }

        int btnWidth = 228;
        int centerX = this.width / 2;
        int fieldX = centerX - btnWidth / 2;
        int centerY = this.height / 2;

                        // Initialize Lobby Widgets (State 0 - Logged In)
        boolean isClean = JanitorPreLaunch.isClean();
        boolean restartReq = com.century.civilization.client.CenturyConfigManager.isRestartRequired();
        
        Component buttonText = com.century.civilization.client.AutoUpdater.isUpdateCompleted() || restartReq ? 
            Component.literal("§c§lRESTART GAME REQUIRED") : 
            (isClean ? Component.literal("§e§lENTER SERVER") : Component.literal("§c§lREMOVE LISTED MODS"));

        this.joinButton = this.addRenderableWidget(Button.builder(buttonText, (b) -> {
            if (com.century.civilization.client.CenturyConfigManager.isRestartRequired()) {
                this.loginStatus = "§cRestart Minecraft to apply mod configuration changes!";
                return;
            }
            if (JanitorPreLaunch.isClean() && CenturyModClient.isLoggedIn()) {
                this.joinButton.active = false;
                this.loginStatus = "§eVerifying authorization...";
                Thread verifyThread = new Thread(() -> {
                    boolean valid = CenturyModClient.verifySession();
                    this.minecraft.execute(() -> {
                        this.joinButton.active = true;
                        this.loginStatus = "";
                        if (valid) {
                            CenturyModClient.joinServer(this);
                        } else {
                            this.updateWidgetVisibility();
                        }
                    });
                });
                verifyThread.setDaemon(true);
                verifyThread.start();
            }
        })
        .bounds(fieldX, centerY + 14, btnWidth, 20)
        .build());

        int halfWidth = (btnWidth - 4) / 2;

        this.optionsButton = this.addRenderableWidget(Button.builder(Component.literal("Options"), (b) -> {
            this.minecraft.setScreenAndShow(new OptionsScreen(this, this.minecraft.options, false));
        })
        .bounds(fieldX, centerY + 38, halfWidth, 20)
        .build());

        this.configButton = this.addRenderableWidget(Button.builder(Component.literal("⚙ Config"), (b) -> {
            this.minecraft.setScreenAndShow(new com.century.civilization.client.CenturyConfigScreen(this));
        })
        .bounds(fieldX + halfWidth + 4, centerY + 38, halfWidth, 20)
        .build());

        this.quitButton = this.addRenderableWidget(Button.builder(Component.literal("Quit Game"), (b) -> {
            this.minecraft.stop();
        })
        .bounds(fieldX, centerY + 62, btnWidth, 20)
        .build());
        // Initialize Login Widgets (State 1 - Not Logged In)
        this.usernameField = this.addRenderableWidget(new EditBox(this.font, fieldX, centerY - 2, btnWidth, 16, Component.literal("Username")));
        this.usernameField.setMaxLength(32);

        this.passwordField = this.addRenderableWidget(new EditBox(this.font, fieldX, centerY + 28, btnWidth, 16, Component.literal("Password")));
        this.passwordField.setMaxLength(32);

        this.loginButton = this.addRenderableWidget(Button.builder(Component.literal("§a§lLOG IN"), (b) -> {
            String username = this.usernameField.getValue().trim();
            String password = this.passwordField.getValue();

            if (username.isEmpty() || password.isEmpty()) {
                this.loginStatus = "§cUsername and password required";
                return;
            }

            this.loginButton.active = false;
            this.loginStatus = "§eAuthenticating...";
            Thread authThread = new Thread(() -> {
                String error = CenturyModClient.login(username, password);
                this.minecraft.execute(() -> {
                    if (error == null) {
                        this.loginStatus = "";
                        this.passwordField.setValue("");
                        this.updateWidgetVisibility();
                    } else {
                        this.loginStatus = "§c" + error;
                        this.loginButton.active = true;
                    }
                });
            });
            authThread.setDaemon(true);
            authThread.start();
        })
        .bounds(fieldX, centerY + 48, btnWidth, 16)
        .build());

        this.loginQuitButton = this.addRenderableWidget(Button.builder(Component.literal("Quit Game"), (b) -> {
            this.minecraft.stop();
        })
        .bounds(fieldX, centerY + 68, btnWidth, 16)
        .build());

                // Initialize Top-Right Logout Widget (State 2 - Logged In)
        int px = this.width - 130;
        this.logoutButton = this.addRenderableWidget(Button.builder(Component.literal("§cLog Out"), (b) -> {
            CenturyModClient.logout();
            this.loginStatus = "";
            this.updateWidgetVisibility();
        })
        .bounds(px, 56, 120, 16)
        .build());

        Component netText = CenturyModClient.isUseDirectLink() ?
            Component.literal("§7Route: §e§lPROXY") :
            Component.literal("§7Route: §a§lDIRECT");

        this.networkModeButton = this.addRenderableWidget(Button.builder(netText, (b) -> {
            boolean current = CenturyModClient.isUseDirectLink();
            CenturyModClient.setUseDirectLink(!current);
            b.setMessage(CenturyModClient.isUseDirectLink() ?
                Component.literal("§7Route: §e§lPROXY") :
                Component.literal("§7Route: §a§lDIRECT")
            );
        })
        .bounds(px, 74, 120, 16)
        .build());

        this.updateWidgetVisibility();
    }

    private void updateWidgetVisibility() {
        boolean loggedIn = CenturyModClient.isLoggedIn();

        // Lobby (State 0)
        if (this.joinButton != null) this.joinButton.visible = loggedIn;
        if (this.optionsButton != null) this.optionsButton.visible = loggedIn;
        if (this.configButton != null) this.configButton.visible = loggedIn;
        if (this.networkModeButton != null) this.networkModeButton.visible = loggedIn;
        if (this.quitButton != null) this.quitButton.visible = loggedIn;
        if (this.usernameField != null) this.usernameField.visible = !loggedIn;
        if (this.passwordField != null) this.passwordField.visible = !loggedIn;
        if (this.loginButton != null) this.loginButton.visible = !loggedIn;
        if (this.loginQuitButton != null) this.loginQuitButton.visible = !loggedIn;

        // Logout (State 2)
        if (this.logoutButton != null) this.logoutButton.visible = loggedIn;
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        if (!CenturyModClient.isLoggedIn()) {
            if (this.usernameField != null && this.usernameField.keyPressed(event)) {
                return true;
            }
            if (this.passwordField != null && this.passwordField.keyPressed(event)) {
                return true;
            }
            // Consumed to block other key bindings on TitleScreen when not logged in
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(net.minecraft.client.input.CharacterEvent event) {
        if (!CenturyModClient.isLoggedIn()) {
            if (this.usernameField != null && this.usernameField.charTyped(event)) {
                return true;
            }
            if (this.passwordField != null && this.passwordField.charTyped(event)) {
                return true;
            }
            // Consumed to block other character inputs on TitleScreen when not logged in
            return true;
        }
        return super.charTyped(event);
    }
    @Inject(at = @At("HEAD"), method = "extractRenderState")
    private void onDrawBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        // Draw the main solid onyx screen background
        context.fill(0, 0, this.width, this.height, 0xFF0E0E11);

        // Calculate card position (20% upscaled to 324x230, shifted up to centerY - 120)
        int cardWidth = 324;
        int cardHeight = 230;
        int centerX = this.width / 2;
        int centerY = this.height / 2;
        int cardX = centerX - cardWidth / 2;
        int cardY = centerY - 120;

        // Draw slightly transparent sleek slate card panel
        context.fill(cardX, cardY, cardX + cardWidth, cardY + cardHeight, 0xEE0B0B0F);

        // Draw soft outer glowing border (translucent gold)
        int glowColor = 0x33D4AF37;
        context.fill(cardX - 2, cardY - 2, cardX + cardWidth + 2, cardY - 1, glowColor); // Top
        context.fill(cardX - 2, cardY + cardHeight + 1, cardX + cardWidth + 2, cardY + cardHeight + 2, glowColor); // Bottom
        context.fill(cardX - 2, cardY - 1, cardX - 1, cardY + cardHeight + 1, glowColor); // Left
        context.fill(cardX + cardWidth + 1, cardY - 1, cardX + cardWidth + 2, cardY + cardHeight + 1, glowColor); // Right

        // Draw sharp inner border (solid gold)
        int borderColor = 0xFFD4AF37;
        context.fill(cardX - 1, cardY - 1, cardX + cardWidth + 1, cardY, borderColor);         // Top Edge
        context.fill(cardX - 1, cardY + cardHeight, cardX + cardWidth + 1, cardY + cardHeight + 1, borderColor); // Bottom Edge
        context.fill(cardX - 1, cardY, cardX, cardY + cardHeight, borderColor);                 // Left Edge
        context.fill(cardX + cardWidth, cardY, cardX + cardWidth + 1, cardY + cardHeight, borderColor); // Right Edge

        // Draw premium futuristic corner HUD brackets
        // Top-Left corner shape
        context.fill(cardX - 4, cardY - 4, cardX + 4, cardY - 3, borderColor);
        context.fill(cardX - 4, cardY - 3, cardX - 3, cardY + 4, borderColor);
        // Top-Right corner shape
        context.fill(cardX + cardWidth - 4, cardY - 4, cardX + cardWidth + 4, cardY - 3, borderColor);
        context.fill(cardX + cardWidth + 3, cardY - 3, cardX + cardWidth + 4, cardY + 4, borderColor);
        // Bottom-Left corner shape
        context.fill(cardX - 4, cardY + cardHeight + 3, cardX + 4, cardY + cardHeight + 4, borderColor);
        context.fill(cardX - 4, cardY + cardHeight - 4, cardX - 3, cardY + cardHeight + 3, borderColor);
        // Bottom-Right corner shape
        context.fill(cardX + cardWidth - 4, cardY + cardHeight + 3, cardX + cardWidth + 4, cardY + cardHeight + 4, borderColor);
        context.fill(cardX + cardWidth + 3, cardY + cardHeight - 4, cardX + cardWidth + 4, cardY + cardHeight + 3, borderColor);
    }

    @Inject(at = @At("TAIL"), method = "extractRenderState")
    private void onDrawOverlay(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        int centerX = this.width / 2;
        int centerY = this.height / 2;

        // Render Century logo scaled 20% larger (305x88)
        int logoWidth = 305;
        int logoHeight = 88;
        int logoX = centerX - logoWidth / 2;
        int logoY = centerY - 114;

        context.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, CENTURY_LOGO, logoX, logoY, 0.0f, 0.0f, logoWidth, logoHeight, 1024, 256, 1024, 256, 0xFFFFFFFF);

        // Draw separator line below the logo (subtle gold line)
        context.fill(centerX - 140, centerY - 18, centerX + 140, centerY - 17, 0x44D4AF37);

        boolean loggedIn = CenturyModClient.isLoggedIn();
        boolean clean = JanitorPreLaunch.isClean();

        if (!loggedIn) {
            // STATE_LOGIN (Not Logged In)
            context.centeredText(this.font, "§e§lACCOUNT LOGIN", centerX, centerY - 12, 0xFFFFFFFF);
            
            // Render text field labels inline
            context.text(this.font, "§7Username", centerX - 114, centerY - 11, 0xFFAAAAAA, false);
            context.text(this.font, "§7Password", centerX - 114, centerY + 19, 0xFFAAAAAA, false);

            if (!this.loginStatus.isEmpty()) {
                context.centeredText(this.font, this.loginStatus, centerX, centerY + 92, 0xFFFFFFFF);
            } else {
                context.centeredText(this.font, "§8Provide credentials whitelisted on the admin panel.", centerX, centerY + 92, 0xFFFFFFFF);
            }
        } else {
                        // STATE_LOBBY (Logged In)
            String status = BridgeManager.getStatus();
            String connectionDisplay;
                        if (!clean) {
                connectionDisplay = "§cDisabled (Locked)";
            } else if (CenturyModClient.isUseDirectLink()) {
                connectionDisplay = "§6Proxy";
            } else if (status != null && status.startsWith("FORWARDED:")) {
                String relay = status.substring("FORWARDED:".length());
                connectionDisplay = "§6Relay (" + relay + ")";
            } else {
                connectionDisplay = "§aDirect Connection";
            }

            context.centeredText(this.font, "§7Connection: " + connectionDisplay, centerX, centerY - 11, 0xFFFFFFFF);

                                                if (com.century.civilization.client.AutoUpdater.isUpdateCompleted()) {
                context.centeredText(this.font, "§aPlease restart the mod as a new update was available and update has been completed", centerX, centerY + 94, 0xFFFFFFFF);
            } else if (!clean) {
                String modsStr = String.join(", ", JanitorPreLaunch.getIllegalMods());
                if (modsStr.length() > 60) {
                    modsStr = modsStr.substring(0, 57) + "...";
                }
                context.centeredText(this.font, "§7Remove the following: §c" + modsStr, centerX, centerY + 94, 0xFFFFFFFF);
            } else {
                context.centeredText(this.font, "§8Device Whitelisted • Sec-ID: CC-483A (Secure Mode)", centerX, centerY + 94, 0xFFFFFFFF);
            }

            // Draw Premium Top-Right Profile Card
            int px = this.width - 130;
            int py = 10;
            int pw = 120;
            int ph = 42;

            // Translucent backing
            context.fill(px, py, px + pw, py + ph, 0xEE0B0B0F);

            // Glowing gold border
            context.fill(px - 1, py - 1, px + pw + 1, py, 0xFFD4AF37); // Top
            context.fill(px - 1, py + ph, px + pw + 1, py + ph + 1, 0xFFD4AF37); // Bottom
            context.fill(px - 1, py, px, py + ph, 0xFFD4AF37); // Left
            context.fill(px + pw, py, px + pw + 1, py + ph, 0xFFD4AF37); // Right

            // Truncate long username for top right render safety
            String dispName = CenturyModClient.getAuthenticatedUsername();
            if (dispName.length() > 10) dispName = dispName.substring(0, 8) + "..";

            context.text(this.font, "§e" + dispName, px + 6, py + 8, 0xFFFFFFFF, false);
            context.text(this.font, "§7Role: §b" + CenturyModClient.getUserRole().toUpperCase(), px + 6, py + 22, 0xFFFFFFFF, false);

            // Render Player Face Region (8x8) stretched to 24x24 dynamically
            Identifier skinId = CenturyModClient.getPlayerSkinIdentifier();
            if (skinId != null) {
                context.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, skinId, px + pw - 30, py + 8, 8.0f, 8.0f, 24, 24, 8, 8, 64, 64, 0xFFFFFFFF);
            }
        }

        // Keep active/visible states synchronized on every tick
        if (this.joinButton != null) {
            if (com.century.civilization.client.AutoUpdater.isUpdateCompleted()) {
                this.joinButton.active = false;
                this.joinButton.setMessage(Component.literal("§a§lRESTART GAME"));
            } else {
                this.joinButton.active = clean && BridgeManager.isReady() && loggedIn;
            }
        }

        // Draw gear icon next to Options button (only in Lobby state)
        if (loggedIn) {
            int optionsButtonY = centerY + 38;
            int textWidth = this.font.width("Options");
            int gearSize = 12;
            int gearX = centerX - (textWidth / 2) - gearSize - 4;
            int gearY = optionsButtonY + (20 - gearSize) / 2;
            context.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, GEAR_TEXTURE, gearX, gearY, 0.0f, 0.0f, gearSize, gearSize, gearSize, gearSize, 0xFFFFFFFF);
        }
    }
}
