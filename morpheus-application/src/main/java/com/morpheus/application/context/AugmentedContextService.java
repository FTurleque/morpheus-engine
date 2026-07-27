package com.morpheus.application.context;

import com.morpheus.application.query.ChangeContextQueryService;
import com.morpheus.application.operability.LocalOperationalRuntime;
import com.morpheus.application.operability.OperationalExecution;
import com.morpheus.application.store.ExternalReferenceStore;
import com.morpheus.application.store.KnowledgeStoreException;
import com.morpheus.application.store.SnapshotBusinessContentStore;
import com.morpheus.application.store.SpecificationKnowledgeStore;
import com.morpheus.application.store.TraceabilityStore;
import com.morpheus.application.store.VersionedRequirementStore;
import com.morpheus.domain.change.ChangeId;
import com.morpheus.domain.change.ChangeProposal;
import com.morpheus.domain.constraint.Constraint;
import com.morpheus.domain.decision.DesignDecision;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.requirement.Requirement;
import com.morpheus.domain.requirement.RequirementId;
import com.morpheus.domain.task.ImplementationTask;
import com.morpheus.domain.traceability.TraceabilityRelationType;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Builds deterministic MORPHEUS intent seeds and delegates all technical selection/ranking to an external provider. */
public final class AugmentedContextService {
    public static final int MAX_INTENT_QUERY_CHARS = 16_000;

    private final SpecificationKnowledgeStore snapshotStore;
    private final VersionedRequirementStore requirementStore;
    private final ChangeContextQueryService changeContextService;
    private final TechnicalContextProvider provider;
    private final OperationalExecution execution;

    public AugmentedContextService(
            SpecificationKnowledgeStore snapshotStore,
            SnapshotBusinessContentStore contentStore,
            VersionedRequirementStore requirementStore,
            TraceabilityStore traceabilityStore,
            ExternalReferenceStore externalReferenceStore,
            TechnicalContextProvider provider) {
        this(snapshotStore, contentStore, requirementStore, traceabilityStore, externalReferenceStore,
                provider, new OperationalExecution(LocalOperationalRuntime.recorder()));
    }

    public AugmentedContextService(
            SpecificationKnowledgeStore snapshotStore,
            SnapshotBusinessContentStore contentStore,
            VersionedRequirementStore requirementStore,
            TraceabilityStore traceabilityStore,
            ExternalReferenceStore externalReferenceStore,
            TechnicalContextProvider provider,
            OperationalExecution execution) {
        this.snapshotStore = Objects.requireNonNull(snapshotStore, "snapshotStore");
        this.requirementStore = Objects.requireNonNull(requirementStore, "requirementStore");
        this.changeContextService = new ChangeContextQueryService(
                snapshotStore,
                Objects.requireNonNull(contentStore, "contentStore"),
                requirementStore,
                Objects.requireNonNull(traceabilityStore, "traceabilityStore"),
                Objects.requireNonNull(externalReferenceStore, "externalReferenceStore"));
        this.provider = Objects.requireNonNull(provider, "provider");
        this.execution = Objects.requireNonNull(execution, "execution");
    }

    public Optional<AugmentedContextResult> requirement(
            ProjectSpecificationId projectId,
            RequirementId requirementId,
            TechnicalContextOptions options) {
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(requirementId, "requirementId");
        Objects.requireNonNull(options, "options");

        return snapshotStore.activeSnapshot(projectId).map(snapshot -> {
            Requirement requirement = requirementStore.currentRequirement(snapshot.id(), requirementId.value())
                    .orElseThrow(() -> new KnowledgeStoreException(
                            "requirement not found in ACTIVE snapshot: " + requirementId))
                    .entityVersion().content();
            MorpheusIntentContext intent = requirementIntent(requirement);
            TechnicalContextObservation technical = buildTechnicalContext(intent, options);
            return new AugmentedContextResult(AugmentedSnapshotView.from(snapshot), intent, technical, false);
        });
    }

