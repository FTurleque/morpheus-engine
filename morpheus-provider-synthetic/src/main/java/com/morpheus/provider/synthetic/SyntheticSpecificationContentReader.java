package com.morpheus.provider.synthetic;

import com.morpheus.application.identity.EntityIdentityResolver;
import com.morpheus.application.ingestion.NormalizedProjectContent;
import com.morpheus.application.read.ProviderReadRequest;
import com.morpheus.application.read.ProviderReadResult;
import com.morpheus.application.read.ReadCategory;
import com.morpheus.application.read.ReadCategoryReport;
import com.morpheus.application.read.ReadCategoryStatus;
import com.morpheus.application.read.SpecificationContentReader;
import com.morpheus.domain.acceptance.AcceptanceCriterion;
import com.morpheus.domain.acceptance.AcceptanceCriterionId;
import com.morpheus.domain.acceptance.VerificationStatus;
import com.morpheus.domain.change.ChangeId;
import com.morpheus.domain.change.ChangeProposal;
import com.morpheus.domain.diagnostic.Diagnostic;
import com.morpheus.domain.diagnostic.DiagnosticCode;
import com.morpheus.domain.evidence.Evidence;
import com.morpheus.domain.evidence.EvidenceId;
import com.morpheus.domain.evidence.SourceRange;
import com.morpheus.domain.project.ProjectSpecification;
import com.morpheus.domain.provenance.Provenance;
import com.morpheus.domain.provider.ProviderId;
import com.morpheus.domain.provider.ProviderProbeStatus;
import com.morpheus.domain.requirement.Requirement;
import com.morpheus.domain.requirement.RequirementId;
import com.morpheus.domain.scenario.Scenario;
import com.morpheus.domain.scenario.ScenarioId;
import com.morpheus.domain.source.SourceLocator;
import com.morpheus.domain.specification.Specification;
import com.morpheus.domain.specification.SpecificationId;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Normalizes the verification-only synthetic JSON source through the public read contract. */
public final class SyntheticSpecificationContentReader implements SpecificationContentReader {
    private final SyntheticSpecificationProvider provider;

    public SyntheticSpecificationContentReader() {
        this(new SyntheticSpecificationProvider());
    }

    SyntheticSpecificationContentReader(SyntheticSpecificationProvider provider) {
        this.provider = Objects.requireNonNull(provider, "provider");
    }

    @Override
    public ProviderId providerId() {
        return SyntheticSpecificationProvider.ID;
    }

    @Override
    public ProviderReadResult read(ProviderReadRequest request, EntityIdentityResolver identityResolver) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(identityResolver, "identityResolver");

        Path root = request.workspaceRoot();
        var probe = provider.probe(root);
        if (probe.status() != ProviderProbeStatus.SUPPORTED) {
            List<Diagnostic> diagnostics = probe.diagnostics().isEmpty()
                    ? List.of(Diagnostic.error(
                            DiagnosticCode.UNSUPPORTED_SOURCE,
                            "Synthetic provider does not recognize the requested workspace",
                            Map.of("workspace", root.toString())))
                    : probe.diagnostics();
            return new ProviderReadResult(
                    providerId(),
                    Optional.empty(),
                    request.requestedCategories().stream()
                            .sorted()
                            .map(category -> report(category, ReadCategoryStatus.FAILED, 0, diagnostics.getFirst().code()))
                            .toList(),
                    diagnostics);
        }

