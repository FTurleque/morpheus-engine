package com.morpheus.provider.openspec;

import com.morpheus.application.identity.EntityIdentityResolver;
import com.morpheus.application.ingestion.NormalizedProjectContent;
import com.morpheus.application.read.ProviderIngestionBudget;
import com.morpheus.application.read.ProviderIngestionLimitException;
import com.morpheus.application.read.ProviderProjectRoot;
import com.morpheus.application.read.ProviderReadRequest;
import com.morpheus.application.read.ProviderReadResult;
import com.morpheus.application.read.ReadCategory;
import com.morpheus.application.read.ReadCategoryReport;
import com.morpheus.application.read.ReadCategoryStatus;
import com.morpheus.application.read.SpecificationContentReader;
import com.morpheus.domain.change.ChangeId;
import com.morpheus.domain.change.ChangeProposal;
import com.morpheus.domain.constraint.Constraint;
import com.morpheus.domain.decision.DesignDecision;
import com.morpheus.domain.diagnostic.Diagnostic;
import com.morpheus.domain.diagnostic.DiagnosticCode;
import com.morpheus.domain.diagnostic.DiagnosticSeverity;
import com.morpheus.domain.evidence.Evidence;
import com.morpheus.domain.evidence.EvidenceId;
import com.morpheus.domain.project.ProjectSpecification;
import com.morpheus.domain.provider.ProviderCapability;
import com.morpheus.domain.provider.ProviderId;
import com.morpheus.domain.provider.ProviderProbeResult;
import com.morpheus.domain.provider.ProviderProbeStatus;
import com.morpheus.domain.requirement.Requirement;
import com.morpheus.domain.requirement.RequirementDelta;
import com.morpheus.domain.scenario.Scenario;
import com.morpheus.domain.specification.Specification;
import com.morpheus.domain.task.ImplementationTask;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Unified M2 OpenSpec read adapter with explicit per-category outcomes. */
public final class OpenSpecSpecificationContentReader implements SpecificationContentReader {
    private final OpenSpecSpecificationProvider provider;
    private final OpenSpecCurrentSpecificationReader currentReader;
    private final OpenSpecChangeMetadataReader changeReader;
    private final OpenSpecRequirementDeltaReader deltaReader;

    public OpenSpecSpecificationContentReader() {
        this(
                new OpenSpecSpecificationProvider(),
                new OpenSpecCurrentSpecificationReader(),
                new OpenSpecChangeMetadataReader(),
                new OpenSpecRequirementDeltaReader());
    }

    OpenSpecSpecificationContentReader(
            OpenSpecSpecificationProvider provider,
            OpenSpecCurrentSpecificationReader currentReader,
            OpenSpecChangeMetadataReader changeReader,
            OpenSpecRequirementDeltaReader deltaReader) {
        this.provider = Objects.requireNonNull(provider, "provider");
        this.currentReader = Objects.requireNonNull(currentReader, "currentReader");
        this.changeReader = Objects.requireNonNull(changeReader, "changeReader");
        this.deltaReader = Objects.requireNonNull(deltaReader, "deltaReader");
    }

    @Override
    public ProviderId providerId() {
        return OpenSpecSpecificationProvider.ID;
    }

    @Override
    public ProviderReadResult read(ProviderReadRequest request, EntityIdentityResolver identityResolver) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(identityResolver, "identityResolver");

        Path root = request.workspaceRoot();
        ProviderIngestionBudget.Session budget = OpenSpecIngestionBudgets.open(root);
        ProviderProbeResult probe = provider.probe(root, budget);
        List<Diagnostic> diagnostics = new ArrayList<>(probe.diagnostics());

        if (probe.status() != ProviderProbeStatus.SUPPORTED) {
            List<DiagnosticCode> codes = diagnostics.stream().map(Diagnostic::code).distinct().toList();
            List<ReadCategoryReport> reports = ordered(request.requestedCategories()).stream()
                    .map(category -> new ReadCategoryReport(
                            category,
                            ReadCategoryStatus.FAILED,
                            0,
                            codes,
                            Optional.of("provider probe did not support the requested source")))
                    .toList();
            return new ProviderReadResult(providerId(), Optional.empty(), reports, diagnostics);
        }

