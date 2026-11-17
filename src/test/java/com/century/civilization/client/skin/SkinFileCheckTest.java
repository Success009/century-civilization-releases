package com.century.civilization.client.skin;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class SkinFileCheckTest {

    /** Builds a minimal PNG-like byte array: signature + IHDR header fields, padded to the requested length. */
    private static byte[] png(int w, int h, int depth, int colorType, int interlace, int totalLength) {
        byte[] d = new byte[Math.max(totalLength, 33)];
        byte[] sig = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
        System.arraycopy(sig, 0, d, 0, 8);
        d[8] = 0; d[9] = 0; d[10] = 0; d[11] = 13;
        d[12] = 'I'; d[13] = 'H'; d[14] = 'D'; d[15] = 'R';
        putInt(d, 16, w);
        putInt(d, 20, h);
        d[24] = (byte) depth;
        d[25] = (byte) colorType;
        d[26] = 0;
        d[27] = 0;
        d[28] = (byte) interlace;
        return d;
    }

    private static byte[] valid() {
        return png(64, 64, 8, 6, 0, 100);
    }

    private static void putInt(byte[] d, int off, int v) {
        d[off] = (byte) (v >>> 24);
        d[off + 1] = (byte) (v >>> 16);
        d[off + 2] = (byte) (v >>> 8);
        d[off + 3] = (byte) v;
    }

    @Test
    @DisplayName("a valid 64x64 8-bit RGBA PNG is accepted")
    void acceptsRgba() {
        assertNull(SkinFileCheck.check(valid()));
    }

    @Test
    @DisplayName("a valid 64x64 8-bit RGB PNG is accepted")
    void acceptsRgb() {
        assertNull(SkinFileCheck.check(png(64, 64, 8, 2, 0, 100)));
    }

    @Test
    void rejectsNull() {
        assertEquals("The file is empty.", SkinFileCheck.check(null));
    }

    @Test
    void rejectsEmpty() {
        assertEquals("The file is empty.", SkinFileCheck.check(new byte[0]));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 8, 20, 32})
    void rejectsTooShortToHoldHeader(int len) {
        assertEquals("Not a valid PNG file.", SkinFileCheck.check(new byte[len]));
    }

    @Test
    void acceptsExactlyMaxBytes() {
        assertNull(SkinFileCheck.check(png(64, 64, 8, 6, 0, SkinFileCheck.MAX_BYTES)));
    }

    @Test
    void rejectsOneByteOverMax() {
        String msg = SkinFileCheck.check(png(64, 64, 8, 6, 0, SkinFileCheck.MAX_BYTES + 1));
        assertNotNull(msg);
        assertTrue(msg.startsWith("File too big"));
    }

    @Test
    void sizeMessageMentionsLimitInKb() {
        String msg = SkinFileCheck.check(new byte[SkinFileCheck.MAX_BYTES + 5]);
        assertTrue(msg.contains((SkinFileCheck.MAX_BYTES / 1024) + " KB"));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3, 4, 5, 6, 7})
    void rejectsEachBrokenSignatureByte(int index) {
        byte[] d = valid();
        d[index] = (byte) (d[index] ^ 0x55);
        assertEquals("Not a PNG file.", SkinFileCheck.check(d));
    }

    @ParameterizedTest
    @ValueSource(ints = {12, 13, 14, 15})
    void rejectsBrokenIhdrTag(int index) {
        byte[] d = valid();
        d[index] = 'X';
        assertEquals("Not a valid PNG file.", SkinFileCheck.check(d));
    }

    @ParameterizedTest
    @CsvSource({
            "32,64", "64,32", "128,128", "63,64", "64,63", "65,64", "64,65",
            "1,1", "0,0", "256,256", "64,128", "128,64"
    })
    void rejectsWrongDimensions(int w, int h) {
        String msg = SkinFileCheck.check(png(w, h, 8, 6, 0, 100));
        assertNotNull(msg);
        assertTrue(msg.contains("64x64"));
        assertTrue(msg.contains(w + "x" + h));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 4, 16})
    void rejectsNon8BitDepth(int depth) {
        assertEquals("Skin must be an 8-bit RGB or RGBA PNG.", SkinFileCheck.check(png(64, 64, depth, 6, 0, 100)));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 3, 4})
    void rejectsUnsupportedColorTypes(int colorType) {
        assertEquals("Skin must be an 8-bit RGB or RGBA PNG.", SkinFileCheck.check(png(64, 64, 8, colorType, 0, 100)));
    }

    @Test
    void rejectsInterlaced() {
        assertEquals("Interlaced PNGs are not supported.", SkinFileCheck.check(png(64, 64, 8, 6, 1, 100)));
    }

    @Test
    void rejectsPlainTextFile() {
        byte[] d = "this is definitely not a png, just some text padding it out".getBytes();
        assertEquals("Not a PNG file.", SkinFileCheck.check(d));
    }

    @Test
    void rejectsJpegSignature() {
        byte[] d = new byte[100];
        d[0] = (byte) 0xFF; d[1] = (byte) 0xD8; d[2] = (byte) 0xFF;
        assertEquals("Not a PNG file.", SkinFileCheck.check(d));
    }

    @Test
    void doesNotMutateInput() {
        byte[] d = valid();
        byte[] copy = d.clone();
        SkinFileCheck.check(d);
        assertArrayEquals(copy, d);
    }

    @Test
    void maxBytesIsOneMegabyte() {
        assertEquals(1024 * 1024, SkinFileCheck.MAX_BYTES);
    }
}