        Path sourceFile = root.resolve(SyntheticSpecificationProvider.SOURCE_FILE);
        try {
            String sourceText = Files.readString(sourceFile, StandardCharsets.UTF_8);
            Map<String, Object> payload = SyntheticJsonParser.parseObject(sourceText);
            Normalization normalization = normalize(payload, sourceText, request, identityResolver);

            List<Diagnostic> diagnostics = new ArrayList<>();
            List<ReadCategoryReport> reports = new ArrayList<>();
            for (ReadCategory category : request.requestedCategories().stream().sorted().toList()) {
                reports.add(report(category, normalization, diagnostics));
            }

            NormalizedProjectContent content = new NormalizedProjectContent(
                    normalization.project(),
                    normalization.specifications(),
                    normalization.requirements(),
                    normalization.scenarios(),
                    normalization.changes(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    normalization.acceptanceCriteria(),
                    normalization.evidence(),
                    diagnostics);
            return new ProviderReadResult(providerId(), Optional.of(content), reports, diagnostics);
        } catch (IOException | IllegalArgumentException exception) {
            Diagnostic diagnostic = Diagnostic.error(
                    DiagnosticCode.INVALID_SOURCE,
                    "Synthetic source read failed: " + exception.getMessage(),
                    Map.of("source", sourceFile.toString()));
            return new ProviderReadResult(
                    providerId(),
                    Optional.empty(),
                    request.requestedCategories().stream()
                            .sorted()
                            .map(category -> report(category, ReadCategoryStatus.FAILED, 0, diagnostic.code()))
                            .toList(),
                    List.of(diagnostic));
        }
    }

    private Normalization normalize(
            Map<String, Object> payload,
            String sourceText,
            ProviderReadRequest request,
            EntityIdentityResolver identities) throws IOException {
        SourceLocator source = SourceLocator.file(SyntheticSpecificationProvider.SOURCE_FILE);
        int sourceLines = Math.max(1, sourceText.lines().toList().size());
        List<Evidence> evidence = new ArrayList<>();
        List<AcceptanceCriterion> acceptanceCriteria = new ArrayList<>();

        Map<String, Object> current = object(payload, "current");
        Map<String, Object> specificationSource = object(current, "specification");
        String specificationKey = string(specificationSource, "key");
        String specificationTitle = string(specificationSource, "title");
        String specificationExternalId = "specification:" + specificationKey;
        Evidence specificationEvidence = evidence(identities, specificationExternalId, source, sourceText, sourceLines);
        evidence.add(specificationEvidence);
        SpecificationId specificationId = new SpecificationId(identities.resolve(
                providerId(), "specification", specificationExternalId));
        Specification specification = new Specification(
                specificationId,
                request.projectId(),
                specificationKey,
                specificationTitle,
                Optional.empty(),
                provenance(specificationExternalId, source, specificationEvidence.id()));

        List<Requirement> requirements = new ArrayList<>();
        List<Scenario> scenarios = new ArrayList<>();
        for (Object rawRequirement : array(current, "requirements")) {
            Map<String, Object> requirementSource = object(rawRequirement, "requirement");
            String requirementKey = string(requirementSource, "key");
            String requirementExternalId = "requirement:" + requirementKey;
            Evidence requirementEvidence = evidence(identities, requirementExternalId, source, sourceText, sourceLines);
            evidence.add(requirementEvidence);
            RequirementId requirementId = new RequirementId(identities.resolve(
                    providerId(), "requirement", requirementExternalId));
            requirements.add(new Requirement(
                    requirementId,
                    specificationId,
                    Optional.of(requirementKey),
                    string(requirementSource, "title"),
                    string(requirementSource, "statement"),
                    provenance(requirementExternalId, source, requirementEvidence.id())));

            for (Object rawScenario : array(requirementSource, "scenarios")) {
                Map<String, Object> scenarioSource = object(rawScenario, "scenario");
                String scenarioTitle = string(scenarioSource, "title");
                String scenarioExternalId = "scenario:" + requirementKey + "/" + slug(scenarioTitle);
                ScenarioSemantics semantics = semantics(strings(scenarioSource, "steps"));
                Evidence scenarioEvidence = evidence(identities, scenarioExternalId, source, sourceText, sourceLines);
                evidence.add(scenarioEvidence);
                ScenarioId scenarioId = new ScenarioId(identities.resolve(providerId(), "scenario", scenarioExternalId));
                scenarios.add(new Scenario(
                        scenarioId,
                        Optional.of(requirementId),
                        scenarioTitle,
                        semantics.preconditions(),
                        semantics.action(),
                        semantics.expectedOutcome(),
                        provenance(scenarioExternalId, source, scenarioEvidence.id())));
            }

            for (Object rawCriterion : optionalArray(requirementSource, "acceptance_criteria")) {
                acceptanceCriteria.add(acceptanceCriterion(
                        object(rawCriterion, "acceptance criterion"),
                        Optional.of(requirementId),
                        Optional.empty(),
                        requirementExternalId,
                        source,
                        sourceText,
                        sourceLines,
                        request.workspaceRoot(),
                        identities,
                        evidence));
            }
        }

        List<ChangeProposal> changes = new ArrayList<>();
        Map<String, Object> proposed = object(payload, "proposed");
        for (Object rawChange : array(proposed, "changes")) {
            Map<String, Object> changeSource = object(rawChange, "change");
            String changeKey = string(changeSource, "key");
            String changeExternalId = "change:" + changeKey;
            Map<String, Object> proposal = object(changeSource, "proposal");
            Evidence changeEvidence = evidence(identities, changeExternalId, source, sourceText, sourceLines);
            evidence.add(changeEvidence);
            ChangeId changeId = new ChangeId(identities.resolve(providerId(), "change", changeExternalId));
            changes.add(new ChangeProposal(
                    changeId,
                    request.projectId(),
                    Optional.of(changeKey),
                    string(changeSource, "title"),
                    string(proposal, "intent"),
                    strings(proposal, "scope"),
                    strings(proposal, "out_of_scope"),
                    strings(proposal, "risks"),
                    provenance(changeExternalId, source, changeEvidence.id())));

            for (Object rawCriterion : optionalArray(changeSource, "acceptance_criteria")) {
                acceptanceCriteria.add(acceptanceCriterion(
                        object(rawCriterion, "acceptance criterion"),
                        Optional.empty(),
                        Optional.of(changeId),
                        changeExternalId,
                        source,
                        sourceText,
                        sourceLines,
                        request.workspaceRoot(),
                        identities,
                        evidence));
            }
        }

        String displayName = request.workspaceRoot().getFileName() == null
                ? request.workspaceRoot().toString()
                : request.workspaceRoot().getFileName().toString();
        ProjectSpecification project = new ProjectSpecification(
                request.projectId(),
                displayName,
                SourceLocator.file(request.workspaceRoot().toString()));

        return new Normalization(
                project,
                List.of(specification),
                requirements,
                scenarios,
                changes,
                acceptanceCriteria,
                evidence);
    }

