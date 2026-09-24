package com.morpheus.provider.openspec;

import com.morpheus.application.identity.EntityIdentityResolver;
import com.morpheus.application.read.ProviderReadRequest;
import com.morpheus.application.read.ProviderReadResult;
import com.morpheus.application.read.ReadCategory;
import com.morpheus.application.read.ReadCategoryStatus;
import com.morpheus.application.security.ServerLocationDisclosure;
import com.morpheus.domain.diagnostic.Diagnostic;
import com.morpheus.domain.diagnostic.DiagnosticCode;
import com.morpheus.domain.identity.DomainIdentity;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.provider.ProviderId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
    void aFileWithoutTitleIsNamedRelativeToTheWorkspaceAndItsCauseIsReported(@TempDir Path workspace) throws Exception {
        writeValidSpecification(workspace, "alpha");
        writeValidSpecification(workspace, "beta");
        Path broken = workspace.resolve("openspec/specs/broken/spec.md");
        Files.createDirectories(broken.getParent());
        Files.writeString(broken, """
                ## Requirements

                ### Requirement: Untitled
                The system SHALL reject a specification without a title.
                """);

        var result = new OpenSpecSpecificationContentReader().read(
                request(workspace, EnumSet.of(ReadCategory.CURRENT_SPECIFICATIONS, ReadCategory.REQUIREMENTS)),
                new StableTestIdentityResolver());

        Diagnostic failure = singleInvalidSource(result);
        assertEquals(Optional.of("openspec/specs/broken/spec.md"), failure.source());
        assertTrue(failure.message().contains("openspec/specs/broken/spec.md"), failure.message());
        assertTrue(failure.message().contains("has no title"), failure.message());
        assertEquals("IllegalArgumentException", failure.details().get("exception"));
        assertNamesNoServerLocation(failure);
        assertEquals(ReadCategoryStatus.FAILED, status(result, ReadCategory.CURRENT_SPECIFICATIONS));
        assertEquals(0, result.content().orElseThrow().specifications().size(),
                "a failed group publishes nothing, so the two valid files read before it are not partially kept");
    }

    @Test
    void aPlatformFailureNamingAnAbsolutePathIsAttributedWithoutRelayingThatPath(@TempDir Path workspace)
            throws Exception {
        writeValidSpecification(workspace, "alpha");
        String absolute = workspace.resolve("openspec/specs/alpha/spec.md").toAbsolutePath().toString();
        EntityIdentityResolver failing = (providerId, entityType, externalId) -> {
            throw new UncheckedIOException(new NoSuchFileException(absolute));
        };

        var result = new OpenSpecSpecificationContentReader().read(
                request(workspace, EnumSet.of(ReadCategory.CURRENT_SPECIFICATIONS)),
                failing);

        Diagnostic failure = singleInvalidSource(result);
        assertEquals(Optional.of("openspec/specs/alpha/spec.md"), failure.source());
        assertTrue(failure.message().contains("openspec/specs/alpha/spec.md"), failure.message());
        assertTrue(failure.message().contains("UncheckedIOException"), failure.message());
        assertFalse(failure.message().contains(absolute), failure.message());
        assertEquals("UncheckedIOException", failure.details().get("exception"));
        assertNamesNoServerLocation(failure);
        assertEquals(ReadCategoryStatus.FAILED, status(result, ReadCategory.CURRENT_SPECIFICATIONS));
    }

    @Test
    void aRefusedReadKeepsItsCauseAndNamesTheFileWithForwardSlashesOnEveryPlatform(@TempDir Path workspace)
            throws Exception {
        writeValidSpecification(workspace, "alpha");
        Files.write(workspace.resolve("openspec/specs/alpha/spec.md"), new byte[] {'#', ' ', (byte) 0xC3, (byte) 0x28});

        var result = new OpenSpecSpecificationContentReader().read(
                request(workspace, EnumSet.of(ReadCategory.CURRENT_SPECIFICATIONS)),
                new StableTestIdentityResolver());

        Diagnostic failure = singleInvalidSource(result);
        assertEquals(Optional.of("openspec/specs/alpha/spec.md"), failure.source());
        assertTrue(failure.message().contains(
                "openspec/specs/alpha/spec.md: workspace file is not valid UTF-8: openspec/specs/alpha/spec.md"),
                failure.message());
        assertNamesNoServerLocation(failure);
    }

    @Test
    void aProposalWithoutIntentIsNamedAsTheFileAtFault(@TempDir Path workspace) throws Exception {
        writeChange(workspace, "# Proposal: Missing intent\n\n## Scope\n\n- nothing\n", null, null);

        Diagnostic failure = singleInvalidSource(new OpenSpecSpecificationContentReader().read(
                request(workspace, EnumSet.of(ReadCategory.CHANGES)), new StableTestIdentityResolver()));

        assertEquals(Optional.of("openspec/changes/demo/proposal.md"), failure.source());
        assertTrue(failure.message().contains("openspec/changes/demo/proposal.md: OpenSpec change has no Intent"),
                failure.message());
        assertNamesNoServerLocation(failure);
    }

    @Test
    void aDesignDecisionWithoutBodyIsNamedAsTheDesignFileNotTheProposal(@TempDir Path workspace) throws Exception {
        writeChange(workspace, VALID_PROPOSAL, "# Design\n\n## Decisions\n\n### Empty decision\n", null);

        Diagnostic failure = singleInvalidSource(new OpenSpecSpecificationContentReader().read(
                request(workspace, EnumSet.of(ReadCategory.DESIGN_DECISIONS)), new StableTestIdentityResolver()));

        assertEquals(Optional.of("openspec/changes/demo/design.md"), failure.source());
        assertTrue(failure.message().contains("openspec/changes/demo/design.md: OpenSpec design decision has no body"),
                failure.message());
        assertNamesNoServerLocation(failure);
    }

    @Test
    void aMalformedRequirementDeltaIsNamedAsItsDeltaFile(@TempDir Path workspace) throws Exception {
        writeChange(workspace, VALID_PROPOSAL, null, """
                # Delta

                ## ADDED Requirements

                ### Requirement: Half a scenario
                The system SHALL describe a complete scenario.

                #### Scenario: Missing then
                - **WHEN** the delta is read
                """);

        var result = new OpenSpecSpecificationContentReader().read(
                request(workspace, EnumSet.of(ReadCategory.REQUIREMENT_DELTAS)), new StableTestIdentityResolver());

        Diagnostic failure = singleInvalidSource(result);
        assertEquals(Optional.of("openspec/changes/demo/specs/auth/spec.md"), failure.source());
        assertTrue(failure.message().contains("openspec/changes/demo/specs/auth/spec.md: OpenSpec delta scenario"),
                failure.message());
        assertEquals("requirement-deltas", failure.details().get("group"));
        assertNamesNoServerLocation(failure);
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

    private static void writeValidSpecification(Path workspace, String key) throws Exception {
        Path spec = workspace.resolve("openspec/specs/" + key + "/spec.md");
        Files.createDirectories(spec.getParent());
        Files.writeString(workspace.resolve("openspec/config.yaml"), "schema: spec-driven\n");
        Files.writeString(spec, """
                # Specification %s

                ## Requirements

                ### Requirement: Readable %s
                The system SHALL read %s.

                #### Scenario: Read %s
                - **WHEN** the reader runs
                - **THEN** %s is normalized
                """.formatted(key, key, key, key, key));
    }

    private static final String VALID_PROPOSAL = "# Proposal: Demo\n\n## Intent\n\nExercise attribution.\n";

    private static void writeChange(Path workspace, String proposal, String design, String delta) throws Exception {
        Path change = Files.createDirectories(workspace.resolve("openspec/changes/demo"));
        Files.writeString(workspace.resolve("openspec/config.yaml"), "schema: spec-driven\n");
        Files.writeString(change.resolve("proposal.md"), proposal);
        if (design != null) {
            Files.writeString(change.resolve("design.md"), design);
        }
        if (delta != null) {
            Path deltaFile = change.resolve("specs/auth/spec.md");
            Files.createDirectories(deltaFile.getParent());
            Files.writeString(deltaFile, delta);
        }
    }

    private static Diagnostic singleInvalidSource(ProviderReadResult result) {
        List<Diagnostic> failures = result.diagnostics().stream()
                .filter(diagnostic -> diagnostic.code() == DiagnosticCode.INVALID_SOURCE)
                .toList();
        assertEquals(1, failures.size(), failures.toString());
        return failures.getFirst();
    }

    private static void assertNamesNoServerLocation(Diagnostic diagnostic) {
        assertFalse(ServerLocationDisclosure.namesAServerLocation(diagnostic.message()), diagnostic.message());
        diagnostic.source().ifPresent(source ->
                assertFalse(ServerLocationDisclosure.namesAServerLocation(source), source));
        diagnostic.details().values().forEach(value ->
                assertFalse(ServerLocationDisclosure.namesAServerLocation(value), value));
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
