package com.morpheus.provider.openspec;

import com.morpheus.application.identity.EntityIdentityResolver;
import com.morpheus.application.files.SafeWorkspaceFileResolver;
import com.morpheus.application.read.ProviderIngestionBudget;
import com.morpheus.application.read.ProviderIngestionLimitException;
import com.morpheus.application.read.ProviderReadRequest;
import com.morpheus.application.read.ProviderReadResult;
import com.morpheus.application.read.ReadCategory;
import com.morpheus.application.security.ServerLocationDisclosure;
import com.morpheus.domain.diagnostic.Diagnostic;
import com.morpheus.domain.diagnostic.DiagnosticCode;
import com.morpheus.domain.identity.DomainIdentity;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.source.SourceLocator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A refusal names the file as it exists, and the locator recorded for the same file stays a locator (PRV-6).
 *
 * <p>Two texts designate one file and are written by different rules. The locator of a delta, evidence or
 * specification normalizes: {@link SourceLocator#file(String)} rewrites a backslash to a slash, so that two platforms
 * record the same locator. The text of a refusal substitutes nothing, because on Linux a backslash is a legal
 * character of a file name and a rewritten name is another file. They agree wherever a name holds no backslash and
 * differ where it does, on a platform that allows it.</p>
 *
 * <p>That case is reachable only where a backslash is not the path separator. On Windows {@code a\b} is two
 * components, the refusal and the locator agree on {@code a/b}, and the tests that build the name on disk are
 * skipped; {@link #aRefusalAndALocatorOfTheSameFileAreWrittenByTheirOwnRules} runs everywhere
 * and states the expected pair for the platform it runs on.</p>
 */
class OpenSpecRefusedFileNameTest {

    private static final String BACKSLASH_DIRECTORY = "a\\b";

    @Test
    void aRefusalAndALocatorOfTheSameFileAreWrittenByTheirOwnRules(@TempDir Path workspace) {
        Path file = workspace.resolve("openspec").resolve("specs").resolve(BACKSLASH_DIRECTORY).resolve("spec.md");
        boolean backslashIsASeparator = File.separatorChar == '\\';

        IllegalArgumentException refusal = assertThrows(IllegalArgumentException.class, () ->
                OpenSpecSourceAttribution.attribute(workspace, file, () -> {
                    throw new IllegalArgumentException("rejected content");
                }));

        String refusedAs = backslashIsASeparator ? "openspec/specs/a/b/spec.md" : "openspec/specs/a\\b/spec.md";
        assertEquals(refusedAs + ": rejected content", refusal.getMessage());
        assertEquals(refusedAs, ((OpenSpecSourceAttribution.AttributedFailure) refusal).source());
        assertEquals("openspec/specs/a/b/spec.md",
                SourceLocator.file(workspace.relativize(file).toString()).value(),
                "the locator of the same file normalizes on every platform");
    }

    @Test
    void aRefusalAndTheLocatorOfAFileWithoutBackslashAreTheSameText(@TempDir Path workspace) throws Exception {
        writeSpecification(workspace, "alpha", "just text, no heading\n");

        Diagnostic refusal = singleInvalidSource(read(workspace));

        assertEquals(Optional.of("openspec/specs/alpha/spec.md"), refusal.source());

        writeSpecification(workspace, "alpha", VALID_SPECIFICATION);
        SourceLocator locator = read(workspace).content().orElseThrow().specifications().getFirst()
                .provenance().source();
        assertEquals("openspec/specs/alpha/spec.md", locator.value());
        assertEquals(refusal.source().orElseThrow(), locator.value());
    }

    @Test
    @EnabledOnOs({OS.LINUX, OS.MAC})
    void aRefusalNamesAFileWhoseNameHoldsABackslashAsItExists(@TempDir Path workspace) throws Exception {
        writeSpecification(workspace, BACKSLASH_DIRECTORY, "just text, no heading\n");

        Diagnostic refusal = singleInvalidSource(read(workspace));

        assertEquals(Optional.of("openspec/specs/a\\b/spec.md"), refusal.source(),
                "the refusal must name the file the operator will look for, not a rewritten path");
        assertFalse(refusal.message().contains("openspec/specs/a/b/spec.md"),
                "no text may name the rewritten path, which is another file: " + refusal.message());
        assertEquals("OpenSpec content reader failed for group current: InvalidOpenSpecSource", refusal.message(),
                "a text holding a backslash is withheld by the second relay check of the content reader, "
                        + "as it is for every refusal that names such a file; the exact path is in source");
        assertEquals("IllegalArgumentException", refusal.details().get("exception"));
        assertTrue(ServerLocationDisclosure.namesAServerLocation(refusal.source().orElseThrow()),
                "a boundary filter treats a backslash as a possible server location");
    }

    @Test
    @EnabledOnOs({OS.LINUX, OS.MAC})
    void theRefusalOfTheWholeReadNamesTheSameFileAsItExists(@TempDir Path workspace) throws Exception {
        writeSpecification(workspace, BACKSLASH_DIRECTORY, "just text, no heading\n");

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, () ->
                new OpenSpecProjectContentReader().read(workspace, ProjectSpecificationId.generate(), resolver()));

        assertTrue(failure.getMessage().startsWith("openspec/specs/a\\b/spec.md: "), failure.getMessage());
    }

    @Test
    @EnabledOnOs({OS.LINUX, OS.MAC})
    void theLocatorRecordedForTheSameFileIsStillNormalized(@TempDir Path workspace) throws Exception {
        writeSpecification(workspace, BACKSLASH_DIRECTORY, VALID_SPECIFICATION);

        ProviderReadResult result = read(workspace);

        SourceLocator locator = result.content().orElseThrow().specifications().getFirst().provenance().source();
        assertEquals(new SourceLocator("file", "openspec/specs/a/b/spec.md"), locator,
                "the locator stays platform-independent: it is what the stores persist and compare");
        assertTrue(result.diagnostics().stream().noneMatch(item -> item.code() == DiagnosticCode.INVALID_SOURCE),
                result.diagnostics().toString());
    }

    @Test
    @EnabledOnOs({OS.LINUX, OS.MAC})
    void anEvidenceBudgetRefusalNamesTheFileAsItExistsInEveryReader(@TempDir Path workspace) throws Exception {
        writeSpecification(workspace, BACKSLASH_DIRECTORY, VALID_SPECIFICATION);
        Path change = Files.createDirectories(workspace.resolve("openspec").resolve("changes").resolve(BACKSLASH_DIRECTORY));
        Files.writeString(change.resolve("proposal.md"), "# Proposal: Demo\n\n## Intent\n\nExercise attribution.\n");
        Path delta = Files.createDirectories(change.resolve("specs").resolve("auth")).resolve("spec.md");
        Files.writeString(delta, "# Delta\n\n## ADDED Requirements\n\n### Requirement: Audit\nThe system SHALL audit.\n");

        ProviderIngestionLimitException current = assertThrows(ProviderIngestionLimitException.class, () ->
                new OpenSpecCurrentSpecificationReader().read(
                        workspace, ProjectSpecificationId.generate(), resolver(), tinyEvidenceBudget(workspace)));
        ProviderIngestionLimitException changes = assertThrows(ProviderIngestionLimitException.class, () ->
                new OpenSpecChangeMetadataReader().read(
                        workspace, ProjectSpecificationId.generate(), resolver(), tinyEvidenceBudget(workspace)));
        ProviderIngestionLimitException deltas = assertThrows(ProviderIngestionLimitException.class, () ->
                new OpenSpecRequirementDeltaReader().read(workspace, resolver(), tinyEvidenceBudget(workspace)));

        assertTrue(current.getMessage().startsWith(
                "provider ingestion evidence bytes exceeds budget for openspec/specs/a\\b/spec.md: "),
                current.getMessage());
        assertTrue(changes.getMessage().startsWith(
                "provider ingestion evidence bytes exceeds budget for openspec/changes/a\\b/proposal.md: "),
                changes.getMessage());
        assertTrue(deltas.getMessage().startsWith(
                "provider ingestion evidence bytes exceeds budget for openspec/changes/a\\b/specs/auth/spec.md: "),
                deltas.getMessage());
    }

    @Test
    @EnabledOnOs({OS.LINUX, OS.MAC})
    void aResolverRefusalOnTheSameNameIsAlsoReportedByItsTypeAlone(@TempDir Path workspace) throws Exception {
        writeSpecification(workspace, BACKSLASH_DIRECTORY, "");
        Files.write(
                workspace.resolve("openspec").resolve("specs").resolve(BACKSLASH_DIRECTORY).resolve("spec.md"),
                new byte[] {'#', ' ', (byte) 0xC3, (byte) 0x28});

        Diagnostic refusal = singleInvalidSource(read(workspace));

        assertEquals(Optional.of("openspec/specs/a\\b/spec.md"), refusal.source());
        assertEquals("OpenSpec content reader failed for group current: InvalidOpenSpecSource", refusal.message(),
                "the resolver's own refusal names the file with a backslash, so it is withheld the same way");
    }

    private static ProviderIngestionBudget.Session tinyEvidenceBudget(Path workspace) throws IOException {
        return new ProviderIngestionBudget(1024 * 1024, 2_000, 32L * 1024 * 1024, 100_000, 100_000, 100_000, 10)
                .open(SafeWorkspaceFileResolver.rootedAt(workspace));
    }

    private static final String VALID_SPECIFICATION = """
            # Specification demo

            ## Requirements

            ### Requirement: Readable
            The system SHALL read it.

            #### Scenario: Read
            - **WHEN** the reader runs
            - **THEN** it is normalized
            """;

    private static void writeSpecification(Path workspace, String directory, String content) throws Exception {
        Path spec = workspace.resolve("openspec").resolve("specs").resolve(directory).resolve("spec.md");
        Files.createDirectories(spec.getParent());
        Files.writeString(workspace.resolve("openspec").resolve("config.yaml"), "schema: spec-driven\n");
        Files.writeString(spec, content);
    }

    private static ProviderReadResult read(Path workspace) {
        return new OpenSpecSpecificationContentReader().read(
                new ProviderReadRequest(
                        workspace,
                        ProjectSpecificationId.generate(),
                        EnumSet.of(ReadCategory.CURRENT_SPECIFICATIONS)),
                resolver());
    }

    private static Diagnostic singleInvalidSource(ProviderReadResult result) {
        List<Diagnostic> failures = result.diagnostics().stream()
                .filter(diagnostic -> diagnostic.code() == DiagnosticCode.INVALID_SOURCE)
                .toList();
        assertEquals(1, failures.size(), failures.toString());
        return failures.getFirst();
    }

    private static EntityIdentityResolver resolver() {
        Map<String, DomainIdentity> identities = new HashMap<>();
        return (providerId, entityType, externalId) -> identities.computeIfAbsent(
                providerId.value() + "|" + entityType + "|" + externalId, ignored -> DomainIdentity.generate());
    }
}
