package com.morpheus.sdk.provider;

import com.morpheus.application.identity.EntityIdentityResolver;
import com.morpheus.application.provider.SpecificationProvider;
import com.morpheus.application.read.ProviderReadRequest;
import com.morpheus.application.read.ProviderReadResult;
import com.morpheus.application.read.SpecificationContentReader;
import com.morpheus.domain.provider.ProviderCapabilitySet;
import com.morpheus.domain.provider.ProviderId;
import com.morpheus.domain.provider.ProviderProbeResult;
import com.morpheus.domain.provider.ProviderProbeStatus;
import com.morpheus.domain.source.SourceLocator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.locks.LockSupport;

/** Test-only plugin that returns successfully after intentionally leaving a descendant process alive. */
public final class TestSuccessfulDescendantProviderPlugin implements MorpheusProviderPlugin {
    static final String CHILD_PID_FILE = "successful-probe-child.pid";
    private static final ProviderId PROVIDER_ID = new ProviderId("successful-descendant-provider");

    @Override
    public ProviderPluginMetadata metadata() {
        return new ProviderPluginMetadata(
                "successful-descendant-plugin",
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
                startBlockingChild(workspaceRoot);
                return new ProviderProbeResult(
                        PROVIDER_ID,
                        version(),
                        ProviderProbeStatus.SUPPORTED,
                        Optional.of("fixture"),
                        Optional.of("1"),
                        Optional.of(SourceLocator.file("successful-descendant/source")),
                        ProviderCapabilitySet.of(),
                        false,
                        List.of());
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
                throw new UnsupportedOperationException("successful descendant probe fixture is never read");
            }
        };
    }

    private void startBlockingChild(Path workspaceRoot) {
        boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
        Path java = Path.of(
                System.getProperty("java.home"),
                "bin",
                windows ? "java.exe" : "java").toAbsolutePath().normalize();
        String classPath = System.getProperty("java.class.path");
        try {
            Process child = new ProcessBuilder(
                    java.toString(),
                    "-cp",
                    classPath,
                    BlockingChild.class.getName())
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .start();
            try {
                Files.writeString(workspaceRoot.resolve(CHILD_PID_FILE), Long.toString(child.pid()));
            } catch (IOException failure) {
                child.destroyForcibly();
                throw failure;
            }
        } catch (IOException failure) {
            throw new IllegalStateException("cannot start successful probe descendant fixture", failure);
        }
    }

    /** Separate JVM entry point retained on the test classpath used by the isolated probe worker. */
    public static final class BlockingChild {
        private BlockingChild() {
        }

        public static void main(String[] args) {
            while (true) {
                LockSupport.parkNanos(100_000_000L);
                Thread.interrupted();
            }
        }
    }
}
