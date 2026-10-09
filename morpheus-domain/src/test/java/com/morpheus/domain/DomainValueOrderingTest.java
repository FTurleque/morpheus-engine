package com.morpheus.domain;

import com.morpheus.domain.change.lifecycle.ChangeLifecycleIdempotencyKey;
import com.morpheus.domain.change.lifecycle.ChangeLifecycleMutationId;
import com.morpheus.domain.change.lifecycle.ChangeLifecycleRevision;
import com.morpheus.domain.provider.ProviderCapabilitySet;
import com.morpheus.domain.provider.ProviderId;
import com.morpheus.domain.reference.ExternalReferenceId;
import com.morpheus.domain.requirement.RequirementDeltaId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotId;
import com.morpheus.domain.source.SourceLocator;
import com.morpheus.domain.source.SpecificationSource;
import com.morpheus.domain.version.SpecificationVersionId;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static com.morpheus.domain.OrderingAssertions.assertStrictlyOrdered;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The natural order of domain values is what makes every published collection deterministic, so each comparison is
 * held in both directions, at equality, and at every tie-breaking key (PIT-AUD-4: no test executed these orders).
 */
class DomainValueOrderingTest {
    static final String FIRST = "01920000-0000-7000-8000-000000000001";
    static final String SECOND = "01920000-0000-7000-8000-000000000002";

    @Test
    void identifiersOrderByTheirIdentity() {
        assertStrictlyOrdered(ChangeLifecycleMutationId.parse(FIRST), ChangeLifecycleMutationId.parse(SECOND));
        assertStrictlyOrdered(ExternalReferenceId.parse(FIRST), ExternalReferenceId.parse(SECOND));
        assertStrictlyOrdered(RequirementDeltaId.parse(FIRST), RequirementDeltaId.parse(SECOND));
        assertStrictlyOrdered(KnowledgeSnapshotId.parse(FIRST), KnowledgeSnapshotId.parse(SECOND));
        assertStrictlyOrdered(SpecificationVersionId.parse(FIRST), SpecificationVersionId.parse(SECOND));
        assertStrictlyOrdered(new ChangeLifecycleIdempotencyKey("key-a"), new ChangeLifecycleIdempotencyKey("key-b"));
        assertStrictlyOrdered(new ChangeLifecycleRevision(1), new ChangeLifecycleRevision(2));
    }

    @Test
    void aParsedRequirementDeltaIdIsTheIdentityItWasParsedFrom() {
        assertEquals(FIRST, RequirementDeltaId.parse(FIRST).toString());
    }

    @Test
    void aSourceLocatorOrdersBySchemeThenValue() {
        assertStrictlyOrdered(new SourceLocator("file", "z"), new SourceLocator("https", "a"));
        assertStrictlyOrdered(new SourceLocator("file", "a"), new SourceLocator("file", "b"));
    }

    @Test
    void aSpecificationSourceOrdersByProviderThenLocator() {
        assertStrictlyOrdered(source("markdown", "z"), source("openspec", "a"));
        assertStrictlyOrdered(source("openspec", "a"), source("openspec", "b"));
    }

    private static SpecificationSource source(String provider, String locator) {
        return new SpecificationSource(new ProviderId(provider), SourceLocator.file(locator),
                Optional.empty(), Optional.empty(), ProviderCapabilitySet.of());
    }
}
