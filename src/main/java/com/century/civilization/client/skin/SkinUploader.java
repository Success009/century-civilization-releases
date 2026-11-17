package com.century.civilization.client.skin;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Uploads a skin PNG to the Century web server: POST /api/skins/upload,
 * multipart field "skin", bearer-token auth, reply {success:true} or {error:"..."}.
 * Body building and response parsing are static and have no Minecraft imports.
 */
public final class SkinUploader {
    public record Result(boolean ok, String message) {}

    private SkinUploader() {}

    /** Builds a multipart/form-data body with a single file part named "skin". */
    public static byte[] buildMultipart(String boundary, String fileName, byte[] png) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream(png.length + 256);
            String head = "--" + boundary + "\r\n"
                    + "Content-Disposition: form-data; name=\"skin\"; filename=\"" + sanitize(fileName) + "\"\r\n"
                    + "Content-Type: image/png\r\n\r\n";
            out.write(head.getBytes(StandardCharsets.UTF_8));
            out.write(png);
            out.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
            return out.toByteArray();
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Strips characters that could break out of the quoted filename header. */
    static String sanitize(String name) {
        if (name == null || name.isBlank()) return "skin.png";
        String s = name.replaceAll("[\\r\\n\"\\\\]", "_");
        return s.isBlank() ? "skin.png" : s;
    }

    /** Interprets the server reply. 200 with success=true is ok; otherwise returns a player-facing message. */
    public static Result parseResponse(int status, String body) {
        String error = null;
        boolean success = false;
        try {
            if (body != null && !body.isBlank()) {
                JsonObject o = JsonParser.parseString(body).getAsJsonObject();
                if (o.has("success") && !o.get("success").isJsonNull()) success = o.get("success").getAsBoolean();
                if (o.has("error") && !o.get("error").isJsonNull()) error = o.get("error").getAsString();
            }
        } catch (Exception ignored) {
            // non-JSON body, fall through to status handling
        }
        if (status >= 200 && status < 300 && success) return new Result(true, "Skin uploaded.");
        if (error != null && !error.isBlank()) return new Result(false, error);
        if (status == 401 || status == 403) return new Result(false, "You are not logged in. Please log in again.");
        if (status == 413) return new Result(false, "File too big for the server.");
        return new Result(false, "Upload failed (HTTP " + status + ").");
    }

    /** Blocking upload. Call off the render thread. Validates the PNG first. */
    public static Result upload(HttpClient client, String baseUrl, String token, String fileName, byte[] png) {
        String problem = SkinFileCheck.check(png);
        if (problem != null) return new Result(false, problem);
        if (token == null || token.isBlank()) return new Result(false, "You are not logged in.");
        try {
            String boundary = "----CenturySkin" + Long.toHexString(System.nanoTime());
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/skins/upload"))
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", "Bearer " + token)
                    .header("User-Agent", "CenturyMod-Client")
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(buildMultipart(boundary, fileName, png)))
                    .build();
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            return parseResponse(resp.statusCode(), resp.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Result(false, "Upload cancelled.");
        } catch (Exception e) {
            return new Result(false, "Connection failed: " + e.getMessage());
        }
    }
}
