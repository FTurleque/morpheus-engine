package com.morpheus.provider.openspec;

import com.morpheus.application.read.ProviderReadRequest;
import com.morpheus.application.read.ProviderReadResult;
import com.morpheus.application.read.ReadCategory;
import com.morpheus.application.read.ReadCategoryStatus;
import com.morpheus.domain.diagnostic.Diagnostic;
import com.morpheus.domain.diagnostic.DiagnosticCode;
import com.morpheus.domain.identity.DomainIdentity;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.provider.ProviderCapability;
import com.morpheus.domain.provider.ProviderProbeResult;
import com.morpheus.domain.provider.ProviderProbeStatus;
import com.morpheus.domain.source.SourceLocator;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenSpecSpecificationProviderTest {
    private final OpenSpecSpecificationProvider provider = new OpenSpecSpecificationProvider();

    @Test
    void probesBasicM0FixtureAndExposesOnlyEffectiveReadCapabilities() {
        var result = provider.probe(RepositoryRoot.fixture("openspec-basic"));

        assertEquals(ProviderProbeStatus.SUPPORTED, result.status());
        assertEquals("spec-driven", result.schema().orElseThrow());
        assertTrue(result.formatVersion().isEmpty());
        assertEquals(SourceLocator.file("openspec/config.yaml"), result.sourceLocator().orElseThrow());
        assertTrue(result.capabilities().contains(ProviderCapability.DISCOVER_PROJECT));
        assertTrue(result.capabilities().contains(ProviderCapability.READ_CURRENT_SPECIFICATIONS));
        assertTrue(result.capabilities().contains(ProviderCapability.READ_REQUIREMENTS));
        assertTrue(result.capabilities().contains(ProviderCapability.READ_SCENARIOS));
        assertTrue(result.capabilities().contains(ProviderCapability.READ_CHANGES));
        assertTrue(result.capabilities().contains(ProviderCapability.READ_DESIGN_DECISIONS));
        assertTrue(result.capabilities().contains(ProviderCapability.READ_IMPLEMENTATION_TASKS));
        assertFalse(result.capabilities().contains(ProviderCapability.READ_ACCEPTANCE_CRITERIA));
        assertFalse(result.capabilities().contains(ProviderCapability.WRITE_CHANGE));
        assertFalse(result.capabilities().contains(ProviderCapability.WRITE_TASK_STATE));
        assertFalse(result.capabilities().contains(ProviderCapability.ARCHIVE_CHANGE));
        assertTrue(result.diagnostics().isEmpty());
    }

    @Test
    void probesPartialM0FixtureWithoutInventingWriteOrAcceptanceCapabilities() {
        var result = provider.probe(RepositoryRoot.fixture("openspec-partial"));

        assertEquals(ProviderProbeStatus.SUPPORTED, result.status());
        assertEquals("spec-driven", result.schema().orElseThrow());
        assertTrue(result.formatVersion().isEmpty());
        assertEquals(SourceLocator.file("openspec/config.yaml"), result.sourceLocator().orElseThrow());
        assertTrue(result.capabilities().contains(ProviderCapability.DISCOVER_PROJECT));
        assertFalse(result.capabilities().contains(ProviderCapability.READ_ACCEPTANCE_CRITERIA));
        assertFalse(result.capabilities().contains(ProviderCapability.WRITE_CHANGE));
        assertFalse(result.capabilities().contains(ProviderCapability.WRITE_TASK_STATE));
        assertFalse(result.capabilities().contains(ProviderCapability.ARCHIVE_CHANGE));
    }

    @Test
    void rejectsUnknownOpenSpecSchemaExplicitly() {
        var result = provider.probe(RepositoryRoot.fixture("openspec-unsupported-schema"));

        assertEquals(ProviderProbeStatus.UNSUPPORTED, result.status());
        assertEquals("research-first", result.schema().orElseThrow());
        assertTrue(result.formatVersion().isEmpty());
        assertEquals(SourceLocator.file("openspec/config.yaml"), result.sourceLocator().orElseThrow());
        assertTrue(result.diagnostics().stream()
                .anyMatch(diagnostic -> diagnostic.code() == DiagnosticCode.UNSUPPORTED_PROVIDER_SCHEMA));
    }

    @Test
    void unrelatedWorkspaceIsSimplyUnsupported(@TempDir Path workspace) {
        var result = provider.probe(workspace);

        assertEquals(ProviderProbeStatus.UNSUPPORTED, result.status());
        assertTrue(result.sourceLocator().isEmpty());
        assertTrue(result.capabilities().values().isEmpty());
        assertTrue(result.diagnostics().isEmpty());
    }

    @Test
    void configWithoutSchemaIsInvalid(@TempDir Path workspace) throws Exception {
        Path openspec = Files.createDirectories(workspace.resolve("openspec"));
        Files.writeString(openspec.resolve("config.yaml"), "context: missing schema\n");

        var result = provider.probe(workspace);

        assertEquals(ProviderProbeStatus.INVALID, result.status());
        assertEquals(SourceLocator.file("openspec/config.yaml"), result.sourceLocator().orElseThrow());
        assertTrue(result.diagnostics().stream()
                .anyMatch(diagnostic -> diagnostic.code() == DiagnosticCode.INVALID_SOURCE));
    }

    @Test
    void aSchemaWrittenAsAQuotedScalarIsTheSameSchema(@TempDir Path workspace) throws Exception {
        Path openspec = Files.createDirectories(workspace.resolve("openspec"));
        for (String declaration : List.of(
                "schema: \"spec-driven\"\n", "schema: 'spec-driven'\n", "schema: spec-driven # default\n")) {
            Files.writeString(openspec.resolve("config.yaml"), declaration);

            var result = provider.probe(workspace);

            assertEquals(ProviderProbeStatus.SUPPORTED, result.status(), declaration);
            assertEquals(Optional.of("spec-driven"), result.schema(), declaration);
        }
    }

    @Test
    void aSchemaWithMismatchedQuotesIsNotRecognized(@TempDir Path workspace) throws Exception {
        Path openspec = Files.createDirectories(workspace.resolve("openspec"));
        Files.writeString(openspec.resolve("config.yaml"), "schema: \"spec-driven'\n");

        assertNotEquals(ProviderProbeStatus.SUPPORTED, provider.probe(workspace).status());
    }

    @Test
    void aSchemaThatDeclaresItDerivesFromSpecDrivenIsReadAsSpecDriven(@TempDir Path workspace) throws Exception {
        Path basic = RepositoryRoot.fixture("openspec-basic");
        copyFixture(basic, workspace);
        declareSchema(workspace, "detailed-uml");
        writeSchemaDefinition(workspace, "detailed-uml", "name: detailed-uml\nversion: 1\nderives: spec-driven\n");

        var derived = provider.probe(workspace);
        var reference = provider.probe(basic);

        assertEquals(ProviderProbeStatus.SUPPORTED, derived.status(), () -> derived.diagnostics().toString());
        assertEquals(Optional.of("detailed-uml"), derived.schema());
        assertEquals(SourceLocator.file("openspec/config.yaml"), derived.sourceLocator().orElseThrow());
        assertEquals(reference.capabilities(), derived.capabilities());
        assertEquals(List.of(), derived.diagnostics());

        var derivedRead = read(workspace);
        var referenceRead = read(basic);
        assertEquals(List.of(), derivedRead.diagnostics());
        for (ReadCategory category : ALL_CATEGORIES) {
            var derivedReport = derivedRead.report(category).orElseThrow();
            var referenceReport = referenceRead.report(category).orElseThrow();
            assertEquals(referenceReport.status(), derivedReport.status(), category.name());
            assertEquals(referenceReport.itemCount(), derivedReport.itemCount(), category.name());
        }
    }

    @Test
    void aSchemaThatDeclaresNoParentStaysUnsupported(@TempDir Path workspace) throws Exception {
        copyFixture(RepositoryRoot.fixture("openspec-basic"), workspace);
        declareSchema(workspace, "detailed-uml");
        writeSchemaDefinition(workspace, "detailed-uml", "name: detailed-uml\nversion: 1\n");

        var result = provider.probe(workspace);

        assertRefused(result, "detailed-uml", "no-declared-parent");
        assertFalse(result.diagnostics().getFirst().details().containsKey("declaredParent"));
        assertNothingIsRead(workspace);
    }

    @Test
    void aDeclaredSchemaWithoutItsDefinitionStaysUnsupported(@TempDir Path workspace) throws Exception {
        copyFixture(RepositoryRoot.fixture("openspec-basic"), workspace);
        declareSchema(workspace, "detailed-uml");

        var withoutDirectory = provider.probe(workspace);
        Files.createDirectories(workspace.resolve("openspec/schemas/detailed-uml"));
        var withoutFile = provider.probe(workspace);

        assertRefused(withoutDirectory, "detailed-uml", "schema-definition-missing");
        assertRefused(withoutFile, "detailed-uml", "schema-definition-missing");
        assertNothingIsRead(workspace);
    }

    @Test
    void aSchemaThatDerivesFromAnUnknownParentStaysUnsupported(@TempDir Path workspace) throws Exception {
        copyFixture(RepositoryRoot.fixture("openspec-basic"), workspace);
        declareSchema(workspace, "detailed-uml");
        writeSchemaDefinition(workspace, "detailed-uml", "name: detailed-uml\nderives: \"another-schema\" # parent\n");

        var result = provider.probe(workspace);

        assertRefused(result, "detailed-uml", "unsupported-parent");
        assertEquals("another-schema", result.diagnostics().getFirst().details().get("declaredParent"));
        assertNothingIsRead(workspace);
    }

    @Test
    void aSchemaThatDerivesFromADerivedSchemaStaysUnsupported(@TempDir Path workspace) throws Exception {
        copyFixture(RepositoryRoot.fixture("openspec-basic"), workspace);
        declareSchema(workspace, "second-level");
        writeSchemaDefinition(workspace, "second-level", "name: second-level\nderives: detailed-uml\n");
        writeSchemaDefinition(workspace, "detailed-uml", "name: detailed-uml\nderives: spec-driven\n");

        var result = provider.probe(workspace);

        assertRefused(result, "second-level", "unsupported-parent");
        assertEquals("detailed-uml", result.diagnostics().getFirst().details().get("declaredParent"));
    }

    @Test
    void aSchemaNameThatIsNotASinglePathSegmentIsRefusedBeforeAnyRead(@TempDir Path workspace) throws Exception {
        copyFixture(RepositoryRoot.fixture("openspec-basic"), workspace);
        writeSchemaDefinition(workspace, "detailed-uml", "derives: spec-driven\n");
        Files.writeString(workspace.resolve("openspec/schema.yaml"), "derives: spec-driven\n");
        for (String name : List.of("../schemas/detailed-uml", "detailed-uml/..", "..", ".", "a\\b", "C:x")) {
            declareSchema(workspace, name);

            var result = provider.probe(workspace);

            assertRefused(result, name, "invalid-schema-name");
        }
    }

    @Test
    void anUnreadableSchemaDefinitionIsInvalidAndNamed(@TempDir Path workspace) throws Exception {
        copyFixture(RepositoryRoot.fixture("openspec-basic"), workspace);
        declareSchema(workspace, "detailed-uml");
        Files.createDirectories(workspace.resolve("openspec/schemas/detailed-uml/schema.yaml"));

        assertInvalidDefinition(provider.probe(workspace), "openspec/schemas/detailed-uml/schema.yaml");
        assertNothingIsRead(workspace);
    }

    @Test
    void aSymbolicSchemaDefinitionIsInvalidAndNamed(@TempDir Path workspace, @TempDir Path outside)
            throws Exception {
        copyFixture(RepositoryRoot.fixture("openspec-basic"), workspace);
        declareSchema(workspace, "detailed-uml");
        Path target = Files.writeString(outside.resolve("schema.yaml"), "derives: spec-driven\n");
        Path link = Files.createDirectories(workspace.resolve("openspec/schemas/detailed-uml")).resolve("schema.yaml");
        try {
            Files.createSymbolicLink(link, target);
        } catch (UnsupportedOperationException | IOException | SecurityException unavailable) {
            Assumptions.abort("symbolic links cannot be created in this environment");
        }

        assertInvalidDefinition(provider.probe(workspace), "openspec/schemas/detailed-uml/schema.yaml");
        assertNothingIsRead(workspace);
    }

    private static final Set<ReadCategory> ALL_CATEGORIES = EnumSet.of(
            ReadCategory.CURRENT_SPECIFICATIONS,
            ReadCategory.REQUIREMENTS,
            ReadCategory.SCENARIOS,
            ReadCategory.CHANGES,
            ReadCategory.REQUIREMENT_DELTAS,
            ReadCategory.CONSTRAINTS,
            ReadCategory.DESIGN_DECISIONS,
            ReadCategory.IMPLEMENTATION_TASKS);

    private static void assertRefused(ProviderProbeResult result, String schema, String reason) {
        assertEquals(ProviderProbeStatus.UNSUPPORTED, result.status(), reason);
        assertEquals(Optional.of(schema), result.schema(), reason);
        assertTrue(result.capabilities().values().isEmpty(), reason);
        assertEquals(1, result.diagnostics().size(), reason);
        Diagnostic diagnostic = result.diagnostics().getFirst();
        assertEquals(DiagnosticCode.UNSUPPORTED_PROVIDER_SCHEMA, diagnostic.code(), reason);
        assertEquals(schema, diagnostic.details().get("schema"), reason);
        assertEquals("spec-driven", diagnostic.details().get("supportedSchema"), reason);
        assertEquals(reason, diagnostic.details().get("reason"), reason);
    }

    private static void assertInvalidDefinition(ProviderProbeResult result, String relativeSource) {
        assertEquals(ProviderProbeStatus.INVALID, result.status());
        assertTrue(result.capabilities().values().isEmpty());
        assertEquals(1, result.diagnostics().size());
        Diagnostic diagnostic = result.diagnostics().getFirst();
        assertEquals(DiagnosticCode.INVALID_SOURCE, diagnostic.code());
        assertEquals(Optional.of(relativeSource), diagnostic.source());
        assertEquals(SourceLocator.file(relativeSource), result.sourceLocator().orElseThrow());
    }

    private void assertNothingIsRead(Path workspace) {
        var result = read(workspace);
        assertTrue(result.content().isEmpty());
        for (ReadCategory category : ALL_CATEGORIES) {
            assertEquals(ReadCategoryStatus.FAILED, result.report(category).orElseThrow().status(), category.name());
        }
    }

    private static ProviderReadResult read(Path workspace) {
        Map<String, DomainIdentity> identities = new HashMap<>();
        return new OpenSpecSpecificationContentReader().read(
                new ProviderReadRequest(workspace, ProjectSpecificationId.generate(), ALL_CATEGORIES),
                (providerId, entityType, externalId) -> identities.computeIfAbsent(
                        providerId.value() + "|" + entityType + "|" + externalId,
                        ignored -> DomainIdentity.generate()));
    }

    private static void declareSchema(Path workspace, String schema) throws IOException {
        Files.writeString(workspace.resolve("openspec/config.yaml"), "schema: " + schema + "\n");
    }

    private static void writeSchemaDefinition(Path workspace, String schema, String content) throws IOException {
        Path directory = Files.createDirectories(workspace.resolve("openspec/schemas").resolve(schema));
        Files.writeString(directory.resolve("schema.yaml"), content);
    }

    private static void copyFixture(Path fixture, Path workspace) throws IOException {
        try (var files = Files.walk(fixture)) {
            for (Path source : files.sorted().toList()) {
                Path target = workspace.resolve(fixture.relativize(source).toString());
                if (Files.isDirectory(source)) {
                    Files.createDirectories(target);
                } else {
                    Files.copy(source, target);
                }
            }
        }
    }
}
