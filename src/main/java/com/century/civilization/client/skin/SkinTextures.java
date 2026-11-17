package com.century.civilization.client.skin;

import com.century.civilization.client.CenturyModClient;
import com.mojang.blaze3d.platform.NativeImage;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/**
 * Downloads player skins from the Century web server (/skins/<name>.png) and registers them
 * as textures. Network work is async, texture registration runs on the render thread.
 * Never throws: until a skin arrives, texture() returns null and callers use the vanilla skin.
 */
public final class SkinTextures {
    /** A player the server has no skin for is asked about again only after this long. */
    private static final long MISS_RETRY_MS = 300_000L;

    private static final SkinRegistry REGISTRY = new SkinRegistry();
    private static final Map<String, Boolean> IN_FLIGHT = new ConcurrentHashMap<>();
    private static final Map<String, Long> LAZY_ASKED = new ConcurrentHashMap<>();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private SkinTextures() {}

    /** Texture id of a player's downloaded skin, or null. */
    public static Identifier texture(String name) {
        SkinRegistry.Entry e = REGISTRY.get(name);
        return e == null ? null : Identifier.fromNamespaceAndPath("century", e.path());
    }

    public static boolean isSlim(String name) {
        SkinRegistry.Entry e = REGISTRY.get(name);
        return e != null && e.slim();
    }

    /** Called when a player is rendered. Cheap: known skins return at once, misses retry every 5 minutes. */
    public static void requestLazy(String name) {
        String key = SkinRegistry.key(name);
        if (key == null || REGISTRY.has(key)) return;
        long now = System.currentTimeMillis();
        Long last = LAZY_ASKED.get(key);
        if (last != null && now - last < MISS_RETRY_MS) return;
        LAZY_ASKED.put(key, now);
        fetch(key, false);
    }

    /** Forces a re-download, for example right after the local player uploads a new skin. */
    public static void refresh(String name) {
        String key = SkinRegistry.key(name);
        if (key == null) return;
        LAZY_ASKED.remove(key);
        fetch(key, true);
    }

    private static void fetch(String key, boolean bustCache) {
        if (IN_FLIGHT.putIfAbsent(key, Boolean.TRUE) != null) return;
        CompletableFuture.runAsync(() -> {
            try {
                String url = CenturyModClient.API_BASE_URL + "/skins/" + key + ".png"
                        + (bustCache ? "?v=" + System.currentTimeMillis() : "");
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofSeconds(15))
                        .header("User-Agent", "CenturyMod-Client")
                        .GET().build();
                HttpResponse<byte[]> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofByteArray());
                String type = resp.headers().firstValue("Content-Type").orElse("").toLowerCase();
                // The web server falls back to an HTML page for unknown paths, so check the type.
                if (resp.statusCode() != 200 || !type.contains("image/png")) return;
                byte[] bytes = resp.body();
                if (SkinFileCheck.check(bytes) != null) return;
                NativeImage img = NativeImage.read(new java.io.ByteArrayInputStream(bytes));
                boolean slim = SkinModel.isSlim(img.getPixelsABGR(), img.getWidth(), img.getHeight());
                Minecraft.getInstance().execute(() -> register(key, img, slim));
            } catch (Throwable ignored) {
                // fail soft: the vanilla skin stays
            } finally {
                IN_FLIGHT.remove(key);
            }
        });
    }

    private static void register(String key, NativeImage img, boolean slim) {
        try {
            Minecraft mc = Minecraft.getInstance();
            String path = REGISTRY.nextPath("skins", key);
            if (path == null) return;
            Identifier id = Identifier.fromNamespaceAndPath("century", path);
            mc.getTextureManager().register(id, new DynamicTexture(() -> "century skin " + key, img));
            SkinRegistry.Entry old = REGISTRY.put(key, path, slim);
            if (old != null && !old.path().equals(path)) {
                mc.getTextureManager().release(Identifier.fromNamespaceAndPath("century", old.path()));
            }
        } catch (Throwable ignored) {
        }
    }
}
