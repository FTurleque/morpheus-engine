package com.morpheus.api;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Local provider-plugin route group. Executable probing remains remote-only and SHA-256 pinned. */
final class MorpheusProviderPluginHttpRoutes {
    private final boolean probeEnabled;

    MorpheusProviderPluginHttpRoutes(boolean probeEnabled) {
        this.probeEnabled = probeEnabled;
    }

    MorpheusHttpRouteResponse route(String method, List<String> segments, MorpheusHttpQuery query) {
        Objects.requireNonNull(method, "method");
        Objects.requireNonNull(segments, "segments");
        Objects.requireNonNull(query, "query");

        MorpheusProviderPluginApiService plugins = new MorpheusProviderPluginApiService();
        return switch (segments.get(1)) {
            case "discover" -> {
                MorpheusHttpRouteGuards.requireMethod(method, "GET");
                query.rejectUnknown(Set.of("directory"));
                yield ok(plugins.discover(query.required("directory")));
            }
            case "probe" -> {
                if (!probeEnabled) {
                    throw ApiFailure.notFound("provider-plugin probe is remote-only");
                }
                MorpheusHttpRouteGuards.requireMethod(method, "POST");
                query.rejectUnknown(Set.of("directory", "pluginId", "workspace", "sha256"));
                String directory = query.required("directory");
                String pluginId = query.required("pluginId");
                String workspace = query.required("workspace");
                String sha256 = query.required("sha256");
                yield ok(plugins.probe(directory, pluginId, workspace, sha256));
            }
            default -> throw ApiFailure.notFound("unknown provider-plugin route");
        };
    }

    private MorpheusHttpRouteResponse ok(Object data) {
        return new MorpheusHttpRouteResponse(200, data);
    }
}
