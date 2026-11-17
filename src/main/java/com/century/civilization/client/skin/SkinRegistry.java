package com.century.civilization.client.skin;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Pure, thread-safe bookkeeping of which player names have a registered skin texture.
 * Keys are lowercase player names. Values hold an opaque texture path string, so this class
 * needs no Minecraft imports and can be unit tested directly. Lookups are plain map reads
 * because getSkin() runs every frame.
 */
public final class SkinRegistry {
    public record Entry(String path, boolean slim, long version) {}

    private final Map<String, Entry> entries = new ConcurrentHashMap<>();
    private final AtomicLong versions = new AtomicLong();

    /** Lowercase key for a player name, or null when the name is null or blank. */
    public static String key(String name) {
        if (name == null) return null;
        String t = name.trim();
        return t.isEmpty() ? null : t.toLowerCase(Locale.ROOT);
    }

    /** Returns a fresh texture path for the next version of this player's skin (new id per version). */
    public String nextPath(String namespaceFolder, String name) {
        String k = key(name);
        if (k == null) return null;
        return namespaceFolder + "/" + k + "-" + versions.incrementAndGet();
    }

    /** Stores the new entry and returns the previous one (to release its texture), or null. */
    public Entry put(String name, String path, boolean slim) {
        String k = key(name);
        if (k == null || path == null) return null;
        Entry e = new Entry(path, slim, versions.get());
        return entries.put(k, e);
    }

    public Entry get(String name) {
        String k = key(name);
        return k == null ? null : entries.get(k);
    }

    /** Removes the entry and returns it so the caller can release its texture. */
    public Entry remove(String name) {
        String k = key(name);
        return k == null ? null : entries.remove(k);
    }

    public boolean has(String name) {
        return get(name) != null;
    }

    public int size() {
        return entries.size();
    }

    public void clear() {
        entries.clear();
    }
}
