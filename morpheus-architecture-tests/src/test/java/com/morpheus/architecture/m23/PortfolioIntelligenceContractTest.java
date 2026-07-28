package com.morpheus.architecture.m23;

import com.morpheus.application.portfolio.PortfolioQueryService;
import com.morpheus.application.portfolio.PortfolioRegistryService;
import com.morpheus.application.portfolio.PortfolioTraversalDirection;
import com.morpheus.application.portfolio.PortfolioTraversalService;
import com.morpheus.application.store.PortfolioStore;
import com.morpheus.domain.identity.DomainIdentity;
import com.morpheus.domain.portfolio.PortfolioEntityRef;
import com.morpheus.domain.portfolio.PortfolioFreshnessState;
import com.morpheus.domain.portfolio.PortfolioMembershipStatus;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.provider.ProviderId;
import com.morpheus.domain.source.SourceLocator;
import com.morpheus.store.memory.MemoryPortfolioStore;
import com.morpheus.store.sqlite.SqlitePortfolioStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PortfolioIntelligenceContractTest {
    private static final Instant NOW = Instant.parse("2026-07-28T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @TempDir
    Path temporaryDirectory;

    @Test
    void projectIdentityIsNotDerivedFromWorkspaceRepositoryOrProvider() {
        PortfolioStore store = new MemoryPortfolioStore();
        PortfolioRegistryService registry = new PortfolioRegistryService(store, CLOCK);
        var portfolio = registry.create("Platform");
        ProjectSpecificationId first = ProjectSpecificationId.generate();
        ProjectSpecificationId second = ProjectSpecificationId.generate();

        var one = registry.registerProject(
                portfolio.id(), first, "Alpha",
                Optional.of(SourceLocator.file("/workspace/shared")),
                Optional.of(new SourceLocator("git", "https://example.test/shared.git")),
                Set.of(new ProviderId("openspec")));
        var two = registry.registerProject(
                portfolio.id(), second, "Beta",
                Optional.of(SourceLocator.file("/workspace/shared")),
                Optional.of(new SourceLocator("git", "https://example.test/shared.git")),
                Set.of(new ProviderId("openspec")));

        assertNotEquals(one.projectId(), two.projectId());
        assertEquals(one.workspace(), two.workspace());
        assertEquals(one.repository(), two.repository());
        assertEquals(one.providers(), two.providers());
    }

    @Test
    void missingProjectRetainsIdentityReferencesAndFreshnessHistory() {
        Fixture fixture = fixture(new MemoryPortfolioStore());
        fixture.registry().observeFreshness(
                fixture.portfolioId(), fixture.projectA(), PortfolioFreshnessState.FRESH,
                Optional.of("rev-1"), Optional.empty());
        fixture.addReference(fixture.entityA(), fixture.entityB(), "DEPENDS_ON", "provider-a");

        var missing = fixture.registry().markMissing(fixture.portfolioId(), fixture.projectA());

        assertEquals(PortfolioMembershipStatus.MISSING, missing.status());
        assertEquals(fixture.projectA(), missing.projectId());
        assertEquals(1, fixture.query().references(fixture.portfolioId(), 0, 100).size());
        assertEquals(
                PortfolioFreshnessState.MISSING,
                fixture.store().findFreshness(fixture.portfolioId(), fixture.projectA()).orElseThrow().state());
        assertEquals(Optional.of("rev-1"),
                fixture.store().findFreshness(fixture.portfolioId(), fixture.projectA()).orElseThrow().revision());
    }

    @Test
    void conflictsPreserveAllProviderObservations() {
        Fixture fixture = fixture(new MemoryPortfolioStore());
        fixture.addReference(fixture.entityA(), fixture.entityB(), "IMPLEMENTS", "provider-a");
        fixture.addReference(fixture.entityA(), fixture.entityC(), "IMPLEMENTS", "provider-b");

        var conflicts = fixture.query().conflicts(fixture.portfolioId());

        assertEquals(1, conflicts.size());
        assertEquals(2, conflicts.getFirst().observations().size());
        assertEquals(Set.of("provider-a", "provider-b"), conflicts.getFirst().observations().stream()
                .map(item -> item.providerId().value())
                .collect(java.util.stream.Collectors.toSet()));
    }

    @Test
    void traversalIsDeterministicBoundedAndExplainable() {
        Fixture fixture = fixture(new MemoryPortfolioStore());
        fixture.addReference(fixture.entityA(), fixture.entityB(), "DEPENDS_ON", "provider-a");
        fixture.addReference(fixture.entityB(), fixture.entityC(), "DEPENDS_ON", "provider-a");

        var full = fixture.traversal().traverse(
                fixture.portfolioId(), fixture.entityA(), 3, 10, 10, PortfolioTraversalDirection.OUTGOING);
        var bounded = fixture.traversal().traverse(
                fixture.portfolioId(), fixture.entityA(), 1, 10, 10, PortfolioTraversalDirection.OUTGOING);

        assertEquals(List.of(fixture.entityA(), fixture.entityB(), fixture.entityC()),
                full.depthByNode().keySet().stream().toList());
        assertFalse(full.truncated());
        assertTrue(bounded.truncated());
        assertEquals(Optional.of("DEPTH_BUDGET_REACHED:1"), bounded.truncationReason());
    }

    @Test
    void memoryAndSqliteAdaptersHaveEquivalentObservableState() {
        Fixture memory = fixture(new MemoryPortfolioStore());
        memory.addReference(memory.entityA(), memory.entityB(), "DEPENDS_ON", "provider-a");
        memory.registry().observeFreshness(
                memory.portfolioId(), memory.projectA(), PortfolioFreshnessState.FRESH,
                Optional.of("rev-42"), Optional.of("incremental"));

        try (SqlitePortfolioStore sqliteStore = new SqlitePortfolioStore(temporaryDirectory.resolve("portfolio.db"))) {
            Fixture sqlite = fixture(sqliteStore);
            sqlite.addReference(sqlite.entityA(), sqlite.entityB(), "DEPENDS_ON", "provider-a");
            sqlite.registry().observeFreshness(
                    sqlite.portfolioId(), sqlite.projectA(), PortfolioFreshnessState.FRESH,
                    Optional.of("rev-42"), Optional.of("incremental"));

            assertEquals(3, sqlite.query().overview(sqlite.portfolioId()).memberships().size());
            assertEquals(1, sqlite.query().overview(sqlite.portfolioId()).referenceCount());
            assertEquals("rev-42", sqlite.query().overview(sqlite.portfolioId()).freshness().getFirst()
                    .revision().orElseThrow());
            assertTrue(sqliteStore.findPortfolio(sqlite.portfolioId()).isPresent());
        }
    }

    private Fixture fixture(PortfolioStore store) {
        PortfolioRegistryService registry = new PortfolioRegistryService(store, CLOCK);
        PortfolioQueryService query = new PortfolioQueryService(store);
        PortfolioTraversalService traversal = new PortfolioTraversalService(store);
        var portfolio = registry.create("M23 Portfolio");
        ProjectSpecificationId projectA = ProjectSpecificationId.generate();
        ProjectSpecificationId projectB = ProjectSpecificationId.generate();
        ProjectSpecificationId projectC = ProjectSpecificationId.generate();
        register(registry, portfolio.id(), projectA, "A");
        register(registry, portfolio.id(), projectB, "B");
        register(registry, portfolio.id(), projectC, "C");
        return new Fixture(
                store, registry, query, traversal, portfolio.id(), projectA, projectB, projectC,
                new PortfolioEntityRef(projectA, "requirement", DomainIdentity.generate()),
                new PortfolioEntityRef(projectB, "specification", DomainIdentity.generate()),
                new PortfolioEntityRef(projectC, "specification", DomainIdentity.generate()));
    }

    private void register(
            PortfolioRegistryService registry,
            com.morpheus.domain.portfolio.PortfolioId portfolioId,
            ProjectSpecificationId projectId,
            String name) {
        registry.registerProject(
                portfolioId,
                projectId,
                name,
                Optional.of(SourceLocator.file("/workspace/" + name.toLowerCase())),
                Optional.of(new SourceLocator("git", "https://example.test/" + name.toLowerCase() + ".git")),
                Set.of(new ProviderId("reference")));
    }

    private record Fixture(
            PortfolioStore store,
            PortfolioRegistryService registry,
            PortfolioQueryService query,
            PortfolioTraversalService traversal,
            com.morpheus.domain.portfolio.PortfolioId portfolioId,
            ProjectSpecificationId projectA,
            ProjectSpecificationId projectB,
            ProjectSpecificationId projectC,
            PortfolioEntityRef entityA,
            PortfolioEntityRef entityB,
            PortfolioEntityRef entityC) {
        void addReference(PortfolioEntityRef source, PortfolioEntityRef target, String relation, String provider) {
            registry.addReference(
                    portfolioId, source, target, relation, new ProviderId(provider), Optional.empty(), Optional.empty());
        }
    }
}
