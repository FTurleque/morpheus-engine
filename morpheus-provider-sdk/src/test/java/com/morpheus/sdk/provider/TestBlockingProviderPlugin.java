package com.morpheus.sdk.provider;

import com.morpheus.application.identity.EntityIdentityResolver;
import com.morpheus.application.provider.SpecificationProvider;
import com.morpheus.application.read.ProviderReadRequest;
import com.morpheus.application.read.ProviderReadResult;
import com.morpheus.application.read.SpecificationContentReader;
import com.morpheus.domain.provider.ProviderId;
import com.morpheus.domain.provider.ProviderProbeResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.locks.LockSupport;

/** Test-only plugin whose probe spawns a descendant process, never returns and ignores interruption. */
public final class TestBlockingProviderPlugin implements MorpheusProviderPlugin {
    static final String CHILD_PID_FILE = "blocking-probe-child.pid";
    private static final ProviderId PROVIDER_ID = new ProviderId("blocking-provider");

    @Override
    public ProviderPluginMetadata metadata() {
        return new ProviderPluginMetadata(
                "blocking-plugin",
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
                Process child = startBlockingChild(workspaceRoot);
                try {
                    while (true) {
                        LockSupport.parkNanos(100_000_000L);
                        Thread.interrupted();
                    }
                } finally {
                    if (child.isAlive()) child.destroyForcibly();
                }
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
                throw new UnsupportedOperationException("blocking probe fixture is never read");
            }
        };
    }

    private Process startBlockingChild(Path workspaceRoot) {
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
            return child;
        } catch (IOException failure) {
            throw new IllegalStateException("cannot start blocking probe descendant fixture", failure);
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
