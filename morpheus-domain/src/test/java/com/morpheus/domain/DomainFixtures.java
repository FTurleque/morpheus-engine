package com.morpheus.domain;

import com.morpheus.domain.evidence.EvidenceId;
import com.morpheus.domain.provenance.Provenance;
import com.morpheus.domain.provider.ProviderId;
import com.morpheus.domain.source.SourceLocator;

import java.util.Optional;

/** Values every normalized entity requires but that a test of that entity does not examine. */
public final class DomainFixtures {
    private DomainFixtures() {
    }

    public static Provenance provenance() {
        return new Provenance(
                new ProviderId("synthetic"),
                Optional.of("1.0"),
                SourceLocator.file("specs/fixture.md"),
                Optional.empty(),
                Optional.empty(),
                EvidenceId.generate());
    }
}
