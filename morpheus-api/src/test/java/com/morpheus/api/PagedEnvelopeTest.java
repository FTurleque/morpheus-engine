package com.morpheus.api;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PagedEnvelopeTest {
    @Test
    void aPageIsExactlyTheFiveCanonicalKeys() {
        Map<String, Object> page = PagedEnvelope.slice(1, 1, List.of("a", "b", "c"), Function.identity());

        assertEquals(List.of("offset", "limit", "totalMatches", "hasMore", "items"), List.copyOf(page.keySet()));
        assertEquals(3, page.get("totalMatches"));
        assertEquals(true, page.get("hasMore"));
        assertEquals(List.of("b"), page.get("items"));
    }

    @Test
    void aSliceBeyondTheEndIsEmptyAndDoesNotOverflow() {
        Map<String, Object> page = PagedEnvelope.slice(5, Integer.MAX_VALUE, List.of("a", "b"), Function.identity());

        assertEquals(List.of(), page.get("items"));
        assertEquals(false, page.get("hasMore"));
        assertEquals(5, page.get("offset"));
        assertEquals(2, page.get("totalMatches"));
    }

    @Test
    void identifiersPrecedeThePageAndMayNotReuseAPageKey() {
        Map<String, Object> page = PagedEnvelope.page(0, 10, 0, false, List.of());

        Map<String, Object> response = PagedEnvelope.following(Map.of("snapshotId", "s"), page);
        assertEquals(List.of("snapshotId", "offset", "limit", "totalMatches", "hasMore", "items"),
                List.copyOf(response.keySet()));

        IllegalArgumentException collision = assertThrows(IllegalArgumentException.class,
                () -> PagedEnvelope.following(Map.of("offset", 3), page));
        assertEquals("response identifier collides with page key: offset", collision.getMessage());
    }
}
