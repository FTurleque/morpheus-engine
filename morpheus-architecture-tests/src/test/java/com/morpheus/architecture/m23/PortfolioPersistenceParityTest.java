package com.morpheus.architecture.m23;

import com.morpheus.application.store.PortfolioStore;
import com.morpheus.domain.portfolio.PortfolioDefinition;
import com.morpheus.domain.portfolio.PortfolioId;
import com.morpheus.domain.portfolio.PortfolioMembership;
import com.morpheus.domain.portfolio.PortfolioMembershipStatus;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.provider.ProviderId;
import com.morpheus.store.memory.MemoryPortfolioStore;
import com.morpheus.store.sqlite.SqlitePortfolioStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * A membership reads back the providers it was registered with, in either store.
 *
 * <p>Each case compares outcomes, not a chosen one: an identifier the domain refuses is refused the same way by
 * both stores, and an identifier it accepts comes back unchanged from both. What neither store may do is return a
 * provider set other than the one registered.</p>
 */
class PortfolioPersistenceParityTest {
    private static final Instant CREATED = Instant.parse("2026-09-01T08:00:00Z");

    @TempDir
    Path tempDir;

    @Test
    void severalProvidersRoundTripInBothStores() {
        assertSameFaithfulOutcome("several",
                () -> Set.of(new ProviderId("openspec"), new ProviderId("markdown")));
    }

    @Test
    void anIdentifierCarryingALineBreakIsNeverReadBackAsTwoProviders() {
        assertSameFaithfulOutcome("line-break", () -> Set.of(new ProviderId("openspec\nmarkdown")));
    }

    @Test
    void anIdentifierCarryingABlankLineIsNeverReadBackAsTwoProviders() {
        assertSameFaithfulOutcome("blank-line", () -> Set.of(new ProviderId("openspec\n\nmarkdown")));
    }

    @Test
    void anIdentifierThatContainsAnotherOneDoesNotSwallowIt() {
        assertSameFaithfulOutcome("overlapping",
                () -> Set.of(new ProviderId("openspec\nmarkdown"), new ProviderId("openspec")));
    }

    private void assertSameFaithfulOutcome(String name, Supplier<Set<ProviderId>> providers) {
        Outcome memory = registerAndReadBack(new MemoryPortfolioStore(), providers);
        Outcome sqlite;
        try (SqlitePortfolioStore store = new SqlitePortfolioStore(tempDir.resolve(name + ".db"))) {
            sqlite = registerAndReadBack(store, providers);
        }

        assertFalse(memory instanceof Altered, () -> "memory store returned another provider set: " + visible(memory));
        assertFalse(sqlite instanceof Altered, () -> "SQLite store returned another provider set: " + visible(sqlite));
        assertEquals(memory, sqlite, () -> "both stores must answer the same registration the same way: memory "
                + visible(memory) + ", SQLite " + visible(sqlite));
    }

    private static String visible(Outcome outcome) {
        return outcome.toString().replace("\n", "\\n");
    }

    private static Outcome registerAndReadBack(PortfolioStore store, Supplier<Set<ProviderId>> providers) {
        PortfolioId portfolioId = PortfolioId.generate();
        ProjectSpecificationId projectId = ProjectSpecificationId.generate();
        store.putPortfolio(new PortfolioDefinition(portfolioId, "platform", CREATED, CREATED));
        Set<ProviderId> registered;
        try {
            registered = providers.get();
            store.putMembership(new PortfolioMembership(
                    portfolioId,
                    projectId,
                    "checkout",
                    Optional.empty(),
                    Optional.empty(),
                    registered,
                    PortfolioMembershipStatus.ACTIVE,
                    CREATED,
                    CREATED));
        } catch (IllegalArgumentException refusal) {
            return new Refused(refusal.getMessage());
        }
        Set<ProviderId> readBack = store.findMembership(portfolioId, projectId)
                .orElseThrow(() -> new AssertionError("a registered membership must be found again"))
                .providers();
        return readBack.equals(registered) ? new Faithful(registered) : new Altered(registered, readBack);
    }

    private sealed interface Outcome permits Faithful, Altered, Refused {
    }

    private record Faithful(Set<ProviderId> providers) implements Outcome {
    }

    private record Altered(Set<ProviderId> registered, Set<ProviderId> readBack) implements Outcome {
    }

    private record Refused(String message) implements Outcome {
    }
}
