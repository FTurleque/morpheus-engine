package com.morpheus.architecture.m25;

import com.morpheus.application.policy.PolicyAuditPage;
import com.morpheus.application.policy.PolicyConfiguration;
import com.morpheus.application.policy.PolicyIds;
import com.morpheus.application.policy.PolicyPackService;
import com.morpheus.application.policy.PolicyRule;
import com.morpheus.application.query.PageRequest;
import com.morpheus.application.store.PolicyPackStore;
import com.morpheus.domain.change.ChangeId;
import com.morpheus.domain.change.lifecycle.ChangeLifecycleState;
import com.morpheus.store.memory.MemoryPolicyPackStore;
import com.morpheus.store.sqlite.SqlitePolicyPackStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A policy audit comes back by instant, ties by identity, from the store port and from the public audit alike.
 *
 * <p>The writes are stamped in chronological order, with instants whose ISO-8601 texts sort differently from time
 * ({@code :00Z}, {@code :00.500Z}, {@code :00.500100Z}), then {@value #WRITES_WITHIN_ONE_INSTANT} writes within one
 * instant. Records sharing an instant keep a stable order, not their write order: the system clock does not stamp two
 * audit writes with one instant, so the tie only needs to be deterministic and the same in both stores.</p>
 */
class PolicyAuditOrderTest {
    private static final int WRITES_WITHIN_ONE_INSTANT = 8;
    private static final int PAGE_SIZE = 4;
    private static final Instant SECOND = Instant.parse("2026-10-09T14:30:00Z");
    private static final List<Instant> STAMPS = stamps();

    @TempDir
    Path tempDir;

    @Test
    void bothStoresListTheAuditByInstantThenIdentity() {
        assertAuditOrder("store port", PolicyPackStore::listAudit);
    }

    @Test
    void thePublicAuditIsListedByInstantThenIdentity() {
        assertAuditOrder("public audit", (store, packId, page) ->
                new PolicyPackService(store, Clock.systemUTC()).audit(packId, page));
    }

    private void assertAuditOrder(
            String level, PageReader read) {
        assertOrdered(level + " of the memory store", new MemoryPolicyPackStore(), read);
        try (SqlitePolicyPackStore store = new SqlitePolicyPackStore(tempDir.resolve(level.replace(' ', '-') + ".db"))) {
            assertOrdered(level + " of the SQLite store", store, read);
        }
    }

    private static void assertOrdered(
            String label,
            PolicyPackStore store,
            PageReader read) {
        PolicyIds.PackId packId = write(store);
        List<PolicyConfiguration.AuditRecord> audit = readByPages(read, store, packId);
        List<String> reasons = audit.stream().map(PolicyConfiguration.AuditRecord::reason).toList();

        assertEquals(List.of("write-0", "write-1", "write-2"), reasons.subList(0, 3), label + ": " + reasons);
        assertEquals(
                IntStream.range(3, STAMPS.size()).mapToObj(index -> "write-" + index).collect(Collectors.toSet()),
                Set.copyOf(reasons.subList(3, reasons.size())),
                label + ": " + reasons);
        assertEquals(audit.stream().sorted().toList(), audit, label + " must order ties by identity: " + reasons);
        assertEquals(audit, readByPages(read, store, packId), label + " must answer the same order on every read");
        assertEquals(audit, read.page(store, packId, PageRequest.first(PageRequest.MAX_LIMIT)).items(),
                label + ": pages of " + PAGE_SIZE + " must concatenate to the page that holds everything");
    }

    /** Pages of {@value #PAGE_SIZE}: the journal is longer, so the boundary falls inside the three odd timestamps. */
    private static List<PolicyConfiguration.AuditRecord> readByPages(
            PageReader read, PolicyPackStore store, PolicyIds.PackId packId) {
        List<PolicyConfiguration.AuditRecord> all = new ArrayList<>();
        PolicyAuditPage page;
        int offset = 0;
        do {
            page = read.page(store, packId, new PageRequest(offset, PAGE_SIZE));
            assertEquals(STAMPS.size(), page.totalMatches(), "every page reports the whole journal's size");
            all.addAll(page.items());
            offset += PAGE_SIZE;
        } while (page.hasMore());
        return all;
    }

    @FunctionalInterface
    private interface PageReader {
        PolicyAuditPage page(PolicyPackStore store, PolicyIds.PackId packId, PageRequest page);
    }

    private static PolicyIds.PackId write(PolicyPackStore store) {
        PolicyPackService service = new PolicyPackService(store, new SequenceClock(STAMPS));
        var definition = service.create("Governance", List.of(rule()), "alice", "write-0");
        for (int index = 1; index < STAMPS.size(); index++) {
            service.update(definition.id(), index, "Governance", List.of(rule()), "alice", "write-" + index);
        }
        return definition.id();
    }

    private static List<Instant> stamps() {
        List<Instant> stamps = new ArrayList<>(List.of(
                SECOND, SECOND.plusMillis(500), SECOND.plusNanos(500_100_000)));
        Instant tie = SECOND.plusMillis(600);
        for (int index = 0; index < WRITES_WITHIN_ONE_INSTANT; index++) {
            stamps.add(tie);
        }
        return List.copyOf(stamps);
    }

    private static PolicyRule rule() {
        return new PolicyRule(
                PolicyIds.RuleId.generate(),
                "Lifecycle guard",
                PolicyRule.Kind.LIFECYCLE_GUARD,
                PolicyRule.Severity.WARNING,
                new PolicyRule.LifecycleGuard(
                        ChangeId.generate(), ChangeLifecycleState.PROPOSED, ChangeLifecycleState.SPECIFIED));
    }

    /** Hands out one stamp per write, so each audit record carries the instant the test chose for it. */
    private static final class SequenceClock extends Clock {
        private final Iterator<Instant> stamps;

        SequenceClock(List<Instant> stamps) {
            this.stamps = stamps.iterator();
        }

        @Override
        public Instant instant() {
            return stamps.next();
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            throw new UnsupportedOperationException("a sequence clock has no zone to change");
        }
    }
}
