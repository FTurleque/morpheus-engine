package com.morpheus.domain.version;

import com.morpheus.domain.identity.DomainIdentity;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.temporal.TemporalState;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TemporalVersioningTest {

    @Test
    void temporalStatesAreExplicitAndLimitedToTheThreeMorpheusDimensions() {
        assertEquals(
                List.of(TemporalState.CURRENT, TemporalState.PROPOSED, TemporalState.HISTORICAL),
                List.of(TemporalState.values()));
    }

    @Test
    void entityVersionIdentityIsDistinctFromLogicalDomainIdentity() {
        DomainIdentity logicalIdentity = DomainIdentity.generate();
        EntityVersion<String> occurrence = new EntityVersion<>(
                EntityVersionId.generate(),
                logicalIdentity,
                SpecificationVersionId.generate(),
                TemporalState.CURRENT,
                "baseline");

        assertEquals(logicalIdentity, occurrence.entityIdentity());
        assertNotEquals(logicalIdentity, occurrence.id().value());
    }

    @Test
    void sameLogicalIdentityCanHaveSeveralVersionedOccurrencesAndTemporalStates() {
        DomainIdentity logicalIdentity = DomainIdentity.generate();
        SpecificationVersionId currentVersion = SpecificationVersionId.generate();
        SpecificationVersionId proposedVersion = SpecificationVersionId.generate();

        EntityVersion<String> current = new EntityVersion<>(
                EntityVersionId.generate(),
                logicalIdentity,
                currentVersion,
                TemporalState.CURRENT,
                "30 minutes");
        EntityVersion<String> proposed = new EntityVersion<>(
                EntityVersionId.generate(),
                logicalIdentity,
                proposedVersion,
                TemporalState.PROPOSED,
                "60 minutes");

        assertEquals(current.entityIdentity(), proposed.entityIdentity());
        assertNotEquals(current.id(), proposed.id());
        assertNotEquals(current.specificationVersionId(), proposed.specificationVersionId());
        assertNotEquals(current.temporalState(), proposed.temporalState());
        assertNotEquals(current.content(), proposed.content());
    }

    @Test
    void specificationVersionRejectsNonPositiveSequence() {
        assertThrows(IllegalArgumentException.class, () -> new SpecificationVersion(
                SpecificationVersionId.generate(),
                ProjectSpecificationId.generate(),
                Optional.of(0L),
                Optional.empty(),
                Optional.empty(),
                Instant.parse("2026-07-22T20:00:00Z"),
                Optional.empty()));
    }

    @Test
    void specificationVersionRejectsSelfPredecessorAndNormalizesBlankMetadata() {
        SpecificationVersionId id = SpecificationVersionId.generate();

        assertThrows(IllegalArgumentException.class, () -> new SpecificationVersion(
                id,
                ProjectSpecificationId.generate(),
                Optional.of(2L),
                Optional.of("provider-1"),
                Optional.of("source-2"),
                Instant.parse("2026-07-22T20:00:00Z"),
                Optional.of(id)));

        SpecificationVersion normalized = new SpecificationVersion(
                SpecificationVersionId.generate(),
                ProjectSpecificationId.generate(),
                Optional.empty(),
                Optional.of("   "),
                Optional.of("  rev-1  "),
                Instant.parse("2026-07-22T20:00:00Z"),
                Optional.empty());

        assertTrue(normalized.providerVersion().isEmpty());
        assertEquals(Optional.of("rev-1"), normalized.sourceRevision());
    }
}
