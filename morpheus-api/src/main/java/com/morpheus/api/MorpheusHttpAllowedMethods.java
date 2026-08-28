package com.morpheus.api;

import java.util.List;
import java.util.Objects;

/** Computes the stable local HTTP Allow header without owning route execution or failures. */
final class MorpheusHttpAllowedMethods {
    private final MorpheusHttpPathParser pathParser;

    MorpheusHttpAllowedMethods(MorpheusHttpPathParser pathParser) {
        this.pathParser = Objects.requireNonNull(pathParser, "pathParser");
    }

    String forPath(String path) {
        List<String> segments;
        try {
            segments = pathParser.segments(path);
        } catch (RuntimeException ignored) {
            return "GET";
        }
        if (segments.isEmpty()) return "GET";
        if (segments.size() == 2 && segments.getFirst().equals("provider-plugins")) {
            return switch (segments.get(1)) {
                case "discover" -> "GET";
                case "probe" -> "POST";
                default -> "GET";
            };
        }
        if (segments.size() == 1 && (segments.getFirst().equals("projects") || segments.getFirst().equals("portfolios"))) {
            return "GET, POST";
        }
        if (segments.getFirst().equals("portfolios")) {
            if (segments.size() == 3 && (segments.get(2).equals("projects") || segments.get(2).equals("references"))) {
                return "GET, POST";
            }
            if (segments.size() == 3 && segments.get(2).equals("traverse")) return "POST";
            if (segments.size() == 5 && segments.get(2).equals("projects")
                    && (segments.get(4).equals("missing") || segments.get(4).equals("freshness"))) return "POST";
        }
        if (segments.size() == 3 && segments.getFirst().equals("projects") && segments.get(2).equals("sync")) return "POST";
        if (segments.size() == 5 && segments.getFirst().equals("projects")
                && (segments.get(2).equals("requirements") || segments.get(2).equals("changes"))
                && (segments.get(4).equals("augmented-context")
                    || segments.get(4).equals("transition-check")
                    || segments.get(4).equals("lifecycle-transitions"))) return "POST";
        return "GET";
    }
}
