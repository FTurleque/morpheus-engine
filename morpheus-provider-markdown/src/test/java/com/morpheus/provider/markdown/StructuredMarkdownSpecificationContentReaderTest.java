package com.morpheus.provider.markdown;

import com.morpheus.application.identity.EntityIdentityResolver;
import com.morpheus.application.read.ProviderReadRequest;
import com.morpheus.domain.identity.DomainIdentity;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.provider.ProviderCapability;
import com.morpheus.domain.provider.ProviderProbeStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        if (!createSymlink(source, outside)) return;

        assertEquals(ProviderProbeStatus.INVALID, new StructuredMarkdownSpecificationProvider().probe(workspace).status());
        EntityIdentityResolver identities = (providerId, entityType, externalId) -> DomainIdentity.generate();
        var result = new StructuredMarkdownSpecificationContentReader()
                .read(ProviderReadRequest.all(workspace, ProjectSpecificationId.generate()), identities);
        assertTrue(result.content().isEmpty());
        assertFalse(result.diagnostics().isEmpty());
    }

    private boolean createSymlink(Path link, Path target) {
        try {
            Files.createSymbolicLink(link, target);
            return true;
        } catch (UnsupportedOperationException | java.io.IOException | SecurityException unsupported) {
            return false;
        }
    }
}
