package com.morpheus.api.transport;

import java.util.Objects;

/**
 * Computes the local HTTP Allow header from {@link MorpheusHttpRouteTable} without owning route execution or failures.
 *
 * <p>A path no route matches allows no method, which RFC 9110 writes as an empty {@code Allow}.</p>
 */
public final class MorpheusHttpAllowedMethods {
    private final MorpheusHttpPathParser pathParser;

    public MorpheusHttpAllowedMethods(MorpheusHttpPathParser pathParser) {
        this.pathParser = Objects.requireNonNull(pathParser, "pathParser");
    }

    public String forPath(String path) {
        return pathParser.segmentsIfRoutable(path)
                .map(segments -> MorpheusHttpRouteTable.allowHeader(segments))
                .orElse("");
    }
}
