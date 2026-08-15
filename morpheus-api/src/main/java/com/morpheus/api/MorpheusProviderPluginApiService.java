package com.morpheus.api;

import com.morpheus.sdk.provider.ProviderPluginProbeOutcome;
import com.morpheus.sdk.provider.ProviderPluginService;
import com.morpheus.sdk.provider.ProviderPluginViews;

import java.nio.file.Path;

/** Provider-plugin API adapter. Executable probing always requires a trusted SHA-256 pin. */
final class MorpheusProviderPluginApiService {
    private final ProviderPluginService service = new ProviderPluginService();

    ProviderPluginViews.DiscoveryView discover(String directory) {
        return ProviderPluginViews.discovery(service.discover(Path.of(directory)));
    }

    ProviderPluginProbeOutcome probe(String directory, String pluginId, String workspace, String expectedSha256) {
        return service.probe(Path.of(directory), pluginId, Path.of(workspace), expectedSha256);
    }
}
