package com.morpheus.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LoopbackHostPolicyTest {

    @Test
    void acceptsIpv4AndIpv6Loopback() {
        assertEquals("127.0.0.1", LoopbackHostPolicy.requireLoopback("127.0.0.1"));
        assertEquals("::1", LoopbackHostPolicy.requireLoopback("::1"));
    }

    @Test
    void rejectsWildcardAndKnownNonLoopbackAddress() {
        IllegalArgumentException wildcardFailure =
                assertThrows(IllegalArgumentException.class, () -> LoopbackHostPolicy.requireLoopback("0.0.0.0"));
        assertEquals("non-loopback API bind requires explicit remote mode with TLS and authentication",
                wildcardFailure.getMessage());
        IllegalArgumentException lanFailure =
                assertThrows(IllegalArgumentException.class, () -> LoopbackHostPolicy.requireLoopback("192.0.2.1"));
        assertEquals("non-loopback API bind requires explicit remote mode with TLS and authentication",
                lanFailure.getMessage());
    }

    @Test
    void rejectsBlankOrUnknownHost() {
        IllegalArgumentException blankFailure =
                assertThrows(IllegalArgumentException.class, () -> LoopbackHostPolicy.requireLoopback(" "));
        assertEquals("local API host must not be blank", blankFailure.getMessage());
        IllegalArgumentException unknownFailure = assertThrows(IllegalArgumentException.class,
                () -> LoopbackHostPolicy.requireLoopback("definitely-not-a-morpheus-host.invalid"));
        assertEquals("cannot resolve local API host", unknownFailure.getMessage());
    }

    @Test
    void validatesEveryAddressReturnedByTheResolver() throws Exception {
        InetAddress ipv4Loopback = InetAddress.getByName("127.0.0.1");
        InetAddress ipv6Loopback = InetAddress.getByName("::1");
        InetAddress lan = InetAddress.getByName("192.0.2.1");

        assertEquals("all-loopback", LoopbackHostPolicy.requireLoopback(
                "all-loopback",
                ignored -> new InetAddress[]{ipv4Loopback, ipv6Loopback}));
        IllegalArgumentException mixedFailure = assertThrows(IllegalArgumentException.class,
                () -> LoopbackHostPolicy.requireLoopback(
                        "mixed",
                        ignored -> new InetAddress[]{ipv4Loopback, lan}));
        assertEquals("non-loopback API bind requires explicit remote mode with TLS and authentication",
                mixedFailure.getMessage());
    }

    @Test
    void directServerCallerCannotBindWildcard(@TempDir Path workspace) {
        Path database = workspace.resolve("morpheus.db");

        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> MorpheusHttpServer.start(database, "0.0.0.0", 0));
        assertEquals("non-loopback API bind requires explicit remote mode with TLS and authentication",
                failure.getMessage());
        assertFalse(Files.exists(database));
    }
}
