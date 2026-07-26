package com.morpheus.provider.synthetic;

import com.morpheus.application.identity.EntityIdentityResolver;
import com.morpheus.application.read.ProviderReadRequest;
import com.morpheus.application.read.ReadCategory;
import com.morpheus.application.read.ReadCategoryStatus;
import com.morpheus.domain.acceptance.VerificationStatus;
import com.morpheus.domain.change.lifecycle.ChangeLifecycleState;
import com.morpheus.domain.constraint.ConstraintBlockingMode;
import com.morpheus.domain.constraint.ConstraintSatisfaction;
import com.morpheus.domain.constraint.ConstraintSeverity;
import com.morpheus.domain.identity.DomainIdentity;
import com.morpheus.domain.project.ProjectSpecificationId;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SyntheticSpecificationContentReaderTest {
    private final SyntheticSpecificationContentReader reader = new SyntheticSpecificationContentReader();

    @Test
    void normalizesSyntheticFixtureThroughPublicReadContract() {
        Path root = fixture("synthetic-basic");
        ProjectSpecificationId projectId = ProjectSpecificationId.generate();

        var result = reader.read(
                ProviderReadRequest.all(root, projectId),
                new InMemoryResolver());

        assertEquals(SyntheticSpecificationProvider.ID, result.providerId());
        var content = result.content().orElseThrow();
        assertEquals(projectId, content.project().id());
        assertEquals(1, content.specifications().size());
        assertEquals(1, content.requirements().size());
        assertEquals(1, content.scenarios().size());
        assertEquals(1, content.changes().size());
        assertEquals(2, content.constraints().size());
        assertEquals(2, content.acceptanceCriteria().size());
        assertEquals(11, content.evidence().size());

        var specification = content.specifications().getFirst();
        assertEquals("billing", specification.key());
        assertEquals("Billing", specification.title());
        assertEquals(SyntheticSpecificationProvider.ID, specification.provenance().providerId());

        var requirement = content.requirements().getFirst();
        assertEquals("billing/invoice-retention", requirement.key().orElseThrow());
        assertEquals("Invoice retention", requirement.title());
        assertTrue(requirement.statement().contains("retain invoices"));

        var scenario = content.scenarios().getFirst();
        assertEquals("Retain invoice", scenario.title());
        assertEquals("the retention policy is evaluated", scenario.action());
        assertEquals("retain it", scenario.expectedOutcome());

        var change = content.changes().getFirst();
        assertEquals("extend-retention", change.key().orElseThrow());
        assertEquals("Extend retention", change.title());
        assertEquals("Extend retention", change.intent());

        var blocking = content.constraints().stream()
                .filter(item -> item.blockingPolicy().mode() == ConstraintBlockingMode.BLOCK_WHEN_VIOLATED)
                .findFirst()
                .orElseThrow();
        assertEquals(change.id(), blocking.changeId());
        assertEquals(ConstraintSeverity.CRITICAL, blocking.severity());
        assertEquals(ConstraintSatisfaction.VIOLATED, blocking.satisfaction());
        assertEquals(java.util.List.of(ChangeLifecycleState.VERIFYING), blocking.blockingPolicy().targetStates());
        assertEquals(1, blocking.supportingEvidenceIds().size());
        assertEquals("reviews/security-review.txt", content.evidence().stream()
                .filter(item -> item.id().equals(blocking.supportingEvidenceIds().getFirst()))
                .findFirst().orElseThrow().source().value());

        var warning = content.constraints().stream()
                .filter(item -> item.severity() == ConstraintSeverity.WARNING)
                .findFirst()
                .orElseThrow();
        assertEquals(ConstraintBlockingMode.NON_BLOCKING, warning.blockingPolicy().mode());
        assertEquals(ConstraintSatisfaction.VIOLATED, warning.satisfaction());
        assertTrue(warning.blockingPolicy().targetStates().isEmpty());

        var requirementCriterion = content.acceptanceCriteria().stream()
                .filter(criterion -> criterion.requirementId().isPresent())
                .findFirst()
                .orElseThrow();
        assertEquals(requirement.id(), requirementCriterion.requirementId().orElseThrow());
        assertTrue(requirementCriterion.changeId().isEmpty());
        assertEquals(VerificationStatus.VERIFIED, requirementCriterion.verificationStatus());
        assertEquals(1, requirementCriterion.verificationEvidenceIds().size());
        assertEquals("tests/billing-retention.txt", content.evidence().stream()
                .filter(item -> item.id().equals(requirementCriterion.verificationEvidenceIds().getFirst()))
                .findFirst()
                .orElseThrow()
                .source()
                .value());

        var changeCriterion = content.acceptanceCriteria().stream()
                .filter(criterion -> criterion.changeId().isPresent())
                .findFirst()
                .orElseThrow();
        assertEquals(change.id(), changeCriterion.changeId().orElseThrow());
        assertTrue(changeCriterion.requirementId().isEmpty());
        assertEquals(VerificationStatus.NOT_VERIFIED, changeCriterion.verificationStatus());
        assertTrue(changeCriterion.verificationEvidenceIds().isEmpty());

        assertFalse(content.acceptanceCriteria().stream()
                .anyMatch(criterion -> criterion.title().equals(scenario.title())));
    }

    @Test
    void reportsImplementedAndUnsupportedCategoriesExplicitly() {
        var result = reader.read(
                ProviderReadRequest.all(fixture("synthetic-basic"), ProjectSpecificationId.generate()),
                new InMemoryResolver());

        assertEquals(ReadCategoryStatus.READ, result.report(ReadCategory.CURRENT_SPECIFICATIONS).orElseThrow().status());
        assertEquals(ReadCategoryStatus.READ, result.report(ReadCategory.REQUIREMENTS).orElseThrow().status());
        assertEquals(ReadCategoryStatus.READ, result.report(ReadCategory.SCENARIOS).orElseThrow().status());
        assertEquals(ReadCategoryStatus.READ, result.report(ReadCategory.CHANGES).orElseThrow().status());
        assertEquals(ReadCategoryStatus.READ, result.report(ReadCategory.CONSTRAINTS).orElseThrow().status());
        assertEquals(2, result.report(ReadCategory.CONSTRAINTS).orElseThrow().itemCount());
        assertEquals(ReadCategoryStatus.READ, result.report(ReadCategory.ACCEPTANCE_CRITERIA).orElseThrow().status());
        assertEquals(2, result.report(ReadCategory.ACCEPTANCE_CRITERIA).orElseThrow().itemCount());
        assertEquals(ReadCategoryStatus.UNSUPPORTED, result.report(ReadCategory.EXTERNAL_REFERENCES).orElseThrow().status());
        assertEquals(ReadCategoryStatus.UNSUPPORTED, result.report(ReadCategory.ARCHIVES).orElseThrow().status());
    }

    @Test
    void returnsOnlyRequestedCategoryReports() {
        var request = new ProviderReadRequest(
                fixture("synthetic-basic"),
                ProjectSpecificationId.generate(),
                EnumSet.of(ReadCategory.REQUIREMENTS, ReadCategory.CONSTRAINTS, ReadCategory.ACCEPTANCE_CRITERIA));
        var result = reader.read(request, new InMemoryResolver());

        assertEquals(3, result.categoryReports().size());
        assertEquals(ReadCategoryStatus.READ, result.report(ReadCategory.REQUIREMENTS).orElseThrow().status());
        assertEquals(ReadCategoryStatus.READ, result.report(ReadCategory.CONSTRAINTS).orElseThrow().status());
        assertEquals(ReadCategoryStatus.READ, result.report(ReadCategory.ACCEPTANCE_CRITERIA).orElseThrow().status());
        assertFalse(result.report(ReadCategory.SCENARIOS).isPresent());
    }

    @Test
    void reusesSyntheticIdentitiesAcrossRepeatedReads() {
        Path root = fixture("synthetic-basic");
        ProjectSpecificationId projectId = ProjectSpecificationId.generate();
        InMemoryResolver resolver = new InMemoryResolver();
        ProviderReadRequest request = ProviderReadRequest.all(root, projectId);

        var first = reader.read(request, resolver).content().orElseThrow();
        var second = reader.read(request, resolver).content().orElseThrow();

        assertEquals(first.specifications().getFirst().id(), second.specifications().getFirst().id());
        assertEquals(first.requirements().getFirst().id(), second.requirements().getFirst().id());
        assertEquals(first.scenarios().getFirst().id(), second.scenarios().getFirst().id());
        assertEquals(first.changes().getFirst().id(), second.changes().getFirst().id());
        assertEquals(first.constraints(), second.constraints());
        assertEquals(first.acceptanceCriteria().getFirst().id(), second.acceptanceCriteria().getFirst().id());
        assertEquals(
                first.acceptanceCriteria().getFirst().verificationEvidenceIds(),
                second.acceptanceCriteria().getFirst().verificationEvidenceIds());
    }

    private Path fixture(String name) {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        while (current != null) {
            Path candidate = current.resolve("experiments/m0/fixtures").resolve(name);
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Cannot locate fixture " + name);
    }

    private static final class InMemoryResolver implements EntityIdentityResolver {
        private final Map<String, DomainIdentity> identities = new HashMap<>();

        @Override
        public DomainIdentity resolve(com.morpheus.domain.provider.ProviderId providerId, String entityType, String externalId) {
            String key = providerId + "|" + entityType + "|" + externalId;
            return identities.computeIfAbsent(key, ignored -> DomainIdentity.generate());
        }
    }
}
