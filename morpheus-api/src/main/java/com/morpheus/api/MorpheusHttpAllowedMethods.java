package com.morpheus.api;

import java.util.List;
import java.util.Objects;

/**
 * Computes the local HTTP Allow header from {@link MorpheusHttpRouteTable} without owning route execution or failures.
 *
 * <p>A path no route matches allows no method, which RFC 9110 writes as an empty {@code Allow}.</p>
 */
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
            return "";
        }
        return MorpheusHttpRouteTable.allowHeader(segments);
    }
}
