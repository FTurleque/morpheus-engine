package com.morpheus.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LoopbackHostPolicyTest {

    @Test
    void acceptsIpv4AndIpv6Loopback() {
        assertEquals("127.0.0.1", LoopbackHostPolicy.requireLoopback("127.0.0.1"));
        assertEquals("::1", LoopbackHostPolicy.requireLoopback("::1"));
    }

    @Test
    void rejectsWildcardAndKnownNonLoopbackAddress() {
        assertThrows(IllegalArgumentException.class, () -> LoopbackHostPolicy.requireLoopback("0.0.0.0"));
        assertThrows(IllegalArgumentException.class, () -> LoopbackHostPolicy.requireLoopback("192.0.2.1"));
    }

    @Test
    void rejectsBlankOrUnknownHost() {
        assertThrows(IllegalArgumentException.class, () -> LoopbackHostPolicy.requireLoopback(" "));
        assertThrows(IllegalArgumentException.class,
                () -> LoopbackHostPolicy.requireLoopback("definitely-not-a-morpheus-host.invalid"));
    }
}
