package com.morpheus.api;

import com.morpheus.application.operability.OperationalRecorder;
import com.morpheus.sdk.provider.ProviderPluginProbeOutcome;
import com.morpheus.sdk.provider.ProviderPluginService;
import com.morpheus.sdk.provider.ProviderPluginViews;

import java.nio.file.Path;

/** Provider-plugin API adapter. Executable probing always requires a trusted SHA-256 pin. */
public final class MorpheusProviderPluginApiService {
    private final ProviderPluginService service;

    public MorpheusProviderPluginApiService(OperationalRecorder recorder) {
        this.service = new ProviderPluginService(recorder);
    }

    public ProviderPluginViews.RemoteDiscoveryView discover(String directory) {
        // The HTTP surface is relayed to remote callers verbatim, so it must not carry server filesystem paths.
        return ProviderPluginViews.remoteDiscovery(service.discover(Path.of(directory)));
    }

    public ProviderPluginViews.RemoteProbeView probe(
            String directory, String pluginId, String workspace, String expectedSha256) {
        return ProviderPluginViews.remoteProbe(
                service.probe(Path.of(directory), pluginId, Path.of(workspace), expectedSha256));
    }
}
