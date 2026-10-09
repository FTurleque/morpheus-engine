package com.morpheus.api;

import com.morpheus.application.reference.ExternalIntegrationStatus;
import com.morpheus.application.security.IntegrationStatusDisclosure;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Shapes an optional-integration status for {@code GET /api/v1/integrations/{system}/status}.
 *
 * <p>What the status may say about the server is decided once, by {@link IntegrationStatusDisclosure}, which the
 * augmented-context routes and MCP tools also go through; this class only chooses the response keys.</p>
 */
final class IntegrationStatusViews {
    private IntegrationStatusViews() {
    }

    static Map<String, Object> status(ExternalIntegrationStatus status) {
        ExternalIntegrationStatus projected = IntegrationStatusDisclosure.project(Objects.requireNonNull(status, "status"));
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("system", projected.system());
        view.put("state", projected.state());
        view.put("configured", projected.configured());
        view.put("message", projected.message());
        view.put("details", projected.details());
        return Map.copyOf(view);
    }
}
