package com.morpheus.store.sqlite.adapter;

import com.morpheus.application.store.ProjectStoreEntry;
import com.morpheus.domain.evidence.EvidenceId;
import com.morpheus.domain.identity.DomainIdentity;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotMetadata;
import com.morpheus.domain.snapshot.KnowledgeSnapshotState;
import com.morpheus.domain.source.SourceLocator;
import com.morpheus.domain.traceability.TraceabilityEntityKind;
import com.morpheus.domain.traceability.TraceabilityEntityRef;
import com.morpheus.domain.traceability.TraceabilityLink;
import com.morpheus.domain.traceability.TraceabilityLinkId;
import com.morpheus.domain.traceability.TraceabilityLinkOrigin;
import com.morpheus.domain.traceability.TraceabilityRelationType;
import com.morpheus.domain.traceability.TraceabilityResolutionState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Storing a batch of traceability links costs a number of prepared statements that does not grow with the batch.
 *
 * <p>The store prepared, for each link of a batch, a lookup of its definition, an insert of the definition, a batch of
 * its evidence and an insert of its membership. Its one production caller is the publication of a snapshot, which
 * generates a new identity for every link: on that path one statement in four was a lookup that could not find
 * anything, for tens of thousands of links, in the transaction that holds the database longest.</p>
 */
class SqliteTraceabilityBatchWriteCostTest {
    private static final Instant T0 = Instant.parse("2026-10-13T12:00:00Z");

    @TempDir
    Path tempDir;

    @Test
    void theNumberOfStatementsOfABatchDoesNotGrowWithItsLinks() throws Exception {
        Path database = tempDir.resolve("batch-cost.db");
        KnowledgeSnapshotId small = snapshot(database);
        KnowledgeSnapshotId large = snapshot(database);

        int statementsOfThree = writeCounting(database, small, links(3));
        // More links than one lookup slice and more evidence rows than one write slice hold.
        List<TraceabilityLink> many = links(650);
        int statementsOfMany = writeCounting(database, large, many);

        assertEquals(statementsOfThree, statementsOfMany, "a batch two hundred times larger must not prepare more "
                + "statements: three links=" + statementsOfThree + ", " + many.size() + " links=" + statementsOfMany);
        assertTrue(statementsOfMany <= 8, () -> "statements of one batch: " + statementsOfMany);
        try (SqliteTraceabilityStore reopened = new SqliteTraceabilityStore(database)) {
            for (TraceabilityLink link : many) {
                assertEquals(link, reopened.findLink(large, link.id()).orElseThrow());
            }
        }
    }

    @Test
    void aBatchOfLinksAlreadyDefinedCostsNoMoreThanABatchOfNewOnes() throws Exception {
        Path database = tempDir.resolve("batch-cost-defined.db");
        KnowledgeSnapshotId first = snapshot(database);
        KnowledgeSnapshotId second = snapshot(database);
        List<TraceabilityLink> links = links(250);

        int statementsWhenNew = writeCounting(database, first, links);
        int statementsWhenDefined = writeCounting(database, second, links);

        assertEquals(statementsWhenNew, statementsWhenDefined);
        try (SqliteTraceabilityStore reopened = new SqliteTraceabilityStore(database)) {
            assertEquals(links.getLast(), reopened.findLink(second, links.getLast().id()).orElseThrow());
        }
    }

    private static int writeCounting(Path database, KnowledgeSnapshotId snapshotId, List<TraceabilityLink> links)
            throws Exception {
        AtomicInteger prepared = new AtomicInteger();
        try (Connection real = DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath())) {
            Connection counting = (Connection) Proxy.newProxyInstance(
                    Connection.class.getClassLoader(),
                    new Class<?>[] {Connection.class},
                    (proxy, method, arguments) -> {
                        if (method.getName().equals("prepareStatement")) {
                            prepared.incrementAndGet();
                        }
                        try {
                            return method.invoke(real, arguments);
                        } catch (InvocationTargetException failure) {
                            throw failure.getCause();
                        }
                    });
            new SqliteTraceabilityStore(counting).putLinks(snapshotId, links);
        }
        return prepared.get();
    }

    private static KnowledgeSnapshotId snapshot(Path database) {
        ProjectSpecificationId projectId = ProjectSpecificationId.generate();
        KnowledgeSnapshotId snapshotId = KnowledgeSnapshotId.generate();
        try (var snapshots = new SqliteSpecificationKnowledgeStore(database);
             var traceability = new SqliteTraceabilityStore(database)) {
            snapshots.putProject(new ProjectStoreEntry(projectId, SourceLocator.file("workspace-" + projectId)));
            snapshots.putSnapshot(new KnowledgeSnapshotMetadata(
                    snapshotId, projectId, Optional.empty(), KnowledgeSnapshotState.READY,
                    Optional.of("revision"), T0));
        }
        return snapshotId;
    }

    private static List<TraceabilityLink> links(int count) {
        TraceabilityEntityRef source = new TraceabilityEntityRef(
                TraceabilityEntityKind.CHANGE, DomainIdentity.generate());
        List<TraceabilityLink> links = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            links.add(new TraceabilityLink(
                    TraceabilityLinkId.generate(),
                    source,
                    TraceabilityRelationType.AFFECTS,
                    new TraceabilityEntityRef(TraceabilityEntityKind.REQUIREMENT, DomainIdentity.generate()),
                    TraceabilityLinkOrigin.DERIVED,
                    TraceabilityResolutionState.RESOLVED,
                    Optional.empty(),
                    Set.of(EvidenceId.generate(), EvidenceId.generate()),
                    T0.plusSeconds(index)));
        }
        return links;
    }
}
