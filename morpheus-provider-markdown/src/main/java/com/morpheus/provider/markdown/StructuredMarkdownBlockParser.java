package com.morpheus.provider.markdown;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

final class StructuredMarkdownBlockParser {

    List<Block> parse(String source) {
        Objects.requireNonNull(source, "source");
        List<String> lines = source.lines().toList();
        List<Block> blocks = new ArrayList<>();

        int index = 0;
        while (index < lines.size()) {
            String line = lines.get(index).trim();
            if (!line.startsWith("```morpheus ")) {
                index++;
                continue;
            }
            int startLine = index + 1;
            String type = line.substring("```morpheus ".length()).trim().toLowerCase(Locale.ROOT);
            if (type.isEmpty()) {
                throw new IllegalArgumentException("blank morpheus block type at line " + startLine);
            }
            index++;
            Map<String, String> values = new LinkedHashMap<>();
            StringBuilder raw = new StringBuilder(line).append('\n');
            boolean closed = false;
            while (index < lines.size()) {
                String rawLine = lines.get(index);
                raw.append(rawLine).append('\n');
                if (rawLine.trim().equals("```")) {
                    closed = true;
                    break;
                }
                if (!rawLine.isBlank() && !rawLine.stripLeading().startsWith("#")) {
                    int equals = rawLine.indexOf('=');
                    if (equals <= 0) {
                        throw new IllegalArgumentException(
                                "expected key=value in morpheus block at line " + (index + 1));
                    }
                    String key = rawLine.substring(0, equals).trim().toLowerCase(Locale.ROOT);
                    String value = rawLine.substring(equals + 1).trim();
                    if (key.isEmpty() || value.isEmpty()) {
                        throw new IllegalArgumentException("blank key/value at line " + (index + 1));
                    }
                    if (values.putIfAbsent(key, value) != null) {
                        throw new IllegalArgumentException("duplicate key '" + key + "' at line " + (index + 1));
                    }
                }
                index++;
            }
            if (!closed) {
                throw new IllegalArgumentException("unclosed morpheus block starting at line " + startLine);
            }
            blocks.add(new Block(type, values, startLine, index + 1, raw.toString()));
            index++;
        }
        if (blocks.isEmpty()) {
            throw new IllegalArgumentException("no fenced ```morpheus blocks found");
        }
        return List.copyOf(blocks);
    }

    record Block(String type, Map<String, String> values, int startLine, int endLine, String raw) {
        Block {
            type = Objects.requireNonNull(type, "type");
            values = Map.copyOf(Objects.requireNonNull(values, "values"));
            raw = Objects.requireNonNull(raw, "raw");
        }

        String required(String key) {
            String value = values.get(key);
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("missing '" + key + "' in " + type + " block at line " + startLine);
            }
            return value.trim();
        }

        String optional(String key, String fallback) {
            String value = values.get(key);
            return value == null || value.isBlank() ? fallback : value.trim();
        }

        List<String> list(String key) {
            String value = values.get(key);
            if (value == null || value.isBlank()) {
                return List.of();
            }
            return java.util.Arrays.stream(value.split(";"))
                    .map(String::trim)
                    .filter(item -> !item.isEmpty())
                    .toList();
        }
    }
}
