package com.morpheus.provider.openspec;

import com.morpheus.application.identity.EntityIdentityResolver;
import com.morpheus.application.security.ServerLocationDisclosure;
import com.morpheus.domain.diagnostic.Diagnostic;
import com.morpheus.domain.diagnostic.DiagnosticCode;
import com.morpheus.domain.diagnostic.DiagnosticSeverity;
import com.morpheus.domain.identity.DomainIdentity;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.provider.ProviderId;
import com.morpheus.domain.requirement.RequirementDeltaKind;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenSpecRequirementDeltaReaderTest {

    @Test
    void normalizesModifiedAndAddedRequirementDeltasFromM0Fixture() {
        var result = new OpenSpecRequirementDeltaReader().read(
                fixture("openspec-basic"),
                new StableTestIdentityResolver());

        assertEquals(3, result.requirementDeltas().size());
        assertEquals(1, result.requirementDeltas().stream()
                .filter(delta -> delta.kind() == RequirementDeltaKind.MODIFIED)
                .count());
        assertEquals(2, result.requirementDeltas().stream()
                .filter(delta -> delta.kind() == RequirementDeltaKind.ADDED)
                .count());
        assertEquals(0, result.requirementDeltas().stream()
                .filter(delta -> delta.kind() == RequirementDeltaKind.REMOVED)
                .count());
        assertEquals(5, result.requirementDeltas().stream().mapToInt(delta -> delta.scenarios().size()).sum());
        assertEquals(8, result.evidence().size());
        assertTrue(result.diagnostics().isEmpty());
        assertEquals(0, result.skippedRequirements());

        var modified = result.requirementDeltas().stream()
                .filter(delta -> delta.kind() == RequirementDeltaKind.MODIFIED)
                .findFirst()
                .orElseThrow();
        assertEquals("auth-session/session-expiration", modified.key().orElseThrow());
        assertEquals("Session expiration", modified.title());
        assertEquals(2, modified.scenarios().size());

        var addedKeys = result.requirementDeltas().stream()
                .filter(delta -> delta.kind() == RequirementDeltaKind.ADDED)
                .map(delta -> delta.key().orElseThrow())
                .sorted()
                .toList();
        assertEquals(
                java.util.List.of(
                        "auth-session/explicit-remember-me-opt-in",
                        "auth-session/persistent-credential-revocation"),
                addedKeys);
    }

    @Test
    void modifiedDeltaReusesCurrentLogicalRequirementIdentityWithoutReplacingBaselineContent() {
        ProjectSpecificationId projectId = ProjectSpecificationId.generate();
        StableTestIdentityResolver identities = new StableTestIdentityResolver();
        Path workspace = fixture("openspec-basic");

        var current = new OpenSpecCurrentSpecificationReader().read(workspace, projectId, identities);
        var deltas = new OpenSpecRequirementDeltaReader().read(workspace, identities);

        var currentExpiration = current.requirements().stream()
                .filter(requirement -> requirement.key().orElseThrow().equals("auth-session/session-expiration"))
                .findFirst()
                .orElseThrow();
        var modifiedExpiration = deltas.requirementDeltas().stream()
                .filter(delta -> delta.key().orElseThrow().equals("auth-session/session-expiration"))
                .findFirst()
                .orElseThrow();

        assertEquals(currentExpiration.id(), modifiedExpiration.requirementId());
        assertNotEquals(currentExpiration.statement(), modifiedExpiration.statement().orElseThrow());
        assertTrue(currentExpiration.statement().contains("30 minutes of inactivity"));
        assertTrue(modifiedExpiration.statement().orElseThrow().contains("remember-me session"));
    }

    @Test
    void deltaOccurrenceIdentityIsDistinctFromLogicalRequirementIdentityAndHasSourceEvidence() {
        var result = new OpenSpecRequirementDeltaReader().read(
                fixture("openspec-basic"),
                new StableTestIdentityResolver());

        var modified = result.requirementDeltas().stream()
                .filter(delta -> delta.kind() == RequirementDeltaKind.MODIFIED)
                .findFirst()
                .orElseThrow();

        assertNotEquals(modified.id().value(), modified.requirementId().value());
        assertEquals(
                "requirement-delta:add-remember-me:modified:auth-session/session-expiration",
                modified.provenance().externalId().orElseThrow());
        assertEquals(
                "file:openspec/changes/add-remember-me/specs/auth-session/spec.md",
                modified.provenance().source().toString());
        assertTrue(result.evidence().stream()
                .anyMatch(item -> item.id().equals(modified.provenance().evidenceId())));
    }

    @Test
    void supportsRemovedRequirementWithoutInventingStatement(@TempDir Path workspace) throws Exception {
        Path openspec = workspace.resolve("openspec");
        Path deltaFile = openspec.resolve("changes/remove-legacy/specs/auth-session/spec.md");
        Files.createDirectories(deltaFile.getParent());
        Files.writeString(openspec.resolve("config.yaml"), "schema: spec-driven\n");
        Files.writeString(deltaFile, """
                # Authentication Session Delta

                ## REMOVED Requirements

                ### Requirement: Legacy session warning
                """);

        var result = new OpenSpecRequirementDeltaReader().read(workspace, new StableTestIdentityResolver());

        assertEquals(1, result.requirementDeltas().size());
        var removed = result.requirementDeltas().getFirst();
        assertEquals(RequirementDeltaKind.REMOVED, removed.kind());
        assertEquals("auth-session/legacy-session-warning", removed.key().orElseThrow());
        assertTrue(removed.statement().isEmpty());
        assertTrue(removed.scenarios().isEmpty());
        assertEquals(1, result.evidence().size());
    }

    @Test
    void aRequirementUnderAnUnrecognizedSectionDoesNotInheritTheKindOfThePreviousSection(@TempDir Path workspace)
            throws Exception {
        writeDelta(workspace, "remove-legacy", """
                # Authentication Session Delta

                ## REMOVED Requirements

                ### Requirement: Legacy session warning

                ## Notes

                ### Requirement: Keep the audit trail
                The system SHALL keep the audit trail.
                """);

        var result = new OpenSpecRequirementDeltaReader().read(workspace, new StableTestIdentityResolver());

        assertEquals(List.of("REMOVED Legacy session warning"), result.requirementDeltas().stream()
                .map(delta -> delta.kind() + " " + delta.title())
                .toList());
        assertEquals(1, result.skippedRequirements());

        String source = "openspec/changes/remove-legacy/specs/auth-session/spec.md";
        Diagnostic section = only(result.diagnostics(), DiagnosticCode.UNRECOGNIZED_SECTION);
        assertEquals(DiagnosticSeverity.WARNING, section.severity());
        assertEquals("Notes", section.details().get("section"));
        assertEquals("7", section.details().get("line"));
        assertEquals("remove-legacy", section.details().get("change"));
        assertEquals(source, section.source().orElseThrow());

        Diagnostic skipped = only(result.diagnostics(), DiagnosticCode.PARTIAL_INGESTION);
        assertEquals(DiagnosticSeverity.WARNING, skipped.severity());
        assertEquals("Keep the audit trail", skipped.details().get("requirement"));
        assertEquals("9", skipped.details().get("line"));
        assertEquals(source, skipped.source().orElseThrow());

        for (Diagnostic diagnostic : result.diagnostics()) {
            assertFalse(ServerLocationDisclosure.namesAServerLocation(diagnostic.message()), diagnostic.message());
            assertFalse(ServerLocationDisclosure.namesAServerLocation(diagnostic.source().orElseThrow()));
            diagnostic.details().values().forEach(value ->
                    assertFalse(ServerLocationDisclosure.namesAServerLocation(value), value));
        }
    }

    @Test
    void aRequirementBeforeAnyRecognizedSectionIsNamedInsteadOfSilentlyDropped(@TempDir Path workspace)
            throws Exception {
        writeDelta(workspace, "early", """
                # Delta

                ### Requirement: Orphan requirement
                The system SHALL be read.

                ## ADDED Requirements

                ### Requirement: Placed requirement
                The system SHALL be placed.
                """);

        var result = new OpenSpecRequirementDeltaReader().read(workspace, new StableTestIdentityResolver());

        assertEquals(List.of("ADDED Placed requirement"), result.requirementDeltas().stream()
                .map(delta -> delta.kind() + " " + delta.title())
                .toList());
        assertEquals(1, result.skippedRequirements());
        Diagnostic skipped = only(result.diagnostics(), DiagnosticCode.PARTIAL_INGESTION);
        assertEquals("Orphan requirement", skipped.details().get("requirement"));
        assertEquals("3", skipped.details().get("line"));
        assertTrue(result.diagnostics().stream().noneMatch(
                diagnostic -> diagnostic.code() == DiagnosticCode.UNRECOGNIZED_SECTION));
    }

    @Test
    void theRenamedSectionIsRecognizedAndEndsThePreviousSectionWithoutAWarning(@TempDir Path workspace)
            throws Exception {
        writeDelta(workspace, "rename-login", """
                # Delta

                ## ADDED Requirements

                ### Requirement: Session audit
                The system SHALL audit sessions.

                ## RENAMED Requirements

                - FROM: `### Requirement: Login`
                - TO: `### Requirement: User authentication`
                """);

        var result = new OpenSpecRequirementDeltaReader().read(workspace, new StableTestIdentityResolver());

        assertEquals(List.of("ADDED Session audit"), result.requirementDeltas().stream()
                .map(delta -> delta.kind() + " " + delta.title())
                .toList());
        assertEquals(
                "The system SHALL audit sessions.",
                result.requirementDeltas().getFirst().statement().orElseThrow());
        assertTrue(result.diagnostics().isEmpty());
        assertEquals(0, result.skippedRequirements());
    }

    @Test
    void aLevelTwoHeadingInsideARequirementStillEndsItsBody(@TempDir Path workspace) throws Exception {
        writeDelta(workspace, "truncated", """
                # Delta

                ## ADDED Requirements

                ### Requirement: Truncated requirement
                The system SHALL keep this sentence.

                ## Rationale
                This sentence was never part of the requirement.
                """);

        var result = new OpenSpecRequirementDeltaReader().read(workspace, new StableTestIdentityResolver());

        assertEquals(1, result.requirementDeltas().size());
        assertEquals(
                "The system SHALL keep this sentence.",
                result.requirementDeltas().getFirst().statement().orElseThrow());
        assertEquals("Rationale", only(result.diagnostics(), DiagnosticCode.UNRECOGNIZED_SECTION)
                .details().get("section"));
        assertEquals(0, result.skippedRequirements());
    }

    @Test
    void theStateMatrixDeltasReadExactlyAsBeforeWithoutAnyDiagnostic() {
        var result = new OpenSpecRequirementDeltaReader().read(
                fixture("openspec-state-matrix"),
                new StableTestIdentityResolver());

        assertEquals(
                List.of("MODIFIED Session expiration", "MODIFIED Session expiration"),
                result.requirementDeltas().stream()
                        .map(delta -> delta.kind() + " " + delta.title())
                        .toList());
        assertTrue(result.diagnostics().isEmpty());
        assertEquals(0, result.skippedRequirements());
    }

    @Test
    void aLevelTwoLineInsideACodeFenceNeitherEndsTheSectionNorWarns(@TempDir Path workspace) throws Exception {
        writeDelta(workspace, "fenced", """
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

        var result = new OpenSpecRequirementDeltaReader().read(workspace, new StableTestIdentityResolver());

        assertEquals(List.of("ADDED Render headings", "ADDED Render lists"), result.requirementDeltas().stream()
                .map(delta -> delta.kind() + " " + delta.title())
                .toList());
        assertEquals(
                "The renderer SHALL render level-two headings, for example: ```markdown",
                result.requirementDeltas().getFirst().statement().orElseThrow());
        assertTrue(result.diagnostics().isEmpty());
        assertEquals(0, result.skippedRequirements());
    }

    @Test
    void aShorterRunOfTheSameCharacterDoesNotCloseAFence(@TempDir Path workspace) throws Exception {
        assertOnlyTheSectionAfterTheFenceIsNamed(workspace, """
                # Delta

                ## ADDED Requirements

                ### Requirement: Render headings
                The renderer SHALL render level-two headings.

                ~~~~
                ## Overview
                ~~~
                ## Still inside
                ~~~~

                ## Notes

                ### Requirement: Keep the audit trail
                The system SHALL keep the audit trail.
                """);
    }

    @Test
    void aRunOfTheOtherCharacterDoesNotCloseAFence(@TempDir Path workspace) throws Exception {
        assertOnlyTheSectionAfterTheFenceIsNamed(workspace, """
                # Delta

                ## ADDED Requirements

                ### Requirement: Render headings
                The renderer SHALL render level-two headings.

                ~~~~
                ## Overview
                ````
                ## Still inside
                ~~~~

                ## Notes

                ### Requirement: Keep the audit trail
                The system SHALL keep the audit trail.
                """);
    }

    @Test
    void aFenceRunFollowedByTextDoesNotCloseAFence(@TempDir Path workspace) throws Exception {
        assertOnlyTheSectionAfterTheFenceIsNamed(workspace, """
                # Delta

                ## ADDED Requirements

                ### Requirement: Render headings
                The renderer SHALL render level-two headings.

                ```
                ## Overview
                ``` not a closing fence
                ## Still inside
                ```

                ## Notes

                ### Requirement: Keep the audit trail
                The system SHALL keep the audit trail.
                """);
    }

    @Test
    void aFenceIsRecognizedAtAnyIndentationAsUpstreamOpenSpecDoes(@TempDir Path workspace) throws Exception {
        writeDelta(workspace, "indented", """
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

        var result = new OpenSpecRequirementDeltaReader().read(workspace, new StableTestIdentityResolver());

        assertEquals(List.of("ADDED Render headings", "ADDED Render lists"), result.requirementDeltas().stream()
                .map(delta -> delta.kind() + " " + delta.title())
                .toList());
        assertEquals(
                "The renderer SHALL render level-two headings, for example: ```markdown",
                result.requirementDeltas().getFirst().statement().orElseThrow());
        assertTrue(result.diagnostics().isEmpty());
        assertEquals(0, result.skippedRequirements());
    }

    @Test
    void aNonBreakingSpaceBeforeAFenceIsWhitespaceAsInUpstreamOpenSpec(@TempDir Path workspace) throws Exception {
        writeDelta(workspace, "nbsp", String.join("\n",
                "# Delta",
                "",
                "## ADDED Requirements",
                "",
                "### Requirement: Render headings",
                "The renderer SHALL render level-two headings, for example:",
                "\u00A0```markdown",
                "## Overview",
                "\u00A0```",
                "",
                "### Requirement: Render lists",
                "The renderer SHALL render lists.",
                ""));

        var result = new OpenSpecRequirementDeltaReader().read(workspace, new StableTestIdentityResolver());

        assertEquals(List.of("ADDED Render headings", "ADDED Render lists"), result.requirementDeltas().stream()
                .map(delta -> delta.kind() + " " + delta.title())
                .toList());
        assertEquals(
                "The renderer SHALL render level-two headings, for example: \u00A0```markdown",
                result.requirementDeltas().getFirst().statement().orElseThrow());
        assertTrue(result.diagnostics().isEmpty());
        assertEquals(0, result.skippedRequirements());
    }

    /**
     * Until 30 September 2026 this file read as two REMOVED deltas and no diagnostic: the unclosed fence masked
     * {@code ## Notes}, so the kind was never reset and the second requirement came out as a phantom removal. The fence
     * is now named, and no requirement after its opening line is given a kind.
     */
    @Test
    void anUnclosedFenceIsNamedAndNoRequirementAfterItInheritsAKind(@TempDir Path workspace) throws Exception {
        writeDelta(workspace, "unclosed", """
                # Delta

                ## REMOVED Requirements

                ### Requirement: Legacy session warning

                ```inline``` markers are gone

                ## Notes

                ### Requirement: Keep the audit trail
                The system SHALL keep the audit trail.
                """);

        var result = new OpenSpecRequirementDeltaReader().read(workspace, new StableTestIdentityResolver());

        assertEquals(List.of("REMOVED Legacy session warning"), result.requirementDeltas().stream()
                .map(delta -> delta.kind() + " " + delta.title())
                .toList());
        assertEquals(1, result.skippedRequirements());
        assertEquals(1, result.unclosedCodeFences());

        String source = "openspec/changes/unclosed/specs/auth-session/spec.md";
        Diagnostic fence = only(result.diagnostics(), DiagnosticCode.UNCLOSED_CODE_FENCE);
        assertEquals(DiagnosticSeverity.WARNING, fence.severity());
        assertEquals("7", fence.details().get("line"));
        assertEquals("```", fence.details().get("fence"));
        assertEquals("unclosed", fence.details().get("change"));
        assertEquals(source, fence.source().orElseThrow());

        Diagnostic skipped = only(result.diagnostics(), DiagnosticCode.PARTIAL_INGESTION);
        assertEquals("Keep the audit trail", skipped.details().get("requirement"));
        assertEquals("11", skipped.details().get("line"));
        assertTrue(skipped.message().contains("never closed"), skipped.message());
        assertTrue(result.diagnostics().stream().noneMatch(
                diagnostic -> diagnostic.code() == DiagnosticCode.UNRECOGNIZED_SECTION));
        for (Diagnostic diagnostic : result.diagnostics()) {
            assertFalse(ServerLocationDisclosure.namesAServerLocation(diagnostic.message()), diagnostic.message());
            diagnostic.details().values().forEach(value ->
                    assertFalse(ServerLocationDisclosure.namesAServerLocation(value), value));
        }
    }

    @Test
    void aDeltaSectionAfterAnUnclosedFenceIsMaskedSoItsRequirementIsNamedRatherThanGivenTheKindBeforeTheFence(
            @TempDir Path workspace) throws Exception {
        writeDelta(workspace, "unclosed", """
                # Delta

                ## REMOVED Requirements

                ### Requirement: Legacy session warning

                ~~~ this tilde run opens a fence that never closes

                ## ADDED Requirements

                ### Requirement: Keep the audit trail
                The system SHALL keep the audit trail.
                """);

        var result = new OpenSpecRequirementDeltaReader().read(workspace, new StableTestIdentityResolver());

        assertEquals(List.of("REMOVED Legacy session warning"), result.requirementDeltas().stream()
                .map(delta -> delta.kind() + " " + delta.title())
                .toList());
        assertEquals("Keep the audit trail", only(result.diagnostics(), DiagnosticCode.PARTIAL_INGESTION)
                .details().get("requirement"));
        assertEquals("~~~", only(result.diagnostics(), DiagnosticCode.UNCLOSED_CODE_FENCE).details().get("fence"));
        assertEquals(1, result.skippedRequirements());
    }

    /**
     * The cost of the rule, pinned: 1.2.0 published {@code ADDED A} here, with the right kind, because a delta section
     * heading was read even inside a fence. The heading is now masked like upstream OpenSpec masks it, so the
     * requirement is skipped and named instead; upstream drops it without a word.
     */
    @Test
    void aWellFormedSectionAfterAFenceThatNeverClosesNoLongerPublishesItsRequirement(@TempDir Path workspace)
            throws Exception {
        writeDelta(workspace, "unclosed", """
                ```
                ## ADDED Requirements

                ### Requirement: A
                The system SHALL a.
                """);

        var result = new OpenSpecRequirementDeltaReader().read(workspace, new StableTestIdentityResolver());

        assertTrue(result.requirementDeltas().isEmpty(), result.requirementDeltas().toString());
        Diagnostic skipped = only(result.diagnostics(), DiagnosticCode.PARTIAL_INGESTION);
        assertEquals("A", skipped.details().get("requirement"));
        assertTrue(skipped.message().contains("follows a code fence that is never closed"), skipped.message());
        assertEquals("1", only(result.diagnostics(), DiagnosticCode.UNCLOSED_CODE_FENCE).details().get("line"));
    }

    /**
     * 1.2.0 published {@code ADDED Phantom} out of this closed example. Nothing is published now, and the example's
     * requirement heading is named as skipped: an improvement with an accepted false positive, since the category turns
     * PARTIAL because of a code example.
     */
    @Test
    void aDeltaSectionInAClosedExampleUnderAForeignSectionNamesTheExampleRequirementInsteadOfPublishingIt(
            @TempDir Path workspace) throws Exception {
        writeDelta(workspace, "example", """
                # Delta

                ## ADDED Requirements

                ### Requirement: A
                The system SHALL a.

                ## Notes

                ```markdown
                ## ADDED Requirements
                ### Requirement: Phantom
                ```
                """);

        var result = new OpenSpecRequirementDeltaReader().read(workspace, new StableTestIdentityResolver());

        assertEquals(List.of("ADDED A"), result.requirementDeltas().stream()
                .map(delta -> delta.kind() + " " + delta.title())
                .toList());
        assertEquals("Phantom", only(result.diagnostics(), DiagnosticCode.PARTIAL_INGESTION)
                .details().get("requirement"));
        assertEquals(1, result.skippedRequirements());
        assertEquals(0, result.unclosedCodeFences());
    }

    @Test
    void aRemovedSectionWrittenInACodeExampleDoesNotChangeTheKind(@TempDir Path workspace) throws Exception {
        writeDelta(workspace, "example", """
                # Delta

                ## ADDED Requirements

                ### Requirement: Document delta files
                The guide SHALL show a delta file, for example:
                ```markdown
                ## REMOVED Requirements
                ```

                ### Requirement: Render lists
                The renderer SHALL render lists.
                """);

        var result = new OpenSpecRequirementDeltaReader().read(workspace, new StableTestIdentityResolver());

        assertEquals(List.of("ADDED Document delta files", "ADDED Render lists"), result.requirementDeltas().stream()
                .map(delta -> delta.kind() + " " + delta.title())
                .toList());
        assertTrue(result.diagnostics().isEmpty(), result.diagnostics().toString());
        assertEquals(0, result.skippedRequirements());
        assertEquals(0, result.unclosedCodeFences());
    }

    /**
     * The three delta files the reader reads in the repository, frozen: kinds, keys, statements, scenarios, evidence
     * ranges and diagnostics. The fourth delta file present, under {@code changes/archive/}, is never read. A change to
     * the reader that moves any of this is a change to published deltas and must say so.
     */
    @Test
    void theRepositoryDeltaFixturesReadExactlyAsFrozen() {
        assertEquals("""
                MODIFIED auth-session/session-expiration | Session expiration | The system SHALL expire a standard \
                authenticated session after 30 minutes of inactivity and SHALL preserve a remember-me session according \
                to its persistent-session policy when the user explicitly opted in. | \
                file:openspec/changes/add-remember-me/specs/auth-session/spec.md
                  Expire a standard inactive session | given [an authenticated user session without remember-me enabled, \
                no activity has occurred for 30 minutes] | when the user attempts a protected action | then the system SHALL \
                require authentication again
                  Preserve an opted-in remember-me session | given [an authenticated session created with remember-me \
                explicitly enabled] | when the standard inactivity window expires | then the system MAY restore \
                authentication using the valid persistent credential AND restoration SHALL fail if that credential has \
                been revoked
                ADDED auth-session/explicit-remember-me-opt-in | Explicit remember-me opt-in | The system SHALL enable \
                persistent authentication only when the user explicitly requests remember-me during authentication. | \
                file:openspec/changes/add-remember-me/specs/auth-session/spec.md
                  Default authentication remains non-persistent | given [a user authenticating without selecting \
                remember-me] | when authentication succeeds | then the session SHALL use the standard non-persistent policy
                  User opts into persistent authentication | given [a user authenticating on a trusted device] | when the \
                user explicitly selects remember-me | then the system SHALL create a revocable persistent credential
                ADDED auth-session/persistent-credential-revocation | Persistent credential revocation | The system SHALL \
                revoke the active persistent credential when the user explicitly logs out. | \
                file:openspec/changes/add-remember-me/specs/auth-session/spec.md
                  Logout prevents future restoration | given [a valid persistent credential] | when the user explicitly \
                logs out | then the persistent credential SHALL be revoked AND a later request SHALL NOT restore \
                authentication from that credential
                evidence 5-8 9-15 16-22 25-28 29-34 35-40 41-44 45-50
                diagnostics [] skipped 0 unclosed 0
                """, frozen(fixture("openspec-basic")));
        assertEquals("""
                MODIFIED auth-session/session-expiration | Session expiration | The system SHALL expire an authenticated \
                session after 60 minutes. | file:openspec/changes/extend-timeout/specs/auth-session/spec.md
                  Proposed timeout | given [] | when the proposed timeout passes | then the session SHALL expire
                MODIFIED auth-session/session-expiration | Session expiration | The system SHALL expire an authenticated \
                session after 15 minutes. | file:openspec/changes/shorten-timeout/specs/auth-session/spec.md
                  Proposed timeout | given [] | when the proposed timeout passes | then the session SHALL expire
                evidence 5-8 9-12 5-8 9-12
                diagnostics [] skipped 0 unclosed 0
                """, frozen(fixture("openspec-state-matrix")));
    }

    private String frozen(Path workspace) {
        var result = new OpenSpecRequirementDeltaReader().read(workspace, new StableTestIdentityResolver());
        StringBuilder text = new StringBuilder();
        for (var delta : result.requirementDeltas()) {
            text.append(delta.kind()).append(' ').append(delta.key().orElseThrow())
                    .append(" | ").append(delta.title())
                    .append(" | ").append(delta.statement().orElse("<none>"))
                    .append(" | ").append(delta.provenance().source())
                    .append('\n');
            for (var scenario : delta.scenarios()) {
                text.append("  ").append(scenario.title())
                        .append(" | given ").append(scenario.preconditions())
                        .append(" | when ").append(scenario.action())
                        .append(" | then ").append(scenario.expectedOutcome())
                        .append('\n');
            }
        }
        text.append("evidence");
        result.evidence().forEach(item -> text.append(' ')
                .append(item.range().orElseThrow().startLine()).append('-').append(item.range().orElseThrow().endLine()));
        return text.append('\n')
                .append("diagnostics ").append(result.diagnostics())
                .append(" skipped ").append(result.skippedRequirements())
                .append(" unclosed ").append(result.unclosedCodeFences())
                .append('\n')
                .toString();
    }

    private void assertOnlyTheSectionAfterTheFenceIsNamed(Path workspace, String delta) throws Exception {
        writeDelta(workspace, "fenced", delta);

        var result = new OpenSpecRequirementDeltaReader().read(workspace, new StableTestIdentityResolver());

        assertEquals(List.of("ADDED Render headings"), result.requirementDeltas().stream()
                .map(item -> item.kind() + " " + item.title())
                .toList());
        assertEquals("Notes", only(result.diagnostics(), DiagnosticCode.UNRECOGNIZED_SECTION)
                .details().get("section"));
        assertEquals("Keep the audit trail", only(result.diagnostics(), DiagnosticCode.PARTIAL_INGESTION)
                .details().get("requirement"));
        assertEquals(1, result.skippedRequirements());
    }

    private void writeDelta(Path workspace, String change, String content) throws Exception {
        Path openspec = workspace.resolve("openspec");
        Path deltaFile = openspec.resolve("changes/" + change + "/specs/auth-session/spec.md");
        Files.createDirectories(deltaFile.getParent());
        Files.writeString(openspec.resolve("config.yaml"), "schema: spec-driven\n");
        Files.writeString(deltaFile, content);
    }

    private Diagnostic only(List<Diagnostic> diagnostics, DiagnosticCode code) {
        List<Diagnostic> matching = diagnostics.stream().filter(diagnostic -> diagnostic.code() == code).toList();
        assertEquals(1, matching.size(), "diagnostics with code " + code + ": " + diagnostics);
        return matching.getFirst();
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
