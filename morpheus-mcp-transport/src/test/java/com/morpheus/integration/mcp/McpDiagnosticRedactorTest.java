package com.morpheus.integration.mcp;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class McpDiagnosticRedactorTest {

    @Test
    void redactsAuthorizationSchemesAndNamedSecrets() {
        String bearer = "bearer-token-value";
        String basic = "dXNlcjpwYXNzd29yZA==";
        String apiKey = "api-key-value";

        String diagnostic = McpDiagnosticRedactor.redact(
                "Authorization: Bearer " + bearer
                        + " fallback=Basic " + basic
                        + " api_key=" + apiKey
                        + " status=401");

        assertEquals(
                "Authorization: <redacted> fallback=Basic <redacted> api_key=<redacted> status=401",
                diagnostic);
        assertFalse(diagnostic.contains(bearer));
        assertFalse(diagnostic.contains(basic));
        assertFalse(diagnostic.contains(apiKey));
    }

    @Test
    void redactsJsonStyleSecretsAndThrowableMessages() {
        String token = "json-token-value";
        String password = "json-password-value";
        IllegalStateException failure = new IllegalStateException(
                "peer rejected {\"token\":\"" + token + "\", \"password\": \"" + password + "\"}");

        String diagnostic = McpDiagnosticRedactor.describe(failure);

        assertFalse(diagnostic.contains(token));
        assertFalse(diagnostic.contains(password));
        assertEquals(
                "IllegalStateException: peer rejected {\"token\":\"<redacted>\", \"password\": \"<redacted>\"}",
                diagnostic);
    }

    @Test
    void preservesNonSensitiveDiagnostics() {
        assertEquals(
                "indexing failed status=503 retry=true",
                McpDiagnosticRedactor.redact("indexing failed status=503 retry=true"));
    }
}
