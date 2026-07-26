package com.morpheus.provider.markdown;

import com.morpheus.application.identity.EntityIdentityResolver;
import com.morpheus.application.read.ProviderReadRequest;
import com.morpheus.application.read.ReadCategory;
import com.morpheus.application.read.ReadCategoryStatus;
import com.morpheus.domain.identity.DomainIdentity;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.provider.ProviderId;
import com.morpheus.domain.provider.ProviderProbeStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarkdownSpecificationContentReaderTest {

    @TempDir
    Path tempDir;

    @Test
    void probeRecognizesRealStructuredMarkdownSource() throws IOException {
        Files.createDirectories(tempDir.resolve(".morpheus/specs"));

        var result = new MarkdownSpecificationProvider().probe(tempDir);

        assertEquals(ProviderProbeStatus.SUPPORTED, result.status());
        assertEquals("morpheus-structured-markdown", result.schema().orElseThrow());
        assertEquals("1", result.formatVersion().orElseThrow());
        assertTrue(result.capabilities().contains(com.morpheus.domain.provider.ProviderCapability.READ_REQUIREMENTS));
    }

    @Test
    void readsSpecificationRequirementScenarioAndProvenance() throws IOException {
        write("payments.md", """
                ---
                morpheus-format: 1
                spec: payments
                title: Payments
                ---

                # Requirements

                ## payments/reject-invalid — Refuser les paiements invalides
                Le système refuse un paiement dont la validation échoue.

                ### Scenario — Carte expirée
                Given: une carte expirée
                When: le paiement est soumis
                Then: le paiement est refusé
                """);

        var reader = new MarkdownSpecificationContentReader();
        var result = reader.read(
                ProviderReadRequest.all(tempDir, ProjectSpecificationId.generate()),
                stableResolver());
        var content = result.content().orElseThrow();

        assertEquals(MarkdownSpecificationProvider.ID, result.providerId());
        assertEquals(1, content.specifications().size());
        assertEquals(1, content.requirements().size());
        assertEquals(1, content.scenarios().size());
        assertEquals(3, content.evidence().size());
        assertEquals("payments", content.specifications().getFirst().key());
        assertEquals("payments/reject-invalid", content.requirements().getFirst().key().orElseThrow());
        assertEquals(MarkdownSpecificationProvider.ID, content.requirements().getFirst().provenance().providerId());
        assertEquals("Carte expirée", content.scenarios().getFirst().title());
        assertTrue(content.diagnostics().stream().anyMatch(item ->
                item.code() == com.morpheus.domain.diagnostic.DiagnosticCode.OPTIONAL_CAPABILITY_UNAVAILABLE));
        assertEquals(
                ReadCategoryStatus.READ,
                result.report(ReadCategory.REQUIREMENTS).orElseThrow().status());
        assertEquals(
                ReadCategoryStatus.UNSUPPORTED,
                result.report(ReadCategory.CHANGES).orElseThrow().status());
    }

    @Test
    void invalidFormatIsExplicitAndDoesNotFabricateContent() throws IOException {
        write("invalid.md", """
                ---
                morpheus-format: 2
                spec: payments
                title: Payments
                ---
                """);

        var result = new MarkdownSpecificationContentReader().read(
                ProviderReadRequest.all(tempDir, ProjectSpecificationId.generate()),
                stableResolver());

        assertTrue(result.content().isPresent());
        assertTrue(result.content().orElseThrow().requirements().isEmpty());
        assertTrue(result.diagnostics().stream().anyMatch(item ->
                item.code() == com.morpheus.domain.diagnostic.DiagnosticCode.UNSUPPORTED_FORMAT_VERSION));
        assertEquals(
                ReadCategoryStatus.FAILED,
                result.report(ReadCategory.REQUIREMENTS).orElseThrow().status());
    }

    @Test
    void absentDirectoryIsNotAProjectFailureByItself() {
        var probe = new MarkdownSpecificationProvider().probe(tempDir);
        assertEquals(ProviderProbeStatus.UNSUPPORTED, probe.status());

        var result = new MarkdownSpecificationContentReader().read(
                ProviderReadRequest.all(tempDir, ProjectSpecificationId.generate()),
                stableResolver());
        assertTrue(result.content().isEmpty());
        assertFalse(result.categoryReports().isEmpty());
        assertEquals(ReadCategoryStatus.ABSENT, result.report(ReadCategory.REQUIREMENTS).orElseThrow().status());
    }

    private void write(String name, String content) throws IOException {
        Path root = tempDir.resolve(".morpheus/specs");
        Files.createDirectories(root);
        Files.writeString(root.resolve(name), content);
    }

    private EntityIdentityResolver stableResolver() {
        Map<String, DomainIdentity> identities = new HashMap<>();
        return (ProviderId providerId, String entityType, String externalId) -> identities.computeIfAbsent(
                providerId.value() + "|" + entityType + "|" + externalId,
                ignored -> DomainIdentity.generate());
    }
}
