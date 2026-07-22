package com.morpheus.application.temporal;

import com.morpheus.domain.identity.DomainIdentity;
import com.morpheus.domain.temporal.TemporalState;
import com.morpheus.domain.version.EntityVersion;
import com.morpheus.domain.version.EntityVersionId;
import com.morpheus.domain.version.SpecificationVersionId;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TemporalProjectionTest {

    @Test
    void currentViewNeverIncludesProposedOrHistoricalContent() {
        DomainIdentity currentId = DomainIdentity.generate();
        DomainIdentity proposedId = DomainIdentity.generate();
        DomainIdentity historicalId = DomainIdentity.generate();

        TemporalProjection<String> projection = new TemporalProjection<>(List.of(
                occurrence(currentId, SpecificationVersionId.generate(), TemporalState.CURRENT, "current"),
                occurrence(proposedId, SpecificationVersionId.generate(), TemporalState.PROPOSED, "proposed"),
                occurrence(historicalId, SpecificationVersionId.generate(), TemporalState.HISTORICAL, "historical")));

        assertEquals(1, projection.current().size());
        assertEquals("current", projection.current().getFirst().content());
        assertTrue(projection.current().stream()
                .noneMatch(item -> item.temporalState() != TemporalState.CURRENT));
    }

    @Test
    void competingProposalsCanTargetSameLogicalIdentityWithoutReplacingCurrent() {
        DomainIdentity logicalIdentity = DomainIdentity.generate();
        EntityVersion<String> current = occurrence(
                logicalIdentity,
                SpecificationVersionId.generate(),
                TemporalState.CURRENT,
                "30 minutes");
        EntityVersion<String> extend = occurrence(
                logicalIdentity,
                SpecificationVersionId.generate(),
                TemporalState.PROPOSED,
                "60 minutes");
        EntityVersion<String> shorten = occurrence(
                logicalIdentity,
                SpecificationVersionId.generate(),
                TemporalState.PROPOSED,
                "15 minutes");

        TemporalProjection<String> projection = new TemporalProjection<>(List.of(current, extend, shorten));

        assertEquals(3, projection.forEntity(logicalIdentity).size());
        assertEquals(2, projection.proposed().size());
        assertEquals("30 minutes", projection.currentFor(logicalIdentity).orElseThrow().content());
    }

    @Test
    void multipleCurrentOccurrencesForSameLogicalIdentityAreRejected() {
        DomainIdentity logicalIdentity = DomainIdentity.generate();

        assertThrows(IllegalArgumentException.class, () -> new TemporalProjection<>(List.of(
                occurrence(logicalIdentity, SpecificationVersionId.generate(), TemporalState.CURRENT, "first"),
                occurrence(logicalIdentity, SpecificationVersionId.generate(), TemporalState.CURRENT, "second"))));
    }

    @Test
    void technicalReingestionCanReuseExplicitBusinessSpecificationVersion() {
        DomainIdentity logicalIdentity = DomainIdentity.generate();
        SpecificationVersionId businessVersion = SpecificationVersionId.generate();

        EntityVersion<String> firstIngestion = occurrence(
                logicalIdentity,
                businessVersion,
                TemporalState.CURRENT,
                "30 minutes");
        EntityVersion<String> rebuiltOccurrence = occurrence(
                logicalIdentity,
                businessVersion,
                TemporalState.CURRENT,
                "30 minutes");

        TemporalProjection<String> firstProjection = new TemporalProjection<>(List.of(firstIngestion));
        TemporalProjection<String> rebuiltProjection = new TemporalProjection<>(List.of(rebuiltOccurrence));

        assertEquals(
                firstProjection.current().getFirst().specificationVersionId(),
                rebuiltProjection.current().getFirst().specificationVersionId());
        assertNotEquals(
                firstProjection.current().getFirst().id(),
                rebuiltProjection.current().getFirst().id());
    }

    private EntityVersion<String> occurrence(
            DomainIdentity logicalIdentity,
            SpecificationVersionId specificationVersionId,
            TemporalState temporalState,
            String content) {
        return new EntityVersion<>(
                EntityVersionId.generate(),
                logicalIdentity,
                specificationVersionId,
                temporalState,
                content);
    }
}
