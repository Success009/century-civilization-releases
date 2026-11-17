package com.century.civilization.client.skin;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class SkinUploaderTest {

    // ---- sanitize() ----

    @Test
    void sanitizeKeepsNormalName() {
        assertEquals("my skin.png", SkinUploader.sanitize("my skin.png"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "   ", "\t"})
    void sanitizeFallsBackForNullOrBlank(String in) {
        assertEquals("skin.png", SkinUploader.sanitize(in));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "a\"b.png|a_b.png",
            "a\\b.png|a_b.png"
    })
    void sanitizeReplacesQuoteAndBackslash(String in, String expected) {
        assertEquals(expected, SkinUploader.sanitize(in));
    }

    static java.util.stream.Stream<org.junit.jupiter.params.provider.Arguments> lineBreakCases() {
        return java.util.stream.Stream.of(
                org.junit.jupiter.params.provider.Arguments.of("a\rb.png", "a_b.png"),
                org.junit.jupiter.params.provider.Arguments.of("a\nb.png", "a_b.png"),
                org.junit.jupiter.params.provider.Arguments.of("a\r\nb.png", "a__b.png"));
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.MethodSource("lineBreakCases")
    void sanitizeReplacesLineBreaks(String in, String expected) {
        assertEquals(expected, SkinUploader.sanitize(in));
    }

    @Test
    void sanitizeOutputNeverContainsDangerousChars() {
        String out = SkinUploader.sanitize("x\"\r\n\\y\"; evil=\"1");
        assertFalse(out.contains("\""));
        assertFalse(out.contains("\r"));
        assertFalse(out.contains("\n"));
        assertFalse(out.contains("\\"));
    }

    @Test
    void sanitizeOnlyBadCharsStillNotBlank() {
        assertEquals("_", SkinUploader.sanitize("\""));
    }

    // ---- buildMultipart() ----

    private static String latin1(byte[] b) {
        return new String(b, StandardCharsets.ISO_8859_1);
    }

    @Test
    void multipartStartsWithBoundaryAndDisposition() {
        String s = latin1(SkinUploader.buildMultipart("BND", "skin.png", new byte[]{1, 2, 3}));
        assertTrue(s.startsWith("--BND\r\n"));
        assertTrue(s.contains("Content-Disposition: form-data; name=\"skin\"; filename=\"skin.png\"\r\n"));
        assertTrue(s.contains("Content-Type: image/png\r\n\r\n"));
    }

    @Test
    void multipartEndsWithClosingBoundary() {
        String s = latin1(SkinUploader.buildMultipart("BND", "skin.png", new byte[]{1, 2, 3}));
        assertTrue(s.endsWith("\r\n--BND--\r\n"));
    }

    @Test
    @DisplayName("png bytes are embedded unchanged, including bytes >= 0x80")
    void multipartEmbedsPayloadExactly() {
        byte[] png = new byte[300];
        for (int i = 0; i < png.length; i++) png[i] = (byte) i;
        byte[] body = SkinUploader.buildMultipart("BND", "s.png", png);
        String head = "--BND\r\n"
                + "Content-Disposition: form-data; name=\"skin\"; filename=\"s.png\"\r\n"
                + "Content-Type: image/png\r\n\r\n";
        String tail = "\r\n--BND--\r\n";
        int start = head.length();
        assertEquals(head.length() + png.length + tail.length(), body.length);
        assertArrayEquals(png, Arrays.copyOfRange(body, start, start + png.length));
    }

    @Test
    void multipartSanitizesFileName() {
        String s = latin1(SkinUploader.buildMultipart("BND", "a\"b\r\nX-Evil: 1", new byte[]{1}));
        assertFalse(s.contains("X-Evil: 1\r\n"));
        assertTrue(s.contains("filename=\"a_b__X-Evil: 1\""));
    }

    @Test
    void multipartNullFileNameUsesDefault() {
        String s = latin1(SkinUploader.buildMultipart("BND", null, new byte[]{1}));
        assertTrue(s.contains("filename=\"skin.png\""));
    }

    @Test
    void multipartEmptyPayloadStillWellFormed() {
        String s = latin1(SkinUploader.buildMultipart("BND", "e.png", new byte[0]));
        assertTrue(s.contains("\r\n\r\n\r\n--BND--\r\n"));
    }

    // ---- parseResponse() ----

    @ParameterizedTest
    @ValueSource(ints = {200, 201, 204, 299})
    void successTrueOn2xxIsOk(int status) {
        SkinUploader.Result r = SkinUploader.parseResponse(status, "{\"success\":true}");
        assertTrue(r.ok());
        assertEquals("Skin uploaded.", r.message());
    }

    @Test
    void successTrueWithExtraFieldsIsOk() {
        assertTrue(SkinUploader.parseResponse(200, "{\"success\":true,\"url\":\"/skins/a.png\"}").ok());
    }

    @Test
    void successWinsOverErrorField() {
        assertTrue(SkinUploader.parseResponse(200, "{\"success\":true,\"error\":\"ignored\"}").ok());
    }

    @Test
    void successFalseOn200IsGenericFailure() {
        SkinUploader.Result r = SkinUploader.parseResponse(200, "{\"success\":false}");
        assertFalse(r.ok());
        assertEquals("Upload failed (HTTP 200).", r.message());
    }

    @Test
    void successTrueButNon2xxIsFailure() {
        SkinUploader.Result r = SkinUploader.parseResponse(500, "{\"success\":true}");
        assertFalse(r.ok());
        assertEquals("Upload failed (HTTP 500).", r.message());
    }

    @ParameterizedTest
    @CsvSource({"400", "401", "403", "413", "422", "500", "502"})
    void serverErrorMessageIsShownVerbatim(int status) {
        SkinUploader.Result r = SkinUploader.parseResponse(status, "{\"error\":\"Skin rejected by server\"}");
        assertFalse(r.ok());
        assertEquals("Skin rejected by server", r.message());
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 403})
    void authStatusWithoutErrorAsksToLogInAgain(int status) {
        SkinUploader.Result r = SkinUploader.parseResponse(status, "{}");
        assertFalse(r.ok());
        assertEquals("You are not logged in. Please log in again.", r.message());
    }

    @Test
    void status413WithoutErrorSaysTooBig() {
        assertEquals("File too big for the server.", SkinUploader.parseResponse(413, "{}").message());
    }

    @ParameterizedTest
    @CsvSource({"400", "404", "500", "503"})
    void otherStatusesGetGenericMessage(int status) {
        assertEquals("Upload failed (HTTP " + status + ").", SkinUploader.parseResponse(status, "{}").message());
    }

    @Test
    void nullBodyFallsBackToStatus() {
        assertEquals("You are not logged in. Please log in again.", SkinUploader.parseResponse(401, null).message());
        assertEquals("Upload failed (HTTP 500).", SkinUploader.parseResponse(500, null).message());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "\n"})
    void blankBodyFallsBackToStatus(String body) {
        assertEquals("File too big for the server.", SkinUploader.parseResponse(413, body).message());
    }

    @Test
    void htmlBodyFallsBackToStatus() {
        SkinUploader.Result r = SkinUploader.parseResponse(502, "<html><body>Bad gateway</body></html>");
        assertFalse(r.ok());
        assertEquals("Upload failed (HTTP 502).", r.message());
    }

    @ParameterizedTest
    @ValueSource(strings = {"[]", "[1,2]", "\"text\"", "42", "null", "{broken"})
    void nonObjectOrMalformedJsonNeverThrows(String body) {
        SkinUploader.Result r = assertDoesNotThrow(() -> SkinUploader.parseResponse(500, body));
        assertFalse(r.ok());
        assertEquals("Upload failed (HTTP 500).", r.message());
    }

    @Test
    void blankErrorFallsThroughToStatus() {
        assertEquals("Upload failed (HTTP 400).", SkinUploader.parseResponse(400, "{\"error\":\"   \"}").message());
    }

    @Test
    void nullErrorFallsThroughToStatus() {
        assertEquals("Upload failed (HTTP 400).", SkinUploader.parseResponse(400, "{\"error\":null}").message());
    }

    @Test
    void nullSuccessIsNotSuccess() {
        assertFalse(SkinUploader.parseResponse(200, "{\"success\":null}").ok());
    }

    @Test
    void errorWithUnicodeIsPreserved() {
        assertEquals("UngÃ¼ltige Datei", SkinUploader.parseResponse(400, "{\"error\":\"UngÃ¼ltige Datei\"}").message());
    }

    @Test
    void resultRecordEquality() {
        assertEquals(new SkinUploader.Result(true, "x"), new SkinUploader.Result(true, "x"));
        assertNotEquals(new SkinUploader.Result(true, "x"), new SkinUploader.Result(false, "x"));
    }
}
