package com.morpheus.application.composition;

import com.morpheus.application.operability.OperationalExecution;
import com.morpheus.application.read.ProviderReadResult;
import com.morpheus.domain.provider.ProviderId;

import java.util.Objects;

/** Loads a provider result into the M18 composition contract while recording provider timing locally. */
public final class ObservedProviderContributionLoader {
    private final OperationalExecution execution;

    public ObservedProviderContributionLoader(OperationalExecution execution) {
        this.execution = Objects.requireNonNull(execution, "execution");
    }

    public ProviderContribution load(
            ProviderId providerId,
            int priority,
            boolean required,
            OperationalExecution.ThrowingSupplier<ProviderReadResult> readOperation) {
        Objects.requireNonNull(providerId, "providerId");
        Objects.requireNonNull(readOperation, "readOperation");
        ProviderReadResult result = execution.providerRead(providerId.toString(), readOperation);
        if (!result.providerId().equals(providerId)) {
            throw new IllegalArgumentException(
                    "provider read result identity does not match contribution provider: " + providerId);
        }
        return new ProviderContribution(providerId, priority, required, result);
    }
}
