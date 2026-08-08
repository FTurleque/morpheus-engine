package com.morpheus.api;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

class JacksonSecurityRegressionTest {

    @Test
    void excessivelyNestedJsonIsRejectedWithoutStackOverflow() {
        JsonMapper mapper = JsonMapper.builder()
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .build();
        String payload = "[".repeat(2_000) + "0" + "]".repeat(2_000);

        Throwable failure = assertThrows(Throwable.class, () -> mapper.readValue(payload, Object.class));

        assertFalse(failure instanceof StackOverflowError,
                "deep untrusted JSON must be rejected by Jackson constraints before exhausting the JVM stack");
        assertTrue(failure.getClass().getName().startsWith("tools.jackson."),
                () -> "expected a Jackson parsing/constraint failure, got " + failure.getClass().getName());
    }
}
