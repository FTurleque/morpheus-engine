package com.morpheus.application.foundation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A stable key feeds an external identifier, and an external identifier feeds a persisted identity: a key that moves
 * for an existing title moves an entity.
 */
class StableKeyTest {
    @Test
    void anAsciiTitleBecomesLowerCaseWordsJoinedByOneHyphen() {
        assertEquals("configured-retention-period-is-honored",
                StableKey.fromTitle("  Configured retention -- period is HONORED!  "));
    }

    @Test
    void anAccentedLetterIsFoldedToItsBaseLetter() {
        assertEquals("evaluer-la-premiere-etape", StableKey.fromTitle("Évaluer la première étape"));
    }

    @Test
    void aLetterWithoutAnAsciiBaseIsASeparator() {
        assertEquals("mega-2", StableKey.fromTitle("Ωmega 2"));
        assertEquals("phase-2", StableKey.fromTitle("Phase日本2"));
    }

    @Test
    void aTitleThatLeavesNothingIsRefusedByName() {
        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> StableKey.fromTitle("!! 日本 !!"));
        assertEquals("Cannot derive a stable key from: !! 日本 !!", refused.getMessage());
    }
}
