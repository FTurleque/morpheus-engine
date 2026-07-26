package com.morpheus.provider.markdown;

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
import com.morpheus.domain.constraint.Constraint;
import com.morpheus.domain.constraint.ConstraintId;
import com.morpheus.domain.decision.DesignDecision;
import com.morpheus.domain.decision.DesignDecisionId;
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
import com.morpheus.domain.task.ImplementationTask;
import com.morpheus.domain.task.TaskId;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Strict normalization of provider-neutral records embedded in Markdown fenced blocks. */
public final class StructuredMarkdownSpecificationContentReader implements SpecificationContentReader {
    private final StructuredMarkdownSpecificationProvider provider;
    private final StructuredMarkdownBlockParser parser;

    public StructuredMarkdownSpecificationContentReader() {
        this(new StructuredMarkdownSpecificationProvider(), new StructuredMarkdownBlockParser());
    }

    StructuredMarkdownSpecificationContentReader(
            StructuredMarkdownSpecificationProvider provider,
            StructuredMarkdownBlockParser parser) {
        this.provider = Objects.requireNonNull(provider, "provider");
        this.parser = Objects.requireNonNull(parser, "parser");
    }

    @Override
    public ProviderId providerId() {
        return StructuredMarkdownSpecificationProvider.ID;
    }

    @Override
    public ProviderReadResult read(ProviderReadRequest request, EntityIdentityResolver identities) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(identities, "identities");
        var probe = provider.probe(request.workspaceRoot());
        if (probe.status() != ProviderProbeStatus.SUPPORTED) {
            return unavailable(request, probe.diagnostics());
        }