    private AcceptanceCriterion acceptanceCriterion(
            Map<String, Object> criterionSource,
            Optional<RequirementId> requirementId,
            Optional<ChangeId> changeId,
            String ownerExternalId,
            SourceLocator source,
            String sourceText,
            int sourceLines,
            Path workspaceRoot,
            EntityIdentityResolver identities,
            List<Evidence> evidence) throws IOException {
        String key = string(criterionSource, "key");
        String criterionExternalId = "acceptance-criterion:" + ownerExternalId + "/" + key;
        Evidence criterionEvidence = evidence(
                identities,
                criterionExternalId,
                source,
                sourceText,
                sourceLines);
        evidence.add(criterionEvidence);

        List<EvidenceId> verificationEvidenceIds = new ArrayList<>();
        for (Object rawVerificationEvidence : optionalArray(criterionSource, "verification_evidence")) {
            Map<String, Object> verificationEvidence = object(rawVerificationEvidence, "verification evidence");
            String relativePath = string(verificationEvidence, "source");
            Evidence item = fileEvidence(
                    workspaceRoot,
                    relativePath,
                    identities,
                    criterionExternalId + "/verification/" + relativePath);
            evidence.add(item);
            verificationEvidenceIds.add(item.id());
        }

        VerificationStatus status;
        try {
            status = VerificationStatus.valueOf(string(criterionSource, "verification_status").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "unsupported verification_status for acceptance criterion " + key,
                    exception);
        }

        AcceptanceCriterionId criterionId = new AcceptanceCriterionId(identities.resolve(
                providerId(), "acceptance-criterion", criterionExternalId));
        return new AcceptanceCriterion(
                criterionId,
                requirementId,
                changeId,
                string(criterionSource, "title"),
                string(criterionSource, "condition"),
                status,
                verificationEvidenceIds,
                provenance(criterionExternalId, source, criterionEvidence.id()));
    }

