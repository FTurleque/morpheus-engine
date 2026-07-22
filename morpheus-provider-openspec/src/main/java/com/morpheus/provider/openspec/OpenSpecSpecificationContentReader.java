package com.morpheus.provider.openspec;

import com.morpheus.application.identity.EntityIdentityResolver;
import com.morpheus.application.ingestion.NormalizedProjectContent;
import com.morpheus.application.read.ProviderReadRequest;
import com.morpheus.application.read.ProviderReadResult;
import com.morpheus.application.read.ReadCategory;
import com.morpheus.application.read.ReadCategoryReport;
import com.morpheus.application.read.ReadCategoryStatus;
import com.morpheus.application.read.SpecificationContentReader;
import com.morpheus.domain.change.ChangeProposal;
import com.morpheus.domain.constraint.Constraint;
import com.morpheus.domain.decision.DesignDecision;
import com.morpheus.domain.diagnostic.Diagnostic;
import com.morpheus.domain.diagnostic.DiagnosticCode;
import com.morpheus.domain.evidence.Evidence;
import com.morpheus.domain.project.ProjectSpecification;
import com.morpheus.domain.provider.ProviderCapability;
import com.morpheus.domain.provider.ProviderId;
import com.morpheus.domain.provider.ProviderProbeResult;
import com.morpheus.domain.provider.ProviderProbeStatus;
import com.morpheus.domain.requirement.Requirement;
import com.morpheus.domain.requirement.RequirementDelta;
import com.morpheus.domain.scenario.Scenario;
import com.morpheus.domain.source.SourceLocator;
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
        ProviderProbeResult probe = provider.probe(root);
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

        ReadState state = readAvailableGroups(request, identityResolver, probe, diagnostics);
        List<ReadCategoryReport> reports = ordered(request.requestedCategories()).stream()
                .map(category -> report(category, probe, state))
                .toList();

        addUnsupportedDiagnostics(reports, diagnostics);
        addPartialDiagnosticWhenNeeded(reports, diagnostics);

        String displayName = root.getFileName() == null ? root.toString() : root.getFileName().toString();
        ProjectSpecification project = new ProjectSpecification(
                request.projectId(),
                displayName,
                SourceLocator.file(root.toString()));

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
            List<Diagnostic> diagnostics) {
        ReadState state = new ReadState();
        Set<ReadCategory> requested = request.requestedCategories();

        boolean needCurrent = requested.stream().anyMatch(CURRENT_CATEGORIES::contains);
        boolean needChanges = requested.stream().anyMatch(CHANGE_CATEGORIES::contains)
                || requested.contains(ReadCategory.REQUIREMENT_DELTAS);
        boolean needDeltas = requested.contains(ReadCategory.REQUIREMENT_DELTAS);

        if (needCurrent && probe.capabilities().contains(ProviderCapability.READ_CURRENT_SPECIFICATIONS)) {
            state.currentAttempted = true;
            try {
                NormalizedProjectContent current = currentReader.read(
                        request.workspaceRoot(), request.projectId(), identityResolver);
                state.specifications.addAll(current.specifications());
                state.requirements.addAll(current.requirements());
                state.scenarios.addAll(current.scenarios());
                state.evidence.addAll(current.evidence());
                addDistinct(diagnostics, current.diagnostics());
            } catch (RuntimeException exception) {
                state.currentFailed = true;
                addDistinct(diagnostics, List.of(invalidSource("current", exception)));
            }
        }

        if (needChanges && probe.capabilities().contains(ProviderCapability.READ_CHANGES)) {
            state.changeAttempted = true;
            try {
                NormalizedProjectContent changes = changeReader.read(
                        request.workspaceRoot(), request.projectId(), identityResolver);
                state.changes.addAll(changes.changes());
                state.constraints.addAll(changes.constraints());
                state.designDecisions.addAll(changes.designDecisions());
                state.tasks.addAll(changes.tasks());
                state.evidence.addAll(changes.evidence());
                addDistinct(diagnostics, changes.diagnostics());
            } catch (RuntimeException exception) {
                state.changeFailed = true;
                addDistinct(diagnostics, List.of(invalidSource("changes", exception)));
            }
        }

        if (needDeltas
                && !state.changeFailed
                && probe.capabilities().contains(ProviderCapability.READ_CHANGES)) {
            state.deltaAttempted = true;
            try {
                OpenSpecRequirementDeltaReader.ReadResult deltas = deltaReader.read(
                        request.workspaceRoot(), identityResolver);
                state.requirementDeltas.addAll(deltas.requirementDeltas());
                state.evidence.addAll(deltas.evidence());
                addDistinct(diagnostics, deltas.diagnostics());
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
        return Diagnostic.error(
                DiagnosticCode.INVALID_SOURCE,
                "OpenSpec content reader failed for group " + group,
                Map.of(
                        "provider", providerId().value(),
                        "group", group,
                        "exception", exception.getClass().getSimpleName()));
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
        private boolean currentAttempted;
        private boolean currentFailed;
        private boolean changeAttempted;
        private boolean changeFailed;
        private boolean deltaAttempted;
        private boolean deltaFailed;
    }
}
