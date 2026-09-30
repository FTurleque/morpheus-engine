package com.morpheus.application.query;

import com.morpheus.application.store.SnapshotBusinessContent;
import com.morpheus.application.store.SnapshotBusinessContentStore;
import com.morpheus.application.store.SpecificationKnowledgeStore;
import com.morpheus.domain.change.ChangeId;
import com.morpheus.domain.change.ChangeProposal;
import com.morpheus.domain.change.lifecycle.ChangeLifecycleState;
import com.morpheus.domain.constraint.Constraint;
import com.morpheus.domain.constraint.ConstraintApplicability;
import com.morpheus.domain.constraint.ConstraintBlockingPolicy;
import com.morpheus.domain.constraint.ConstraintId;
import com.morpheus.domain.constraint.ConstraintSatisfaction;
import com.morpheus.domain.constraint.ConstraintSeverity;
import com.morpheus.domain.evidence.Evidence;
import com.morpheus.domain.evidence.EvidenceId;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.provenance.Provenance;
import com.morpheus.domain.provider.ProviderId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotMetadata;
import com.morpheus.domain.snapshot.KnowledgeSnapshotState;
import com.morpheus.domain.source.SourceLocator;
import com.morpheus.domain.version.SpecificationVersionId;

import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

/**
 * One ACTIVE snapshot holding one change and its constraints, served by in-test ports so application tests keep
 * depending on no adapter module. Constraints are listed in the order the query service sorts them.
 */
public final class ConstraintSnapshotFixture {
    public static final ChangeLifecycleState TARGET = ChangeLifecycleState.VERIFYING;

    private final ProjectSpecificationId projectId = ProjectSpecificationId.generate();
    private final ChangeId changeId = ChangeId.generate();
    private final KnowledgeSnapshotMetadata snapshot = new KnowledgeSnapshotMetadata(
            KnowledgeSnapshotId.generate(),
            projectId,
            Optional.empty(),
            KnowledgeSnapshotState.ACTIVE,
            Optional.of("rev-1"),
            Instant.parse("2026-09-23T08:00:00Z"));
    private final Evidence definition = new Evidence(
            EvidenceId.generate(), SourceLocator.file("specs/change.md"), Optional.empty(), Optional.of("sha256:def"));
    private final Evidence support = new Evidence(
            EvidenceId.generate(), SourceLocator.file("reviews/check.txt"), Optional.empty(), Optional.of("sha256:sup"));
    private final List<Constraint> constraints;

    private ConstraintSnapshotFixture(int count, int blockingPosition) {
        List<ConstraintId> ids = IntStream.range(0, count)
                .mapToObj(index -> ConstraintId.generate())
                .sorted(Comparator.comparing(ConstraintId::toString))
                .toList();
        List<Constraint> built = new ArrayList<>();
        for (int index = 0; index < ids.size(); index++) {
            built.add(index == blockingPosition ? blocking(ids.get(index)) : nonBlocking(ids.get(index)));
        }
        this.constraints = List.copyOf(built);
    }

    public static ConstraintSnapshotFixture nonBlocking(int count) {
        return new ConstraintSnapshotFixture(count, -1);
    }

    public static ConstraintSnapshotFixture blockingAt(int count, int blockingPosition) {
        return new ConstraintSnapshotFixture(count, blockingPosition);
    }

    public ProjectSpecificationId projectId() {
        return projectId;
    }

    public ChangeId changeId() {
        return changeId;
    }

    public List<Constraint> constraints() {
        return constraints;
    }

    public SpecificationKnowledgeStore snapshots() {
        return port(SpecificationKnowledgeStore.class, "activeSnapshot", Optional.of(snapshot));
    }

    public SnapshotBusinessContentStore content() {
        Provenance provenance = provenance();
        ChangeProposal change = new ChangeProposal(
                changeId, projectId, Optional.of("large-change"), "Large change", "Many constraints",
                List.of(), List.of(), List.of(), provenance);
        SnapshotBusinessContent content = new SnapshotBusinessContent(
                snapshot.id(),
                SpecificationVersionId.generate(),
                List.of(),
                List.of(),
                List.of(change),
                constraints,
                List.of(),
                List.of(),
                List.of(),
                List.of(definition, support));
        return port(SnapshotBusinessContentStore.class, "findSnapshotContent", Optional.of(content));
    }

    private Constraint nonBlocking(ConstraintId id) {
        return new Constraint(
                id,
                changeId,
                "Explicitly non-blocking",
                ConstraintApplicability.APPLICABLE,
                ConstraintSeverity.INFO,
                ConstraintSatisfaction.UNKNOWN,
                ConstraintBlockingPolicy.nonBlocking(),
                List.of(),
                provenance());
    }

    private Constraint blocking(ConstraintId id) {
        return new Constraint(
                id,
                changeId,
                "Violated and blocking",
                ConstraintApplicability.APPLICABLE,
                ConstraintSeverity.CRITICAL,
                ConstraintSatisfaction.VIOLATED,
                ConstraintBlockingPolicy.blockWhenViolated(List.of(TARGET)),
                List.of(support.id()),
                provenance());
    }

    private Provenance provenance() {
        return new Provenance(
                new ProviderId("test-provider"),
                Optional.of("1"),
                SourceLocator.file("specs/change.md"),
                Optional.of("constraint"),
                Optional.of("rev-1"),
                definition.id());
    }

    /** A port that answers one method and fails on any other, so a test states exactly what it reads. */
    private static <T> T port(Class<T> type, String method, Object answer) {
        return type.cast(Proxy.newProxyInstance(
                ConstraintSnapshotFixture.class.getClassLoader(),
                new Class<?>[]{type},
                (proxy, invoked, args) -> {
                    if (invoked.getName().equals(method)) {
                        return answer;
                    }
                    throw new AssertionError("unexpected call " + type.getSimpleName() + "." + invoked.getName());
                }));
    }
}
