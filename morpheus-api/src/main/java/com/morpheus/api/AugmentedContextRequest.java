package com.morpheus.api;

import java.util.List;
import java.util.Map;

/** Strict M13 HTTP request for live technical context augmentation. */
public record AugmentedContextRequest(
        String nexusProject,
        Integer tokenBudget,
        List<String> requestedSources,
        Map<String, String> constraints,
        Boolean explain) {
}
