package com.morpheus.application.composition;

import com.morpheus.application.ingestion.NormalizedProjectContent;
import com.morpheus.domain.change.ChangeProposal;
import com.morpheus.domain.diagnostic.Diagnostic;
import com.morpheus.domain.provenance.Provenance;
import com.morpheus.domain.requirement.Requirement;
import com.morpheus.domain.specification.Specification;
import com.morpheus.domain.specification.SpecificationId;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Provider-neutral deterministic composition. No adapter type or format rule belongs here. */
public final class MultiProviderCompositionService {

    public MultiProviderCompositionResult compose(List<ProviderContribution> contributions) {
        Objects.requireNonNull(contributions, "contributions");
        if (contributions.isEmpty()) {
            throw new IllegalArgumentException("at least one provider contribution is required");
        }

        List<ProviderContribution> ordered = contributions.stream()
                .sorted(Comparator.comparingInt(ProviderContribution::priority).reversed()
                        .thenComparing(ProviderContribution::providerId))
                .toList();

        List<ProviderContribution> missingRequired = ordered.stream()
                .filter(ProviderContribution::required)
                .filter(item -> !item.available())
                .toList();
        if (!missingRequired.isEmpty()) {
            throw new IllegalStateException("required provider contribution is unavailable: "
                    + missingRequired.getFirst().providerId());
        }

        List<ProviderContribution> available = ordered.stream().filter(ProviderContribution::available).toList();
        if (available.isEmpty()) {
            throw new IllegalStateException("no provider contribution contains normalized content");
        }

        NormalizedProjectContent primary = available.getFirst().content().orElseThrow();
        available.forEach(item -> {
            NormalizedProjectContent content = item.content().orElseThrow();
            if (!content.project().id().equals(primary.project().id())) {
                throw new IllegalArgumentException("provider contributions belong to different projects");
            }
        });

        NormalizedProjectContent composed = concatenate(primary, available);
        List<CompositionConflict> conflicts = detectConflicts(available);
        List<Diagnostic> diagnostics = distinctDiagnostics(ordered);

        return new MultiProviderCompositionResult(
                available.getFirst().providerId(),
                composed,
                ordered,
                conflicts,
                diagnostics);
    }

    private NormalizedProjectContent concatenate(
            NormalizedProjectContent primary,
            List<ProviderContribution> contributions) {
        var specifications = new ArrayList<>(primary.specifications());
        var requirements = new ArrayList<>(primary.requirements());
        var scenarios = new ArrayList<>(primary.scenarios());
        var changes = new ArrayList<>(primary.changes());
        var deltas = new ArrayList<>(primary.requirementDeltas());
        var constraints = new ArrayList<>(primary.constraints());
        var decisions = new ArrayList<>(primary.designDecisions());
        var tasks = new ArrayList<>(primary.tasks());
        var acceptance = new ArrayList<>(primary.acceptanceCriteria());
        var evidence = new ArrayList<>(primary.evidence());
        var diagnostics = new ArrayList<>(primary.diagnostics());

        for (int index = 1; index < contributions.size(); index++) {
            NormalizedProjectContent item = contributions.get(index).content().orElseThrow();
            specifications.addAll(item.specifications());
            requirements.addAll(item.requirements());
            scenarios.addAll(item.scenarios());
            changes.addAll(item.changes());
            deltas.addAll(item.requirementDeltas());
            constraints.addAll(item.constraints());
            decisions.addAll(item.designDecisions());
            tasks.addAll(item.tasks());
            acceptance.addAll(item.acceptanceCriteria());
            evidence.addAll(item.evidence());
            item.diagnostics().stream().filter(value -> !diagnostics.contains(value)).forEach(diagnostics::add);
        }

        return new NormalizedProjectContent(
                primary.project(),
                specifications,
                requirements,
                scenarios,
                changes,
                deltas,
                constraints,
                decisions,
                tasks,
                acceptance,
                evidence,
                diagnostics);
    }

    private List<CompositionConflict> detectConflicts(List<ProviderContribution> contributions) {
        Map<ObservationKey, List<Observation>> observations = new LinkedHashMap<>();
        Map<ObservationKey, List<Observation>> identityObservations = new LinkedHashMap<>();

        for (ProviderContribution contribution : contributions) {
            NormalizedProjectContent content = contribution.content().orElseThrow();
            Map<SpecificationId, String> specificationKeys = new LinkedHashMap<>();
            content.specifications().forEach(item -> specificationKeys.put(item.id(), item.key()));

            content.specifications().forEach(item -> {
                observeSpecification(observations, contribution, item);
                observeIdentity(identityObservations, contribution, item.provenance(), item.key(), CompositionEntityType.SPECIFICATION);
            });
            content.requirements().forEach(item -> {
                observeRequirement(
                        observations,
                        contribution,
                        item,
                        specificationKeys.getOrDefault(item.specificationId(), item.specificationId().toString()));
                observeIdentity(
                        identityObservations,
                        contribution,
                        item.provenance(),
                        logicalKey(item.key(), item.provenance(), item.id().toString()),
                        CompositionEntityType.REQUIREMENT);
            });
            content.changes().forEach(item -> {
                observeChange(observations, contribution, item);
                observeIdentity(
                        identityObservations,
                        contribution,
                        item.provenance(),
                        logicalKey(item.key(), item.provenance(), item.id().toString()),
                        CompositionEntityType.CHANGE);
            });
        }

        List<CompositionConflict> result = new ArrayList<>();
        observations.entrySet().stream()
                .filter(entry -> entry.getValue().stream().map(Observation::value).distinct().count() > 1)
                .map(entry -> conflict(entry.getKey(), entry.getValue()))
                .forEach(result::add);
        identityObservations.entrySet().stream()
                .filter(entry -> entry.getValue().stream().map(Observation::value).distinct().count() > 1)
                .map(entry -> conflict(entry.getKey(), entry.getValue()))
                .forEach(result::add);

        return result.stream()
                .sorted(Comparator.comparing((CompositionConflict item) -> item.entityType().name())
                        .thenComparing(CompositionConflict::logicalKey)
                        .thenComparing(CompositionConflict::field))
                .toList();
    }

