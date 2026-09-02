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
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.locks.LockSupport;

/**
 * Test-only plugin that spawns its persistent descendant as the very last action of a successful probe.
 *
 * <p>The settling delay before the spawn lets the MORPHEUS parent complete several observation polls while no
 * descendant exists, so the descendant only appears in the final instant before the worker JVM exits. That is the
 * real-world shape of the lifecycle race: cleanup must not depend on the parent having sampled the process tree at
 * the right moment.
 */
public final class TestLateDescendantProviderPlugin implements MorpheusProviderPlugin {
    static final String CHILD_PID_FILE = "late-descendant-child.pid";
    private static final Duration SETTLE_BEFORE_SPAWN = Duration.ofMillis(150);
    private static final ProviderId PROVIDER_ID = new ProviderId("late-descendant-provider");

    @Override
    public ProviderPluginMetadata metadata() {
        return new ProviderPluginMetadata(
                "late-descendant-plugin",
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
                LockSupport.parkNanos(SETTLE_BEFORE_SPAWN.toNanos());
                startPersistentChild(workspaceRoot);
                return new ProviderProbeResult(
                        PROVIDER_ID,
                        version(),
                        ProviderProbeStatus.SUPPORTED,
                        Optional.of("fixture"),
                        Optional.of("1"),
                        Optional.of(SourceLocator.file("late-descendant/source")),
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
                throw new UnsupportedOperationException("late descendant probe fixture is never read");
            }
        };
    }

    private void startPersistentChild(Path workspaceRoot) {
        boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
        Path java = Path.of(
                System.getProperty("java.home"),
                "bin",
                windows ? "java.exe" : "java").toAbsolutePath().normalize();
        try {
            Process child = new ProcessBuilder(
                    java.toString(),
                    "-cp",
                    System.getProperty("java.class.path"),
                    PersistentChild.class.getName())
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .start();
            try {
                // Published without waiting on the child: any wait here would hand the MORPHEUS parent the
                // observation window this fixture exists to deny it.
                Files.writeString(workspaceRoot.resolve(CHILD_PID_FILE), Long.toString(child.pid()));
            } catch (IOException | RuntimeException failure) {
                child.destroyForcibly();
                throw new IllegalStateException("cannot publish late descendant fixture PID", failure);
            }
        } catch (IOException failure) {
            throw new IllegalStateException("cannot start late descendant fixture", failure);
        }
    }

    /** Separate JVM entry point retained on the test classpath; it never exits on its own. */
    public static final class PersistentChild {
        private PersistentChild() {
        }

        public static void main(String[] args) {
            while (true) {
                LockSupport.parkNanos(100_000_000L);
                Thread.interrupted();
            }
        }
    }
}
