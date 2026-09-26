package com.morpheus.cli;

import java.util.Set;

/**
 * The single refusal of a value-taking option given more than once, for the parsers that do not hold their options
 * in a map refusing a second key.
 *
 * <p>Those parsers assigned each value to a variable, so a repeated option kept its last value and discarded the
 * first without a word. No single-valued option of the CLI gives a repeat a meaning, so it is refused with the wording
 * of the map-based families. An option spelled {@code --x v} and {@code --x=v} is one option: the caller records the
 * option's name, never the token. Value-less flags such as {@code --json} are not recorded, and an option that names a
 * list ({@code --workspace-root}) accumulates instead.</p>
 */
final class OptionOccurrence {
    private OptionOccurrence() {
    }

    static void once(Set<String> given, String option) {
        if (!given.add(option)) {
            throw new IllegalArgumentException("duplicate option: " + option);
        }
    }
}