    private Evidence fileEvidence(
            Path workspaceRoot,
            String relativePath,
            EntityIdentityResolver identities,
            String externalId) throws IOException {
        Path normalizedRoot = workspaceRoot.toAbsolutePath().normalize();
        Path file = normalizedRoot.resolve(relativePath).normalize();
        if (!file.startsWith(normalizedRoot)) {
            throw new IllegalArgumentException("verification evidence escapes workspace: " + relativePath);
        }
        if (!Files.isRegularFile(file)) {
            throw new IllegalArgumentException("verification evidence file does not exist: " + relativePath);
        }
        String text = Files.readString(file, StandardCharsets.UTF_8);
        int lines = Math.max(1, text.lines().toList().size());
        EvidenceId evidenceId = new EvidenceId(identities.resolve(
                providerId(), "evidence", "evidence:" + externalId));
        return new Evidence(
                evidenceId,
                SourceLocator.file(relativePath),
                Optional.of(new SourceRange(1, lines)),
                Optional.of(sha256(text)));
    }

    private ReadCategoryReport report(
            ReadCategory category,
            Normalization normalization,
            List<Diagnostic> diagnostics) {
        return switch (category) {
            case CURRENT_SPECIFICATIONS -> ReadCategoryReport.of(
                    category, ReadCategoryStatus.READ, normalization.specifications().size());
            case REQUIREMENTS -> ReadCategoryReport.of(
                    category, ReadCategoryStatus.READ, normalization.requirements().size());
            case SCENARIOS -> ReadCategoryReport.of(
                    category, ReadCategoryStatus.READ, normalization.scenarios().size());
            case CHANGES -> normalization.changes().isEmpty()
                    ? ReadCategoryReport.of(category, ReadCategoryStatus.ABSENT, 0)
                    : ReadCategoryReport.of(category, ReadCategoryStatus.READ, normalization.changes().size());
            case ACCEPTANCE_CRITERIA -> normalization.acceptanceCriteria().isEmpty()
                    ? ReadCategoryReport.of(category, ReadCategoryStatus.ABSENT, 0)
                    : ReadCategoryReport.of(category, ReadCategoryStatus.READ, normalization.acceptanceCriteria().size());
            default -> unsupported(category, diagnostics);
        };
    }

    private ReadCategoryReport unsupported(ReadCategory category, List<Diagnostic> diagnostics) {
        Diagnostic diagnostic = Diagnostic.warning(
                DiagnosticCode.OPTIONAL_CAPABILITY_UNAVAILABLE,
                "Synthetic verification provider does not expose requested category " + category,
                Map.of("category", category.name(), "provider", providerId().value()));
        diagnostics.add(diagnostic);
        return report(category, ReadCategoryStatus.UNSUPPORTED, 0, diagnostic.code());
    }

    private ReadCategoryReport report(
            ReadCategory category,
            ReadCategoryStatus status,
            int itemCount,
            DiagnosticCode code) {
        return new ReadCategoryReport(category, status, itemCount, List.of(code), Optional.empty());
    }

