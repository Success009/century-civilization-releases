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

    private static int selectedCategoryIndex = 0; // 0: Recommended, 1: Additional, 2: Technical
    private static int currentPage = 0;

    public CenturyConfigScreen(Screen parent) {
        super(Component.literal("Century Config"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        CenturyConfigManager.initIfNeeded();

        int centerX = this.width / 2;
        int cardWidth = 400;
        int cardHeight = 194;
        int cardX = centerX - cardWidth / 2;
        int cardY = 22;

        List<String> categories = CenturyConfigManager.getCategories();
        if (selectedCategoryIndex >= categories.size()) {
            selectedCategoryIndex = 0;
        }

        // --- Category Tab Buttons ---
        int tabW = 124;
        int tabH = 18;
        int tabY = cardY + 6;

        for (int i = 0; i < categories.size(); i++) {
            final int catIdx = i;
            String catName = categories.get(i);
            int count = CenturyConfigManager.getModsByCategory(catName).size();
            boolean isSelected = (i == selectedCategoryIndex);

            String label = isSelected 
                    ? "§6§l[ " + catName + " (" + count + ") ]" 
                    : "§7" + catName + " (" + count + ")";

            int btnX = cardX + 10 + (i * 128);
            this.addRenderableWidget(Button.builder(Component.literal(label), (b) -> {
                selectedCategoryIndex = catIdx;
                currentPage = 0;
                this.clearWidgets();
                this.init();
            })
            .bounds(btnX, tabY, tabW, tabH)
            .build());
        }

        // --- Active Category Mods ---
        String activeCat = categories.get(selectedCategoryIndex);
        List<CenturyConfigManager.ModEntry> currentMods = CenturyConfigManager.getModsByCategory(activeCat);

        int totalPages = Math.max(1, (int) Math.ceil((double) currentMods.size() / 12.0));
        if (currentPage >= totalPages) {
            currentPage = 0;
        }

        int startIndex = currentPage * 12;
        int endIndex = Math.min(currentMods.size(), startIndex + 12);

        for (int i = startIndex; i < endIndex; i++) {
            CenturyConfigManager.ModEntry entry = currentMods.get(i);
            int rel = i - startIndex;
            int col = rel < 6 ? 0 : 1;
            int row = rel % 6;

            int itemY = cardY + 32 + (row * 22);
            int btnX = col == 0 ? (cardX + 155) : (cardX + 348);

            final CenturyConfigManager.ModEntry currentEntry = entry;
            Button toggleBtn = Button.builder(
                Component.literal(entry.enabled ? "§aON" : "§cOFF"),
                (b) -> {
                    currentEntry.enabled = !currentEntry.enabled;
                    b.setMessage(Component.literal(currentEntry.enabled ? "§aON" : "§cOFF"));
                }
            )
            .bounds(btnX, itemY + 2, 40, 16)
            .build();

            this.addRenderableWidget(toggleBtn);
        }

        // --- Pagination Controls (Overflow Prevention) ---
        if (totalPages > 1) {
            int pageControlsY = cardY + cardHeight - 20;

            if (currentPage > 0) {
                this.addRenderableWidget(Button.builder(Component.literal("§e< Prev"), (b) -> {
                    currentPage--;
                    this.clearWidgets();
                    this.init();
                })
                .bounds(centerX - 75, pageControlsY, 45, 15)
                .build());
            }

            if (currentPage < totalPages - 1) {
                this.addRenderableWidget(Button.builder(Component.literal("§eNext >"), (b) -> {
                    currentPage++;
                    this.clearWidgets();
                    this.init();
                })
                .bounds(centerX + 30, pageControlsY, 45, 15)
                .build());
            }
        }

        // --- Apply Button ---
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

        // --- Back Button ---
        this.addRenderableWidget(Button.builder(Component.literal("Back"), (b) -> {
            if (this.minecraft != null) {
                this.minecraft.setScreenAndShow(this.parent);
            }
        })
        .bounds(centerX + 5, this.height - 28, 100, 20)
        .build());
    }

    private String truncate(String text, int maxWidth) {
        if (this.font.width(text) <= maxWidth) return text;
        String ell = "...";
        int ellW = this.font.width(ell);
        StringBuilder sb = new StringBuilder();
        for (char c : text.toCharArray()) {
            if (this.font.width(sb.toString() + c) + ellW > maxWidth) break;
            sb.append(c);
        }
        return sb.toString() + ell;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        // Dark solid onyx background
        context.fill(0, 0, this.width, this.height, 0xFF0E0E11);

        int centerX = this.width / 2;

        // Title
        context.centeredText(this.font, "§e§lCentury Config", centerX, 8, 0xFFFFFFFF);

        // Card background
        int cardWidth = 400;
        int cardHeight = 194;
        int cardX = centerX - cardWidth / 2;
        int cardY = 22;

        context.fill(cardX, cardY, cardX + cardWidth, cardY + cardHeight, 0xEE0B0B0F);

        // Gold border
        int borderColor = 0xFFD4AF37;
        context.fill(cardX - 1, cardY - 1, cardX + cardWidth + 1, cardY, borderColor);
        context.fill(cardX - 1, cardY + cardHeight, cardX + cardWidth + 1, cardY + cardHeight + 1, borderColor);
        context.fill(cardX - 1, cardY, cardX, cardY + cardHeight, borderColor);
        context.fill(cardX + cardWidth, cardY, cardX + cardWidth + 1, cardY + cardHeight, borderColor);

        // Tab separator divider
        context.fill(cardX + 8, cardY + 28, cardX + cardWidth - 8, cardY + 29, 0x44D4AF37);

        // Render mod titles and descriptions for active category page
        List<String> categories = CenturyConfigManager.getCategories();
        if (selectedCategoryIndex < categories.size()) {
            String activeCat = categories.get(selectedCategoryIndex);
            List<CenturyConfigManager.ModEntry> currentMods = CenturyConfigManager.getModsByCategory(activeCat);

            int totalPages = Math.max(1, (int) Math.ceil((double) currentMods.size() / 12.0));
            int startIndex = currentPage * 12;
            int endIndex = Math.min(currentMods.size(), startIndex + 12);

            for (int i = startIndex; i < endIndex; i++) {
                CenturyConfigManager.ModEntry entry = currentMods.get(i);
                int rel = i - startIndex;
                int col = rel < 6 ? 0 : 1;
                int row = rel % 6;

                int itemY = cardY + 32 + (row * 22);
                int textX = col == 0 ? (cardX + 14) : (cardX + 210);
                int maxTextWidth = col == 0 ? 135 : 132;

                String titleText = truncate("§f" + (i + 1) + ". " + entry.name, maxTextWidth);
                context.text(this.font, titleText, textX, itemY + 2, 0xFFFFFFFF);

                String descText = truncate("§8" + entry.description, maxTextWidth);
                context.text(this.font, descText, textX, itemY + 11, 0xFFAAAAAA);
            }

            if (totalPages > 1) {
                int pageControlsY = cardY + cardHeight - 16;
                String pageText = "§7Page " + (currentPage + 1) + " / " + totalPages;
                context.centeredText(this.font, pageText, centerX - 10, pageControlsY, 0xFFFFFFFF);
            }
        }

        // Render status / download progress bar
        if (!this.statusMessage.isEmpty()) {
            context.centeredText(this.font, this.statusMessage, centerX, this.height - 46, 0xFFFFFFFF);
        }

        if (CenturyConfigManager.isDownloading()) {
            int pbWidth = 180;
            int pbHeight = 6;
            int pbX = centerX - pbWidth / 2;
            int pbY = this.height - 56;

            context.fill(pbX, pbY, pbX + pbWidth, pbY + pbHeight, 0xFF222222);
            float progress = CenturyConfigManager.getDownloadProgress();
            int fillW = (int)(pbWidth * progress);
            context.fill(pbX, pbY, pbX + fillW, pbY + pbHeight, 0xFFD4AF37);
        }

        super.extractRenderState(context, mouseX, mouseY, delta);
    }
}
