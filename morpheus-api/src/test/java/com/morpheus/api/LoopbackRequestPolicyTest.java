package com.morpheus.api;

import com.sun.net.httpserver.Headers;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LoopbackRequestPolicyTest {

    @Test
    void acceptsExplicitLoopbackAuthoritiesAndOrigins() {
        for (String host : new String[]{"127.0.0.1:8765", "127.42.0.9", "localhost:8765", "[::1]:8765"}) {
            Headers headers = new Headers();
            headers.set("Host", host);
            assertDoesNotThrow(() -> LoopbackRequestPolicy.requireAllowed(headers), host);
        }

        Headers localOrigin = new Headers();
        localOrigin.set("Host", "127.0.0.1:8765");
        localOrigin.set("Origin", "http://localhost:3000");
        localOrigin.set("Sec-Fetch-Site", "same-site");
        assertDoesNotThrow(() -> LoopbackRequestPolicy.requireAllowed(localOrigin));
    }

    @Test
    void rejectsDnsRebindingAuthoritiesAndCrossSiteBrowserRequests() {
        Headers rebound = new Headers();
        rebound.set("Host", "attacker.example:8765");
        assertThrows(LoopbackRequestPolicy.RejectedRequestException.class,
                () -> LoopbackRequestPolicy.requireAllowed(rebound));

        Headers foreignOrigin = new Headers();
        foreignOrigin.set("Host", "127.0.0.1:8765");
        foreignOrigin.set("Origin", "https://attacker.example");
        assertThrows(LoopbackRequestPolicy.RejectedRequestException.class,
                () -> LoopbackRequestPolicy.requireAllowed(foreignOrigin));

        Headers crossSite = new Headers();
        crossSite.set("Host", "127.0.0.1:8765");
        crossSite.set("Sec-Fetch-Site", "cross-site");
        assertThrows(LoopbackRequestPolicy.RejectedRequestException.class,
                () -> LoopbackRequestPolicy.requireAllowed(crossSite));
    }

    @Test
    void rejectsMissingDuplicateOrMalformedHostHeaders() {
        assertThrows(LoopbackRequestPolicy.RejectedRequestException.class,
                () -> LoopbackRequestPolicy.requireAllowed(new Headers()));

        Headers duplicate = new Headers();
        duplicate.add("Host", "127.0.0.1:8765");
        duplicate.add("Host", "localhost:8765");
        assertThrows(LoopbackRequestPolicy.RejectedRequestException.class,
                () -> LoopbackRequestPolicy.requireAllowed(duplicate));

        Headers malformed = new Headers();
        malformed.set("Host", "127.0.0.1.evil.example:8765");
        assertThrows(LoopbackRequestPolicy.RejectedRequestException.class,
                () -> LoopbackRequestPolicy.requireAllowed(malformed));
    }
}
