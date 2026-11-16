package com.morpheus.provider.markdown;

import com.morpheus.application.identity.EntityIdentityResolver;
import com.morpheus.application.read.ProviderIngestionBudget;
import com.morpheus.application.read.ProviderReadRequest;
import com.morpheus.application.read.ReadCategory;
import com.morpheus.application.read.ReadCategoryReport;
import com.morpheus.application.read.ReadCategoryStatus;
import com.morpheus.domain.diagnostic.Diagnostic;
import com.morpheus.domain.diagnostic.DiagnosticCode;
import com.morpheus.domain.identity.DomainIdentity;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.provider.ProviderCapability;
import com.morpheus.domain.provider.ProviderProbeStatus;
import com.morpheus.domain.source.SourceLocator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class StructuredMarkdownSpecificationContentReaderTest {

    @TempDir
    Path workspace;

    @Test
    void probesAndNormalizesAllSupportedEntityKinds() throws Exception {
        Path source = workspace.resolve(StructuredMarkdownSpecificationProvider.SOURCE_FILE);
        Files.createDirectories(source.getParent());
        Files.writeString(workspace.resolve("verification.txt"), "verified");
        Files.writeString(source, """
                # Product specification

                ```morpheus specification
                key=core
                title=Core specification
                description=Canonical product behavior
                ```

                ```morpheus requirement
                key=REQ-001
                specification=core
                title=Retain evidence
                statement=The system retains source evidence.
                ```

                ```morpheus scenario
                key=SCN-001
                requirement=REQ-001
                title=Evidence remains traceable
                given=a normalized requirement;a source file
                when=the project is synchronized
                then=the source evidence remains queryable
                ```

                ```morpheus change
                key=CHG-001
                title=Extend retention
                intent=Retain evidence for longer.
                scope=evidence storage;query output
                out_of_scope=remote backup
                risks=database growth
                ```

                ```morpheus constraint
                key=CON-001
                change=CHG-001
                statement=Security review is required.
                ```

                ```morpheus decision
                key=DEC-001
                change=CHG-001
                title=Use append-only evidence
                decision=Evidence records are never rewritten.
                ```

                ```morpheus task
                key=TASK-001
                change=CHG-001
                title=Implement retention policy
                completed=false
                ```

                ```morpheus acceptance
                key=AC-001
                owner_type=requirement
                owner_key=REQ-001
                title=Evidence lookup succeeds
                condition=Evidence is returned with its source.
                verification_status=VERIFIED
                verification_evidence=verification.txt
                ```
                """);

        var provider = new StructuredMarkdownSpecificationProvider();
        var probe = provider.probe(workspace);
        assertEquals(ProviderProbeStatus.SUPPORTED, probe.status());
        assertTrue(probe.capabilities().contains(ProviderCapability.READ_ACCEPTANCE_CRITERIA));
        assertFalse(probe.capabilities().contains(ProviderCapability.WRITE_CHANGE));

        EntityIdentityResolver identities = (providerId, entityType, externalId) -> DomainIdentity.generate();
        var result = new StructuredMarkdownSpecificationContentReader()
                .read(ProviderReadRequest.all(workspace, ProjectSpecificationId.generate()), identities);

        assertTrue(result.content().isPresent());
        var content = result.content().orElseThrow();
        assertEquals(1, content.specifications().size());
        assertEquals(1, content.requirements().size());
        assertEquals(1, content.scenarios().size());
        assertEquals(1, content.changes().size());
        assertEquals(1, content.constraints().size());
        assertEquals(1, content.designDecisions().size());
        assertEquals(1, content.tasks().size());
        assertEquals(1, content.acceptanceCriteria().size());
        assertEquals(9, content.evidence().size());
    }

    /**
     * The project root is the workspace the reader was handed, not the file it read inside it.
     *
     * <p>A registered project stores its workspace root, and publication compares the root a reader publishes
     * with the stored one. Publishing {@code morpheus/specification.md} made every markdown-only workspace
     * unpublishable; the evidence and provenance still name that file, because that is where they were observed.</p>
     */
    @Test
    void publishesTheWorkspaceRootAsProjectRootAndKeepsTheFileAsEvidenceSource() throws Exception {
        Path source = workspace.resolve(StructuredMarkdownSpecificationProvider.SOURCE_FILE);
        Files.createDirectories(source.getParent());
        Files.writeString(source, """
                ```morpheus specification
                key=core
                title=Core
                ```
                ```morpheus requirement
                key=REQ-001
                specification=core
                title=Retain evidence
                statement=The system retains source evidence.
                ```
                """);

        EntityIdentityResolver identities = (providerId, entityType, externalId) -> DomainIdentity.generate();
        var content = new StructuredMarkdownSpecificationContentReader()
                .read(ProviderReadRequest.all(workspace, ProjectSpecificationId.generate()), identities)
                .content()
                .orElseThrow();

        assertEquals(
                SourceLocator.file(workspace.toAbsolutePath().normalize().toString()),
                content.project().rootLocator());
        SourceLocator specificationFile = SourceLocator.file(StructuredMarkdownSpecificationProvider.SOURCE_FILE);
        assertFalse(content.evidence().isEmpty());
        content.evidence().forEach(evidence -> assertEquals(specificationFile, evidence.source()));
        assertEquals(specificationFile, content.requirements().getFirst().provenance().source());
    }

    @Test
    void aByteOrderMarkBeforeTheFirstBlockIsAccepted() throws Exception {
        Path source = workspace.resolve(StructuredMarkdownSpecificationProvider.SOURCE_FILE);
        Files.createDirectories(source.getParent());
        Files.writeString(source, "\uFEFF" + """
                ```morpheus specification
                key=core
                title=Core
                ```
                """);

        EntityIdentityResolver identities = (providerId, entityType, externalId) -> DomainIdentity.generate();
        var content = new StructuredMarkdownSpecificationContentReader()
                .read(ProviderReadRequest.all(workspace, ProjectSpecificationId.generate()), identities)
                .content()
                .orElseThrow();

        assertEquals(1, content.specifications().size(), content.diagnostics().toString());
    }

    @Test
    void invalidRelationFailsExplicitlyInsteadOfInventingIdentity() throws Exception {
        Path source = workspace.resolve(StructuredMarkdownSpecificationProvider.SOURCE_FILE);
        Files.createDirectories(source.getParent());
        Files.writeString(source, """
                ```morpheus specification
                key=core
                title=Core
                ```
                ```morpheus requirement
                key=REQ-001
                specification=missing
                title=Broken
                statement=This must fail.
                ```
                """);

        EntityIdentityResolver identities = (providerId, entityType, externalId) -> DomainIdentity.generate();
        var result = new StructuredMarkdownSpecificationContentReader()
                .read(ProviderReadRequest.all(workspace, ProjectSpecificationId.generate()), identities);

        assertTrue(result.content().isEmpty());
        assertFalse(result.diagnostics().isEmpty());
    }

    @Test
    void rejectsSourceSymlinkThatEscapesWorkspace() throws Exception {
        Path source = workspace.resolve(StructuredMarkdownSpecificationProvider.SOURCE_FILE);
        Files.createDirectories(source.getParent());
        Path outside = Files.writeString(workspace.getParent().resolve("outside-markdown.md"), "# outside");
        assumeTrue(createSymlink(source, outside), "symbolic links cannot be created in this environment");

        assertEquals(ProviderProbeStatus.INVALID, new StructuredMarkdownSpecificationProvider().probe(workspace).status());
        EntityIdentityResolver identities = (providerId, entityType, externalId) -> DomainIdentity.generate();
        var result = new StructuredMarkdownSpecificationContentReader()
                .read(ProviderReadRequest.all(workspace, ProjectSpecificationId.generate()), identities);
        assertTrue(result.content().isEmpty());
        assertFalse(result.diagnostics().isEmpty());
    }

    @Test
    void rejectsOversizedDocumentWithoutPublishingContent() throws Exception {
        Path source = workspace.resolve(StructuredMarkdownSpecificationProvider.SOURCE_FILE);
        Files.createDirectories(source.getParent());
        Files.writeString(source, "x".repeat((1024 * 1024) + 1));

        EntityIdentityResolver identities = (providerId, entityType, externalId) -> DomainIdentity.generate();
        var result = new StructuredMarkdownSpecificationContentReader()
                .read(ProviderReadRequest.all(workspace, ProjectSpecificationId.generate()), identities);

        assertTrue(result.content().isEmpty());
        assertFalse(result.diagnostics().isEmpty());
    }

    /** Through a real provider read with the default budget: a small document with too many lines is refused. */
    @Test
    void aDocumentPastTheLineBudgetIsRefusedNamingTheMetric() throws Exception {
        Path source = workspace.resolve(StructuredMarkdownSpecificationProvider.SOURCE_FILE);
        Files.createDirectories(source.getParent());
        Files.writeString(source, "x\n".repeat(100_001));

        EntityIdentityResolver identities = (providerId, entityType, externalId) -> DomainIdentity.generate();
        var result = new StructuredMarkdownSpecificationContentReader()
                .read(ProviderReadRequest.all(workspace, ProjectSpecificationId.generate()), identities);

        assertTrue(result.content().isEmpty());
        assertEquals("Structured Markdown content reader failed for group ingestion-budget: provider ingestion line "
                        + "count for the whole read exceeds the budget: 100001 > 100000, reached while reading "
                        + "morpheus/specification.md",
                result.diagnostics().getFirst().message());
    }

    /**
     * A budget refusal is a refusal of the whole read, not a malformed source: it fails every category under the
     * {@code ingestion-budget} group, keeps the refusal text, and names no file, since the budget is not one file's.
     */
    @Test
    void aBudgetRefusalFailsEveryCategoryUnderTheIngestionBudgetGroupWithoutAttribution() throws Exception {
        Path source = workspace.resolve(StructuredMarkdownSpecificationProvider.SOURCE_FILE);
        Files.createDirectories(source.getParent());
        Files.writeString(
                workspace.resolve("verification.txt"), "x".repeat(Math.toIntExact(oneEvidenceFileBound() + 1)));
        Files.writeString(source, """
                ```morpheus specification
                key=core
                title=Core
                ```
                ```morpheus requirement
                key=REQ-001
                specification=core
                title=Retain evidence
                statement=The system retains source evidence.
                ```
                ```morpheus acceptance
                key=AC-001
                owner_type=requirement
                owner_key=REQ-001
                title=Evidence lookup succeeds
                condition=Evidence is returned with its source.
                verification_evidence=verification.txt
                ```
                """);

        EntityIdentityResolver identities = (providerId, entityType, externalId) -> DomainIdentity.generate();
        var result = new StructuredMarkdownSpecificationContentReader()
                .read(ProviderReadRequest.all(workspace, ProjectSpecificationId.generate()), identities);

        assertTrue(result.content().isEmpty());
        assertEquals(1, result.diagnostics().size());
        Diagnostic refusal = result.diagnostics().getFirst();
        assertEquals(DiagnosticCode.INVALID_SOURCE, refusal.code());
        assertEquals("ingestion-budget", refusal.details().get("group"));
        assertEquals("ProviderIngestionLimitException", refusal.details().get("exception"));
        assertTrue(refusal.source().isEmpty(), refusal.toString());
        assertTrue(refusal.message().startsWith(
                        "Structured Markdown content reader failed for group ingestion-budget: provider ingestion ")
                        && refusal.message().contains("verification.txt"),
                refusal.message());
        assertEquals(EnumSet.allOf(ReadCategory.class), EnumSet.copyOf(result.categoryReports().stream()
                .map(ReadCategoryReport::category)
                .toList()));
        result.categoryReports().forEach(report -> {
            assertEquals(ReadCategoryStatus.FAILED, report.status(), report.toString());
            assertEquals(List.of(DiagnosticCode.INVALID_SOURCE), report.diagnosticCodes(), report.toString());
            assertEquals(Optional.of("provider ingestion budget exceeded"), report.detail(), report.toString());
        });
    }

    private boolean createSymlink(Path link, Path target) {
        try {
            Files.createSymbolicLink(link, target);
            return true;
        } catch (UnsupportedOperationException | java.io.IOException | SecurityException unsupported) {
            return false;
        }
    }

    /** One evidence file is bounded by the smaller of the document and evidence bounds. */
    private static long oneEvidenceFileBound() {
        return Math.min(
                ProviderIngestionBudget.DEFAULT.maxDocumentBytes(), ProviderIngestionBudget.DEFAULT.maxEvidenceBytes());
    }
}
