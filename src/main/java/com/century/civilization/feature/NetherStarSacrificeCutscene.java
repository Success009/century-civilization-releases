package com.century.civilization.feature;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NetherStarSacrificeCutscene {
    private static final Logger LOGGER = LoggerFactory.getLogger("century-cutscene");

    // Timing constants
    private static final long TOSS_WINDOW_MS = 10_000L;
    private static final long DELAY_BEFORE_CINEMATIC_MS = 2_000L;      // 2.0s countdown
    private static final long LETTERBOX_SLIDE_DURATION_MS = 600L;       // 0.6s slide in
    private static final long CHAR_TYPING_INTERVAL_MS = 45L;            // ~0.045s per char for Line 1
    private static final long DIGIT_INTERVAL_MS = 1_000L;               // 1.0s per digit for Line 2
    private static final long DIGIT_PAUSE_BEFORE_START_MS = 600L;       // 0.6s pause before digits start
    private static final long HOLD_DURATION_MS = 3_500L;                // 3.5s hold full screen
    private static final long FADE_OUT_DURATION_MS = 1_200L;            // 1.2s retreat & fade out

    // Texts
    private static final String LINE_1_FULL = "THE SACRIFICE IS CONSUMED";
    private static final String[] DIGITS = {"3", "2", "4"};

    // State variables
    private static volatile long lastTossTimestamp = 0L;
    private static volatile boolean active = false;
    private static volatile long cutsceneStartTimestamp = 0L;
    private static volatile boolean inputLocked = false;

    // Typewriter tracking
    private static int lastTypedLine1Length = 0;
    private static int lastRevealedDigitsCount = 0;

    // Flash / Impact tracking
    private static volatile long impactTriggerTimestamp = 0L;

    public static void recordToss() {
        lastTossTimestamp = System.currentTimeMillis();
        LOGGER.info("[CUTSCENE] Nether Star toss recorded at timestamp {}", lastTossTimestamp);
    }

    public static boolean isWithinTossWindow() {
        return (System.currentTimeMillis() - lastTossTimestamp) <= TOSS_WINDOW_MS;
    }

    public static boolean isInputLocked() {
        return inputLocked;
    }

    public static boolean isActive() {
        return active;
    }

    public static synchronized void triggerCutscene() {
        if (active) {
            return;
        }

        long now = System.currentTimeMillis();
        active = true;
        cutsceneStartTimestamp = now;
        impactTriggerTimestamp = now;
        inputLocked = false;
        lastTypedLine1Length = 0;
        lastRevealedDigitsCount = 0;

        LOGGER.info("[CUTSCENE] Nether Star sacrifice triggered! Starting impact effects & cinematic sequence.");

        // Immediate visual & audio impact
        playImpactAudio();
    }

    private static void playImpactAudio() {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.getSoundManager() != null) {
                // Primary impact sound: entity.wither.ambient (Volume: 3.0, Pitch: 1.5)
                mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.WITHER_AMBIENT, 1.5f, 3.0f));

                // Layered dark impact rumble
                mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.WITHER_SPAWN, 0.6f, 1.2f));
                mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.LIGHTNING_BOLT_IMPACT, 0.7f, 1.5f));
            }
        } catch (Throwable t) {
            LOGGER.error("Error playing impact audio", t);
        }
    }

    private static void playTypewriterClickSound() {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.getSoundManager() != null) {
                // Crisp typewriter key click
                mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.STONE_BUTTON_CLICK_ON, 1.9f, 0.7f));
            }
        } catch (Throwable t) {
            // Ignore
        }
    }

    private static void playHeavyDigitImpactSound(int digitIndex) {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.getSoundManager() != null) {
                // Pitch progression for tension
                float pitchMod = 0.55f + (digitIndex * 0.08f);

                // Heavy bass drop & lightning thunder
                mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.LIGHTNING_BOLT_THUNDER, pitchMod, 2.0f));
                mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.ANVIL_LAND, pitchMod - 0.1f, 1.4f));
                mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.WARDEN_SONIC_BOOM, pitchMod, 1.6f));
            }
        } catch (Throwable t) {
            LOGGER.error("Error playing digit impact audio", t);
        }
    }

    public static void render(GuiGraphicsExtractor extractor, Font font) {
        if (!active) {
            return;
        }

        long now = System.currentTimeMillis();
        long elapsed = now - cutsceneStartTimestamp;
        int screenWidth = extractor.guiWidth();
        int screenHeight = extractor.guiHeight();
        int centerX = screenWidth / 2;
        int centerY = screenHeight / 2;

        // --- 1. Immediate Impact Flash (0 to 350ms) ---
        long impactElapsed = now - impactTriggerTimestamp;
        if (impactElapsed < 350L) {
            float flashProgress = (float) impactElapsed / 350.0f;
            float flashAlpha = (1.0f - flashProgress);
            int alphaInt = Math.min(255, (int) (flashAlpha * 220));
            // Reddish-orange sacrifice burn flash
            int flashColor = (alphaInt << 24) | 0xFF3300;
            extractor.fill(0, 0, screenWidth, screenHeight, flashColor);
        }

        // --- 2. Countdown Delay Phase (0 to 2.0s) ---
        if (elapsed < DELAY_BEFORE_CINEMATIC_MS) {
            // Player is still free to look around for 2.0 seconds while the sacrifice burns
            inputLocked = false;
            return;
        }
        // Lock input and stop momentum as soon as 2.0s expires
        if (!inputLocked) {
            inputLocked = true;
            try {
                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null) {
                    net.minecraft.world.phys.Vec3 vel = mc.player.getDeltaMovement();
                    mc.player.setDeltaMovement(0.0, vel.y > 0 ? 0.0 : vel.y, 0.0);
                    mc.player.xxa = 0.0f;
                    mc.player.zza = 0.0f;
                    mc.player.setSprinting(false);
                }
            } catch (Throwable t) {
                // Ignore
            }
        }

        long cinematicElapsed = elapsed - DELAY_BEFORE_CINEMATIC_MS;
        // Calculate timeline markers
        long letterboxEnd = LETTERBOX_SLIDE_DURATION_MS;
        long line1TypewriterDuration = LINE_1_FULL.length() * CHAR_TYPING_INTERVAL_MS;
        long line1End = letterboxEnd + line1TypewriterDuration;
        long digitsStart = line1End + DIGIT_PAUSE_BEFORE_START_MS;
        long digitsDuration = DIGITS.length * DIGIT_INTERVAL_MS;
        long digitsEnd = digitsStart + digitsDuration;
        long holdEnd = digitsEnd + HOLD_DURATION_MS;
        long totalDuration = holdEnd + FADE_OUT_DURATION_MS;

        // Check if entire cutscene is finished
        if (cinematicElapsed >= totalDuration) {
            active = false;
            inputLocked = false;
            LOGGER.info("[CUTSCENE] Nether Star sacrifice cutscene concluded. Movement restored.");
            return;
        }

        // --- 3. Letterbox Bars Calculation ---
        int maxBarHeight = (int) (screenHeight * 0.18f); // 18% top and bottom cinematic aspect
        float barProgress = 1.0f;

        if (cinematicElapsed < LETTERBOX_SLIDE_DURATION_MS) {
            // Slide in animation (ease-out cubic)
            float t = (float) cinematicElapsed / LETTERBOX_SLIDE_DURATION_MS;
            barProgress = 1.0f - (float) Math.pow(1.0f - t, 3);
        } else if (cinematicElapsed >= holdEnd) {
            // Fade out / retreat animation
            float t = (float) (cinematicElapsed - holdEnd) / FADE_OUT_DURATION_MS;
            barProgress = (float) Math.pow(1.0f - t, 2);
        }

        int currentBarHeight = (int) (maxBarHeight * barProgress);
        if (currentBarHeight > 0) {
            // Top black bar (0xFF000000)
            extractor.fill(0, 0, screenWidth, currentBarHeight, 0xFF000000);
            // Bottom black bar (0xFF000000)
            extractor.fill(0, screenHeight - currentBarHeight, screenWidth, screenHeight, 0xFF000000);
        }

        // Global text alpha (fades out at the end)
        float textAlpha = 1.0f;
        if (cinematicElapsed >= holdEnd) {
            float fadeProgress = (float) (cinematicElapsed - holdEnd) / FADE_OUT_DURATION_MS;
            textAlpha = Math.max(0.0f, 1.0f - fadeProgress);
        }

        int textAlphaInt = (int) (textAlpha * 255.0f);
        if (textAlphaInt <= 4) {
            return;
        }

        // --- 4. Line 1: "THE SACRIFICE IS CONSUMED" (Fast Typewriter) ---
        if (cinematicElapsed >= letterboxEnd) {
            long line1Elapsed = cinematicElapsed - letterboxEnd;
            int charsToShow = (int) (line1Elapsed / CHAR_TYPING_INTERVAL_MS);
            if (charsToShow > LINE_1_FULL.length()) {
                charsToShow = LINE_1_FULL.length();
            }

            // Audio click per character
            if (charsToShow > lastTypedLine1Length) {
                for (int c = lastTypedLine1Length; c < charsToShow; c++) {
                    if (LINE_1_FULL.charAt(c) != ' ') {
                        playTypewriterClickSound();
                    }
                }
                lastTypedLine1Length = charsToShow;
            }

            String currentLine1 = LINE_1_FULL.substring(0, charsToShow);
            int line1Color = (textAlphaInt << 24) | 0xAA0000; // Bold Dark Red
            int line1ShadowColor = (textAlphaInt << 24) | 0x220000;

            int line1Y = centerY - 28;

            // Render shadow + centered bold line 1 text
            extractor.centeredText(font, Component.literal("§l" + currentLine1), centerX + 1, line1Y + 1, line1ShadowColor);
            extractor.centeredText(font, Component.literal("§l" + currentLine1), centerX, line1Y, line1Color);
        }

        // --- 5. Line 2: "3 2 4" (Slow Typewriter Digits) ---
        if (cinematicElapsed >= digitsStart) {
            long digitsElapsed = cinematicElapsed - digitsStart;
            int digitsToShow = (int) (digitsElapsed / DIGIT_INTERVAL_MS) + 1;
            if (digitsToShow > DIGITS.length) {
                digitsToShow = DIGITS.length;
            }

            // Audio bass impact per digit
            if (digitsToShow > lastRevealedDigitsCount) {
                for (int d = lastRevealedDigitsCount; d < digitsToShow; d++) {
                    playHeavyDigitImpactSound(d);
                }
                lastRevealedDigitsCount = digitsToShow;
            }

            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < digitsToShow; i++) {
                if (i > 0) sb.append("   ");
                sb.append(DIGITS[i]);
            }
            String digitsStr = sb.toString();

            int line2Color = (textAlphaInt << 24) | 0xFFFFFF; // Bold White
            int line2ShadowColor = (textAlphaInt << 24) | 0x000000;

            int line2Y = centerY + 14;

            // Render scale 2.0x for large impactful digits
            float scale = 2.2f;
            extractor.pose().pushMatrix();
            extractor.pose().translate(centerX, line2Y);
            extractor.pose().scale(scale, scale);
            extractor.pose().translate(-centerX, -line2Y);

            extractor.centeredText(font, Component.literal("§l" + digitsStr), centerX + 1, line2Y + 1, line2ShadowColor);
            extractor.centeredText(font, Component.literal("§l" + digitsStr), centerX, line2Y, line2Color);

            extractor.pose().popMatrix();
        }
    }

    public static void reset() {
        active = false;
        inputLocked = false;
        lastTossTimestamp = 0L;
        cutsceneStartTimestamp = 0L;
        lastTypedLine1Length = 0;
        lastRevealedDigitsCount = 0;
    }
}
