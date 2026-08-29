package com.morpheus.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MorpheusLocalHttpServerBootstrapContractTest {

    @TempDir
    Path tempDirectory;

    @Test
    void rejectsPortsOutsideTheTcpRangeAndPreservesEphemeralLoopbackStartup() {
        Path database = tempDirectory.resolve("bootstrap.db");

        IllegalArgumentException negative = assertThrows(
                IllegalArgumentException.class,
                () -> MorpheusHttpServer.start(database, "127.0.0.1", -1));
        assertEquals("port must be between 0 and 65535", negative.getMessage());

        IllegalArgumentException tooHigh = assertThrows(
                IllegalArgumentException.class,
                () -> MorpheusHttpServer.start(database, "127.0.0.1", 65_536));
        assertEquals("port must be between 0 and 65535", tooHigh.getMessage());

        try (MorpheusHttpServer server = MorpheusHttpServer.start(database, "127.0.0.1", 0)) {
            assertEquals("127.0.0.1", server.host());
            assertTrue(server.port() > 0);
            assertTrue(server.baseUri().toString().startsWith("http://127.0.0.1:"));
            assertTrue(server.baseUri().toString().endsWith(MorpheusHttpServer.API_PREFIX));
        }
    }
}
