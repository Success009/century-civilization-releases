package com.century.civilization.client.skin;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class SkinRegistryTest {

    private SkinRegistry reg;

    @BeforeEach
    void setUp() {
        reg = new SkinRegistry();
    }

    // ---- key() ----

    @ParameterizedTest
    @CsvSource({"Steve,steve", "ALEX,alex", "  Notch  ,notch", "MiXeD123,mixed123", "a,a"})
    void keyTrimsAndLowercases(String in, String expected) {
        assertEquals(expected, SkinRegistry.key(in));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "   ", "\t", "\n"})
    void keyReturnsNullForNullOrBlank(String in) {
        assertNull(SkinRegistry.key(in));
    }

    @Test
    void keyUsesRootLocale() {
        // Turkish dotted/dotless I must not change the result
        assertEquals("illegal", SkinRegistry.key("ILLEGAL"));
    }

    // ---- nextPath() ----

    @Test
    void nextPathIsNamespacedAndLowercase() {
        String p = reg.nextPath("skins", "Steve");
        assertTrue(p.startsWith("skins/steve-"));
    }

    @Test
    void nextPathIsUniqueEachCall() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            assertTrue(seen.add(reg.nextPath("skins", "steve")));
        }
    }

    @Test
    void nextPathCounterIsSharedAcrossNames() {
        String a = reg.nextPath("skins", "a");
        String b = reg.nextPath("skins", "b");
        assertNotEquals(a.substring(a.lastIndexOf('-')), b.substring(b.lastIndexOf('-')));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  "})
    void nextPathNullForBadName(String name) {
        assertNull(reg.nextPath("skins", name));
    }

    @Test
    void nextPathSameNameDifferentCaseSharesPrefix() {
        assertTrue(reg.nextPath("skins", "STEVE").startsWith("skins/steve-"));
    }

    // ---- put() / get() ----

    @Test
    void getOnEmptyIsNull() {
        assertNull(reg.get("steve"));
    }

    @Test
    void putThenGetReturnsEntry() {
        reg.put("Steve", "skins/steve-1", false);
        SkinRegistry.Entry e = reg.get("steve");
        assertNotNull(e);
        assertEquals("skins/steve-1", e.path());
        assertFalse(e.slim());
    }

    @Test
    void putStoresSlimFlag() {
        reg.put("alex", "skins/alex-1", true);
        assertTrue(reg.get("alex").slim());
    }

    @Test
    @DisplayName("lookups are case and whitespace insensitive")
    void lookupNormalizesName() {
        reg.put("Steve", "p", false);
        assertNotNull(reg.get("STEVE"));
        assertNotNull(reg.get("  steve "));
    }

    @Test
    void firstPutReturnsNullPrevious() {
        assertNull(reg.put("steve", "p1", false));
    }

    @Test
    void secondPutReturnsPreviousEntry() {
        reg.put("steve", "p1", false);
        SkinRegistry.Entry old = reg.put("steve", "p2", true);
        assertNotNull(old);
        assertEquals("p1", old.path());
        assertEquals("p2", reg.get("steve").path());
        assertTrue(reg.get("steve").slim());
    }

    @Test
    void putWithNullPathIsIgnored() {
        assertNull(reg.put("steve", null, false));
        assertFalse(reg.has("steve"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" "})
    void putWithBadNameIsIgnored(String name) {
        assertNull(reg.put(name, "p", false));
        assertEquals(0, reg.size());
    }

    @Test
    void getWithBadNameIsNull() {
        reg.put("steve", "p", false);
        assertNull(reg.get(null));
        assertNull(reg.get(""));
        assertNull(reg.get("   "));
    }

    @Test
    void putRecordsVersionFromCounter() {
        reg.nextPath("skins", "steve");
        reg.nextPath("skins", "steve");
        reg.put("steve", "p", false);
        assertEquals(2L, reg.get("steve").version());
    }

    // ---- has() / remove() / size() / clear() ----

    @Test
    void hasReflectsPresence() {
        assertFalse(reg.has("steve"));
        reg.put("steve", "p", false);
        assertTrue(reg.has("STEVE"));
    }

    @Test
    void removeReturnsEntryAndDeletes() {
        reg.put("steve", "p", false);
        SkinRegistry.Entry e = reg.remove("Steve");
        assertNotNull(e);
        assertEquals("p", e.path());
        assertFalse(reg.has("steve"));
    }

    @Test
    void removeMissingReturnsNull() {
        assertNull(reg.remove("nobody"));
        assertNull(reg.remove(null));
    }

    @Test
    void sizeCountsDistinctKeysOnly() {
        reg.put("Steve", "a", false);
        reg.put("steve", "b", false);
        reg.put("alex", "c", false);
        assertEquals(2, reg.size());
    }

    @Test
    void clearEmptiesRegistry() {
        reg.put("a", "1", false);
        reg.put("b", "2", false);
        reg.clear();
        assertEquals(0, reg.size());
        assertNull(reg.get("a"));
    }

    @Test
    void clearDoesNotResetVersionCounter() {
        String first = reg.nextPath("skins", "a");
        reg.clear();
        String second = reg.nextPath("skins", "a");
        assertNotEquals(first, second);
    }

    // ---- concurrency ----

    @Test
    void concurrentPutsAndPathsStayConsistent() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Callable<String>> jobs = new ArrayList<>();
            for (int i = 0; i < 200; i++) {
                final int n = i;
                jobs.add(() -> {
                    String path = reg.nextPath("skins", "player" + (n % 10));
                    reg.put("player" + (n % 10), path, n % 2 == 0);
                    return path;
                });
            }
            Set<String> paths = new HashSet<>();
            for (Future<String> f : pool.invokeAll(jobs)) {
                assertTrue(paths.add(f.get()), "duplicate path generated");
            }
            assertEquals(200, paths.size());
            assertEquals(10, reg.size());
        } finally {
            pool.shutdownNow();
        }
    }
}