        ReadState state;
        try {
            state = readAvailableGroups(request, identityResolver, probe, diagnostics, budget);
        } catch (ProviderIngestionLimitException exception) {
            addDistinct(diagnostics, List.of(invalidSource("ingestion-budget", exception)));
            List<ReadCategoryReport> reports = ordered(request.requestedCategories()).stream()
                    .map(category -> failed(category, "provider ingestion budget exceeded"))
                    .toList();
            return new ProviderReadResult(providerId(), Optional.empty(), reports, diagnostics);
        }
        List<ReadCategoryReport> reports = ordered(request.requestedCategories()).stream()
                .map(category -> report(category, probe, state))
                .toList();

        addUnsupportedDiagnostics(reports, diagnostics);
        addPartialDiagnosticWhenNeeded(reports, diagnostics);

        String displayName = root.getFileName() == null ? root.toString() : root.getFileName().toString();
        ProjectSpecification project = new ProjectSpecification(
                request.projectId(),
                displayName,
                ProviderProjectRoot.locator(root));

        NormalizedProjectContent content = new NormalizedProjectContent(
                project,
                state.specifications,
                state.requirements,
                state.scenarios,
                state.changes,
                state.requirementDeltas,
                state.constraints,
                state.designDecisions,
                state.tasks,
                state.evidence,
                diagnostics);

