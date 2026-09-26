package com.morpheus.api;

import com.morpheus.application.query.PageRequest;
import com.morpheus.application.query.SnapshotPage;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * The only place {@code morpheus-api} spells a paginated response (ADR-0107): {@code offset}, {@code limit},
 * {@code totalMatches}, {@code hasMore}, {@code items}. A page either follows a response's own identifiers, or is
 * the value of the key that names its collection. {@code morpheus-mcp} holds an identical copy because sibling
 * adapters may not share code; {@code PagedResponseVocabularyArchitectureTest} keeps the two copies and their
 * callers honest.
 */
final class PagedEnvelope {
    private PagedEnvelope() {
    }

    static Map<String, Object> page(int offset, int limit, int totalMatches, boolean hasMore, List<?> items) {
        Map<String, Object> page = new LinkedHashMap<>();
        page.put("offset", offset);
        page.put("limit", limit);
        page.put("totalMatches", totalMatches);
        page.put("hasMore", hasMore);
        page.put("items", List.copyOf(items));
        return Collections.unmodifiableMap(page);
    }

    static Map<String, Object> page(PageRequest request, int totalMatches, boolean hasMore, List<?> items) {
        return page(request.offset(), request.limit(), totalMatches, hasMore, items);
    }

    static <T> Map<String, Object> slice(int offset, int limit, List<T> source, Function<? super T, ?> projection) {
        int total = source.size();
        int from = Math.min(offset, total);
        int to = (int) Math.min((long) from + limit, total);
        return page(offset, limit, total, to < total, source.subList(from, to).stream().map(projection).toList());
    }

    static Map<String, Object> snapshotPage(SnapshotPage<?> page, List<?> items) {
        return following(
                Map.of("snapshotId", page.snapshot().id().toString()),
                page(page.pageRequest(), page.totalMatches(), page.hasMore(), items));
    }

    /** Places a page after a response's own identifiers; an identifier may not reuse a page key. */
    static Map<String, Object> following(Map<String, Object> identifiers, Map<String, Object> page) {
        Map<String, Object> response = new LinkedHashMap<>(identifiers);
        page.forEach((key, value) -> {
            if (response.containsKey(key)) {
                throw new IllegalArgumentException("response identifier collides with page key: " + key);
            }
            response.put(key, value);
        });
        return Collections.unmodifiableMap(response);
    }
}
