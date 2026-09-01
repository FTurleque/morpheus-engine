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

        for (String origin : new String[]{"http://localhost:3000", "https://127.0.0.1", "http://[::1]:8765"}) {
            Headers headers = new Headers();
            headers.set("Host", "127.0.0.1:8765");
            headers.set("Origin", origin);
            headers.set("Sec-Fetch-Site", "same-site");
            assertDoesNotThrow(() -> LoopbackRequestPolicy.requireAllowed(headers), origin);
        }
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
        crossSite.set("Sec-Fetch-Site", "CrOsS-SiTe");
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

        for (String host : new String[]{
                "",
                "127.0.0.1.evil.example:8765",
                "128.0.0.1:8765",
                "127.0.0:8765",
                "127.0.0.999:8765",
                "127.0.0.1000:8765",
                "127.0.0.a:8765",
                "user@localhost:8765",
                "localhost:8765/path",
                "localhost:8765?query=1",
                "localhost:8765#fragment",
                "[::1%25lo]:8765"}) {
            Headers malformed = new Headers();
            malformed.set("Host", host);
            assertThrows(LoopbackRequestPolicy.RejectedRequestException.class,
                    () -> LoopbackRequestPolicy.requireAllowed(malformed), host);
        }
    }

    @Test
    void rejectsMalformedOrDuplicateOrigins() {
        for (String origin : new String[]{
                "",
                "null",
                "ftp://localhost",
                "https://attacker.example",
                "http://user@localhost",
                "http://localhost/path",
                "http://localhost?query=1",
                "http://localhost#fragment",
                "not-an-origin"}) {
            Headers headers = new Headers();
            headers.set("Host", "localhost:8765");
            headers.set("Origin", origin);
            assertThrows(LoopbackRequestPolicy.RejectedRequestException.class,
                    () -> LoopbackRequestPolicy.requireAllowed(headers), origin);
        }

        Headers duplicate = new Headers();
        duplicate.set("Host", "localhost:8765");
        duplicate.add("Origin", "http://localhost:3000");
        duplicate.add("Origin", "http://127.0.0.1:3000");
        assertThrows(LoopbackRequestPolicy.RejectedRequestException.class,
                () -> LoopbackRequestPolicy.requireAllowed(duplicate));
    }

    @Test
    void rejectsDuplicateFetchMetadata() {
        Headers duplicate = new Headers();
        duplicate.set("Host", "localhost:8765");
        duplicate.add("Sec-Fetch-Site", "same-origin");
        duplicate.add("Sec-Fetch-Site", "same-site");
        assertThrows(LoopbackRequestPolicy.RejectedRequestException.class,
                () -> LoopbackRequestPolicy.requireAllowed(duplicate));
    }
}
