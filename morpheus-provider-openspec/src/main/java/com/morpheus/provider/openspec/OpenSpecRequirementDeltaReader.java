package com.morpheus.provider.openspec;

import com.morpheus.application.identity.EntityIdentityResolver;
import com.morpheus.application.read.ProviderIngestionBudget;
import com.morpheus.domain.change.ChangeId;
import com.morpheus.domain.diagnostic.Diagnostic;
import com.morpheus.domain.evidence.Evidence;
import com.morpheus.domain.evidence.EvidenceId;
import com.morpheus.domain.evidence.SourceRange;
import com.morpheus.domain.provenance.Provenance;
import com.morpheus.domain.provider.ProviderCapability;
import com.morpheus.domain.provider.ProviderProbeStatus;
import com.morpheus.domain.requirement.RequirementDelta;
import com.morpheus.domain.requirement.RequirementDeltaId;
import com.morpheus.domain.requirement.RequirementDeltaKind;
import com.morpheus.domain.requirement.RequirementId;
import com.morpheus.domain.scenario.Scenario;
import com.morpheus.domain.scenario.ScenarioId;
import com.morpheus.domain.source.SourceLocator;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Normalizes OpenSpec ADDED/MODIFIED/REMOVED requirement deltas without applying M3 temporal projection. */
public final class OpenSpecRequirementDeltaReader {
    private static final Pattern DELTA_SECTION = Pattern.compile(
            "^##\\s+(ADDED|MODIFIED|REMOVED)\\s+Requirements\\s*$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern REQUIREMENT_HEADING = Pattern.compile("^###\\s+Requirement:\\s*(.+?)\\s*$");
    private static final Pattern SCENARIO_HEADING = Pattern.compile("^####\\s+Scenario:\\s*(.+?)\\s*$");
    private static final Pattern SCENARIO_STEP = Pattern.compile(
            "^-\\s+\\*\\*(GIVEN|AND|WHEN|THEN)\\*\\*\\s+(.+?)\\s*$",
            Pattern.CASE_INSENSITIVE);

    private final OpenSpecSpecificationProvider provider;

    public OpenSpecRequirementDeltaReader() {
        this(new OpenSpecSpecificationProvider());
    }

    OpenSpecRequirementDeltaReader(OpenSpecSpecificationProvider provider) {
        this.provider = Objects.requireNonNull(provider, "provider");
    }

    public ReadResult read(Path workspaceRoot, EntityIdentityResolver identityResolver) {
        Path root = Objects.requireNonNull(workspaceRoot, "workspaceRoot").toAbsolutePath().normalize();
        ProviderIngestionBudget.Session budget = OpenSpecIngestionBudgets.open(root);
        return read(root, identityResolver, budget);
    }

    ReadResult read(
            Path workspaceRoot,
            EntityIdentityResolver identityResolver,
            ProviderIngestionBudget.Session budget) {
        Path root = Objects.requireNonNull(workspaceRoot, "workspaceRoot").toAbsolutePath().normalize();
        Objects.requireNonNull(identityResolver, "identityResolver");
        Objects.requireNonNull(budget, "budget");

        var probe = provider.probe(root, budget);
        if (probe.status() != ProviderProbeStatus.SUPPORTED) {
            throw new IllegalArgumentException("OpenSpec workspace is not supported: " + root);
        }
        if (!probe.capabilities().contains(ProviderCapability.READ_CHANGES)) {
            throw new IllegalArgumentException("OpenSpec workspace has no readable changes: " + root);
        }

        List<RequirementDelta> deltas = new ArrayList<>();
        List<Evidence> evidence = new ArrayList<>();

        for (Path changeRoot : listChangeRoots(root.resolve("openspec/changes"), budget)) {
            String changeKey = changeRoot.getFileName().toString();
            ChangeId changeId = new ChangeId(identityResolver.resolve(
                    OpenSpecSpecificationProvider.ID,
                    "change",
                    "change:" + changeKey));
            Path specsRoot = changeRoot.resolve("specs");
            for (Path specificationFile : listSpecificationFiles(specsRoot, budget)) {
                normalizeDeltaFile(
                        root,
                        changeKey,
                        changeId,
                        specsRoot,
                        specificationFile,
                        identityResolver,
                        deltas,
                        evidence,
                        budget);
            }
        }

        long scenarios = deltas.stream().mapToLong(delta -> delta.scenarios().size()).sum();
        budget.addBlocks(deltas.size() + scenarios, "openspec/requirement-deltas");
        budget.addEntities(deltas.size() + scenarios + evidence.size(), "openspec/requirement-deltas");

        return new ReadResult(deltas, evidence, probe.diagnostics());
    }

    private void normalizeDeltaFile(
            Path workspaceRoot,
            String changeKey,
            ChangeId changeId,
            Path specsRoot,
            Path specificationFile,
            EntityIdentityResolver identities,
            List<RequirementDelta> deltas,
            List<Evidence> evidence,
            ProviderIngestionBudget.Session budget) {
        List<String> lines = readAllLines(workspaceRoot, specificationFile, budget);
        String specificationKey = specificationKey(specsRoot, specificationFile);
        SourceLocator source = SourceLocator.file(workspaceRoot.relativize(specificationFile).toString());
        RequirementDeltaKind currentKind = null;

        for (int index = 0; index < lines.size(); index++) {
            Matcher section = DELTA_SECTION.matcher(lines.get(index));
            if (section.matches()) {
                currentKind = RequirementDeltaKind.valueOf(section.group(1).toUpperCase(Locale.ROOT));
                continue;
            }

            Matcher requirementHeading = REQUIREMENT_HEADING.matcher(lines.get(index));
            if (currentKind == null || !requirementHeading.matches()) {
                continue;
            }

            int endExclusive = requirementEnd(lines, index + 1);
            normalizeRequirementDelta(
                    lines,
                    index,
                    endExclusive,
                    changeKey,
                    changeId,
                    currentKind,
                    specificationKey,
                    source,
                    identities,
                    deltas,
                    evidence,
                    budget);
            index = endExclusive - 1;
        }
    }

    private void normalizeRequirementDelta(
            List<String> lines,
            int start,
            int endExclusive,
            String changeKey,
            ChangeId changeId,
            RequirementDeltaKind kind,
            String specificationKey,
            SourceLocator source,
            EntityIdentityResolver identities,
            List<RequirementDelta> deltas,
            List<Evidence> evidence,
            ProviderIngestionBudget.Session budget) {
        Matcher heading = REQUIREMENT_HEADING.matcher(lines.get(start));
        if (!heading.matches()) {
            throw new IllegalStateException("requirement delta heading mismatch");
        }

        String title = heading.group(1).trim();
        String requirementKey = specificationKey + "/" + slug(title);
        String requirementExternalId = "requirement:" + requirementKey;
        RequirementId requirementId = new RequirementId(identities.resolve(
                OpenSpecSpecificationProvider.ID,
                "requirement",
                requirementExternalId));

        String kindToken = kind.name().toLowerCase(Locale.ROOT);
        String deltaExternalId = "requirement-delta:" + changeKey + ":" + kindToken + ":" + requirementKey;
        RequirementDeltaId deltaId = new RequirementDeltaId(identities.resolve(
                OpenSpecSpecificationProvider.ID,
                "requirement-delta",
                deltaExternalId));

        int firstScenario = endExclusive;
        for (int index = start + 1; index < endExclusive; index++) {
            if (SCENARIO_HEADING.matcher(lines.get(index)).matches()) {
                firstScenario = index;
                break;
            }
        }

        String statementText = joinContent(lines, start + 1, firstScenario);
        Optional<String> statement = statementText.isBlank() ? Optional.empty() : Optional.of(statementText);
        int deltaEvidenceEnd = Math.max(start + 1, firstScenario);
        Evidence deltaEvidence = evidence(
                identities,
                deltaExternalId,
                source,
                lines,
                start + 1,
                deltaEvidenceEnd,
                budget);
        evidence.add(deltaEvidence);

        List<Scenario> deltaScenarios = new ArrayList<>();
        List<Integer> scenarioHeadings = new ArrayList<>();
        for (int index = firstScenario; index < endExclusive; index++) {
            if (SCENARIO_HEADING.matcher(lines.get(index)).matches()) {
                scenarioHeadings.add(index);
            }
        }

        for (int index = 0; index < scenarioHeadings.size(); index++) {
            int scenarioStart = scenarioHeadings.get(index);
            int scenarioEnd = index + 1 < scenarioHeadings.size()
                    ? scenarioHeadings.get(index + 1)
                    : endExclusive;
            deltaScenarios.add(normalizeScenario(
                    lines,
                    scenarioStart,
                    scenarioEnd,
                    changeKey,
                    kind,
                    requirementKey,
                    requirementId,
                    source,
                    identities,
                    evidence,
                    budget));
        }

        deltas.add(new RequirementDelta(
                deltaId,
                changeId,
                kind,
                specificationKey,
                requirementId,
                Optional.of(requirementKey),
                title,
                statement,
                deltaScenarios,
                provenance(deltaExternalId, source, deltaEvidence.id())));
    }

    private Scenario normalizeScenario(
            List<String> lines,
            int start,
            int endExclusive,
            String changeKey,
            RequirementDeltaKind kind,
            String requirementKey,
            RequirementId requirementId,
            SourceLocator source,
            EntityIdentityResolver identities,
            List<Evidence> evidence,
            ProviderIngestionBudget.Session budget) {
        Matcher heading = SCENARIO_HEADING.matcher(lines.get(start));
        if (!heading.matches()) {
            throw new IllegalStateException("delta scenario heading mismatch");
        }

        String title = heading.group(1).trim();
        String externalId = "scenario-delta:"
                + changeKey + ":"
                + kind.name().toLowerCase(Locale.ROOT) + ":"
                + requirementKey + "/" + slug(title);
        List<String> preconditions = new ArrayList<>();
        StringBuilder action = new StringBuilder();
        StringBuilder expectedOutcome = new StringBuilder();

        for (int index = start + 1; index < endExclusive; index++) {
            Matcher step = SCENARIO_STEP.matcher(lines.get(index).trim());
            if (!step.matches()) {
                continue;
            }

            String keyword = step.group(1).toUpperCase(Locale.ROOT);
            String text = step.group(2).trim();
            switch (keyword) {
                case "GIVEN" -> preconditions.add(text);
                case "AND" -> {
                    if (action.isEmpty()) {
                        preconditions.add(text);
                    } else if (expectedOutcome.isEmpty()) {
                        append(action, text);
                    } else {
                        append(expectedOutcome, text);
                    }
                }
                case "WHEN" -> append(action, text);
                case "THEN" -> append(expectedOutcome, text);
                default -> throw new IllegalStateException("Unexpected scenario keyword: " + keyword);
            }
        }

        if (action.isEmpty() || expectedOutcome.isEmpty()) {
            throw new IllegalArgumentException("OpenSpec delta scenario is missing WHEN/THEN semantics: " + externalId);
        }

        Evidence scenarioEvidence = evidence(
                identities,
                externalId,
                source,
                lines,
                start + 1,
                endExclusive,
                budget);
        evidence.add(scenarioEvidence);

        ScenarioId scenarioId = new ScenarioId(identities.resolve(
                OpenSpecSpecificationProvider.ID,
                "scenario-delta",
                externalId));
        return new Scenario(
                scenarioId,
                Optional.of(requirementId),
                title,
                preconditions,
                action.toString(),
                expectedOutcome.toString(),
                provenance(externalId, source, scenarioEvidence.id()));
    }

    private int requirementEnd(List<String> lines, int from) {
        for (int index = from; index < lines.size(); index++) {
            if (REQUIREMENT_HEADING.matcher(lines.get(index)).matches() || lines.get(index).startsWith("## ")) {
                return index;
            }
        }
        return lines.size();
    }

    private List<Path> listChangeRoots(
            Path changesRoot,
            ProviderIngestionBudget.Session budget) {
        if (!Files.isDirectory(changesRoot)) {
            return List.of();
        }
        try (var paths = Files.list(changesRoot)) {
            List<Path> roots = paths
                    .filter(Files::isDirectory)
                    .filter(path -> !path.getFileName().toString().equals("archive"))
                    .limit(budget.remainingFiles() + 1)
                    .toList();
            budget.requireAdditionalFiles(roots.size(), "openspec/requirement-deltas");
            return roots.stream().sorted().toList();
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot enumerate OpenSpec changes", exception);
        }
    }

    private List<Path> listSpecificationFiles(
            Path specsRoot,
            ProviderIngestionBudget.Session budget) {
        if (!Files.isDirectory(specsRoot)) {
            return List.of();
        }
        try (var paths = Files.walk(specsRoot)) {
            List<Path> files = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().equals("spec.md"))
                    .limit(budget.remainingFiles() + 1)
                    .toList();
            budget.requireAdditionalFiles(files.size(), "openspec/requirement-deltas");
            return files.stream().sorted().toList();
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot enumerate OpenSpec requirement delta specifications", exception);
        }
    }

