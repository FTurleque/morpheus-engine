package com.morpheus.application.operability;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The operational log mode is one of a closed list, or a refusal.
 *
 * <p>Any value the runtime did not recognise used to select the no-op sink: {@code jsno}, {@code true} or {@code 1}
 * turned off the only observability channel of the product without a word, and the operator who had asked for
 * structured logs ran a mute server.</p>
 */
class OperationalLogModeTest {
    @Test
    void anAbsentOrBlankValueMeansNoOperationalLog() {
        assertEquals(OperationalLogMode.OFF, OperationalLogMode.parse(null));
        assertEquals(OperationalLogMode.OFF, OperationalLogMode.parse(""));
        assertEquals(OperationalLogMode.OFF, OperationalLogMode.parse("   "));
    }

    @Test
    void everyAcceptedSpellingSelectsItsModeWhateverItsCaseAndSurroundingSpaces() {
        assertEquals(OperationalLogMode.OFF, OperationalLogMode.parse("off"));
        assertEquals(OperationalLogMode.OFF, OperationalLogMode.parse("None"));
        assertEquals(OperationalLogMode.JSON_LINES, OperationalLogMode.parse("json"));
        assertEquals(OperationalLogMode.JSON_LINES, OperationalLogMode.parse(" JSONL "));
        assertEquals(OperationalLogMode.JSON_LINES, OperationalLogMode.parse("Structured"));
    }

    @Test
    void aMistypedModeIsRefusedWithTheVariableAndTheAcceptedValues() {
        IllegalArgumentException refused =
                assertThrows(IllegalArgumentException.class, () -> OperationalLogMode.parse("jsno"));

        assertEquals("OPERATIONAL_LOG_MODE_UNKNOWN: MORPHEUS_OPERATIONAL_LOGS must be one of off, none, json, jsonl, "
                + "structured, or be unset; it is 'jsno'", refused.getMessage());
    }

    @Test
    void aBooleanIsNotAMode() {
        for (String notAMode : new String[] {"true", "1", "yes", "on", "false", "0"}) {
            IllegalArgumentException refused =
                    assertThrows(IllegalArgumentException.class, () -> OperationalLogMode.parse(notAMode), notAMode);

            assertTrue(refused.getMessage().startsWith(OperationalLogMode.REFUSAL), refused::getMessage);
        }
    }

    @Test
    void theRefusalListsExactlyTheSpellingsTheParserAccepts() {
        for (String spelling : OperationalLogMode.acceptedSpellings()) {
            OperationalLogMode.parse(spelling);
        }
        IllegalArgumentException refused =
                assertThrows(IllegalArgumentException.class, () -> OperationalLogMode.parse("verbose"));

        assertTrue(refused.getMessage().contains(String.join(", ", OperationalLogMode.acceptedSpellings())),
                refused::getMessage);
    }
}
