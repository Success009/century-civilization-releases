package com.century.civilization.client.skin;

/** Pure slim-arm detection for 64x64 skins. No Minecraft imports. */
public final class SkinModel {
    private SkinModel() {}

    /**
     * A slim (Alex) skin leaves the right-arm columns x=54..55, rows y=20..31 fully transparent.
     * Pixels are ABGR ints as returned by NativeImage.getPixelsABGR(); alpha is the top byte.
     */
    public static boolean isSlim(int[] pixels, int width, int height) {
        if (pixels == null || width != 64 || height != 64 || pixels.length < 64 * 64) return false;
        for (int y = 20; y <= 31; y++) {
            for (int x = 54; x <= 55; x++) {
                if (((pixels[y * 64 + x] >>> 24) & 0xFF) != 0) return false;
            }
        }
        return true;
    }
}