    private String specificationKey(Path specsRoot, Path specificationFile) {
        Path relativeParent = specsRoot.relativize(specificationFile.getParent());
        String key = relativeParent.toString().replace('\\', '/');
        if (key.isBlank()) {
            throw new IllegalArgumentException("OpenSpec delta specification must live under a specification key directory");
        }
        return key;
    }

    private Provenance provenance(String externalId, SourceLocator source, EvidenceId evidenceId) {
        return new Provenance(
                OpenSpecSpecificationProvider.ID,
                Optional.of(OpenSpecSpecificationProvider.PROVIDER_VERSION),
                source,
                Optional.of(externalId),
                Optional.empty(),
                evidenceId);
    }

    private Evidence evidence(
            EntityIdentityResolver identities,
            String externalId,
            SourceLocator source,
            List<String> lines,
            int startLine,
            int endLine,
            ProviderIngestionBudget.Session budget) {
        int normalizedStart = Math.max(1, Math.min(startLine, lines.size()));
        int normalizedEnd = Math.max(normalizedStart, Math.min(endLine, lines.size()));
        String excerpt = String.join("\n", lines.subList(normalizedStart - 1, normalizedEnd));
        budget.addEvidenceFragment(excerpt, source.value());
        EvidenceId evidenceId = new EvidenceId(identities.resolve(
                OpenSpecSpecificationProvider.ID,
                "evidence",
                "evidence:" + externalId));
        return new Evidence(
                evidenceId,
                source,
                Optional.of(new SourceRange(normalizedStart, normalizedEnd)),
                Optional.of(sha256(excerpt)));
    }

