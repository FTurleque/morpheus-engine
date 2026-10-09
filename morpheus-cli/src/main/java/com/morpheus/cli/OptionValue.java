package com.morpheus.cli;

import java.nio.file.Path;

/**
 * The single refusal of an option given an empty or blank value, shared by every parser of the CLI.
 *
 * <p>No option of the CLI gives an empty value a meaning that omitting the option does not already have, so a blank
 * value is refused where it is read instead of being dropped, defaulted or turned into the working directory
 * ({@code Path.of("")}). The value is returned as given: a caller that trimmed keeps trimming, a path keeps its
 * spelling.</p>
 *
 * <p>A value is blank when every one of its code points is blank for one of two readings, and neither reading is
 * enough alone. {@code c <= ' '} is what {@link String#trim()} removes: control characters such as U+0001 included,
 * which are not white space for {@link String#isBlank()}, so a value made of them would pass an {@code isBlank} test
 * and become empty in a caller that trims. {@link Character#isWhitespace(int)} is what {@code isBlank()} holds: the em
 * space U+2003 included, which {@code trim()} keeps, so a value made of it would pass a {@code trim} test, stay as it
 * is through the caller's {@code trim()}, and fail further on without naming the option, or name a file in the working
 * directory. The two readings are joined per code point, not per string: {@code isBlank() || trim().isEmpty()} would
 * still let through U+0001 next to U+2003, blank for neither whole-string test though each of its characters is blank
 * for one of them.</p>
 *
 * <p>What stays a value: a no-break space (U+00A0, U+2007, U+202F), and invisible characters that are neither white
 * space nor at or below U+0020 -- the zero-width space U+200B, the byte order mark U+FEFF, the next-line control U+0085,
 * the delete control U+007F. A value made only of them is not refused here and reaches the reader as given, with the
 * consequences an em space had: a reader may fail further on without naming the option, or take it as a file name.</p>
 *
 * <p>This is the only definition of a blank option value in the module. A parser that tests the blank itself holds a
 * second definition, and the two drift: the weaker one reports an option that was given as missing.</p>
 */
final class OptionValue {
    private OptionValue() {
    }

    static String nonBlank(String option, String value) {
        if (value.codePoints().allMatch(c -> c <= ' ' || Character.isWhitespace(c))) {
            throw new IllegalArgumentException(
                    option + " requires a non-blank value; omit the option to leave it unset");
        }
        return value;
    }

    static Path path(String option, String value) {
        return Path.of(nonBlank(option, value));
    }
}
