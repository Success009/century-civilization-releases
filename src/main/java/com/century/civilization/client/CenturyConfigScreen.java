package com.century.civilization.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

public class CenturyConfigScreen extends Screen {
    private final Screen parent;
    private String statusMessage = "";
    private boolean isApplying = false;

    public CenturyConfigScreen(Screen parent) {
        super(Component.literal("Century Config"));
        this.parent = parent;
    }

    private int getYForIndex(int i) {
        if (i < 2) {
            return 52 + (i * 18);
        } else {
            return 106 + ((i - 2) * 18);
        }
    }

    @Override
    protected void init() {
        CenturyConfigManager.initIfNeeded();

        int centerX = this.width / 2;
        List<CenturyConfigManager.ModEntry> entries = CenturyConfigManager.getModEntries();

        for (int i = 0; i < entries.size(); i++) {
            CenturyConfigManager.ModEntry entry = entries.get(i);
            int y = getYForIndex(i);

            final CenturyConfigManager.ModEntry currentEntry = entry;
            Button toggleBtn = Button.builder(
                Component.literal(entry.enabled ? "§aON" : "§cOFF"),
                (b) -> {
                    currentEntry.enabled = !currentEntry.enabled;
                    b.setMessage(Component.literal(currentEntry.enabled ? "§aON" : "§cOFF"));
                }
            )
            .bounds(centerX + 65, y, 45, 16)
            .build();

            this.addRenderableWidget(toggleBtn);
        }

        // Apply button
        this.addRenderableWidget(Button.builder(Component.literal("§aApply"), (b) -> {
            this.isApplying = true;
            this.statusMessage = "§eApplying changes...";
            
            CenturyConfigManager.applyAndSyncAsync(new CenturyConfigManager.DownloadCallback() {
                @Override
                public void onProgress(String modName, float progress) {
                    if (minecraft != null) {
                        minecraft.execute(() -> {
                            statusMessage = "§eDownloading " + modName + "... (" + (int)(progress * 100) + "%)";
                        });
                    }
                }

                @Override
                public void onComplete() {
                    if (minecraft != null) {
                        minecraft.execute(() -> {
                            isApplying = false;
                            if (CenturyConfigManager.isRestartRequired()) {
                                statusMessage = "§aChanges applied. Restart game to take effect.";
                            } else {
                                statusMessage = "§aNo changes needed.";
                            }
                        });
                    }
                }

                @Override
                public void onError(String error) {
                    if (minecraft != null) {
                        minecraft.execute(() -> {
                            isApplying = false;
                            statusMessage = "§cError: " + error;
                        });
                    }
                }
            });
        })
        .bounds(centerX - 105, this.height - 28, 100, 20)
        .build());

        // Back button
        this.addRenderableWidget(Button.builder(Component.literal("Back"), (b) -> {
            if (this.minecraft != null) {
                this.minecraft.setScreenAndShow(this.parent);
            }
        })
        .bounds(centerX + 5, this.height - 28, 100, 20)
        .build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        // Dark solid onyx background
        context.fill(0, 0, this.width, this.height, 0xFF0E0E11);

        int centerX = this.width / 2;

        // Title
        context.centeredText(this.font, "§e§lCentury Config", centerX, 12, 0xFFFFFFFF);

        // Card background
        int cardWidth = 320;
        int cardHeight = 232;
        int cardX = centerX - cardWidth / 2;
        int cardY = 28;

        context.fill(cardX, cardY, cardX + cardWidth, cardY + cardHeight, 0xEE0B0B0F);
        
        // Gold border
        int borderColor = 0xFFD4AF37;
        context.fill(cardX - 1, cardY - 1, cardX + cardWidth + 1, cardY, borderColor);
        context.fill(cardX - 1, cardY + cardHeight, cardX + cardWidth + 1, cardY + cardHeight + 1, borderColor);
        context.fill(cardX - 1, cardY, cardX, cardY + cardHeight, borderColor);
        context.fill(cardX + cardWidth, cardY, cardX + cardWidth + 1, cardY + cardHeight, borderColor);

        // Category headers
        context.text(this.font, "§e§l[ Recommended / Native ]", cardX + 10, 36, 0xFFFFFFFF);
        context.text(this.font, "§e§l[ Additional ]", cardX + 10, 90, 0xFFFFFFFF);

        // Render mod labels
        List<CenturyConfigManager.ModEntry> entries = CenturyConfigManager.getModEntries();

        for (int i = 0; i < entries.size(); i++) {
            CenturyConfigManager.ModEntry entry = entries.get(i);
            int y = getYForIndex(i);

            String titleText = "§f" + (i + 1) + ". " + entry.name;
            context.text(this.font, titleText, cardX + 14, y + 3, 0xFFFFFFFF);
        }

        // Render status / download progress bar
        if (!this.statusMessage.isEmpty()) {
            context.centeredText(this.font, this.statusMessage, centerX, this.height - 42, 0xFFFFFFFF);
        }

        if (CenturyConfigManager.isDownloading()) {
            int pbWidth = 180;
            int pbHeight = 6;
            int pbX = centerX - pbWidth / 2;
            int pbY = this.height - 52;

            context.fill(pbX, pbY, pbX + pbWidth, pbY + pbHeight, 0xFF222222);
            float progress = CenturyConfigManager.getDownloadProgress();
            int fillW = (int)(pbWidth * progress);
            context.fill(pbX, pbY, pbX + fillW, pbY + pbHeight, 0xFFD4AF37);
        }

        super.extractRenderState(context, mouseX, mouseY, delta);
    }
}
