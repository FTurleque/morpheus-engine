package com.morpheus.cli;

import java.nio.file.Path;

/**
 * The single refusal of an option given an empty or blank value, shared by every parser of the CLI.
 *
 * <p>No option of the CLI gives an empty value a meaning that omitting the option does not already have, so a blank
 * value is refused where it is read instead of being dropped, defaulted or turned into the working directory
 * ({@code Path.of("")}). "Blank" is what {@link String#trim()} empties. The value is returned as given: a caller
 * that trimmed keeps trimming, a path keeps its spelling.</p>
 */
final class OptionValue {
    private OptionValue() {
    }

    static String nonBlank(String option, String value) {
        if (value.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    option + " requires a non-blank value; omit the option to leave it unset");
        }
        return value;
    }

    static Path path(String option, String value) {
        return Path.of(nonBlank(option, value));
    }
}
