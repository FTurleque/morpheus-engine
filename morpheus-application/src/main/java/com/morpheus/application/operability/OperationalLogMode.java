package com.morpheus.application.operability;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * What the operational log does, chosen by one environment variable from a closed list of spellings.
 *
 * <p>A value outside the list is a refusal, never a default: a mistyped mode used to select the no-op sink, so the
 * operator who had asked for structured logs ran a server that wrote none and said nothing about it.</p>
 */
public enum OperationalLogMode {
    OFF("off", "none"),
    JSON_LINES("json", "jsonl", "structured");

    public static final String ENVIRONMENT_VARIABLE = "MORPHEUS_OPERATIONAL_LOGS";
    public static final String REFUSAL = "OPERATIONAL_LOG_MODE_UNKNOWN";

    private final List<String> spellings;

    OperationalLogMode(String... spellings) {
        this.spellings = List.of(spellings);
    }

    /**
     * The mode a configured value selects. An absent or blank value selects {@link #OFF}; surrounding spaces and case
     * are not an error; anything else is refused.
     *
     * @throws IllegalArgumentException starting with {@link #REFUSAL}, naming the variable, the accepted spellings
     *         and the value that is none of them
     */
    public static OperationalLogMode parse(String configured) {
        if (configured == null || configured.isBlank()) {
            return OFF;
        }
        String spelling = configured.trim().toLowerCase(Locale.ROOT);
        for (OperationalLogMode mode : values()) {
            if (mode.spellings.contains(spelling)) {
                return mode;
            }
        }
        throw new IllegalArgumentException(REFUSAL + ": " + ENVIRONMENT_VARIABLE + " must be one of "
                + String.join(", ", acceptedSpellings()) + ", or be unset; it is '" + configured.trim() + "'");
    }

    /** Every spelling {@link #parse} accepts, in the order the refusal lists them. */
    public static List<String> acceptedSpellings() {
        List<String> accepted = new ArrayList<>();
        for (OperationalLogMode mode : values()) {
            accepted.addAll(mode.spellings);
        }
        return List.copyOf(accepted);
    }
}
