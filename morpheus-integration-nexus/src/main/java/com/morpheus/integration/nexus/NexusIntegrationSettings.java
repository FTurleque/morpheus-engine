package com.morpheus.integration.nexus;

import com.morpheus.application.security.PeerLaunchSettings;
import com.morpheus.application.security.PeerLaunchSettingsResolver;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

/**
 * The launch settings of the NEXUS peer, resolved by {@link PeerLaunchSettingsResolver} like those of every peer. This
 * type contributes the peer's name and the names of its settings; a configuration problem is never fatal to MORPHEUS
 * bootstrap.
 */
public record NexusIntegrationSettings(
        Optional<Path> jarPath,
        String javaCommand,
        Optional<Path> homeDirectory,
        Duration timeout,
        Optional<String> jarSha256,
        Optional<String> configurationError) {

    public static final String JAR_PROPERTY = "morpheus.nexus.jar";
    public static final String JAR_SHA256_PROPERTY = "morpheus.nexus.jar.sha256";
    public static final String JAVA_PROPERTY = "morpheus.nexus.java";
    public static final String HOME_PROPERTY = "morpheus.nexus.home";
    public static final String TIMEOUT_PROPERTY = "morpheus.nexus.timeoutSeconds";

    public static final String JAR_ENV = "MORPHEUS_NEXUS_JAR";
    public static final String JAR_SHA256_ENV = "MORPHEUS_NEXUS_JAR_SHA256";
    public static final String JAVA_ENV = "MORPHEUS_NEXUS_JAVA";
    public static final String HOME_ENV = "MORPHEUS_NEXUS_HOME";
    public static final String TIMEOUT_ENV = "MORPHEUS_NEXUS_TIMEOUT_SECONDS";

    public static final int DEFAULT_TIMEOUT_SECONDS = PeerLaunchSettings.DEFAULT_TIMEOUT_SECONDS;
    public static final int MAX_TIMEOUT_SECONDS = PeerLaunchSettings.MAX_TIMEOUT_SECONDS;

    private static final PeerLaunchSettingsResolver.Peer PEER =
            PeerLaunchSettingsResolver.Peer.named("NEXUS");

    public NexusIntegrationSettings {
        PeerLaunchSettings launch = new PeerLaunchSettings(
                jarPath, javaCommand, homeDirectory, timeout, jarSha256, configurationError);
        jarPath = launch.jarPath();
        javaCommand = launch.javaCommand();
        homeDirectory = launch.homeDirectory();
        timeout = launch.timeout();
        jarSha256 = launch.jarSha256();
        configurationError = launch.configurationError();
    }

    public static NexusIntegrationSettings resolve(Map<String, String> environment, Properties properties) {
        PeerLaunchSettings launch = PeerLaunchSettingsResolver.resolve(PEER, environment, properties);
        return new NexusIntegrationSettings(launch.jarPath(), launch.javaCommand(), launch.homeDirectory(),
                launch.timeout(), launch.jarSha256(), launch.configurationError());
    }

    public State state() {
        return switch (launch().state()) {
            case DISABLED -> State.DISABLED;
            case CONFIGURED -> State.CONFIGURED;
            case INVALID -> State.INVALID;
        };
    }

    /** External code execution is enabled only when the configured JAR is integrity-pinned. */
    public boolean enabled() {
        return launch().enabled();
    }

    private PeerLaunchSettings launch() {
        return new PeerLaunchSettings(jarPath, javaCommand, homeDirectory, timeout, jarSha256, configurationError);
    }

    public enum State {
        DISABLED,
        CONFIGURED,
        INVALID
    }
}
