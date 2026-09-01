package com.morpheus.api;

import com.morpheus.application.context.DisabledTechnicalContextProvider;
import com.morpheus.application.lifecycle.mutation.ChangeWriteCapabilityObservation;
import com.morpheus.application.reference.ExternalIntegrationStatus;
import com.morpheus.application.reference.ExternalReferenceResolverRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MorpheusRemoteHttpServerBootstrapContractTest {

    @TempDir
    Path tempDirectory;

    @Test
    void rejectsInvalidHostPortConcurrencyAndTlsPasswordBeforeStartupIo() {
        assertMessage("remote host must not be blank", () -> start(null, 0, "changeit".toCharArray(), 1));
        assertMessage("remote host must not be blank", () -> start("   ", 0, "changeit".toCharArray(), 1));
        assertMessage("port must be between 0 and 65535", () -> start("127.0.0.1", -1, "changeit".toCharArray(), 1));
        assertMessage("port must be between 0 and 65535", () -> start("127.0.0.1", 65_536, "changeit".toCharArray(), 1));
        assertMessage("maxConcurrentRequests must be between 1 and 512",
                () -> start("127.0.0.1", 0, "changeit".toCharArray(), 0));
        assertMessage("maxConcurrentRequests must be between 1 and 512",
                () -> start("127.0.0.1", 0, "changeit".toCharArray(), MorpheusRemoteHttpServer.MAX_CONCURRENT_REQUESTS + 1));
        assertMessage("remote TLS keystore password is required", () -> start("127.0.0.1", 0, null, 1));
        assertMessage("remote TLS keystore password is required", () -> start("127.0.0.1", 0, new char[0], 1));
    }

    private void start(String host, int port, char[] password, int maxConcurrentRequests) {
        MorpheusRemoteHttpServer.start(
                tempDirectory.resolve("morpheus.db"),
                tempDirectory.resolve("backups"),
                tempDirectory.resolve("provider-plugins"),
                AllowedWorkspaceRoots.of(List.of(tempDirectory)),
                host,
                port,
                tempDirectory.resolve("remote-auth.txt"),
                tempDirectory.resolve("remote.p12"),
                password,
                maxConcurrentRequests,
                new ExternalReferenceResolverRegistry(List.of()),
                () -> new ExternalIntegrationStatus("MINOS", "DISABLED", false, "test", Map.of()),
                new DisabledTechnicalContextProvider("NEXUS", "test"),
                project -> ChangeWriteCapabilityObservation.denied("test"));
    }

    private void assertMessage(String expected, org.junit.jupiter.api.function.Executable executable) {
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, executable);
        assertEquals(expected, failure.getMessage());
    }
}
