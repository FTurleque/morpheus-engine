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

    /**
     * Parses a raw query string within the {@link HttpQueryBudget}.
     *
     * <p>The scan walks the raw text and bounds each slice before decoding it, rather than splitting the whole
     * query into an array first: the number of parameters, the length of a name and the length of a value are
     * all refused at the point where accepting them would allocate.</p>
     */
    static MorpheusHttpQuery parse(String rawQuery) {
        if (rawQuery == null || rawQuery.isBlank()) return new MorpheusHttpQuery(Map.of());
        HttpQueryBudget.requireBoundedQuery(rawQuery, ApiFailure::badRequest);
        Map<String, String> values = new LinkedHashMap<>();
        int start = 0;
        while (start <= rawQuery.length()) {
            int separator = rawQuery.indexOf('&', start);
            int end = separator < 0 ? rawQuery.length() : separator;
            addParameter(values, rawQuery.substring(start, end));
            start = end + 1;
        }
        return new MorpheusHttpQuery(values);
    }

    private static void addParameter(Map<String, String> values, String part) {
        if (part.isBlank()) return;
        HttpQueryBudget.requireBoundedParameterCount(values.size() + 1, ApiFailure::badRequest);
        int separator = part.indexOf('=');
        String rawKey = separator < 0 ? part : part.substring(0, separator);
        String rawValue = separator < 0 ? "" : part.substring(separator + 1);
        HttpQueryBudget.requireBoundedParameterName(rawKey, ApiFailure::badRequest);
        HttpQueryBudget.requireBoundedParameterValue(rawValue, ApiFailure::badRequest);
        String key = urlDecode(rawKey);
        String value = urlDecode(rawValue);
        if (key.isBlank()) throw ApiFailure.badRequest("query parameter name must not be blank");
        if (values.putIfAbsent(key, value) != null) {
            throw ApiFailure.badRequest("duplicate query parameter: " + key);
        }
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
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException malformed) {
            throw ApiFailure.badRequest("query parameter uses an invalid percent-encoding");
        }
    }
}
