package com.century.civilization.client.skin;

import com.century.civilization.client.CenturyModClient;
import java.net.http.HttpClient;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import org.lwjgl.sdl.SDLDialog;
import org.lwjgl.sdl.SDL_DialogFileCallback;
import org.lwjgl.sdl.SDL_DialogFileFilter;
import org.lwjgl.system.MemoryUtil;

/**
 * Click-the-avatar flow: opens the native SDL file dialog (already shipped with Minecraft 26.3),
 * validates the PNG, uploads it to the Century web server and refreshes the local texture.
 * Cancel does nothing. Reading and uploading run off the render thread; progress is exposed
 * through status() for the profile card.
 */
public final class SkinPicker {
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private static volatile boolean busy = false;
    private static volatile String status = "";
    private static volatile int statusColor = 0xFFAAAAAA;
    private static volatile long statusUntil = 0L;
    /** Kept alive until the next dialog, because SDL may still touch it right after the callback returns. */
    private static SDL_DialogFileCallback lastCallback;

    private SkinPicker() {}

    public static boolean isBusy() {
        return busy;
    }

    /** Current status line, or "" when none (messages fade after a few seconds). */
    public static String status() {
        return !busy && System.currentTimeMillis() > statusUntil ? "" : status;
    }

    public static int statusColor() {
        return statusColor;
    }

    private static void set(String text, int color, long ttlMs) {
        status = text;
        statusColor = color;
        statusUntil = System.currentTimeMillis() + ttlMs;
    }

    /** Opens the file dialog and uploads the chosen skin. Call from the render (main) thread. */
    public static void pickAndUpload() {
        if (busy) return;
        if (!CenturyModClient.isLoggedIn()) {
            set("Log in to Century to upload a skin.", 0xFFFF5555, 4000);
            return;
        }
        String name = CenturyModClient.getAuthenticatedUsername();
        String token = CenturyModClient.getAuthToken();
        if (name == null || name.isBlank() || token == null || token.isBlank()) {
            set("Log in to Century to upload a skin.", 0xFFFF5555, 4000);
            return;
        }
        busy = true;
        ByteBuffer filterName = null;
        ByteBuffer filterPattern = null;
        SDL_DialogFileFilter.Buffer filters = null;
        try {
            if (lastCallback != null) {
                lastCallback.free();
                lastCallback = null;
            }
            final ByteBuffer fn = MemoryUtil.memUTF8("PNG images");
            final ByteBuffer fp = MemoryUtil.memUTF8("png");
            filterName = fn;
            filterPattern = fp;
            filters = SDL_DialogFileFilter.malloc(1);
            filters.name(fn);
            filters.pattern(fp);
            final SDL_DialogFileFilter.Buffer fb = filters;

            lastCallback = SDL_DialogFileCallback.create((userdata, fileList, filter) -> {
                String picked = null;
                boolean failed = fileList == 0L;
                if (!failed) {
                    long first = MemoryUtil.memGetAddress(fileList);
                    if (first != 0L) picked = MemoryUtil.memUTF8(first);
                }
                MemoryUtil.memFree(fn);
                MemoryUtil.memFree(fp);
                fb.free();
                if (failed) {
                    set("Could not open the file dialog.", 0xFFFF5555, 6000);
                    busy = false;
                    return;
                }
                if (picked == null || picked.isEmpty()) { // cancelled: do nothing
                    busy = false;
                    return;
                }
                final String path = picked;
                Thread t = new Thread(() -> {
                    try {
                        upload(name, token, Path.of(path));
                    } finally {
                        busy = false;
                    }
                }, "century-skin-upload");
                t.setDaemon(true);
                t.start();
            });

            String home = System.getProperty("user.home", "");
            String start = home.isEmpty() ? null : home + java.io.File.separator;
            SDLDialog.SDL_ShowOpenFileDialog(lastCallback, 0L, 0L, filters, start, false);
        } catch (Throwable ex) {
            if (filterName != null) MemoryUtil.memFree(filterName);
            if (filterPattern != null) MemoryUtil.memFree(filterPattern);
            if (filters != null) filters.free();
            set("Could not open the file dialog.", 0xFFFF5555, 6000);
            busy = false;
        }
    }

    private static void upload(String name, String token, Path file) {
        byte[] data;
        try {
            if (Files.size(file) > SkinFileCheck.MAX_BYTES) {
                set("File too big (max " + (SkinFileCheck.MAX_BYTES / 1024) + " KB).", 0xFFFF5555, 6000);
                return;
            }
            data = Files.readAllBytes(file);
        } catch (Exception ex) {
            set("Could not read that file.", 0xFFFF5555, 6000);
            return;
        }
        String problem = SkinFileCheck.check(data);
        if (problem != null) {
            set(problem, 0xFFFF5555, 6000);
            return;
        }
        set("Uploading skin...", 0xFFAAAAAA, 30000);
        String fileName = file.getFileName() != null ? file.getFileName().toString() : "skin.png";
        SkinUploader.Result r = SkinUploader.upload(HTTP, CenturyModClient.API_BASE_URL, token, fileName, data);
        if (r.ok()) {
            SkinTextures.refresh(name);
            set("Skin uploaded!", 0xFF55FF55, 5000);
        } else {
            set(r.message(), 0xFFFF5555, 6000);
        }
    }
}
