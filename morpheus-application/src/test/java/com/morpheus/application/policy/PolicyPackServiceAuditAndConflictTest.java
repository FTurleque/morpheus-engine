package com.morpheus.application.policy;

import com.morpheus.application.query.PageRequest;
import com.morpheus.domain.identity.DomainIdentity;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A stale writer is refused as a conflict, and a pack's audit is read one bounded page at a time. */
class PolicyPackServiceAuditAndConflictTest {
    private static final Instant AT = Instant.parse("2026-10-30T08:00:00Z");

    private final PolicyIds.PackId packId = PolicyIds.PackId.generate();
    private final PolicyPack.Definition definition = new PolicyPack.Definition(packId, "pack", 3, 2, AT, AT);
    private final List<PolicyConfiguration.AuditRecord> journal = IntStream.range(0, 5)
            .mapToObj(index -> new PolicyConfiguration.AuditRecord(
                    DomainIdentity.generate(), PolicyConfiguration.AuditAction.UPDATE, packId, Optional.empty(),
                    Optional.empty(), Optional.empty(), "actor", "reason " + index, AT.plusSeconds(index)))
            .toList();

    @Test
    void anUpdateAgainstAStaleRevisionIsAConflictNamingBothRevisions() {
        PolicyPackService service = new PolicyPackService(store());

        PolicyConflictException conflict = assertThrows(PolicyConflictException.class,
                () -> service.update(packId, 2, "pack", List.of(), "actor", "reason"));

        assertEquals("stale policy pack revision: expected 2 but current is 3", conflict.getMessage());
    }

    @Test
    void theAuditIsServedPageByPageAndEachPageSaysWhetherMoreFollows() {
        PolicyPackService service = new PolicyPackService(store());

        PolicyAuditPage first = service.audit(packId, new PageRequest(0, 2));
        PolicyAuditPage last = service.audit(packId, new PageRequest(4, 2));
        PolicyAuditPage beyond = service.audit(packId, new PageRequest(9, 2));

        assertEquals(journal.subList(0, 2), first.items());
        assertTrue(first.hasMore());
        assertEquals(5, first.totalMatches());
        assertEquals(journal.subList(4, 5), last.items());
        assertFalse(last.hasMore());
        assertEquals(List.of(), beyond.items());
        assertFalse(beyond.hasMore());
    }

    @Test
    void aPageRefusesAnInconsistentShape() {
        PageRequest firstTwo = new PageRequest(0, 2);

        IllegalArgumentException negative = assertThrows(IllegalArgumentException.class,
                () -> new PolicyAuditPage(List.of(), firstTwo, -1, false));
        IllegalArgumentException overLimit = assertThrows(IllegalArgumentException.class,
                () -> new PolicyAuditPage(journal.subList(0, 3), firstTwo, 5, true));
        IllegalArgumentException lying = assertThrows(IllegalArgumentException.class,
                () -> new PolicyAuditPage(journal.subList(0, 2), firstTwo, 5, false));

        assertEquals("totalMatches must be >= 0", negative.getMessage());
        assertEquals("items must not exceed page limit", overLimit.getMessage());
        assertEquals("hasMore is inconsistent with page bounds", lying.getMessage());
    }

    /** Answers the definition and the audit; any other call is a read this test did not expect. */
    private PolicyPackStore store() {
        return (PolicyPackStore) Proxy.newProxyInstance(
                PolicyPackServiceAuditAndConflictTest.class.getClassLoader(),
                new Class<?>[]{PolicyPackStore.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findDefinition" -> Optional.of(definition);
                    case "listAudit" -> PolicyAuditPage.of(journal, (PageRequest) args[1]);
                    default -> throw new AssertionError("unexpected call PolicyPackStore." + method.getName());
                });
    }
}
