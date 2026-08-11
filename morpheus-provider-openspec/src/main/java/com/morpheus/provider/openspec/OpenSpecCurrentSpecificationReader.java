package com.morpheus.provider.openspec;

import com.morpheus.application.identity.EntityIdentityResolver;
import com.morpheus.application.files.SafeWorkspaceFileResolver;
import com.morpheus.application.ingestion.NormalizedProjectContent;
import com.morpheus.domain.evidence.Evidence;
import com.morpheus.domain.evidence.EvidenceId;
import com.morpheus.domain.evidence.SourceRange;
import com.morpheus.domain.project.ProjectSpecification;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.provenance.Provenance;
import com.morpheus.domain.provider.ProviderCapability;
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
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * First M2 OpenSpec anti-corruption reader.
 *
 * <p>It reads only current specification documents and maps their structural content to MORPHEUS
 * domain types. Temporal projection remains a M3 responsibility.
 */
public final class OpenSpecCurrentSpecificationReader {
    private static final Pattern REQUIREMENT_HEADING = Pattern.compile("^###\\s+Requirement:\\s*(.+?)\\s*$");
    private static final Pattern SCENARIO_HEADING = Pattern.compile("^####\\s+Scenario:\\s*(.+?)\\s*$");
    private static final Pattern SCENARIO_STEP = Pattern.compile(
            "^-\\s+\\*\\*(GIVEN|AND|WHEN|THEN)\\*\\*\\s+(.+?)\\s*$",
            Pattern.CASE_INSENSITIVE);

    private final OpenSpecSpecificationProvider provider;

    public OpenSpecCurrentSpecificationReader() {
        this(new OpenSpecSpecificationProvider());
    }

    OpenSpecCurrentSpecificationReader(OpenSpecSpecificationProvider provider) {
        this.provider = Objects.requireNonNull(provider, "provider");
    }

    public NormalizedProjectContent read(
            Path workspaceRoot,
            ProjectSpecificationId projectId,
            EntityIdentityResolver identityResolver) {
        Path root = Objects.requireNonNull(workspaceRoot, "workspaceRoot").toAbsolutePath().normalize();
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(identityResolver, "identityResolver");

        var probe = provider.probe(root);
        if (probe.status() != ProviderProbeStatus.SUPPORTED) {
            throw new IllegalArgumentException("OpenSpec workspace is not supported: " + root);
        }
        if (!probe.capabilities().contains(ProviderCapability.READ_CURRENT_SPECIFICATIONS)) {
            throw new IllegalArgumentException("OpenSpec workspace has no readable current specifications: " + root);
        }

        Path specsRoot = root.resolve("openspec/specs");
        List<Path> specificationFiles = listSpecificationFiles(specsRoot);
        List<Specification> specifications = new ArrayList<>();
        List<Requirement> requirements = new ArrayList<>();
        List<Scenario> scenarios = new ArrayList<>();
        List<Evidence> evidence = new ArrayList<>();

        for (Path specificationFile : specificationFiles) {
            normalizeSpecification(
                    root,
                    specsRoot,
                    specificationFile,
                    projectId,
                    identityResolver,
                    specifications,
                    requirements,
                    scenarios,
                    evidence);
        }

        String displayName = root.getFileName() == null ? root.toString() : root.getFileName().toString();
        ProjectSpecification project = new ProjectSpecification(
                projectId,
                displayName,
                SourceLocator.file(root.toString()));

        return new NormalizedProjectContent(
                project,
                specifications,
                requirements,
                scenarios,
                evidence,
                probe.diagnostics());
    }

    private void normalizeSpecification(
            Path workspaceRoot,
            Path specsRoot,
            Path specificationFile,
            ProjectSpecificationId projectId,
            EntityIdentityResolver identityResolver,
            List<Specification> specifications,
            List<Requirement> requirements,
            List<Scenario> scenarios,
            List<Evidence> evidence) {
        List<String> lines = readAllLines(workspaceRoot, specificationFile);
        if (lines.isEmpty()) {
            throw new IllegalArgumentException("OpenSpec specification is empty: " + specificationFile);
        }

        String specificationKey = specificationKey(specsRoot, specificationFile);
        String specificationExternalId = "specification:" + specificationKey;
        SourceLocator source = SourceLocator.file(workspaceRoot.relativize(specificationFile).toString());
        String title = firstHeading(lines)
                .orElseThrow(() -> new IllegalArgumentException("OpenSpec specification has no title: " + specificationFile));
        Optional<String> purpose = sectionBody(lines, "## Purpose");

        Evidence specificationEvidence = evidence(
                identityResolver,
                specificationExternalId,
                source,
                lines,
                1,
                lines.size());
        evidence.add(specificationEvidence);

        SpecificationId specificationId = new SpecificationId(identityResolver.resolve(
                OpenSpecSpecificationProvider.ID,
                "specification",
                specificationExternalId));
        specifications.add(new Specification(
                specificationId,
                projectId,
                specificationKey,
                title,
                purpose,
                provenance(specificationExternalId, source, specificationEvidence.id())));

        List<Integer> requirementHeadings = headingIndexes(lines, REQUIREMENT_HEADING);
        for (int index = 0; index < requirementHeadings.size(); index++) {
            int start = requirementHeadings.get(index);
            int endExclusive = index + 1 < requirementHeadings.size()
                    ? requirementHeadings.get(index + 1)
                    : lines.size();
            normalizeRequirement(
                    lines,
                    start,
                    endExclusive,
                    specificationKey,
                    specificationId,
                    source,
                    identityResolver,
                    requirements,
                    scenarios,
                    evidence);
        }
    }

