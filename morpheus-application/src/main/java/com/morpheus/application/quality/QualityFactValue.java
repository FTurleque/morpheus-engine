package com.morpheus.application.quality;

import java.util.Optional;

/** Tri-state value for a lifecycle fact observed from normalized snapshot data. */
public enum QualityFactValue {
    TRUE,
    FALSE,
    UNAVAILABLE;

    public static QualityFactValue of(boolean value) {
        return value ? TRUE : FALSE;
    }

    public Optional<Boolean> asBoolean() {
        return switch (this) {
            case TRUE -> Optional.of(true);
            case FALSE -> Optional.of(false);
            case UNAVAILABLE -> Optional.empty();
        };
    }
}