    private List<String> readAllLines(
            Path workspaceRoot,
            Path source,
            ProviderIngestionBudget.Session budget) {
        try {
            return budget.readDocument(workspaceRoot.relativize(source))
                    .lines()
                    .toList();
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read OpenSpec source " + source, exception);
        }
    }

    private String joinContent(List<String> lines, int startInclusive, int endExclusive) {
        List<String> content = new ArrayList<>();
        for (int index = startInclusive; index < endExclusive; index++) {
            String line = lines.get(index).trim();
            if (!line.isEmpty() && !line.startsWith("#")) {
                content.add(line);
            }
        }
        return String.join(" ", content).trim();
    }

    private String slug(String value) {
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("Cannot derive a stable key from: " + value);
        }
        return normalized;
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 must be available", exception);
        }
    }

    private void append(StringBuilder target, String value) {
        if (!target.isEmpty()) {
            target.append(" AND ");
        }
        target.append(value);
    }

    public record ReadResult(
            List<RequirementDelta> requirementDeltas,
            List<Evidence> evidence,
            List<Diagnostic> diagnostics) {
        public ReadResult {
            requirementDeltas = List.copyOf(Objects.requireNonNull(requirementDeltas, "requirementDeltas"));
            evidence = List.copyOf(Objects.requireNonNull(evidence, "evidence"));
            diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
        }
    }
}
