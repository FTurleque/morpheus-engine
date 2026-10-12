package com.morpheus.application.quality;


/** Tri-state value for a lifecycle fact observed from normalized snapshot data. */
public enum QualityFactValue {
    TRUE,
    FALSE,
    UNAVAILABLE;

    public static QualityFactValue of(boolean value) {
        return value ? TRUE : FALSE;
    }
}