    public Optional<AugmentedContextResult> change(
            ProjectSpecificationId projectId,
            ChangeId changeId,
            TechnicalContextOptions options) {
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(changeId, "changeId");
        Objects.requireNonNull(options, "options");

        return changeContextService.active(projectId, changeId, 1, Set.of(TraceabilityRelationType.AFFECTS))
                .map(context -> {
                    ChangeProposal change = context.change().orElseThrow(() -> new KnowledgeStoreException(
                            "change not found in ACTIVE snapshot: " + changeId));
                    List<String> requirements = context.affectedRequirements().stream()
                            .map(record -> requirementLine(record.entityVersion().content()))
                            .toList();
                    List<String> constraints = context.constraints().stream().map(Constraint::statement).toList();
                    List<String> decisions = context.designDecisions().stream()
                            .map(this::decisionLine).toList();
                    List<String> tasks = context.implementationTasks().stream()
                            .map(this::taskLine).toList();
                    MorpheusIntentContext intent = changeIntent(change, requirements, constraints, decisions, tasks);
                    TechnicalContextObservation technical = buildTechnicalContext(intent, options);
                    return new AugmentedContextResult(
                            AugmentedSnapshotView.from(context.snapshot()), intent, technical, false);
                });
    }

    private MorpheusIntentContext requirementIntent(Requirement requirement) {
        List<String> lines = List.of(
                "Requirement: " + requirement.key().map(key -> key + " ").orElse("") + requirement.title(),
                "Statement: " + requirement.statement());
        String query = boundedQuery(lines);
        return new MorpheusIntentContext(
                "REQUIREMENT",
                requirement.id().toString(),
                requirement.key(),
                requirement.title(),
                requirement.statement(),
                List.of(), List.of(), List.of(), List.of(), List.of(), query);
    }

    private TechnicalContextObservation buildTechnicalContext(
            MorpheusIntentContext intent,
            TechnicalContextOptions options) {
        return execution.externalCall(
                "technical-context",
                "build",
                () -> provider.build(new TechnicalContextRequest(intent.query(), options)));
    }

    private MorpheusIntentContext changeIntent(
            ChangeProposal change,
            List<String> requirements,
            List<String> constraints,
            List<String> decisions,
            List<String> tasks) {
        List<String> lines = new ArrayList<>();
        lines.add("Change: " + change.key().map(key -> key + " ").orElse("") + change.title());
        lines.add("Intent: " + change.intent());
        append(lines, "Scope", change.scope());
        append(lines, "Affected requirement", requirements);
        append(lines, "Constraint", constraints);
        append(lines, "Design decision", decisions);
        append(lines, "Implementation task", tasks);
        String query = boundedQuery(lines);
        return new MorpheusIntentContext(
                "CHANGE",
                change.id().toString(),
                change.key(),
                change.title(),
                change.intent(),
                change.scope(),
                requirements,
                constraints,
                decisions,
                tasks,
                query);
    }

    private String requirementLine(Requirement requirement) {
        return requirement.key().map(key -> key + " ").orElse("")
                + requirement.title() + ": " + requirement.statement();
    }

    private String decisionLine(DesignDecision decision) {
        return decision.title() + ": " + decision.decision();
    }

    private String taskLine(ImplementationTask task) {
        return task.key().map(key -> key + " ").orElse("") + task.title()
                + (task.completed() ? " [completed]" : " [pending]");
    }

    private void append(List<String> target, String label, List<String> values) {
        for (String value : values) {
            target.add(label + ": " + value);
        }
    }

    private String boundedQuery(List<String> lines) {
        String joined = String.join("\n", lines).trim();
        if (joined.length() <= MAX_INTENT_QUERY_CHARS) {
            return joined;
        }
        return joined.substring(0, MAX_INTENT_QUERY_CHARS - 18) + "\n[context trimmed]";
    }
}
