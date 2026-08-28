package com.morpheus.api;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Parses and validates local HTTP query parameters without owning route dispatch. */
final class MorpheusHttpQuery {
    private final Map<String, String> values;

    private MorpheusHttpQuery(Map<String, String> values) {
        this.values = Map.copyOf(values);
    }

    static MorpheusHttpQuery parse(String rawQuery) {
        if (rawQuery == null || rawQuery.isBlank()) return new MorpheusHttpQuery(Map.of());
        Map<String, String> values = new LinkedHashMap<>();
        for (String part : rawQuery.split("&")) {
            if (part.isBlank()) continue;
            int separator = part.indexOf('=');
            String key = urlDecode(separator < 0 ? part : part.substring(0, separator));
            String value = urlDecode(separator < 0 ? "" : part.substring(separator + 1));
            if (key.isBlank()) throw ApiFailure.badRequest("query parameter name must not be blank");
            if (values.putIfAbsent(key, value) != null) {
                throw ApiFailure.badRequest("duplicate query parameter: " + key);
            }
        }
        return new MorpheusHttpQuery(values);
    }

    Optional<String> string(String name) {
        return Optional.ofNullable(values.get(name));
    }

    String required(String name) {
        String value = values.get(name);
        if (value == null || value.isBlank()) {
            throw ApiFailure.badRequest("query parameter is required: " + name);
        }
        return value;
    }

    int intValue(String name, int defaultValue, int minimum, int maximum) {
        String raw = values.get(name);
        if (raw == null) return defaultValue;
        try {
            int value = Integer.parseInt(raw);
            if (value < minimum || value > maximum) {
                throw ApiFailure.badRequest(name + " must be between " + minimum + " and " + maximum);
            }
            return value;
        } catch (NumberFormatException failure) {
            throw ApiFailure.badRequest(name + " must be an integer");
        }
    }

    long longValue(String name, long defaultValue, long minimum, long maximum) {
        String raw = values.get(name);
        if (raw == null) return defaultValue;
        try {
            long value = Long.parseLong(raw);
            if (value < minimum || value > maximum) {
                throw ApiFailure.badRequest(name + " must be between " + minimum + " and " + maximum);
            }
            return value;
        } catch (NumberFormatException failure) {
            throw ApiFailure.badRequest(name + " must be an integer");
        }
    }

    void rejectUnknown(Set<String> allowed) {
        for (String key : values.keySet()) {
            if (!allowed.contains(key)) throw ApiFailure.badRequest("unknown query parameter: " + key);
        }
    }

    private static String urlDecode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }
}
