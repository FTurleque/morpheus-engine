package com.morpheus.application.reasoning;

/** Optional provider-neutral reasoning extension point. Implementations must remain read-only. */
public interface ReasoningAdapter {
    String id();

    default String description() {
        return id();
    }

    ReasoningContracts.AdapterResult reason(ReasoningContracts.AdapterRequest request);
}
