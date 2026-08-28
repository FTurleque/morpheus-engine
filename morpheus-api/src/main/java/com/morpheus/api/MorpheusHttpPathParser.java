package com.morpheus.api;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Parses local HTTP paths into decoded API segments without owning route dispatch or method policy. */
final class MorpheusHttpPathParser {
    private final String apiPrefix;

    MorpheusHttpPathParser(String apiPrefix) {
        this.apiPrefix = Objects.requireNonNull(apiPrefix, "apiPrefix");
    }

    List<String> segments(String path) {
        if (!path.startsWith(apiPrefix)) throw ApiFailure.notFound("unknown API route: " + path);
        String suffix = path.substring(apiPrefix.length());
        if (suffix.isEmpty() || suffix.equals("/")) return List.of();
        String normalized = suffix.startsWith("/") ? suffix.substring(1) : suffix;
        if (normalized.endsWith("/")) normalized = normalized.substring(0, normalized.length() - 1);
        if (normalized.isEmpty()) return List.of();
        List<String> result = new ArrayList<>();
        for (String segment : normalized.split("/")) {
            if (segment.isEmpty()) throw ApiFailure.notFound("invalid API path");
            result.add(urlDecode(segment));
        }
        return List.copyOf(result);
    }

    private static String urlDecode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }
}
