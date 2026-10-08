package com.morpheus.domain.portfolio;

import com.morpheus.domain.identity.DomainIdentity;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.provider.ProviderId;
import com.morpheus.domain.source.SourceLocator;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import static com.morpheus.domain.OrderingAssertions.assertStrictlyOrdered;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Portfolio values order deterministically on every tie-breaking key, and their transitions keep identity and refuse
 * to move time backwards (PIT-AUD-4: no test executed these orders and transitions).
 */
class PortfolioValueTest {
    private static final String FIRST = "01920000-0000-7000-8000-000000000001";
    private static final String SECOND = "01920000-0000-7000-8000-000000000002";
    private static final String THIRD = "01920000-0000-7000-8000-000000000003";
    private static final Instant T0 = Instant.parse("2026-10-08T00:00:00Z");
    private static final Instant T1 = Instant.parse("2026-10-08T01:00:00Z");

    @Test
    void aDefinitionOrdersByIdAndRenamesForwardInTime() {
        PortfolioDefinition first = new PortfolioDefinition(PortfolioId.parse(FIRST), "Core", T0, T0);
        assertStrictlyOrdered(first, new PortfolioDefinition(PortfolioId.parse(SECOND), "Core", T0, T0));

        PortfolioDefinition renamed = first.rename("Platform", T1);
        assertEquals(new PortfolioDefinition(PortfolioId.parse(FIRST), "Platform", T0, T1), renamed);
        assertEquals("Edge", renamed.rename("Edge", T1).name(), "a rename at the same instant is not a move backwards");
        IllegalArgumentException backwards = assertThrows(IllegalArgumentException.class,
                () -> renamed.rename("Late", T0));
        assertEquals("updatedAt must not move backwards", backwards.getMessage());
    }

    @Test
    void anEntityReferenceOrdersByProjectThenTypeThenEntity() {
        assertStrictlyOrdered(ref(FIRST, "requirement", THIRD), ref(SECOND, "change", FIRST));
        assertStrictlyOrdered(ref(FIRST, "change", THIRD), ref(FIRST, "requirement", FIRST));
        assertStrictlyOrdered(ref(FIRST, "change", FIRST), ref(FIRST, "change", SECOND));
    }

    @Test
    void aCrossProjectReferenceOrdersBySemanticKeyThenProviderThenId() {
        PortfolioEntityRef source = ref(FIRST, "requirement", FIRST);
        PortfolioEntityRef target = ref(SECOND, "requirement", FIRST);

        assertStrictlyOrdered(reference(FIRST, source, target, "depends-on", "openspec"),
                reference(FIRST, source, target, "refines", "openspec"));
        assertStrictlyOrdered(reference(SECOND, source, target, "depends-on", "markdown"),
                reference(FIRST, source, target, "depends-on", "openspec"));
        assertStrictlyOrdered(reference(FIRST, source, target, "depends-on", "openspec"),
                reference(SECOND, source, target, "depends-on", "openspec"));
    }

    @Test
    void freshnessOrdersByPortfolioThenProject() {
        assertStrictlyOrdered(freshness(FIRST, SECOND), freshness(SECOND, FIRST));
        assertStrictlyOrdered(freshness(FIRST, FIRST), freshness(FIRST, SECOND));
    }

    @Test
    void anObservedMembershipIsActiveWithTheObservationAndRefusesAnEarlierOne() {
        PortfolioMembership missing = new PortfolioMembership(PortfolioId.parse(FIRST),
                ProjectSpecificationId.parse(SECOND), "Old name", Optional.empty(), Optional.empty(),
                Set.of(new ProviderId("markdown")), PortfolioMembershipStatus.MISSING, T0, T0);
        Optional<SourceLocator> workspace = Optional.of(SourceLocator.file("workspace"));

        PortfolioMembership observed = missing.observe("New name", workspace, Optional.empty(),
                Set.of(new ProviderId("openspec")), T1);

        assertEquals(new PortfolioMembership(PortfolioId.parse(FIRST), ProjectSpecificationId.parse(SECOND),
                "New name", workspace, Optional.empty(), Set.of(new ProviderId("openspec")),
                PortfolioMembershipStatus.ACTIVE, T0, T1), observed);
        IllegalArgumentException backwards = assertThrows(IllegalArgumentException.class, () -> observed.observe(
                "Name", Optional.empty(), Optional.empty(), Set.of(), T0));
        assertEquals("observedAt must not move backwards", backwards.getMessage());
    }

    private static PortfolioEntityRef ref(String project, String type, String entity) {
        return new PortfolioEntityRef(ProjectSpecificationId.parse(project), type, DomainIdentity.parse(entity));
    }

    private static CrossProjectReference reference(
            String id, PortfolioEntityRef source, PortfolioEntityRef target, String relation, String provider) {
        return new CrossProjectReference(CrossProjectReferenceId.parse(id), PortfolioId.parse(FIRST), source, target,
                relation, new ProviderId(provider), Optional.empty(), Optional.empty(), T0);
    }

    private static PortfolioFreshness freshness(String portfolio, String project) {
        return new PortfolioFreshness(PortfolioId.parse(portfolio), ProjectSpecificationId.parse(project),
                PortfolioFreshnessState.FRESH, T0, Optional.empty(), Optional.empty());
    }
}