    private void normalizeRequirement(
            List<String> lines,
            int start,
            int endExclusive,
            String specificationKey,
            SpecificationId specificationId,
            SourceLocator source,
            EntityIdentityResolver identityResolver,
            List<Requirement> requirements,
            List<Scenario> scenarios,
            List<Evidence> evidence) {
        Matcher requirementMatcher = REQUIREMENT_HEADING.matcher(lines.get(start));
        if (!requirementMatcher.matches()) {
            throw new IllegalStateException("requirement heading index does not point to a requirement");
        }

        String title = requirementMatcher.group(1).trim();
        String requirementKey = specificationKey + "/" + slug(title);
        String requirementExternalId = "requirement:" + requirementKey;

        int firstScenario = endExclusive;
        for (int index = start + 1; index < endExclusive; index++) {
            if (SCENARIO_HEADING.matcher(lines.get(index)).matches()) {
                firstScenario = index;
                break;
            }
        }

        String statement = joinContent(lines, start + 1, firstScenario);
        if (statement.isBlank()) {
            throw new IllegalArgumentException("OpenSpec requirement has no statement: " + requirementKey);
        }

        Evidence requirementEvidence = evidence(
                identityResolver,
                requirementExternalId,
                source,
                lines,
                start + 1,
                endExclusive);
        evidence.add(requirementEvidence);

        RequirementId requirementId = new RequirementId(identityResolver.resolve(
                OpenSpecSpecificationProvider.ID,
                "requirement",
                requirementExternalId));
        requirements.add(new Requirement(
                requirementId,
                specificationId,
                Optional.of(requirementKey),
                title,
                statement,
                provenance(requirementExternalId, source, requirementEvidence.id())));

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
            normalizeScenario(
                    lines,
                    scenarioStart,
                    scenarioEnd,
                    requirementKey,
                    requirementId,
                    source,
                    identityResolver,
                    scenarios,
                    evidence);
        }
    }

    private void normalizeScenario(
            List<String> lines,
            int start,
            int endExclusive,
            String requirementKey,
            RequirementId requirementId,
            SourceLocator source,
            EntityIdentityResolver identityResolver,
            List<Scenario> scenarios,
            List<Evidence> evidence) {
        Matcher heading = SCENARIO_HEADING.matcher(lines.get(start));
        if (!heading.matches()) {
            throw new IllegalStateException("scenario heading index does not point to a scenario");
        }

        String title = heading.group(1).trim();
        String scenarioExternalId = "scenario:" + requirementKey + "/" + slug(title);
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
            throw new IllegalArgumentException("OpenSpec scenario is missing WHEN/THEN semantics: " + scenarioExternalId);
        }

        Evidence scenarioEvidence = evidence(
                identityResolver,
                scenarioExternalId,
                source,
                lines,
                start + 1,
                endExclusive);
        evidence.add(scenarioEvidence);

        ScenarioId scenarioId = new ScenarioId(identityResolver.resolve(
                OpenSpecSpecificationProvider.ID,
                "scenario",
                scenarioExternalId));
        scenarios.add(new Scenario(
                scenarioId,
                Optional.of(requirementId),
                title,
                preconditions,
                action.toString(),
                expectedOutcome.toString(),
                provenance(scenarioExternalId, source, scenarioEvidence.id())));
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
            EntityIdentityResolver identityResolver,
            String externalId,
            SourceLocator source,
            List<String> lines,
            int startLine,
            int endLineExclusive) {
        int normalizedEndLine = Math.max(startLine, endLineExclusive);
        String excerpt = String.join("\n", lines.subList(startLine - 1, normalizedEndLine));
        EvidenceId evidenceId = new EvidenceId(identityResolver.resolve(
                OpenSpecSpecificationProvider.ID,
                "evidence",
                "evidence:" + externalId));
        return new Evidence(
                evidenceId,
                source,
                Optional.of(new SourceRange(startLine, normalizedEndLine)),
                Optional.of(sha256(excerpt)));
    }

    private List<Path> listSpecificationFiles(Path specsRoot) {
        try (var paths = Files.walk(specsRoot)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().equals("spec.md"))
                    .sorted()
                    .toList();
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot enumerate OpenSpec specifications", exception);
        }
    }

    private List<String> readAllLines(Path workspaceRoot, Path source) {
        try {
            return SafeWorkspaceFileResolver.rootedAt(workspaceRoot)
                    .readUtf8(workspaceRoot.relativize(source))
                    .lines()
                    .toList();
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read OpenSpec source " + source, exception);
        }
    }

    private String specificationKey(Path specsRoot, Path specificationFile) {
        Path relativeParent = specsRoot.relativize(specificationFile.getParent());
        String key = relativeParent.toString().replace('\\', '/');
        if (key.isBlank()) {
            throw new IllegalArgumentException("OpenSpec specification must live under a specification key directory");
        }
        return key;
    }

    private Optional<String> firstHeading(List<String> lines) {
        return lines.stream()
                .filter(line -> line.startsWith("# "))
                .map(line -> line.substring(2).trim())
                .filter(value -> !value.isEmpty())
                .findFirst();
    }

    private Optional<String> sectionBody(List<String> lines, String heading) {
        for (int index = 0; index < lines.size(); index++) {
            if (!lines.get(index).trim().equals(heading)) {
                continue;
            }
            int end = index + 1;
            while (end < lines.size() && !lines.get(end).startsWith("## ")) {
                end++;
            }
            String body = joinContent(lines, index + 1, end);
            return body.isBlank() ? Optional.empty() : Optional.of(body);
        }
        return Optional.empty();
    }

    private List<Integer> headingIndexes(List<String> lines, Pattern pattern) {
        List<Integer> indexes = new ArrayList<>();
        for (int index = 0; index < lines.size(); index++) {
            if (pattern.matcher(lines.get(index)).matches()) {
                indexes.add(index);
            }
        }
        return indexes;
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
}
