package com.morpheus.application.composition;

import com.morpheus.application.identity.EntityIdentityResolver;
import com.morpheus.application.ingestion.NormalizedProjectContent;
import com.morpheus.application.read.ProviderReadRequest;
import com.morpheus.application.read.ProviderReadResult;
import com.morpheus.application.read.ReadCategoryReport;
import com.morpheus.application.read.ReadCategoryStatus;
import com.morpheus.domain.acceptance.AcceptanceCriterion;
import com.morpheus.domain.change.ChangeProposal;
import com.morpheus.domain.constraint.Constraint;
import com.morpheus.domain.decision.DesignDecision;
import com.morpheus.domain.diagnostic.Diagnostic;
import com.morpheus.domain.diagnostic.DiagnosticCode;
import com.morpheus.domain.evidence.Evidence;
import com.morpheus.domain.project.ProjectSpecification;
import com.morpheus.domain.provider.ProviderId;
import com.morpheus.domain.requirement.Requirement;
import com.morpheus.domain.requirement.RequirementDelta;
import com.morpheus.domain.requirement.RequirementId;
import com.morpheus.domain.scenario.Scenario;
import com.morpheus.domain.specification.Specification;
import com.morpheus.domain.specification.SpecificationId;
import com.morpheus.domain.task.ImplementationTask;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Function;

/**
 * Provider-neutral M18 composition service.
 *
 * <p>Logical keys are used only to detect candidate continuity/conflicts. They never replace MORPHEUS domain
 * identities. Divergent equal-precedence contributions emit an ERROR diagnostic, which prevents publication by the
 * normal snapshot validation gate.</p>
 */
public final class MultiProviderCompositionService {

    public ComposedProjectContent compose(
            ProviderReadRequest request,
            EntityIdentityResolver identityResolver,
            List<ProviderCompositionSource> sources) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(identityResolver, "identityResolver");
        Objects.requireNonNull(sources, "sources");
        if (sources.isEmpty()) {
            throw new IllegalArgumentException("at least one provider composition source is required");
        }

        List<ProviderCompositionSource> orderedSources = canonicalSources(sources);
        List<ProviderContribution> contributions = new ArrayList<>();
        List<Diagnostic> diagnostics = new ArrayList<>();
        List<SourceRead> reads = new ArrayList<>();

        for (ProviderCompositionSource source : orderedSources) {
            readSource(request, identityResolver, source, contributions, diagnostics).ifPresent(reads::add);
        }
        if (reads.isEmpty()) {
            throw new IllegalStateException("no provider produced normalized project content");
        }

        ProjectSpecification project = reads.getFirst().content().project();
        for (SourceRead read : reads) {
            if (!read.content().project().id().equals(request.projectId())) {
                throw new IllegalStateException("provider returned content for another project: " + read.source().providerId());
            }
        }

        List<ProviderCompositionConflict> conflicts = new ArrayList<>();
        Map<SpecificationId, SpecificationId> specificationRemap = new HashMap<>();
        List<Specification> specifications = composeSpecifications(reads, specificationRemap, conflicts, diagnostics);

        Map<RequirementId, RequirementId> requirementRemap = new HashMap<>();
        List<Requirement> requirements = composeRequirements(
                reads, specificationRemap, requirementRemap, conflicts, diagnostics);
        Map<RequirementId, Requirement> requirementById = new HashMap<>();
        requirements.forEach(requirement -> requirementById.put(requirement.id(), requirement));

        List<Scenario> scenarios = composeScenarios(
                reads, requirementRemap, requirementById, conflicts, diagnostics);

        List<ChangeProposal> changes = uniqueById(
                reads, content -> content.changes(), item -> item.id().toString(), "change", diagnostics);
        List<RequirementDelta> requirementDeltas = uniqueById(
                reads, content -> content.requirementDeltas(), item -> item.id().toString(), "requirementDelta", diagnostics);
        List<Constraint> constraints = uniqueById(
                reads, content -> content.constraints(), item -> item.id().toString(), "constraint", diagnostics);
        List<DesignDecision> decisions = uniqueById(
                reads, content -> content.designDecisions(), item -> item.id().toString(), "designDecision", diagnostics);
        List<ImplementationTask> tasks = uniqueById(
                reads, content -> content.tasks(), item -> item.id().toString(), "task", diagnostics);
        List<AcceptanceCriterion> acceptanceCriteria = composeAcceptanceCriteria(reads, requirementRemap, diagnostics);
        List<Evidence> evidence = uniqueById(
                reads, content -> content.evidence(), item -> item.id().toString(), "evidence", diagnostics);

