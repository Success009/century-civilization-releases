package com.century.civilization.client.skin;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class SkinModelTest {

    private static final int OPAQUE = 0xFF000000;

    /** A fully opaque 64x64 skin (wide / Steve arms). */
    private static int[] opaque() {
        int[] p = new int[64 * 64];
        Arrays.fill(p, OPAQUE | 0x00123456);
        return p;
    }

    /** An opaque skin with the slim-arm region (x=54..55, y=20..31) cleared. */
    private static int[] slim() {
        int[] p = opaque();
        for (int y = 20; y <= 31; y++) {
            for (int x = 54; x <= 55; x++) {
                p[y * 64 + x] = 0;
            }
        }
        return p;
    }

    @Test
    @DisplayName("fully opaque skin is wide")
    void opaqueIsWide() {
        assertFalse(SkinModel.isSlim(opaque(), 64, 64));
    }

    @Test
    @DisplayName("cleared right-arm columns make a slim skin")
    void clearedRegionIsSlim() {
        assertTrue(SkinModel.isSlim(slim(), 64, 64));
    }

    @Test
    void fullyTransparentSkinIsSlim() {
        assertTrue(SkinModel.isSlim(new int[64 * 64], 64, 64));
    }

    @ParameterizedTest
    @CsvSource({"54,20", "55,20", "54,31", "55,31", "54,25", "55,25"})
    void anyOpaquePixelInRegionMakesItWide(int x, int y) {
        int[] p = slim();
        p[y * 64 + x] = OPAQUE;
        assertFalse(SkinModel.isSlim(p, 64, 64));
    }

    @Test
    void almostTransparentPixelStillCountsAsOpaque() {
        int[] p = slim();
        p[25 * 64 + 54] = 0x01000000; // alpha = 1
        assertFalse(SkinModel.isSlim(p, 64, 64));
    }

    @Test
    void onlyAlphaByteMattersNotColor() {
        int[] p = slim();
        p[25 * 64 + 54] = 0x00FFFFFF; // alpha 0, bright color
        assertTrue(SkinModel.isSlim(p, 64, 64));
    }

    @ParameterizedTest
    @CsvSource({"53,25", "56,25", "54,19", "55,32", "0,0", "63,63"})
    void pixelsOutsideRegionAreIgnored(int x, int y) {
        int[] p = slim();
        p[y * 64 + x] = OPAQUE;
        assertTrue(SkinModel.isSlim(p, 64, 64));
    }

    @Test
    void nullPixelsAreNotSlim() {
        assertFalse(SkinModel.isSlim(null, 64, 64));
    }

    @ParameterizedTest
    @CsvSource({"32,64", "64,32", "128,128", "0,0", "63,64", "64,63"})
    void wrongDimensionsAreNotSlim(int w, int h) {
        assertFalse(SkinModel.isSlim(new int[64 * 64], w, h));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 100, 4095})
    void tooShortArrayIsNotSlim(int len) {
        assertFalse(SkinModel.isSlim(new int[len], 64, 64));
    }

    @Test
    void longerArrayIsStillRead() {
        int[] p = new int[64 * 64 + 10];
        assertTrue(SkinModel.isSlim(p, 64, 64));
    }

    @Test
    void doesNotMutateInput() {
        int[] p = slim();
        int[] copy = p.clone();
        SkinModel.isSlim(p, 64, 64);
        assertArrayEquals(copy, p);
    }
}