        return new ProviderReadResult(providerId(), Optional.of(content), reports, diagnostics);
    }

    private ReadState readAvailableGroups(
            ProviderReadRequest request,
            EntityIdentityResolver identityResolver,
            ProviderProbeResult probe,
            List<Diagnostic> diagnostics,
            ProviderIngestionBudget.Session budget) {
        ReadState state = new ReadState();
        Set<ReadCategory> requested = request.requestedCategories();

        boolean needCurrent = requested.stream().anyMatch(CURRENT_CATEGORIES::contains);
        boolean needChanges = requested.stream().anyMatch(CHANGE_CATEGORIES::contains)
                || requested.contains(ReadCategory.REQUIREMENT_DELTAS);
        boolean needDeltas = requested.contains(ReadCategory.REQUIREMENT_DELTAS);

        if (needCurrent && probe.capabilities().contains(ProviderCapability.READ_CURRENT_SPECIFICATIONS)) {
            try {
                NormalizedProjectContent current = currentReader.read(
                        request.workspaceRoot(), request.projectId(), identityResolver, budget);
                state.specifications.addAll(current.specifications());
                state.requirements.addAll(current.requirements());
                state.scenarios.addAll(current.scenarios());
                state.evidence.addAll(current.evidence());
                state.currentUnclosedCodeFences = (int) current.diagnostics().stream()
                        .filter(diagnostic -> diagnostic.code() == DiagnosticCode.UNCLOSED_CODE_FENCE)
                        .count();
                addDistinct(diagnostics, current.diagnostics());
            } catch (ProviderIngestionLimitException exception) {
                throw exception;
            } catch (RuntimeException exception) {
                state.currentFailed = true;
                addDistinct(diagnostics, List.of(invalidSource("current", exception)));
            }
        }

        if (needChanges && probe.capabilities().contains(ProviderCapability.READ_CHANGES)) {
            try {
                OpenSpecChangeMetadataReader.ContainedRead read = changeReader.readContained(
                        request.workspaceRoot(), request.projectId(), identityResolver, budget);
                NormalizedProjectContent changes = read.content();
                state.changes.addAll(changes.changes());
                state.constraints.addAll(changes.constraints());
                state.designDecisions.addAll(changes.designDecisions());
                state.tasks.addAll(changes.tasks());
                state.evidence.addAll(changes.evidence());
                addDistinct(diagnostics, changes.diagnostics());
                for (RuntimeException rejected : read.rejectedChanges()) {
                    addDistinct(diagnostics, List.of(invalidSource("changes", rejected)));
                }
                state.rejectedChanges = read.rejectedChanges().size();
                state.changeFailed = state.rejectedChanges > 0 && state.changes.isEmpty();
            } catch (ProviderIngestionLimitException exception) {
                throw exception;
            } catch (RuntimeException exception) {
                state.changeFailed = true;
                addDistinct(diagnostics, List.of(invalidSource("changes", exception)));
            }
        }

        if (needDeltas
                && !state.changeFailed
                && probe.capabilities().contains(ProviderCapability.READ_CHANGES)) {
            try {
                OpenSpecRequirementDeltaReader.ReadResult deltas = deltaReader.read(
                        request.workspaceRoot(), identityResolver, budget);
                // A delta of a rejected change would reference a change that is not published.
                Set<ChangeId> readChanges = new HashSet<>();
                state.changes.forEach(change -> readChanges.add(change.id()));
                Set<EvidenceId> droppedEvidence = new HashSet<>();
                for (RequirementDelta delta : deltas.requirementDeltas()) {
                    if (readChanges.contains(delta.changeId())) {
                        state.requirementDeltas.add(delta);
                    } else {
                        state.droppedRequirementDeltas++;
                        droppedEvidence.add(delta.provenance().evidenceId());
                        delta.scenarios().forEach(scenario -> droppedEvidence.add(scenario.provenance().evidenceId()));
                    }
                }
                state.skippedRequirementDeltas = deltas.skippedRequirements();
                state.unclosedCodeFences = deltas.unclosedCodeFences();
                deltas.evidence().stream()
                        .filter(item -> !droppedEvidence.contains(item.id()))
                        .forEach(state.evidence::add);
                addDistinct(diagnostics, deltas.diagnostics());
            } catch (ProviderIngestionLimitException exception) {
                throw exception;
            } catch (RuntimeException exception) {
                state.deltaFailed = true;
                addDistinct(diagnostics, List.of(invalidSource("requirement-deltas", exception)));
            }
        }

        return state;
    }

    private ReadCategoryReport report(ReadCategory category, ProviderProbeResult probe, ReadState state) {
        return switch (category) {
            case CURRENT_SPECIFICATIONS -> currentReport(
                    category, probe, state, state.specifications.size(), false);
            case REQUIREMENTS -> currentReport(
                    category, probe, state, state.requirements.size(), false);
            case SCENARIOS -> currentReport(
                    category, probe, state, state.scenarios.size(), hasMissingCurrentScenario(state));
            case CHANGES -> changeReport(category, probe, state, state.changes.size());
            case REQUIREMENT_DELTAS -> deltaReport(category, probe, state);
            case CONSTRAINTS -> changeReport(category, probe, state, state.constraints.size());
            case DESIGN_DECISIONS -> changeReport(category, probe, state, state.designDecisions.size());
            case IMPLEMENTATION_TASKS -> changeReport(category, probe, state, state.tasks.size());
            case ACCEPTANCE_CRITERIA -> unsupported(
                    category,
                    "OpenSpec scenarios are not automatically acceptance criteria");
            case EXTERNAL_REFERENCES -> unsupported(
                    category,
                    "OpenSpec external-reference ingestion is not defined in M2-S6");
            case ARCHIVES -> unsupported(
                    category,
                    "archive normalization is deferred to M3 temporal projection");
        };
    }

    private ReadCategoryReport currentReport(
            ReadCategory category,
            ProviderProbeResult probe,
            ReadState state,
            int count,
            boolean partial) {
        if (state.currentFailed) {
            return failed(category, "current specification reader failed");
        }
        if (!probe.capabilities().contains(ProviderCapability.READ_CURRENT_SPECIFICATIONS)) {
            return ReadCategoryReport.of(category, ReadCategoryStatus.ABSENT, 0);
        }
        if (state.currentUnclosedCodeFences > 0) {
            return new ReadCategoryReport(
                    category,
                    ReadCategoryStatus.PARTIAL,
                    count,
                    List.of(DiagnosticCode.PARTIAL_INGESTION, DiagnosticCode.UNCLOSED_CODE_FENCE),
                    Optional.of("at least one specification opens a code fence that is never closed"));
        }
        if (partial) {
            return new ReadCategoryReport(
                    category,
                    ReadCategoryStatus.PARTIAL,
                    count,
                    List.of(DiagnosticCode.PARTIAL_INGESTION),
                    Optional.of("at least one requirement has no normalized scenario"));
        }
        return ReadCategoryReport.of(
                category,
                count == 0 ? ReadCategoryStatus.ABSENT : ReadCategoryStatus.READ,
                count);
    }

    private ReadCategoryReport changeReport(
            ReadCategory category,
            ProviderProbeResult probe,
            ReadState state,
            int count) {
        if (state.changeFailed) {
            return failed(category, "change metadata reader failed");
        }
        if (!probe.capabilities().contains(ProviderCapability.READ_CHANGES)) {
            return ReadCategoryReport.of(category, ReadCategoryStatus.ABSENT, 0);
        }
        if (state.rejectedChanges > 0) {
            // What a rejected change held is unknown, so even an empty count is PARTIAL, never ABSENT.
            return new ReadCategoryReport(
                    category,
                    ReadCategoryStatus.PARTIAL,
                    count,
                    List.of(DiagnosticCode.PARTIAL_INGESTION, DiagnosticCode.INVALID_SOURCE),
                    Optional.of("at least one change was rejected"));
        }
        return ReadCategoryReport.of(
                category,
                count == 0 ? ReadCategoryStatus.ABSENT : ReadCategoryStatus.READ,
                count);
    }

    private ReadCategoryReport deltaReport(
            ReadCategory category,
            ProviderProbeResult probe,
            ReadState state) {
        if (state.changeFailed || state.deltaFailed) {
            return failed(category, "requirement delta reader failed");
        }
        if (!probe.capabilities().contains(ProviderCapability.READ_CHANGES)) {
            return ReadCategoryReport.of(category, ReadCategoryStatus.ABSENT, 0);
        }
        int count = state.requirementDeltas.size();
        if (state.skippedRequirementDeltas > 0 || state.unclosedCodeFences > 0 || state.droppedRequirementDeltas > 0) {
            List<DiagnosticCode> codes = new ArrayList<>(List.of(DiagnosticCode.PARTIAL_INGESTION));
            List<String> details = new ArrayList<>();
            if (state.droppedRequirementDeltas > 0) {
                codes.add(DiagnosticCode.INVALID_SOURCE);
                details.add("at least one delta belongs to a rejected change");
            }
            if (state.skippedRequirementDeltas > 0) {
                details.add("at least one requirement was not normalized");
            }
            if (state.unclosedCodeFences > 0) {
                codes.add(DiagnosticCode.UNCLOSED_CODE_FENCE);
                details.add("at least one delta file opens a code fence that is never closed");
            }
            return new ReadCategoryReport(
                    category,
                    ReadCategoryStatus.PARTIAL,
                    count,
                    codes,
                    Optional.of(String.join("; ", details)));
        }
        return ReadCategoryReport.of(
                category,
                count == 0 ? ReadCategoryStatus.ABSENT : ReadCategoryStatus.READ,
                count);
    }

    private ReadCategoryReport failed(ReadCategory category, String detail) {
        return new ReadCategoryReport(
                category,
                ReadCategoryStatus.FAILED,
                0,
                List.of(DiagnosticCode.INVALID_SOURCE),
                Optional.of(detail));
    }

    private ReadCategoryReport unsupported(ReadCategory category, String detail) {
        return new ReadCategoryReport(
                category,
                ReadCategoryStatus.UNSUPPORTED,
                0,
                List.of(DiagnosticCode.OPTIONAL_CAPABILITY_UNAVAILABLE),
                Optional.of(detail));
    }

    private boolean hasMissingCurrentScenario(ReadState state) {
        if (state.requirements.isEmpty()) {
            return false;
        }
        Set<Object> scenarioRequirementIds = new HashSet<>();
        for (Scenario scenario : state.scenarios) {
            scenario.requirementId().ifPresent(scenarioRequirementIds::add);
        }
        return state.requirements.stream().anyMatch(requirement -> !scenarioRequirementIds.contains(requirement.id()));
    }

    private void addUnsupportedDiagnostics(List<ReadCategoryReport> reports, List<Diagnostic> diagnostics) {
        for (ReadCategoryReport report : reports) {
            if (report.status() != ReadCategoryStatus.UNSUPPORTED) {
                continue;
            }
            addDistinct(diagnostics, List.of(Diagnostic.warning(
                    DiagnosticCode.OPTIONAL_CAPABILITY_UNAVAILABLE,
                    "Requested read category is unavailable from this provider contract",
                    Map.of(
                            "provider", providerId().value(),
                            "category", report.category().name()))));
        }
    }

    private void addPartialDiagnosticWhenNeeded(List<ReadCategoryReport> reports, List<Diagnostic> diagnostics) {
        boolean partial = reports.stream().anyMatch(report -> report.status() == ReadCategoryStatus.PARTIAL);
        boolean failed = reports.stream().anyMatch(report -> report.status() == ReadCategoryStatus.FAILED);
        boolean read = reports.stream().anyMatch(report -> report.status() == ReadCategoryStatus.READ
                || report.status() == ReadCategoryStatus.PARTIAL);
        if (partial || (failed && read)) {
            addDistinct(diagnostics, List.of(Diagnostic.warning(
                    DiagnosticCode.PARTIAL_INGESTION,
                    "Requested content was only partially normalized",
                    Map.of("provider", providerId().value()))));
        }
    }

    private Diagnostic invalidSource(String group, RuntimeException exception) {
        Optional<String> source = exception instanceof OpenSpecSourceAttribution.AttributedFailure attributed
                ? Optional.of(attributed.source())
                : Optional.empty();
        return new Diagnostic(
                DiagnosticCode.INVALID_SOURCE,
                DiagnosticSeverity.ERROR,
                "OpenSpec content reader failed for group " + group + ": "
                        + OpenSpecSourceAttribution.relayable(exception),
                Map.of(
                        "provider", providerId().value(),
                        "group", group,
                        "exception", OpenSpecSourceAttribution.failureType(exception)),
                source);
    }

    private List<ReadCategory> ordered(Set<ReadCategory> categories) {
        List<ReadCategory> result = new ArrayList<>();
        for (ReadCategory category : ReadCategory.values()) {
            if (categories.contains(category)) {
                result.add(category);
            }
        }
        return result;
    }

    private void addDistinct(List<Diagnostic> target, List<Diagnostic> additions) {
        for (Diagnostic diagnostic : additions) {
            if (!target.contains(diagnostic)) {
                target.add(diagnostic);
            }
        }
    }

    private static final EnumSet<ReadCategory> CURRENT_CATEGORIES = EnumSet.of(
            ReadCategory.CURRENT_SPECIFICATIONS,
            ReadCategory.REQUIREMENTS,
            ReadCategory.SCENARIOS);

    private static final EnumSet<ReadCategory> CHANGE_CATEGORIES = EnumSet.of(
            ReadCategory.CHANGES,
            ReadCategory.CONSTRAINTS,
            ReadCategory.DESIGN_DECISIONS,
            ReadCategory.IMPLEMENTATION_TASKS);

    private static final class ReadState {
        private final List<Specification> specifications = new ArrayList<>();
        private final List<Requirement> requirements = new ArrayList<>();
        private final List<Scenario> scenarios = new ArrayList<>();
        private final List<ChangeProposal> changes = new ArrayList<>();
        private final List<RequirementDelta> requirementDeltas = new ArrayList<>();
        private final List<Constraint> constraints = new ArrayList<>();
        private final List<DesignDecision> designDecisions = new ArrayList<>();
        private final List<ImplementationTask> tasks = new ArrayList<>();
        private final List<Evidence> evidence = new ArrayList<>();
        private boolean currentFailed;
        private int currentUnclosedCodeFences;
        private boolean changeFailed;
        private boolean deltaFailed;
        private int rejectedChanges;
        private int droppedRequirementDeltas;
        private int skippedRequirementDeltas;
        private int unclosedCodeFences;
    }
}
