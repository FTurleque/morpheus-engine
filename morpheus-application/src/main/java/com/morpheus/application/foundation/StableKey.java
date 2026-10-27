package com.morpheus.application.foundation;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Objects;

/**
 * The stable key of a title, as providers place it in an external identifier: accents folded to their base letter,
 * lower case, every run of characters other than an ASCII letter or digit replaced by one hyphen, no hyphen at either
 * end. A title that leaves nothing is refused: an empty key would give every such title the same identity.
 */
public final class StableKey {
    private StableKey() {
    }

    public static String fromTitle(String title) {
        Objects.requireNonNull(title, "title");
        String key = Normalizer.normalize(title, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        if (key.isBlank()) {
            throw new IllegalArgumentException("Cannot derive a stable key from: " + title);
        }
        return key;
    }
}