        Path sourceFile = request.workspaceRoot().resolve(StructuredMarkdownSpecificationProvider.SOURCE_FILE);
        try {
            String sourceText = Files.readString(sourceFile, StandardCharsets.UTF_8);
            Normalization normalized = normalize(request, identities, sourceText);
            List<ReadCategoryReport> reports = request.requestedCategories().stream()
                    .sorted()
                    .map(category -> report(category, normalized))
                    .toList();
            NormalizedProjectContent content = new NormalizedProjectContent(
                    normalized.project,
                    normalized.specifications,
                    normalized.requirements,
                    normalized.scenarios,
                    normalized.changes,
                    List.of(),
                    normalized.constraints,
                    normalized.decisions,
                    normalized.tasks,
                    normalized.acceptanceCriteria,
                    normalized.evidence,
                    List.of());
            return new ProviderReadResult(providerId(), Optional.of(content), reports, List.of());
        } catch (IOException | IllegalArgumentException exception) {
            Diagnostic diagnostic = Diagnostic.error(
                    DiagnosticCode.INVALID_SOURCE,
                    "Structured Markdown normalization failed: " + exception.getMessage(),
                    Map.of("provider", providerId().value(), "source", StructuredMarkdownSpecificationProvider.SOURCE_FILE));
            return new ProviderReadResult(
                    providerId(),
                    Optional.empty(),
                    request.requestedCategories().stream()
                            .sorted()
                            .map(category -> new ReadCategoryReport(
                                    category,
                                    ReadCategoryStatus.FAILED,
                                    0,
                                    List.of(DiagnosticCode.INVALID_SOURCE),
                                    Optional.of(exception.getMessage() == null ? "invalid source" : exception.getMessage())))
                            .toList(),
                    List.of(diagnostic));
        }
    }

    private Normalization normalize(
            ProviderReadRequest request,
            EntityIdentityResolver identities,
            String sourceText) throws IOException {
        List<StructuredMarkdownBlockParser.Block> blocks = parser.parse(sourceText);
        SourceLocator source = SourceLocator.file(StructuredMarkdownSpecificationProvider.SOURCE_FILE);
        Normalization result = new Normalization();
        String displayName = request.workspaceRoot().getFileName() == null
                ? request.workspaceRoot().toString()
                : request.workspaceRoot().getFileName().toString();
        result.project = new ProjectSpecification(request.projectId(), displayName, source);

        for (StructuredMarkdownBlockParser.Block block : blocks) {
            String externalId = block.type() + ":" + block.required("key");
            Evidence evidence = blockEvidence(identities, externalId, source, block);
            result.evidence.add(evidence);
            result.blocks.add(new ParsedBlock(block, externalId, evidence.id()));
        }

        for (ParsedBlock parsed : result.blocks) {
            if (parsed.block.type().equals("specification")) {
                addSpecification(request, identities, parsed, result);
            }
        }
        if (result.specifications.isEmpty()) {
            throw new IllegalArgumentException("at least one specification block is required");
        }
        for (ParsedBlock parsed : result.blocks) {
            if (parsed.block.type().equals("requirement")) {
                addRequirement(identities, parsed, result);
            }
        }
        for (ParsedBlock parsed : result.blocks) {
            switch (parsed.block.type()) {
                case "specification", "requirement" -> { }
                case "scenario" -> addScenario(identities, parsed, result);
                case "change" -> addChange(request, identities, parsed, result);
                default -> { }
            }
        }
        for (ParsedBlock parsed : result.blocks) {
            switch (parsed.block.type()) {
                case "constraint" -> addConstraint(identities, parsed, result);
                case "decision" -> addDecision(identities, parsed, result);
                case "task" -> addTask(identities, parsed, result);
                case "acceptance" -> addAcceptance(request.workspaceRoot(), identities, parsed, result);
                case "specification", "requirement", "scenario", "change" -> { }
                default -> throw new IllegalArgumentException(
                        "unsupported morpheus block type '" + parsed.block.type() + "' at line " + parsed.block.startLine());
            }
        }
        return result;
    }

    private void addSpecification(
            ProviderReadRequest request,
            EntityIdentityResolver identities,
            ParsedBlock parsed,
            Normalization result) {
        String key = parsed.block.required("key");
        rejectDuplicate(result.specificationIdsByKey, key, "specification");
        SpecificationId id = new SpecificationId(identities.resolve(providerId(), "specification", parsed.externalId));
        result.specificationIdsByKey.put(key, id);
        result.specifications.add(new Specification(
                id,
                request.projectId(),
                key,
                parsed.block.required("title"),
                optional(parsed.block.values().get("description")),
                provenance(parsed, source())));
    }

    private void addRequirement(EntityIdentityResolver identities, ParsedBlock parsed, Normalization result) {
        String key = parsed.block.required("key");
        rejectDuplicate(result.requirementIdsByKey, key, "requirement");
        SpecificationId specificationId = required(result.specificationIdsByKey,
                parsed.block.required("specification"), "specification");
        RequirementId id = new RequirementId(identities.resolve(providerId(), "requirement", parsed.externalId));
        result.requirementIdsByKey.put(key, id);
        result.requirements.add(new Requirement(
                id,
                specificationId,
                Optional.of(key),
                parsed.block.required("title"),
                parsed.block.required("statement"),
                provenance(parsed, source())));
    }

    private void addScenario(EntityIdentityResolver identities, ParsedBlock parsed, Normalization result) {
        RequirementId requirementId = required(
                result.requirementIdsByKey, parsed.block.required("requirement"), "requirement");
        ScenarioId id = new ScenarioId(identities.resolve(providerId(), "scenario", parsed.externalId));
        result.scenarios.add(new Scenario(
                id,
                Optional.of(requirementId),
                parsed.block.required("title"),
                parsed.block.list("given"),
                parsed.block.required("when"),
                parsed.block.required("then"),
                provenance(parsed, source())));
    }

    private void addChange(
            ProviderReadRequest request,
            EntityIdentityResolver identities,
            ParsedBlock parsed,
            Normalization result) {
        String key = parsed.block.required("key");
        rejectDuplicate(result.changeIdsByKey, key, "change");
        ChangeId id = new ChangeId(identities.resolve(providerId(), "change", parsed.externalId));
        result.changeIdsByKey.put(key, id);
        result.changes.add(new ChangeProposal(
                id,
                request.projectId(),
                Optional.of(key),
                parsed.block.required("title"),
                parsed.block.required("intent"),
                parsed.block.list("scope"),
                parsed.block.list("out_of_scope"),
                parsed.block.list("risks"),
                provenance(parsed, source())));
    }

    private void addConstraint(EntityIdentityResolver identities, ParsedBlock parsed, Normalization result) {
        ChangeId changeId = required(result.changeIdsByKey, parsed.block.required("change"), "change");
        ConstraintId id = new ConstraintId(identities.resolve(providerId(), "constraint", parsed.externalId));
        result.constraints.add(new Constraint(
                id,
                changeId,
                parsed.block.required("statement"),
                provenance(parsed, source())));
    }

    private void addDecision(EntityIdentityResolver identities, ParsedBlock parsed, Normalization result) {
        ChangeId changeId = required(result.changeIdsByKey, parsed.block.required("change"), "change");
        DesignDecisionId id = new DesignDecisionId(identities.resolve(providerId(), "design-decision", parsed.externalId));
        result.decisions.add(new DesignDecision(
                id,
                changeId,
                parsed.block.required("title"),
                parsed.block.required("decision"),
                provenance(parsed, source())));
    }

    private void addTask(EntityIdentityResolver identities, ParsedBlock parsed, Normalization result) {
        ChangeId changeId = required(result.changeIdsByKey, parsed.block.required("change"), "change");
        String key = parsed.block.required("key");
        TaskId id = new TaskId(identities.resolve(providerId(), "task", parsed.externalId));
        result.tasks.add(new ImplementationTask(
                id,
                changeId,
                Optional.of(key),
                parsed.block.required("title"),
                Boolean.parseBoolean(parsed.block.optional("completed", "false")),
                provenance(parsed, source())));
    }

    private void addAcceptance(
            Path workspaceRoot,
            EntityIdentityResolver identities,
            ParsedBlock parsed,
            Normalization result) throws IOException {
        String ownerType = parsed.block.required("owner_type").toLowerCase(Locale.ROOT);
        String ownerKey = parsed.block.required("owner_key");
        Optional<RequirementId> requirementId = Optional.empty();
        Optional<ChangeId> changeId = Optional.empty();
        if (ownerType.equals("requirement")) {
            requirementId = Optional.of(required(result.requirementIdsByKey, ownerKey, "requirement"));
        } else if (ownerType.equals("change")) {
            changeId = Optional.of(required(result.changeIdsByKey, ownerKey, "change"));
        } else {
            throw new IllegalArgumentException("acceptance owner_type must be requirement or change");
        }

        List<EvidenceId> verificationEvidence = new ArrayList<>();
        for (String relativePath : parsed.block.list("verification_evidence")) {
            Evidence evidence = fileEvidence(workspaceRoot, identities, parsed.externalId, relativePath);
            result.evidence.add(evidence);
            verificationEvidence.add(evidence.id());
        }
        VerificationStatus status;
        try {
            status = VerificationStatus.valueOf(parsed.block.optional("verification_status", "UNKNOWN")
                    .toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("unsupported verification_status in acceptance block", exception);
        }
        AcceptanceCriterionId id = new AcceptanceCriterionId(
                identities.resolve(providerId(), "acceptance-criterion", parsed.externalId));
        result.acceptanceCriteria.add(new AcceptanceCriterion(
                id,
                requirementId,
                changeId,
                parsed.block.required("title"),
                parsed.block.required("condition"),
                status,
                verificationEvidence,
                provenance(parsed, source())));
    }

    private Evidence blockEvidence(
            EntityIdentityResolver identities,
            String externalId,
            SourceLocator source,
            StructuredMarkdownBlockParser.Block block) {
        EvidenceId id = new EvidenceId(identities.resolve(providerId(), "evidence", "evidence:" + externalId));
        return new Evidence(
                id,
                source,
                Optional.of(new SourceRange(block.startLine(), block.endLine())),
                Optional.of(sha256(block.raw())));
    }

    private Evidence fileEvidence(
            Path workspaceRoot,
            EntityIdentityResolver identities,
            String ownerExternalId,
            String relativePath) throws IOException {
        Path root = workspaceRoot.toAbsolutePath().normalize();
        Path file = root.resolve(relativePath).normalize();
        if (!file.startsWith(root)) {
            throw new IllegalArgumentException("verification evidence escapes workspace: " + relativePath);
        }
        if (!Files.isRegularFile(file)) {
            throw new IllegalArgumentException("verification evidence file does not exist: " + relativePath);
        }
        String text = Files.readString(file, StandardCharsets.UTF_8);
        int lines = Math.max(1, text.lines().toList().size());
        EvidenceId id = new EvidenceId(identities.resolve(
                providerId(), "evidence", "evidence:" + ownerExternalId + "/" + relativePath));
        return new Evidence(
                id,
                SourceLocator.file(relativePath),
                Optional.of(new SourceRange(1, lines)),
                Optional.of(sha256(text)));
    }

    private Provenance provenance(ParsedBlock parsed, SourceLocator source) {
        return new Provenance(
                providerId(),
                Optional.of(StructuredMarkdownSpecificationProvider.PROVIDER_VERSION),
                source,
                Optional.of(parsed.externalId),
                Optional.empty(),
                parsed.evidenceId);
    }

    private SourceLocator source() {
        return SourceLocator.file(StructuredMarkdownSpecificationProvider.SOURCE_FILE);
    }

    private ReadCategoryReport report(ReadCategory category, Normalization value) {
        int count = switch (category) {
            case CURRENT_SPECIFICATIONS -> value.specifications.size();
            case REQUIREMENTS -> value.requirements.size();
            case SCENARIOS -> value.scenarios.size();
            case CHANGES -> value.changes.size();
            case CONSTRAINTS -> value.constraints.size();
            case DESIGN_DECISIONS -> value.decisions.size();
            case IMPLEMENTATION_TASKS -> value.tasks.size();
            case ACCEPTANCE_CRITERIA -> value.acceptanceCriteria.size();
            case REQUIREMENT_DELTAS, EXTERNAL_REFERENCES, ARCHIVES -> -1;
        };
        if (count < 0) {
            return new ReadCategoryReport(
                    category,
                    ReadCategoryStatus.UNSUPPORTED,
                    0,
                    List.of(DiagnosticCode.OPTIONAL_CAPABILITY_UNAVAILABLE),
                    Optional.of("category is not represented by morpheus-markdown-v1"));
        }
        return ReadCategoryReport.of(category, count == 0 ? ReadCategoryStatus.ABSENT : ReadCategoryStatus.READ, count);
    }

    private ProviderReadResult unavailable(ProviderReadRequest request, List<Diagnostic> diagnostics) {
        List<Diagnostic> effective = diagnostics.isEmpty()
                ? List.of(Diagnostic.error(
                        DiagnosticCode.UNSUPPORTED_SOURCE,
                        "Structured Markdown provider does not recognize this workspace",
                        Map.of("provider", providerId().value())))
                : diagnostics;
        return new ProviderReadResult(
                providerId(),
                Optional.empty(),
                request.requestedCategories().stream()
                        .sorted()
                        .map(category -> new ReadCategoryReport(
                                category,
                                ReadCategoryStatus.FAILED,
                                0,
                                List.of(effective.getFirst().code()),
                                Optional.of("provider source is unavailable")))
                        .toList(),
                effective);
    }

    private static <T> T required(Map<String, T> values, String key, String type) {
        T result = values.get(key);
        if (result == null) {
            throw new IllegalArgumentException("unknown " + type + " key: " + key);
        }
        return result;
    }

    private static void rejectDuplicate(Map<String, ?> values, String key, String type) {
        if (values.containsKey(key)) {
            throw new IllegalArgumentException("duplicate " + type + " key: " + key);
        }
    }

    private static Optional<String> optional(String value) {
        return Optional.ofNullable(value).map(String::trim).filter(item -> !item.isEmpty());
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private record ParsedBlock(
            StructuredMarkdownBlockParser.Block block,
            String externalId,
            EvidenceId evidenceId) {
    }

    private static final class Normalization {
        private ProjectSpecification project;
        private final List<ParsedBlock> blocks = new ArrayList<>();
        private final Map<String, SpecificationId> specificationIdsByKey = new LinkedHashMap<>();
        private final Map<String, RequirementId> requirementIdsByKey = new LinkedHashMap<>();
        private final Map<String, ChangeId> changeIdsByKey = new LinkedHashMap<>();
        private final List<Specification> specifications = new ArrayList<>();
        private final List<Requirement> requirements = new ArrayList<>();
        private final List<Scenario> scenarios = new ArrayList<>();
        private final List<ChangeProposal> changes = new ArrayList<>();
        private final List<Constraint> constraints = new ArrayList<>();
        private final List<DesignDecision> decisions = new ArrayList<>();
        private final List<ImplementationTask> tasks = new ArrayList<>();
        private final List<AcceptanceCriterion> acceptanceCriteria = new ArrayList<>();
        private final List<Evidence> evidence = new ArrayList<>();
    }
}
