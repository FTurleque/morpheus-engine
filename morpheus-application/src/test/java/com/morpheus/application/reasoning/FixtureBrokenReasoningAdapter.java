package com.morpheus.application.reasoning;

/**
 * An optional reasoning adapter that is present on the class path and cannot be built. It is declared as a service
 * provider in this module's test resources, so that discovery meets what an operator meets when an installed adapter
 * is broken.
 */
public final class FixtureBrokenReasoningAdapter implements ReasoningAdapter {
    static final String FAILURE = "fixture adapter cannot start";

    public FixtureBrokenReasoningAdapter() {
        throw new IllegalStateException(FAILURE);
    }

    @Override
    public String id() {
        return "fixture-broken";
    }

    @Override
    public ReasoningContracts.AdapterResult reason(ReasoningContracts.AdapterRequest request) {
        return ReasoningContracts.AdapterResult.empty();
    }
}
