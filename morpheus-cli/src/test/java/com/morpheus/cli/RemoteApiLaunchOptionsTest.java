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

        assertThrows(IllegalArgumentException.class, () -> ApiLaunchOptions.parse(
                new String[]{"--data-dir", temp.toString(), "api", "--host", "0.0.0.0"},
                Map.of(), properties));
    }

    @Test
    void remoteModeRequiresExplicitFlagTlsMaterialAndProtectedPasswordSource() {
        Path keyStore = temp.resolve("server.p12");
        Path auth = temp.resolve("auth.txt");
        Path pluginDirectory = temp.resolve("trusted-provider-plugins");
        Properties properties = properties();

        assertFalse(RemoteApiLaunchOptions.isRemoteApiCommand(new String[]{"api"}));
        assertTrue(RemoteApiLaunchOptions.isRemoteApiCommand(new String[]{"api", "--remote"}));

        assertThrows(IllegalArgumentException.class, () -> RemoteApiLaunchOptions.parse(
                new String[]{"--data-dir", temp.toString(), "api", "--remote", "--tls-keystore", keyStore.toString()},
                Map.of(), properties));

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
        assertEquals("test-password", new String(remote.tlsPasswordChars()));
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
    void remoteModeFailsClosedWithoutServerConfiguredWorkspaceRoot() {
        assertThrows(IllegalArgumentException.class, () -> RemoteApiLaunchOptions.parse(
                new String[]{
                        "--data-dir", temp.toString(), "api", "--remote",
                        "--tls-keystore", temp.resolve("server.p12").toString()
                },
                Map.of("MORPHEUS_SERVER_TLS_PASSWORD", "protected-source"),
                properties()));
    }

    @Test
    void tlsPasswordIsNotAcceptedAsCommandLineArgument() {
        assertThrows(IllegalArgumentException.class, () -> RemoteApiLaunchOptions.parse(
                new String[]{
                        "--data-dir", temp.toString(), "api", "--remote",
                        "--tls-keystore", temp.resolve("server.p12").toString(),
                        "--tls-password", "do-not-allow-this"
                },
                Map.of("MORPHEUS_SERVER_TLS_PASSWORD", "protected-source"),
                properties()));
    }

    private Properties properties() {
        Properties properties = new Properties();
        properties.setProperty("user.home", temp.toString());
        properties.setProperty("os.name", System.getProperty("os.name", "Linux"));
        return properties;
    }
}