    private ScenarioSemantics semantics(List<String> steps) {
        List<String> preconditions = new ArrayList<>();
        StringBuilder action = new StringBuilder();
        StringBuilder expected = new StringBuilder();
        for (String raw : steps) {
            String step = raw.trim();
            int separator = step.indexOf(' ');
            if (separator <= 0) {
                continue;
            }
            String keyword = step.substring(0, separator).toUpperCase(Locale.ROOT);
            String text = step.substring(separator + 1).trim();
            switch (keyword) {
                case "GIVEN" -> preconditions.add(text);
                case "WHEN" -> append(action, text);
                case "THEN" -> append(expected, text);
                case "AND" -> {
                    if (action.isEmpty()) {
                        preconditions.add(text);
                    } else if (expected.isEmpty()) {
                        append(action, text);
                    } else {
                        append(expected, text);
                    }
                }
                default -> {
                    // Synthetic source keeps unknown step words provider-internal rather than leaking them.
                }
            }
        }
        if (action.isEmpty() || expected.isEmpty()) {
            throw new IllegalArgumentException("synthetic scenario requires WHEN and THEN semantics");
        }
        return new ScenarioSemantics(preconditions, action.toString(), expected.toString());
    }

    private void append(StringBuilder target, String value) {
        if (!target.isEmpty()) {
            target.append("; ");
        }
        target.append(value);
    }

    private Provenance provenance(String externalId, SourceLocator source, EvidenceId evidenceId) {
        return new Provenance(
                providerId(),
                Optional.of(SyntheticSpecificationProvider.PROVIDER_VERSION),
                source,
                Optional.of(externalId),
                Optional.empty(),
                evidenceId);
    }

    private Evidence evidence(
            EntityIdentityResolver identities,
            String externalId,
            SourceLocator source,
            String sourceText,
            int sourceLines) {
        EvidenceId evidenceId = new EvidenceId(identities.resolve(
                providerId(), "evidence", "evidence:" + externalId));
        return new Evidence(
                evidenceId,
                source,
                Optional.of(new SourceRange(1, sourceLines)),
                Optional.of(sha256(sourceText)));
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private String slug(String value) {
        return value.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-|-$", "");
    }

    private Map<String, Object> object(Map<String, Object> source, String key) {
        return object(source.get(key), key);
    }

    private Map<String, Object> object(Object value, String name) {
        if (!(value instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException(name + " must be an object");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> typed = (Map<String, Object>) map;
        return typed;
    }

    private List<Object> array(Map<String, Object> source, String key) {
        Object value = source.get(key);
        if (!(value instanceof List<?> list)) {
            throw new IllegalArgumentException(key + " must be an array");
        }
        return List.copyOf(list);
    }

    private List<Object> optionalArray(Map<String, Object> source, String key) {
        Object value = source.get(key);
        if (value == null) {
            return List.of();
        }
        if (!(value instanceof List<?> list)) {
            throw new IllegalArgumentException(key + " must be an array");
        }
        return List.copyOf(list);
    }

    private List<String> strings(Map<String, Object> source, String key) {
        return array(source, key).stream().map(value -> {
            if (!(value instanceof String text)) {
                throw new IllegalArgumentException(key + " must contain strings");
            }
            return text;
        }).toList();
    }

    private String string(Map<String, Object> source, String key) {
        Object value = source.get(key);
        if (!(value instanceof String text) || text.isBlank()) {
            throw new IllegalArgumentException(key + " must be a non-blank string");
        }
        return text.trim();
    }

    private record ScenarioSemantics(List<String> preconditions, String action, String expectedOutcome) {
        private ScenarioSemantics {
            preconditions = List.copyOf(preconditions);
        }
    }

    private record Normalization(
            ProjectSpecification project,
            List<Specification> specifications,
            List<Requirement> requirements,
            List<Scenario> scenarios,
            List<ChangeProposal> changes,
            List<AcceptanceCriterion> acceptanceCriteria,
            List<Evidence> evidence) {
        private Normalization {
            specifications = List.copyOf(specifications);
            requirements = List.copyOf(requirements);
            scenarios = List.copyOf(scenarios);
            changes = List.copyOf(changes);
            acceptanceCriteria = List.copyOf(acceptanceCriteria);
            evidence = List.copyOf(evidence);
        }
    }
}
