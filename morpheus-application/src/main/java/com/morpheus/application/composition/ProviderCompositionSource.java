package com.morpheus.application.composition;

import com.morpheus.application.read.SpecificationContentReader;
import com.morpheus.domain.provider.ProviderId;

import java.util.Objects;

/** One explicitly configured provider source participating in a composition pass. */
public record ProviderCompositionSource(
        SpecificationContentReader reader,
        int precedence,
        boolean required) {

    public ProviderCompositionSource {
        Objects.requireNonNull(reader, "reader");
    }

    public ProviderId providerId() {
        return reader.providerId();
    }
}
