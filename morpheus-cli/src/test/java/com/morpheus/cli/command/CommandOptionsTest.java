package com.morpheus.cli.command;

import com.morpheus.cli.command.CommandOptions.Declared;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandOptionsTest {
    private static final Declared DECLARED = Declared.values("project", "limit").andRepeated("source").andFlags("force");

    @Test
    void aFlagAndAValueOptionAreReadWithoutAParserOfTheSubCommand() {
        CommandOptions options = CommandOptions.parse(List.of("--force", "--project", " p "), DECLARED);

        assertTrue(options.flag("force"));
        assertEquals("p", options.required("project"));
        assertEquals(Optional.of(" p "), options.asGiven("project"));
        assertFalse(options.given("limit"));
    }

    @Test
    void aRepeatedOptionKeepsEveryValueInOrder() {
        CommandOptions options = CommandOptions.parse(List.of("--source", "b", "--source", "a"), DECLARED);

        assertEquals(List.of("b", "a"), options.all("source"));
    }

    @Test
    void anUndeclaredOptionIsRefusedByNameBeforeItsValueIsRead() {
        assertRefused("unknown option: --projet", "--projet", " ");
        assertRefused("unknown option: --projet", "--projet");
    }

    @Test
    void aSingleValuedOptionOrAFlagGivenTwiceIsRefused() {
        assertRefused("duplicate option: --project", "--project", "a", "--project", "b");
        assertRefused("duplicate option: --project", "--project", "a", "--project");
        assertRefused("duplicate option: --force", "--force", "--force");
    }

    @Test
    void aValueOptionWithoutAValueIsRefused() {
        assertRefused("--project requires a value", "--project");
        assertRefused("--project requires a value", "--project", "--force");
    }

    @Test
    void aBlankValueIsRefusedByTheOneDefinition() {
        assertRefused("--project requires a non-blank value; omit the option to leave it unset", "--project", " ");
        assertRefused("--source requires a non-blank value; omit the option to leave it unset", "--source", "\t");
    }

    @Test
    void aTokenThatIsNotAnOptionIsRefused() {
        assertRefused("unexpected positional argument: stray", "stray");
        assertRefused("unknown option: --", "--");
    }

    @Test
    void aNumberIsBoundedAndNamed() {
        CommandOptions options = CommandOptions.parse(List.of("--limit", " 7 "), DECLARED);

        assertEquals(7, options.intValue("limit", 20, 1, 10));
        assertEquals(20, CommandOptions.parse(List.of(), DECLARED).intValue("limit", 20, 1, 100));
        IllegalArgumentException range = assertThrows(IllegalArgumentException.class,
                () -> options.intValue("limit", 20, 1, 5));
        assertEquals("--limit must be between 1 and 5", range.getMessage());
        IllegalArgumentException missing = assertThrows(IllegalArgumentException.class,
                () -> CommandOptions.parse(List.of(), DECLARED).requiredLong("limit", 0, 9));
        assertEquals("--limit is required", missing.getMessage());
    }

    @Test
    void anOptionCannotBeDeclaredTwice() {
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> Declared.values("force").andFlags("force"));

        assertEquals("option declared twice: --force", failure.getMessage());
    }

    private static void assertRefused(String message, String... tokens) {
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> CommandOptions.parse(List.of(tokens), DECLARED));
        assertEquals(message, failure.getMessage());
    }
}
