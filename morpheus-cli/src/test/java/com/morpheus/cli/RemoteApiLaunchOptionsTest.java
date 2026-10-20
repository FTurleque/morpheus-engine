package com.morpheus.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RemoteApiLaunchOptionsTest {
    @TempDir
    Path temp;

    @Test
    void localApiRemainsLoopbackOnly() {
        Properties properties = properties();
        ApiLaunchOptions local = ApiLaunchOptions.parse(
                new String[]{"--data-dir", temp.toString(), "api", "--host", "127.0.0.1", "--port", "8765"},
                Map.of(), properties);
        assertEquals("127.0.0.1", local.host());
        assertEquals(8765, local.port());

        IllegalArgumentException nonLoopback = assertThrows(IllegalArgumentException.class, () -> ApiLaunchOptions.parse(
                new String[]{"--data-dir", temp.toString(), "api", "--host", "0.0.0.0"},
                Map.of(), properties));
        assertEquals("non-loopback API bind requires explicit remote mode with TLS and authentication",
                nonLoopback.getMessage());
    }

    @Test
    void remoteModeRequiresExplicitFlagTlsMaterialAndProtectedPasswordSource() {
        Path keyStore = temp.resolve("server.p12");
        Path auth = temp.resolve("auth.txt");
        Path pluginDirectory = temp.resolve("trusted-provider-plugins");
        Properties properties = properties();

        assertFalse(RemoteApiLaunchOptions.isRemoteApiCommand(new String[]{"api"}));
        assertTrue(RemoteApiLaunchOptions.isRemoteApiCommand(new String[]{"api", "--remote"}));

        IllegalArgumentException withoutPassword = assertThrows(IllegalArgumentException.class, () -> RemoteApiLaunchOptions.parse(
                new String[]{
                        "--data-dir", temp.toString(), "api", "--remote", "--tls-keystore", keyStore.toString(),
                        "--workspace-root", temp.toString()
                },
                Map.of(), properties));
        assertEquals(TlsKeystorePassword.MISSING, withoutPassword.getMessage());

        Map<String, String> environment = new HashMap<>();
        environment.put("MORPHEUS_SERVER_TLS_PASSWORD", "test-password");
        RemoteApiLaunchOptions remote = RemoteApiLaunchOptions.parse(
                new String[]{
                        "--data-dir", temp.toString(), "api", "--remote",
                        "--host", "0.0.0.0", "--port", "9443",
                        "--tls-keystore", keyStore.toString(),
                        "--auth-file", auth.toString(),
                        "--provider-plugin-dir", pluginDirectory.toString(),
                        "--workspace-root", temp.toString(),
                        "--max-concurrent", "7"
                },
                environment,
                properties);

        assertEquals("0.0.0.0", remote.host());
        assertEquals(9443, remote.port());
        assertEquals(7, remote.maxConcurrentRequests());
        assertEquals(auth.toAbsolutePath().normalize(), remote.authFile());
        assertEquals(keyStore.toAbsolutePath().normalize(), remote.tlsKeyStore());
        assertEquals(pluginDirectory.toAbsolutePath().normalize(), remote.providerPluginDirectory());
        assertEquals(List.of(temp.toAbsolutePath().normalize()), remote.allowedWorkspaceRoots());
        assertEquals("test-password", new String(remote.tlsPassword().resolve()));
    }

    /**
     * The parsed options carry the way back to the password, never the password.
     *
     * <p>They live as long as the running server does, and the record's generated {@code toString()} renders
     * every component, so a {@code String} field here was a copy of the secret with the lifetime of the process
     * and a rendering path into any diagnostic that printed the options.</p>
     */
    @Test
    void parsedOptionsNeitherRetainNorRenderTheTlsPassword() {
        Path keyStore = temp.resolve("server.p12");
        RemoteApiLaunchOptions remote = RemoteApiLaunchOptions.parse(
                new String[]{"--data-dir", temp.toString(), "api", "--remote", "--tls-keystore", keyStore.toString()},
                Map.of(
                        "MORPHEUS_SERVER_TLS_PASSWORD", "must-not-be-rendered",
                        "MORPHEUS_SERVER_WORKSPACE_ROOTS", temp.toString()),
                properties());

        assertFalse(remote.toString().contains("must-not-be-rendered"),
                () -> "the launch options must never render the TLS password: " + remote.toString());
        assertFalse(remote.tlsPassword().toString().contains("must-not-be-rendered"),
                "the password handle must never render its value");
        assertTrue(remote.tlsPassword().toString().contains("redacted"));

        char[] first = remote.tlsPassword().resolve();
        assertEquals("must-not-be-rendered", new String(first));

        // Wiping the resolved buffer must not damage a later resolution: the buffer is the caller's copy, and
        // the whole point of resolving late is that nothing is cached between calls.
        java.util.Arrays.fill(first, '\0');
        assertEquals("must-not-be-rendered", new String(remote.tlsPassword().resolve()));
    }

    /** A launch missing the password fails while parsing, before anything has been started. */
    @Test
    void aMissingTlsPasswordIsRefusedWhileParsing() {
        String[] arguments = {
                "--data-dir", temp.toString(), "api", "--remote",
                "--tls-keystore", temp.resolve("server.p12").toString(),
                "--workspace-root", temp.toString()
        };
        Properties properties = properties();

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> RemoteApiLaunchOptions.parse(arguments, Map.of(), properties));

        assertTrue(failure.getMessage().contains("TLS keystore password"));
    }

    /**
     * A JVM property is a command-line argument too: {@code -Dmorpheus.server.tls.password=...} lands in
     * {@code /proc/<pid>/cmdline}, which every account of a default Linux host can read, where
     * {@code /proc/<pid>/environ} is readable by the owner alone. The property used to be accepted as a fallback;
     * restoring that fallback makes this test fail on the absence of the expected refusal (observed 2026-09-22).
     */
    @Test
    void aTlsPasswordGivenOnlyAsAJvmPropertyIsRefused() {
        String[] arguments = {
                "--data-dir", temp.toString(), "api", "--remote",
                "--tls-keystore", temp.resolve("server.p12").toString(),
                "--workspace-root", temp.toString()
        };
        Properties properties = properties();
        properties.setProperty("morpheus.server.tls.password", "visible-to-every-account");

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> RemoteApiLaunchOptions.parse(arguments, Map.of(), properties));

        assertTrue(failure.getMessage().contains("MORPHEUS_SERVER_TLS_PASSWORD environment variable"),
                failure.getMessage());
    }

    @Test
    void remotePluginDirectoryDefaultsToServerConfigDirectory() {
        Path keyStore = temp.resolve("server.p12");
        RemoteApiLaunchOptions remote = RemoteApiLaunchOptions.parse(
                new String[]{"--data-dir", temp.toString(), "api", "--remote", "--tls-keystore", keyStore.toString()},
                Map.of(
                        "MORPHEUS_SERVER_TLS_PASSWORD", "protected-source",
                        "MORPHEUS_SERVER_WORKSPACE_ROOTS", temp.toString()),
                properties());

        assertEquals(
                remote.layout().configDirectory().resolve("provider-plugins").toAbsolutePath().normalize(),
                remote.providerPluginDirectory());
    }

    @Test
    void remotePluginDirectoryIsTakenFromTheOptionThenTheEnvironmentThenTheJvmProperty() {
        Path keyStore = temp.resolve("server.p12");
        Path fromOption = temp.resolve("plugins-from-option");
        Path fromEnvironment = temp.resolve("plugins-from-environment");
        Path fromProperty = temp.resolve("plugins-from-property");
        String[] base = {"--data-dir", temp.toString(), "api", "--remote", "--tls-keystore", keyStore.toString()};
        String[] withOption = {
                "--data-dir", temp.toString(), "api", "--remote", "--tls-keystore", keyStore.toString(),
                "--provider-plugin-dir", fromOption.toString()};
        Map<String, String> environment = new HashMap<>();
        environment.put("MORPHEUS_SERVER_TLS_PASSWORD", "protected-source");
        environment.put("MORPHEUS_SERVER_WORKSPACE_ROOTS", temp.toString());
        Properties withProperty = properties();
        withProperty.setProperty("morpheus.server.providerPluginDirectory", fromProperty.toString());

        assertEquals(fromProperty.toAbsolutePath().normalize(),
                RemoteApiLaunchOptions.parse(base, environment, withProperty).providerPluginDirectory(),
                "the JVM property is consulted before the default");

        environment.put("MORPHEUS_SERVER_PROVIDER_PLUGIN_DIR", fromEnvironment.toString());
        assertEquals(fromEnvironment.toAbsolutePath().normalize(),
                RemoteApiLaunchOptions.parse(base, environment, withProperty).providerPluginDirectory(),
                "the environment variable wins over the JVM property");
        assertEquals(fromOption.toAbsolutePath().normalize(),
                RemoteApiLaunchOptions.parse(withOption, environment, withProperty).providerPluginDirectory(),
                "the option wins over the environment variable");
    }

    @Test
    void remoteModeFailsClosedWithoutServerConfiguredWorkspaceRoot() {
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, () -> RemoteApiLaunchOptions.parse(
                new String[]{
                        "--data-dir", temp.toString(), "api", "--remote",
                        "--tls-keystore", temp.resolve("server.p12").toString()
                },
                Map.of("MORPHEUS_SERVER_TLS_PASSWORD", "protected-source"),
                properties()));
        assertEquals("remote mode requires at least one --workspace-root or MORPHEUS_SERVER_WORKSPACE_ROOTS",
                failure.getMessage());
    }

    @Test
    void tlsPasswordIsNotAcceptedAsCommandLineArgument() {
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, () -> RemoteApiLaunchOptions.parse(
                new String[]{
                        "--data-dir", temp.toString(), "api", "--remote",
                        "--tls-keystore", temp.resolve("server.p12").toString(),
                        "--tls-password", "do-not-allow-this"
                },
                Map.of("MORPHEUS_SERVER_TLS_PASSWORD", "protected-source"),
                properties()));
        assertTrue(failure.getMessage().startsWith("unknown remote API arguments: [--tls-password"),
                () -> "the password flag must be refused as an unknown argument: " + failure.getMessage());
    }

    private Properties properties() {
        Properties properties = new Properties();
        properties.setProperty("user.home", temp.toString());
        properties.setProperty("os.name", System.getProperty("os.name", "Linux"));
        return properties;
    }
}
