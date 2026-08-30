package com.morpheus.provider.markdown;

import com.morpheus.application.identity.EntityIdentityResolver;
import com.morpheus.application.read.ProviderReadRequest;
import com.morpheus.domain.identity.DomainIdentity;
import com.morpheus.domain.project.ProjectSpecificationId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StructuredMarkdownStrictValidationTest {
    @TempDir
    Path workspace;

    @Test
    void rejectsMalformedTaskBooleanInsteadOfSilentlyCoercingToFalse() throws Exception {
        writeSource("""
                ```morpheus specification
                key=core
                title=Core
                ```
                ```morpheus change
                key=CHG-1
                title=Change
                intent=Change something
                ```
                ```morpheus task
                key=TASK-1
                change=CHG-1
                title=Implement
                completed=tru
                ```
                """);

        var result = read();

        assertTrue(result.content().isEmpty());
        assertFalse(result.diagnostics().isEmpty());
        assertTrue(result.diagnostics().getFirst().message().contains("unsupported boolean"));
    }

    @Test
    void rejectsDuplicateScenarioKeysDuringProviderNormalization() throws Exception {
        writeSource("""
                ```morpheus specification
                key=core
                title=Core
                ```
                ```morpheus requirement
                key=REQ-1
                specification=core
                title=Requirement
                statement=Requirement statement
                ```
                ```morpheus scenario
                key=SCN-1
                requirement=REQ-1
                title=First
                when=first action
                then=first outcome
                ```
                ```morpheus scenario
                key=SCN-1
                requirement=REQ-1
                title=Second
                when=second action
                then=second outcome
                ```
                """);

        var result = read();

        assertTrue(result.content().isEmpty());
        assertFalse(result.diagnostics().isEmpty());
        assertTrue(result.diagnostics().getFirst().message().contains("duplicate scenario key"));
    }

    private void writeSource(String content) throws Exception {
        Path source = workspace.resolve(StructuredMarkdownSpecificationProvider.SOURCE_FILE);
        Files.createDirectories(source.getParent());
        Files.writeString(source, content);
    }

    private com.morpheus.application.read.ProviderReadResult read() {
        EntityIdentityResolver identities = (providerId, entityType, externalId) -> DomainIdentity.generate();
        return new StructuredMarkdownSpecificationContentReader()
                .read(ProviderReadRequest.all(workspace, ProjectSpecificationId.generate()), identities);
    }
}
