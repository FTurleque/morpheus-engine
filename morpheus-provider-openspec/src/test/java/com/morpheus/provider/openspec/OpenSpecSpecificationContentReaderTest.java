package com.morpheus.provider.openspec;

import com.morpheus.application.identity.EntityIdentityResolver;
import com.morpheus.application.read.ProviderReadRequest;
import com.morpheus.application.read.ReadCategory;
import com.morpheus.application.read.ReadCategoryStatus;
import com.morpheus.domain.diagnostic.DiagnosticCode;
import com.morpheus.domain.identity.DomainIdentity;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.provider.ProviderId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenSpecSpecificationContentReaderTest {

    @Test
    void basicFixtureReportsAllImplementedM2CategoriesAsRead() {
        var result = new OpenSpecSpecificationContentReader().read(
                request(fixture("openspec-basic"), EnumSet.of(
                        ReadCategory.CURRENT_SPECIFICATIONS,
                        ReadCategory.REQUIREMENTS,
                        ReadCategory.SCENARIOS,
                        ReadCategory.CHANGES,
                        ReadCategory.REQUIREMENT_DELTAS,
                        ReadCategory.CONSTRAINTS,
                        ReadCategory.DESIGN_DECISIONS,
                        ReadCategory.IMPLEMENTATION_TASKS)),
                new StableTestIdentityResolver());

        assertTrue(result.content().isPresent());
        assertEquals(ReadCategoryStatus.READ, status(result, ReadCategory.CURRENT_SPECIFICATIONS));
        assertEquals(ReadCategoryStatus.READ, status(result, ReadCategory.REQUIREMENTS));
        assertEquals(ReadCategoryStatus.READ, status(result, ReadCategory.SCENARIOS));
        assertEquals(ReadCategoryStatus.READ, status(result, ReadCategory.CHANGES));
        assertEquals(ReadCategoryStatus.READ, status(result, ReadCategory.REQUIREMENT_DELTAS));
        assertEquals(ReadCategoryStatus.READ, status(result, ReadCategory.CONSTRAINTS));
        assertEquals(ReadCategoryStatus.READ, status(result, ReadCategory.DESIGN_DECISIONS));
        assertEquals(ReadCategoryStatus.READ, status(result, ReadCategory.IMPLEMENTATION_TASKS));

        assertEquals(1, count(result, ReadCategory.CURRENT_SPECIFICATIONS));
        assertEquals(2, count(result, ReadCategory.REQUIREMENTS));
        assertEquals(2, count(result, ReadCategory.SCENARIOS));
        assertEquals(1, count(result, ReadCategory.CHANGES));
        assertEquals(3, count(result, ReadCategory.REQUIREMENT_DELTAS));
        assertEquals(2, count(result, ReadCategory.CONSTRAINTS));
        assertEquals(2, count(result, ReadCategory.DESIGN_DECISIONS));
        assertEquals(8, count(result, ReadCategory.IMPLEMENTATION_TASKS));
        assertTrue(result.diagnostics().isEmpty());
    }

    @Test
    void partialFixtureKeepsReadableRequirementsAndMarksScenariosPartial() {
        var result = new OpenSpecSpecificationContentReader().read(
                request(fixture("openspec-partial"), EnumSet.of(
                        ReadCategory.CURRENT_SPECIFICATIONS,
                        ReadCategory.REQUIREMENTS,
                        ReadCategory.SCENARIOS,
                        ReadCategory.CHANGES)),
                new StableTestIdentityResolver());

        var content = result.content().orElseThrow();
        assertEquals(1, content.specifications().size());
        assertEquals(2, content.requirements().size());
        assertEquals(1, content.scenarios().size());

        assertEquals(ReadCategoryStatus.READ, status(result, ReadCategory.CURRENT_SPECIFICATIONS));
        assertEquals(ReadCategoryStatus.READ, status(result, ReadCategory.REQUIREMENTS));
        assertEquals(ReadCategoryStatus.PARTIAL, status(result, ReadCategory.SCENARIOS));
        assertEquals(ReadCategoryStatus.ABSENT, status(result, ReadCategory.CHANGES));
        assertTrue(result.diagnostics().stream().anyMatch(diagnostic -> diagnostic.code() == DiagnosticCode.PARTIAL_INGESTION));
        assertFalse(result.diagnostics().stream().anyMatch(diagnostic -> diagnostic.code() == DiagnosticCode.INVALID_SOURCE));
    }

    @Test
    void unsupportedSemanticCategoriesAreExplicitAndScenarioIsNeverAcceptanceCriterion() {
        var result = new OpenSpecSpecificationContentReader().read(
                request(fixture("openspec-basic"), EnumSet.of(
                        ReadCategory.SCENARIOS,
                        ReadCategory.ACCEPTANCE_CRITERIA,
                        ReadCategory.EXTERNAL_REFERENCES,
                        ReadCategory.ARCHIVES)),
                new StableTestIdentityResolver());

        assertEquals(ReadCategoryStatus.READ, status(result, ReadCategory.SCENARIOS));
        assertEquals(ReadCategoryStatus.UNSUPPORTED, status(result, ReadCategory.ACCEPTANCE_CRITERIA));
        assertEquals(ReadCategoryStatus.UNSUPPORTED, status(result, ReadCategory.EXTERNAL_REFERENCES));
        assertEquals(ReadCategoryStatus.UNSUPPORTED, status(result, ReadCategory.ARCHIVES));
        assertEquals(3, result.diagnostics().stream()
                .filter(diagnostic -> diagnostic.code() == DiagnosticCode.OPTIONAL_CAPABILITY_UNAVAILABLE)
                .count());
    }

    @Test
    void malformedCurrentSourceFailsRequestedCurrentCategoriesWithoutThrowing(@TempDir Path workspace) throws Exception {
        Path spec = workspace.resolve("openspec/specs/demo/spec.md");
        Files.createDirectories(spec.getParent());
        Files.writeString(workspace.resolve("openspec/config.yaml"), "schema: spec-driven\n");
        Files.writeString(spec, """
                # Broken Specification

                ## Requirements

                ### Requirement: Broken scenario
                The system SHALL expose an invalid scenario for this test.

                #### Scenario: Missing then
                - **WHEN** reading the source
                """);

        var result = new OpenSpecSpecificationContentReader().read(
                request(workspace, EnumSet.of(
                        ReadCategory.CURRENT_SPECIFICATIONS,
                        ReadCategory.REQUIREMENTS,
                        ReadCategory.SCENARIOS)),
                new StableTestIdentityResolver());

        assertTrue(result.content().isPresent());
        assertEquals(ReadCategoryStatus.FAILED, status(result, ReadCategory.CURRENT_SPECIFICATIONS));
        assertEquals(ReadCategoryStatus.FAILED, status(result, ReadCategory.REQUIREMENTS));
        assertEquals(ReadCategoryStatus.FAILED, status(result, ReadCategory.SCENARIOS));
        assertTrue(result.diagnostics().stream().anyMatch(diagnostic -> diagnostic.code() == DiagnosticCode.INVALID_SOURCE));
        assertFalse(result.diagnostics().stream().anyMatch(diagnostic -> diagnostic.code() == DiagnosticCode.PARTIAL_INGESTION));
    }

    @Test
    void unsupportedProviderProbeReturnsNoContentAndExplicitFailure(@TempDir Path workspace) throws Exception {
        Path openspec = workspace.resolve("openspec");
        Files.createDirectories(openspec);
        Files.writeString(openspec.resolve("config.yaml"), "schema: research-first\n");

        var result = new OpenSpecSpecificationContentReader().read(
                request(workspace, Set.of(ReadCategory.REQUIREMENTS)),
                new StableTestIdentityResolver());

        assertTrue(result.content().isEmpty());
        assertEquals(ReadCategoryStatus.FAILED, status(result, ReadCategory.REQUIREMENTS));
        assertTrue(result.diagnostics().stream().anyMatch(
                diagnostic -> diagnostic.code() == DiagnosticCode.UNSUPPORTED_PROVIDER_SCHEMA));
    }

    @Test
    void budgetFailureDiscardsAlreadyNormalizedGroups(@TempDir Path workspace) throws Exception {
        Path spec = workspace.resolve("openspec/specs/demo/spec.md");
        Path proposal = workspace.resolve("openspec/changes/oversized/proposal.md");
        Files.createDirectories(spec.getParent());
        Files.createDirectories(proposal.getParent());
        Files.writeString(workspace.resolve("openspec/config.yaml"), "schema: spec-driven\n");
        Files.writeString(spec, """
                # Valid Specification

                ## Requirements

                ### Requirement: Preserve atomicity
                The system SHALL discard partial provider snapshots.

                #### Scenario: Oversized later group
                - **WHEN** a later group exceeds its budget
                - **THEN** no earlier group is published
                """);
        Files.writeString(proposal, "x".repeat((1024 * 1024) + 1));

        var result = new OpenSpecSpecificationContentReader().read(
                request(workspace, EnumSet.of(
                        ReadCategory.CURRENT_SPECIFICATIONS,
                        ReadCategory.REQUIREMENTS,
                        ReadCategory.CHANGES)),
                new StableTestIdentityResolver());

        assertTrue(result.content().isEmpty());
        assertEquals(ReadCategoryStatus.FAILED, status(result, ReadCategory.CURRENT_SPECIFICATIONS));
        assertEquals(ReadCategoryStatus.FAILED, status(result, ReadCategory.REQUIREMENTS));
        assertEquals(ReadCategoryStatus.FAILED, status(result, ReadCategory.CHANGES));
        assertTrue(result.diagnostics().stream().anyMatch(
                diagnostic -> diagnostic.code() == DiagnosticCode.INVALID_SOURCE));
        assertFalse(result.diagnostics().stream().anyMatch(
                diagnostic -> diagnostic.code() == DiagnosticCode.PARTIAL_INGESTION));
    }

    @Test
    void aSkippedRequirementDeltaMakesTheCategoryPartialInsteadOfRead(@TempDir Path workspace) throws Exception {
        Path change = workspace.resolve("openspec/changes/early");
        Path delta = change.resolve("specs/auth-session/spec.md");
        Files.createDirectories(delta.getParent());
        Files.writeString(workspace.resolve("openspec/config.yaml"), "schema: spec-driven\n");
        Files.writeString(change.resolve("proposal.md"), """
                # Proposal: Early requirement

                ## Intent

                Show that a requirement outside a delta section is named.
                """);
        Files.writeString(delta, """
                # Delta

                ### Requirement: Orphan requirement
                The system SHALL be read.

                ## ADDED Requirements

                ### Requirement: Placed requirement
                The system SHALL be placed.
                """);

        var result = new OpenSpecSpecificationContentReader().read(
                request(workspace, EnumSet.of(ReadCategory.CHANGES, ReadCategory.REQUIREMENT_DELTAS)),
                new StableTestIdentityResolver());

        var report = result.report(ReadCategory.REQUIREMENT_DELTAS).orElseThrow();
        assertEquals(ReadCategoryStatus.PARTIAL, report.status());
        assertEquals(1, report.itemCount());
        assertEquals(java.util.List.of(DiagnosticCode.PARTIAL_INGESTION), report.diagnosticCodes());
        assertEquals(ReadCategoryStatus.READ, status(result, ReadCategory.CHANGES));
        assertEquals(1, result.content().orElseThrow().requirementDeltas().size());
        assertTrue(result.diagnostics().stream().anyMatch(diagnostic ->
                diagnostic.code() == DiagnosticCode.PARTIAL_INGESTION
                        && "Orphan requirement".equals(diagnostic.details().get("requirement"))));
    }

    @Test
    void aLevelTwoLineInsideACodeFenceLeavesTheCategoryRead(@TempDir Path workspace) throws Exception {
        Path change = workspace.resolve("openspec/changes/fenced");
        Path delta = change.resolve("specs/renderer/spec.md");
        Files.createDirectories(delta.getParent());
        Files.writeString(workspace.resolve("openspec/config.yaml"), "schema: spec-driven\n");
        Files.writeString(change.resolve("proposal.md"), """
                # Proposal: Fenced example

                ## Intent

                Show a Markdown example inside a requirement.
                """);
        Files.writeString(delta, """
                # Delta

                ## ADDED Requirements

                ### Requirement: Render headings
                The renderer SHALL render level-two headings, for example:
                ```markdown
                ## Overview
                ```

                ### Requirement: Render lists
                The renderer SHALL render lists.
                """);

        var result = new OpenSpecSpecificationContentReader().read(
                request(workspace, EnumSet.of(ReadCategory.CHANGES, ReadCategory.REQUIREMENT_DELTAS)),
                new StableTestIdentityResolver());

        assertEquals(ReadCategoryStatus.READ, status(result, ReadCategory.REQUIREMENT_DELTAS));
        assertEquals(2, count(result, ReadCategory.REQUIREMENT_DELTAS));
        assertTrue(result.diagnostics().isEmpty());
    }

    private ProviderReadRequest request(Path workspace, Set<ReadCategory> categories) {
        return new ProviderReadRequest(workspace, ProjectSpecificationId.generate(), categories);
    }

    private ReadCategoryStatus status(com.morpheus.application.read.ProviderReadResult result, ReadCategory category) {
        return result.report(category).orElseThrow().status();
    }

    private int count(com.morpheus.application.read.ProviderReadResult result, ReadCategory category) {
        return result.report(category).orElseThrow().itemCount();
    }

    private Path fixture(String name) {
        Path current = Path.of("").toAbsolutePath().normalize();
        Path fromRoot = current.resolve("experiments/m0/fixtures").resolve(name);
        if (Files.isDirectory(fromRoot)) {
            return fromRoot;
        }

        Path fromModule = current.resolve("../experiments/m0/fixtures").normalize().resolve(name);
        if (Files.isDirectory(fromModule)) {
            return fromModule;
        }

        throw new IllegalStateException("M0 fixture not found: " + name + " from " + current);
    }

    private static final class StableTestIdentityResolver implements EntityIdentityResolver {
        private final Map<String, DomainIdentity> identities = new HashMap<>();

        @Override
        public DomainIdentity resolve(ProviderId providerId, String entityType, String externalId) {
            String key = providerId.value() + "|" + entityType + "|" + externalId;
            return identities.computeIfAbsent(key, ignored -> DomainIdentity.generate());
        }
    }
}