    private void observeSpecification(
            Map<ObservationKey, List<Observation>> target,
            ProviderContribution contribution,
            Specification item) {
        String key = item.key();
        add(target, contribution, item.provenance(), CompositionEntityType.SPECIFICATION, key, "title", item.title());
        add(target, contribution, item.provenance(), CompositionEntityType.SPECIFICATION, key, "description",
                item.description().orElse(""));
    }

    private void observeRequirement(
            Map<ObservationKey, List<Observation>> target,
            ProviderContribution contribution,
            Requirement item,
            String ownerSpecificationKey) {
        String key = logicalKey(item.key(), item.provenance(), item.id().toString());
        add(target, contribution, item.provenance(), CompositionEntityType.REQUIREMENT, key, "title", item.title());
        add(target, contribution, item.provenance(), CompositionEntityType.REQUIREMENT, key, "statement", item.statement());
        add(target, contribution, item.provenance(), CompositionEntityType.REQUIREMENT, key, "ownerSpecification", ownerSpecificationKey);
    }

    private void observeChange(
            Map<ObservationKey, List<Observation>> target,
            ProviderContribution contribution,
            ChangeProposal item) {
        String key = logicalKey(item.key(), item.provenance(), item.id().toString());
        add(target, contribution, item.provenance(), CompositionEntityType.CHANGE, key, "title", item.title());
        add(target, contribution, item.provenance(), CompositionEntityType.CHANGE, key, "intent", item.intent());
        add(target, contribution, item.provenance(), CompositionEntityType.CHANGE, key, "scope", String.join("\n", item.scope()));
        add(target, contribution, item.provenance(), CompositionEntityType.CHANGE, key, "outOfScope", String.join("\n", item.outOfScope()));
        add(target, contribution, item.provenance(), CompositionEntityType.CHANGE, key, "risks", String.join("\n", item.risks()));
    }

    private void observeIdentity(
            Map<ObservationKey, List<Observation>> target,
            ProviderContribution contribution,
            Provenance provenance,
            String logicalKey,
            CompositionEntityType observedType) {
        add(
                target,
                contribution,
                provenance,
                CompositionEntityType.IDENTITY,
                logicalKey,
                "entityType",
                observedType.name());
    }

    private String logicalKey(Optional<String> explicitKey, Provenance provenance, String fallback) {
        return explicitKey.or(() -> provenance.externalId()).orElse(fallback);
    }

    private void add(
            Map<ObservationKey, List<Observation>> target,
            ProviderContribution contribution,
            Provenance provenance,
            CompositionEntityType entityType,
            String logicalKey,
            String field,
            String value) {
        ObservationKey key = new ObservationKey(entityType, logicalKey, field);
        target.computeIfAbsent(key, ignored -> new ArrayList<>()).add(new Observation(
                contribution.providerId(),
                contribution.priority(),
                value,
                provenance.source().toString(),
                provenance.evidenceId().toString()));
    }

    private CompositionConflict conflict(ObservationKey key, List<Observation> observations) {
        List<Observation> sorted = observations.stream()
                .sorted(Comparator.comparingInt(Observation::priority).reversed()
                        .thenComparing(Observation::providerId))
                .toList();
        int highestPriority = sorted.getFirst().priority();
        List<Observation> highest = sorted.stream()
                .filter(item -> item.priority() == highestPriority)
                .toList();
        boolean topValuesAgree = highest.stream().map(Observation::value).distinct().count() == 1;

        CompositionResolution resolution = topValuesAgree
                ? CompositionResolution.SELECTED_BY_PRECEDENCE
                : CompositionResolution.UNRESOLVED;
        Optional<com.morpheus.domain.provider.ProviderId> selected = topValuesAgree
                ? Optional.of(highest.getFirst().providerId())
                : Optional.empty();
        String reason = topValuesAgree
                ? "Highest-priority provider selected while all observations remain preserved"
                : "Multiple highest-priority providers disagree; explicit resolution is required";

        List<CompositionCandidate> candidates = sorted.stream()
                .map(item -> new CompositionCandidate(
                        item.providerId(), item.priority(), item.value(), item.source(), item.evidenceId()))
                .toList();
        return new CompositionConflict(
                key.entityType(), key.logicalKey(), key.field(), candidates, resolution, selected, reason);
    }

    private List<Diagnostic> distinctDiagnostics(List<ProviderContribution> contributions) {
        List<Diagnostic> result = new ArrayList<>();
        for (ProviderContribution contribution : contributions) {
            contribution.readResult().diagnostics().stream()
                    .filter(item -> !result.contains(item))
                    .forEach(result::add);
        }
        return List.copyOf(result);
    }

    private record ObservationKey(CompositionEntityType entityType, String logicalKey, String field) {
    }

    private record Observation(
            com.morpheus.domain.provider.ProviderId providerId,
            int priority,
            String value,
            String source,
            String evidenceId) {
    }
}
