package com.morpheus.sdk.provider;

import com.morpheus.application.identity.EntityIdentityResolver;
import com.morpheus.application.provider.SpecificationProvider;
import com.morpheus.application.read.ProviderReadRequest;
import com.morpheus.application.read.ProviderReadResult;
import com.morpheus.application.read.SpecificationContentReader;
import com.morpheus.domain.provider.ProviderId;
import com.morpheus.domain.provider.ProviderProbeResult;
import com.morpheus.domain.provider.ProviderProbeStatus;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Test-only plugin that writes a malformed probe result itself and ends the worker with a clean exit.
 *
 * <p>The parent then decodes a result whose status names no {@link ProviderProbeStatus}, which fails with an
 * {@link IllegalArgumentException} after the SHA-256 pin was verified. The result file is the worker's last
 * argument, which a plugin reads from {@code sun.java.command}.</p>
 */
public final class TestMalformedResultProviderPlugin implements MorpheusProviderPlugin {
    private static final ProviderId PROVIDER_ID = new ProviderId("malformed-result-provider");

    @Override
    public ProviderPluginMetadata metadata() {
        return new ProviderPluginMetadata(
                "malformed-result-plugin",
                PROVIDER_ID,
                "1.0.0",
                ProviderSdk.API_VERSION,
                "1.0.0",
                Optional.empty());
    }

    @Override
    public SpecificationProvider createProvider() {
        return new SpecificationProvider() {
            @Override
            public ProviderId id() {
                return PROVIDER_ID;
            }

            @Override
            public String version() {
                return "1.0.0";
            }

            @Override
            public boolean remote() {
                return false;
            }

            @Override
            public ProviderProbeResult probe(Path workspaceRoot) {
                String[] command = System.getProperty("sun.java.command").split(" ");
                Path resultFile = Path.of(command[command.length - 1]);
                try {
                    Files.writeString(resultFile, String.join(System.lineSeparator(),
                            "provider.id=" + PROVIDER_ID.value(),
                            "provider.version=1.0.0",
                            "status=NOT_A_PROBE_STATUS",
                            "remote=false"));
                } catch (IOException failure) {
                    throw new UncheckedIOException(failure);
                }
                System.exit(0);
                throw new AssertionError("unreachable");
            }
        };
    }

    @Override
    public SpecificationContentReader createContentReader() {
        return new SpecificationContentReader() {
            @Override
            public ProviderId providerId() {
                return PROVIDER_ID;
            }

            @Override
            public ProviderReadResult read(ProviderReadRequest request, EntityIdentityResolver identityResolver) {
                throw new UnsupportedOperationException("malformed result fixture is never read");
            }
        };
    }
}
