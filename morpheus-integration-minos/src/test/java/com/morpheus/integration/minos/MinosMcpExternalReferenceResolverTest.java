package com.morpheus.integration.minos;

import com.morpheus.application.reference.ExternalReferenceResolverResult;
import com.morpheus.domain.reference.ExternalReferenceTarget;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinosMcpExternalReferenceResolverTest {
    private static final String PROJECT = "morpheus-engine";
    private static final String SNAPSHOT = "minos-snapshot-42";
    private static final String KEY = "symbol:RequirementService";

    @Test
    void resolvesOnlyOneExactSymbolKeyPreservesCoordinatesAndExportsDeterministicFacts() {
        FakeGateway gateway = new FakeGateway(status(), List.of(
                symbol("symbol:RequirementServiceHelper", "helper-id"),
                symbol(KEY, "symbol-id")));
        MinosMcpExternalReferenceResolver resolver = new MinosMcpExternalReferenceResolver(() -> gateway);
        ExternalReferenceTarget requested = target(Optional.empty());

        ExternalReferenceResolverResult result = resolver.resolve(requested);

        assertEquals(ExternalReferenceResolverResult.Status.FOUND, result.status());
        var resolved = result.resolvedTarget().orElseThrow();
        assertEquals(requested, resolved.target());
        assertTrue(resolved.target().revision().isEmpty());
        assertEquals(KEY, resolved.target().externalId());
        assertEquals("symbol-id", resolved.attributes().get("minos.symbolId"));
        assertEquals(KEY, resolved.attributes().get("minos.symbolKey"));
        assertEquals("com.morpheus.RequirementService", resolved.attributes().get("minos.qualifiedName"));
        assertEquals(SNAPSHOT, resolved.attributes().get("minos.activeSnapshotId"));
        assertEquals("project-123", resolved.attributes().get("minos.projectId"));
    }

    @Test
    void lexicalButNonExactResultIsNotPromotedAndDuplicateExactKeysAreAmbiguous() {
        MinosMcpExternalReferenceResolver nonExact = new MinosMcpExternalReferenceResolver(
                () -> new FakeGateway(status(), List.of(symbol(KEY + "Helper", "helper"))));
        assertEquals(ExternalReferenceResolverResult.Status.NOT_FOUND,
                nonExact.resolve(target(Optional.empty())).status());

        MinosMcpExternalReferenceResolver ambiguous = new MinosMcpExternalReferenceResolver(
                () -> new FakeGateway(status(), List.of(symbol(KEY, "one"), symbol(KEY, "two"))));
        assertEquals(ExternalReferenceResolverResult.Status.AMBIGUOUS,
                ambiguous.resolve(target(Optional.empty())).status());
    }

    @Test
    void matchingRevisionIsPreservedAndMismatchOrUnsupportedTypeAreExplicit() {
        MinosMcpExternalReferenceResolver resolver = new MinosMcpExternalReferenceResolver(
                () -> new FakeGateway(status(), List.of(symbol(KEY, "id"))));

        ExternalReferenceTarget matching = target(Optional.of(SNAPSHOT));
        ExternalReferenceResolverResult found = resolver.resolve(matching);
        assertEquals(ExternalReferenceResolverResult.Status.FOUND, found.status());
        assertEquals(matching, found.resolvedTarget().orElseThrow().target());

        assertEquals(ExternalReferenceResolverResult.Status.REVISION_MISMATCH,
                resolver.resolve(target(Optional.of("old-snapshot"))).status());

        ExternalReferenceTarget unsupported = new ExternalReferenceTarget(
                "MINOS", Optional.of(PROJECT), "TEST", "test:one", Optional.empty());
        assertEquals(ExternalReferenceResolverResult.Status.UNSUPPORTED, resolver.resolve(unsupported).status());
    }

    @Test
    void unavailableGatewayNeverEscapesAsMorpheusFailure() {
        MinosMcpExternalReferenceResolver resolver = new MinosMcpExternalReferenceResolver(
                () -> { throw new MinosIntegrationException("offline"); });

        ExternalReferenceResolverResult result = resolver.resolve(target(Optional.empty()));

        assertEquals(ExternalReferenceResolverResult.Status.UNAVAILABLE, result.status());
        assertTrue(result.resolvedTarget().isEmpty());
    }

    private ExternalReferenceTarget target(Optional<String> revision) {
        return new ExternalReferenceTarget("MINOS", Optional.of(PROJECT), "SYMBOL", KEY, revision);
    }

    private MinosCodeGateway.IndexStatus status() {
        return new MinosCodeGateway.IndexStatus(
                "project-123", PROJECT, "READY", SNAPSHOT, "scip-java", "1.7.0");
    }

    private MinosCodeGateway.Symbol symbol(String key, String id) {
        return new MinosCodeGateway.Symbol(
                id,
                key,
                "project-123",
                "module-main",
                "src/main/java/RequirementService.java",
                "CLASS",
                "RequirementService",
                "com.morpheus.RequirementService",
                "class RequirementService",
                "java",
                "RESOLVED",
                new MinosCodeGateway.Origin("scip-java", "1.7.0", "run-99"));
    }

    private static final class FakeGateway implements MinosCodeGateway {
        private final IndexStatus status;
        private final List<Symbol> symbols;

        private FakeGateway(IndexStatus status, List<Symbol> symbols) {
            this.status = status;
            this.symbols = List.copyOf(symbols);
        }

        @Override
        public IndexStatus indexStatus(String project) {
            return status;
        }

        @Override
        public List<Symbol> findSymbols(String project, String query, int limit) {
            return symbols;
        }

        @Override
        public void close() {
        }
    }
}
