package com.morpheus.api;

import com.morpheus.sdk.provider.ProviderPluginProbeOutcome;
import com.morpheus.sdk.provider.ProviderPluginService;
import com.morpheus.sdk.provider.ProviderPluginViews;

import java.nio.file.Path;

/** Read-only adapter service for explicit M22 provider-plugin discovery and probe routes. */
final class MorpheusProviderPluginApiService {
    private final ProviderPluginService service = new ProviderPluginService();

    ProviderPluginViews.DiscoveryView discover(String directory) {
        return ProviderPluginViews.discovery(service.discover(Path.of(directory)));
    }

    ProviderPluginProbeOutcome probe(String directory, String pluginId, String workspace) {
        return service.probe(Path.of(directory), pluginId, Path.of(workspace));
    }
}
