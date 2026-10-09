package com.morpheus.domain.provider;

import java.util.Objects;

/**
 * Stable adapter identifier, distinct from specification domain identity.
 *
 * <p>An identifier carries no control character: stores and transports keep provider sets as delimited text, and a
 * line break inside one identifier would read back as two providers.</p>
 */
public record ProviderId(String value) implements Comparable<ProviderId> {

    public ProviderId {
        value = Objects.requireNonNull(value, "value").trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("provider id must not be blank");
        }
        if (value.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(
                    "provider id must not contain control characters: \"" + escapeControls(value) + "\"");
        }
    }

    private static String escapeControls(String value) {
        StringBuilder escaped = new StringBuilder(value.length() + 8);
        value.chars().forEach(character -> {
            if (Character.isISOControl(character)) {
                escaped.append("\\u%04X".formatted(character));
            } else {
                escaped.append((char) character);
            }
        });
        return escaped.toString();
    }

    @Override
    public int compareTo(ProviderId other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value;
    }
}
