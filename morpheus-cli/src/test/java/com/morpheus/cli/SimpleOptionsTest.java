package com.morpheus.cli;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimpleOptionsTest {

    @Test
    void anEmptyOrBlankValueIsRefusedAtTheFrontierAndNamesItsOption() {
        for (String value : List.of("", " ", "\t", "   ")) {
            IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                    () -> SimpleOptions.parse(List.of("--name", "n", "--project", value)));
            assertTrue(failure.getMessage().startsWith("--project requires a non-blank value"), failure.getMessage());
        }
    }

    @Test
    void anOmittedOptionIsStillAbsentAndARequiredOneStillSaysSo() {
        SimpleOptions options = SimpleOptions.parse(List.of("--name", "  n  "));

        assertEquals(Optional.empty(), options.optional("project"));
        assertEquals(Optional.of("n"), options.optional("name"));
        IllegalArgumentException missing = assertThrows(IllegalArgumentException.class, () -> options.required("project"));
        assertEquals("--project is required", missing.getMessage());
        IllegalArgumentException noValue = assertThrows(IllegalArgumentException.class,
                () -> SimpleOptions.parse(List.of("--project")));
        assertEquals("--project requires a value", noValue.getMessage());
    }
}