        NormalizedProjectContent content = new NormalizedProjectContent(
                project,
                specifications,
                requirements,
                scenarios,
                changes,
                requirementDeltas,
                constraints,
                decisions,
                tasks,
                acceptanceCriteria,
                evidence,
                List.copyOf(diagnostics));
        ProviderCompositionReport report = new ProviderCompositionReport(contributions, conflicts);
        return new ComposedProjectContent(content, report);
    }

    private Optional<SourceRead> readSource(
            ProviderReadRequest request,
            EntityIdentityResolver identityResolver,
            ProviderCompositionSource source,
            List<ProviderContribution> contributions,
            List<Diagnostic> diagnostics) {
        try {
            ProviderReadResult result = source.reader().read(request, identityResolver);
            if (!result.providerId().equals(source.providerId())) {
                throw new IllegalStateException("reader returned a different provider id");
            }
            addDistinct(diagnostics, result.diagnostics());
            ProviderContributionStatus status = status(result);
            int itemCount = result.categoryReports().stream().mapToInt(ReadCategoryReport::itemCount).sum();
            Optional<String> detail = firstDetail(result);
            contributions.add(new ProviderContribution(
                    source.providerId(), source.precedence(), source.required(), status, itemCount, detail));

            if (source.required() && (result.content().isEmpty()
                    || status == ProviderContributionStatus.FAILED
                    || status == ProviderContributionStatus.UNSUPPORTED)) {
                addDistinct(diagnostics, List.of(Diagnostic.error(
                        DiagnosticCode.REQUIRED_PROVIDER_UNAVAILABLE,
                        "Required provider did not produce usable normalized content",
                        Map.of("provider", source.providerId().value()))));
            }
            return result.content().map(content -> new SourceRead(source, content));
        } catch (RuntimeException exception) {
            contributions.add(new ProviderContribution(
                    source.providerId(),
                    source.precedence(),
                    source.required(),
                    ProviderContributionStatus.FAILED,
                    0,
                    Optional.of(exception.getClass().getSimpleName())));
            Diagnostic diagnostic = source.required()
                    ? Diagnostic.error(
                            DiagnosticCode.REQUIRED_PROVIDER_UNAVAILABLE,
                            "Required provider failed during normalized read",
                            Map.of(
                                    "provider", source.providerId().value(),
                                    "exception", exception.getClass().getSimpleName()))
                    : Diagnostic.warning(
                            DiagnosticCode.PARTIAL_INGESTION,
                            "Optional provider failed during normalized read",
                            Map.of(
                                    "provider", source.providerId().value(),
                                    "exception", exception.getClass().getSimpleName()));
            addDistinct(diagnostics, List.of(diagnostic));
            return Optional.empty();
        }
    }

    private List<ProviderCompositionSource> canonicalSources(List<ProviderCompositionSource> sources) {
        List<ProviderCompositionSource> copy = new ArrayList<>(sources);
        copy.forEach(source -> Objects.requireNonNull(source, "provider composition source"));
        copy.sort(Comparator.comparingInt(ProviderCompositionSource::precedence).reversed()
                .thenComparing(source -> source.providerId().value()));
        Set<ProviderId> seen = new java.util.HashSet<>();
        for (ProviderCompositionSource source : copy) {
            if (!seen.add(source.providerId())) {
                throw new IllegalArgumentException("duplicate provider composition source: " + source.providerId());
            }
        }
        return List.copyOf(copy);
    }

    private ProviderContributionStatus status(ProviderReadResult result) {
        boolean failed = result.categoryReports().stream().anyMatch(report -> report.status() == ReadCategoryStatus.FAILED);
        boolean partial = result.categoryReports().stream().anyMatch(report -> report.status() == ReadCategoryStatus.PARTIAL);
        boolean read = result.categoryReports().stream().anyMatch(report -> report.status() == ReadCategoryStatus.READ);
        boolean unsupportedOnly = !result.categoryReports().isEmpty()
                && result.categoryReports().stream().allMatch(report -> report.status() == ReadCategoryStatus.UNSUPPORTED);
        if (failed) {
            return ProviderContributionStatus.FAILED;
        }
        if (partial) {
            return ProviderContributionStatus.PARTIAL;
        }
        if (read) {
            return ProviderContributionStatus.READ;
        }
        if (unsupportedOnly) {
            return ProviderContributionStatus.UNSUPPORTED;
        }
        return ProviderContributionStatus.ABSENT;
    }

    private Optional<String> firstDetail(ProviderReadResult result) {
        return result.categoryReports().stream()
                .map(ReadCategoryReport::detail)
                .flatMap(Optional::stream)
                .findFirst();
    }

    private List<Specification> composeSpecifications(
            List<SourceRead> reads,
            Map<SpecificationId, SpecificationId> remap,
            List<ProviderCompositionConflict> conflicts,
            List<Diagnostic> diagnostics) {
        Map<String, List<Candidate<Specification>>> groups = new LinkedHashMap<>();
        for (SourceRead read : reads) {
            for (Specification specification : read.content().specifications()) {
                groups.computeIfAbsent(normalizeKey(specification.key()), ignored -> new ArrayList<>())
                        .add(new Candidate<>(read.source(), specification));
            }
        }

        List<Specification> result = new ArrayList<>();
        for (Map.Entry<String, List<Candidate<Specification>>> entry : groups.entrySet()) {
            List<Candidate<Specification>> group = onePerProvider(
                    entry.getValue(), item -> item.id().toString(), ProviderEntityKind.SPECIFICATION, entry.getKey(), diagnostics);
            Candidate<Specification> winner = selectWinner(
                    ProviderEntityKind.SPECIFICATION,
                    entry.getKey(),
                    group,
                    this::equivalentSpecification,
                    item -> item.id().toString(),
                    conflicts,
                    diagnostics);
            for (Candidate<Specification> candidate : entry.getValue()) {
                remap.put(candidate.value().id(), winner.value().id());
            }
            result.add(winner.value());
        }
        result.sort(Comparator.comparing(item -> item.id().toString()));
        return List.copyOf(result);
    }

    private List<Requirement> composeRequirements(
            List<SourceRead> reads,
            Map<SpecificationId, SpecificationId> specificationRemap,
            Map<RequirementId, RequirementId> requirementRemap,
            List<ProviderCompositionConflict> conflicts,
            List<Diagnostic> diagnostics) {
        Map<String, List<Candidate<Requirement>>> keyed = new LinkedHashMap<>();
        List<Candidate<Requirement>> unkeyed = new ArrayList<>();
        for (SourceRead read : reads) {
            for (Requirement original : read.content().requirements()) {
                SpecificationId targetSpecification = specificationRemap.getOrDefault(
                        original.specificationId(), original.specificationId());
                Requirement requirement = new Requirement(
                        original.id(),
                        targetSpecification,
                        original.key(),
                        original.title(),
                        original.statement(),
                        original.provenance());
                if (requirement.key().isPresent()) {
                    keyed.computeIfAbsent(normalizeKey(requirement.key().orElseThrow()), ignored -> new ArrayList<>())
                            .add(new Candidate<>(read.source(), requirement));
                } else {
                    unkeyed.add(new Candidate<>(read.source(), requirement));
                }
            }
        }

        List<Requirement> result = new ArrayList<>();
        for (Map.Entry<String, List<Candidate<Requirement>>> entry : keyed.entrySet()) {
            List<Candidate<Requirement>> group = onePerProvider(
                    entry.getValue(), item -> item.id().toString(), ProviderEntityKind.REQUIREMENT, entry.getKey(), diagnostics);
            Candidate<Requirement> winner = selectWinner(
                    ProviderEntityKind.REQUIREMENT,
                    entry.getKey(),
                    group,
                    this::equivalentRequirement,
                    item -> item.id().toString(),
                    conflicts,
                    diagnostics);
            for (Candidate<Requirement> candidate : entry.getValue()) {
                requirementRemap.put(candidate.value().id(), winner.value().id());
            }
            result.add(winner.value());
        }
        for (Candidate<Requirement> candidate : unkeyed) {
            requirementRemap.put(candidate.value().id(), candidate.value().id());
            result.add(candidate.value());
        }
        result.sort(Comparator.comparing(item -> item.id().toString()));
        return List.copyOf(result);
    }

    private List<Scenario> composeScenarios(
            List<SourceRead> reads,
            Map<RequirementId, RequirementId> requirementRemap,
            Map<RequirementId, Requirement> requirementById,
            List<ProviderCompositionConflict> conflicts,
            List<Diagnostic> diagnostics) {
        Map<String, List<Candidate<Scenario>>> keyed = new LinkedHashMap<>();
        List<Candidate<Scenario>> unkeyed = new ArrayList<>();
        for (SourceRead read : reads) {
            for (Scenario original : read.content().scenarios()) {
                Optional<RequirementId> remappedRequirement = original.requirementId()
                        .map(id -> requirementRemap.getOrDefault(id, id));
                Scenario scenario = new Scenario(
                        original.id(),
                        remappedRequirement,
                        original.title(),
                        original.preconditions(),
                        original.action(),
                        original.expectedOutcome(),
                        original.provenance());
                Optional<String> logicalKey = remappedRequirement
                        .flatMap(id -> Optional.ofNullable(requirementById.get(id)))
                        .flatMap(Requirement::key)
                        .map(key -> normalizeKey(key) + "::" + normalizeKey(scenario.title()));
                if (logicalKey.isPresent()) {
                    keyed.computeIfAbsent(logicalKey.orElseThrow(), ignored -> new ArrayList<>())
                            .add(new Candidate<>(read.source(), scenario));
                } else {
                    unkeyed.add(new Candidate<>(read.source(), scenario));
                }
            }
        }

        List<Scenario> result = new ArrayList<>();
        for (Map.Entry<String, List<Candidate<Scenario>>> entry : keyed.entrySet()) {
            List<Candidate<Scenario>> group = onePerProvider(
                    entry.getValue(), item -> item.id().toString(), ProviderEntityKind.SCENARIO, entry.getKey(), diagnostics);
            Candidate<Scenario> winner = selectWinner(
                    ProviderEntityKind.SCENARIO,
                    entry.getKey(),
                    group,
                    this::equivalentScenario,
                    item -> item.id().toString(),
                    conflicts,
                    diagnostics);
            result.add(winner.value());
        }
        unkeyed.forEach(candidate -> result.add(candidate.value()));
        result.sort(Comparator.comparing(item -> item.id().toString()));
        return List.copyOf(result);
    }

    private List<AcceptanceCriterion> composeAcceptanceCriteria(
            List<SourceRead> reads,
            Map<RequirementId, RequirementId> requirementRemap,
            List<Diagnostic> diagnostics) {
        List<AcceptanceCriterion> remapped = new ArrayList<>();
        for (SourceRead read : reads) {
            for (AcceptanceCriterion original : read.content().acceptanceCriteria()) {
                remapped.add(new AcceptanceCriterion(
                        original.id(),
                        original.requirementId().map(id -> requirementRemap.getOrDefault(id, id)),
                        original.changeId(),
                        original.title(),
                        original.condition(),
                        original.verificationStatus(),
                        original.verificationEvidenceIds(),
                        original.provenance()));
            }
        }
        return uniqueValues(remapped, item -> item.id().toString(), "acceptanceCriterion", diagnostics);
    }

    private <T> Candidate<T> selectWinner(
            ProviderEntityKind kind,
            String logicalKey,
            List<Candidate<T>> candidates,
            BiPredicate<T, T> equivalent,
            Function<T, String> id,
            List<ProviderCompositionConflict> conflicts,
            List<Diagnostic> diagnostics) {
        if (candidates.isEmpty()) {
            throw new IllegalArgumentException("candidate group must not be empty");
        }
        List<Candidate<T>> ordered = new ArrayList<>(candidates);
        ordered.sort(candidateComparator(id));
        Candidate<T> winner = ordered.getFirst();
        boolean divergent = ordered.stream().anyMatch(candidate -> !equivalent.test(winner.value(), candidate.value()));
        if (!divergent) {
            return winner;
        }

        int topPrecedence = winner.source().precedence();
        boolean divergentAtTop = ordered.stream()
                .filter(candidate -> candidate.source().precedence() == topPrecedence)
                .anyMatch(candidate -> !equivalent.test(winner.value(), candidate.value()));
        List<ProviderConflictContender> contenders = ordered.stream()
                .map(candidate -> new ProviderConflictContender(
                        candidate.source().providerId(), id.apply(candidate.value()), candidate.source().precedence()))
                .toList();
        if (divergentAtTop) {
            ProviderCompositionConflict conflict = new ProviderCompositionConflict(
                    kind,
                    logicalKey,
                    ProviderConflictResolution.UNRESOLVED_EQUAL_PRECEDENCE,
                    Optional.empty(),
                    contenders,
                    "Divergent contributions have the same highest precedence");
            conflicts.add(conflict);
            addDistinct(diagnostics, List.of(Diagnostic.error(
                    DiagnosticCode.PROVIDER_COMPOSITION_CONFLICT,
                    "Provider composition conflict cannot be resolved at equal precedence",
                    Map.of("entityKind", kind.name(), "logicalKey", logicalKey))));
            return winner;
        }

        ProviderConflictContender selected = new ProviderConflictContender(
                winner.source().providerId(), id.apply(winner.value()), winner.source().precedence());
        conflicts.add(new ProviderCompositionConflict(
                kind,
                logicalKey,
                ProviderConflictResolution.RESOLVED_BY_PRECEDENCE,
                Optional.of(selected),
                contenders,
                "Higher explicit provider precedence selected the canonical contribution"));
        addDistinct(diagnostics, List.of(Diagnostic.warning(
                DiagnosticCode.PROVIDER_COMPOSITION_CONFLICT,
                "Provider composition conflict resolved by explicit precedence",
                Map.of(
                        "entityKind", kind.name(),
                        "logicalKey", logicalKey,
                        "winnerProvider", winner.source().providerId().value()))));
        return winner;
    }

    private <T> List<Candidate<T>> onePerProvider(
            List<Candidate<T>> candidates,
            Function<T, String> id,
            ProviderEntityKind kind,
            String logicalKey,
            List<Diagnostic> diagnostics) {
        Map<ProviderId, Candidate<T>> unique = new LinkedHashMap<>();
        List<Candidate<T>> ordered = new ArrayList<>(candidates);
        ordered.sort(candidateComparator(id));
        for (Candidate<T> candidate : ordered) {
            Candidate<T> previous = unique.putIfAbsent(candidate.source().providerId(), candidate);
            if (previous != null) {
                addDistinct(diagnostics, List.of(Diagnostic.error(
                        DiagnosticCode.PROVIDER_COMPOSITION_CONFLICT,
                        "One provider contributed the same logical key more than once",
                        Map.of(
                                "provider", candidate.source().providerId().value(),
                                "entityKind", kind.name(),
                                "logicalKey", logicalKey))));
            }
        }
        return List.copyOf(unique.values());
    }

    private <T> Comparator<Candidate<T>> candidateComparator(Function<T, String> id) {
        return Comparator.<Candidate<T>>comparingInt(candidate -> candidate.source().precedence()).reversed()
                .thenComparing(candidate -> candidate.source().providerId().value())
                .thenComparing(candidate -> id.apply(candidate.value()));
    }

    private boolean equivalentSpecification(Specification left, Specification right) {
        return normalizeKey(left.key()).equals(normalizeKey(right.key()))
                && left.title().equals(right.title())
                && left.description().equals(right.description());
    }

    private boolean equivalentRequirement(Requirement left, Requirement right) {
        return left.specificationId().equals(right.specificationId())
                && left.key().map(this::normalizeKey).equals(right.key().map(this::normalizeKey))
                && left.title().equals(right.title())
                && left.statement().equals(right.statement());
    }

    private boolean equivalentScenario(Scenario left, Scenario right) {
        return left.requirementId().equals(right.requirementId())
                && left.title().equals(right.title())
                && left.preconditions().equals(right.preconditions())
                && left.action().equals(right.action())
                && left.expectedOutcome().equals(right.expectedOutcome());
    }

    private <T> List<T> uniqueById(
            List<SourceRead> reads,
            Function<NormalizedProjectContent, List<T>> values,
            Function<T, String> id,
            String type,
            List<Diagnostic> diagnostics) {
        List<T> all = new ArrayList<>();
        for (SourceRead read : reads) {
            all.addAll(values.apply(read.content()));
        }
        return uniqueValues(all, id, type, diagnostics);
    }

    private <T> List<T> uniqueValues(
            List<T> values,
            Function<T, String> id,
            String type,
            List<Diagnostic> diagnostics) {
        Map<String, T> unique = new LinkedHashMap<>();
        List<T> ordered = new ArrayList<>(values);
        ordered.sort(Comparator.comparing(id));
        for (T value : ordered) {
            String identity = id.apply(value);
            T previous = unique.putIfAbsent(identity, value);
            if (previous != null && !previous.equals(value)) {
                addDistinct(diagnostics, List.of(Diagnostic.error(
                        DiagnosticCode.IDENTITY_COLLISION,
                        "Different provider content reused the same MORPHEUS identity",
                        Map.of("entityType", type, "identity", identity))));
            }
        }
        return List.copyOf(unique.values());
    }

    private String normalizeKey(String value) {
        return Objects.requireNonNull(value, "logical key").trim().toLowerCase(Locale.ROOT);
    }

    private void addDistinct(List<Diagnostic> target, List<Diagnostic> additions) {
        for (Diagnostic diagnostic : additions) {
            if (!target.contains(diagnostic)) {
                target.add(diagnostic);
            }
        }
    }

    private record SourceRead(ProviderCompositionSource source, NormalizedProjectContent content) {
        private SourceRead {
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(content, "content");
        }
    }

    private record Candidate<T>(ProviderCompositionSource source, T value) {
        private Candidate {
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(value, "value");
        }
    }
}
