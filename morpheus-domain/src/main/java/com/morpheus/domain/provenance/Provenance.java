package com.morpheus.domain.provenance;

import com.morpheus.domain.evidence.EvidenceId;
import com.morpheus.domain.provider.ProviderId;
import com.morpheus.domain.source.SourceLocator;

import java.util.Objects;
import java.util.Optional;

/** Provider-neutral origin of one normalized MORPHEUS entity. */
public record Provenance(
        ProviderId providerId,
        Optional<String> providerVersion,
        SourceLocator source,
        Optional<String> externalId,
        Optional<String> sourceRevision,
        EvidenceId evidenceId) {

    public Provenance {
        Objects.requireNonNull(providerId, "providerId");
        providerVersion = normalized(providerVersion, "providerVersion");
        Objects.requireNonNull(source, "source");
        externalId = normalized(externalId, "externalId");
        sourceRevision = normalized(sourceRevision, "sourceRevision");
        Objects.requireNonNull(evidenceId, "evidenceId");
    }

    private static Optional<String> normalized(Optional<String> value, String name) {
        return Objects.requireNonNull(value, name)
                .map(String::trim)
                .filter(candidate -> !candidate.isEmpty());
    }
}
