package com.morpheus.application.composition;

import com.morpheus.application.ingestion.NormalizedProjectContent;
import com.morpheus.application.read.ProviderReadResult;
import com.morpheus.domain.provider.ProviderId;

import java.util.Objects;
import java.util.Optional;

/** One normalized provider observation participating in a multi-provider composition. */
public record ProviderContribution(
        ProviderId providerId,
        int priority,
        boolean required,
        ProviderReadResult readResult) {

    public ProviderContribution {
        Objects.requireNonNull(providerId, "providerId");
        Objects.requireNonNull(readResult, "readResult");
        if (!providerId.equals(readResult.providerId())) {
            throw new IllegalArgumentException("provider contribution id does not match read result");
        }
    }

    public Optional<NormalizedProjectContent> content() {
        return readResult.content();
    }

    public boolean available() {
        return content().isPresent();
    }
}
