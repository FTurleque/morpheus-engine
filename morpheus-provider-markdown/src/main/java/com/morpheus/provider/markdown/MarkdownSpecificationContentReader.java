package com.morpheus.provider.markdown;

import com.morpheus.application.identity.EntityIdentityResolver;
import com.morpheus.application.ingestion.NormalizedProjectContent;
import com.morpheus.application.read.ProviderReadRequest;
import com.morpheus.application.read.ProviderReadResult;
import com.morpheus.application.read.ReadCategory;
import com.morpheus.application.read.ReadCategoryReport;
import com.morpheus.application.read.ReadCategoryStatus;
import com.morpheus.application.read.SpecificationContentReader;
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
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Deterministic normalized reader for the M18 Structured Markdown provider. */
public final class MarkdownSpecificationContentReader implements SpecificationContentReader {
    private static final Pattern REQUIREMENT_HEADING = Pattern.compile("^##\\s+([^\\s]+)\\s+(?:—|-)\\s+(.+?)\\s*$");
    private static final Pattern SCENARIO_HEADING = Pattern.compile("^###\\s+Scenario\\s+(?:—|-)\\s+(.+?)\\s*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern SCENARIO_STEP = Pattern.compile("^(Given|And|When|Then):\\s*(.+?)\\s*$", Pattern.CASE_INSENSITIVE);
    private static final Set<ReadCategory> SUPPORTED = EnumSet.of(
            ReadCategory.CURRENT_SPECIFICATIONS,
            ReadCategory.REQUIREMENTS,
            ReadCategory.SCENARIOS);

    private final MarkdownSpecificationProvider provider;

    public MarkdownSpecificationContentReader() {
        this(new MarkdownSpecificationProvider());
    }

    MarkdownSpecificationContentReader(MarkdownSpecificationProvider provider) {
        this.provider = Objects.requireNonNull(provider, "provider");
    }

    @Override
    public ProviderId providerId() {
        return MarkdownSpecificationProvider.ID;
    }

    @Override
    public ProviderReadResult read(ProviderReadRequest request, EntityIdentityResolver identityResolver) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(identityResolver, "identityResolver");

        var probe = provider.probe(request.workspaceRoot());
        if (probe.status() != ProviderProbeStatus.SUPPORTED) {
            return absentResult(request);
        }

        List<Specification> specifications = new ArrayList<>();
        List<Requirement> requirements = new ArrayList<>();
        List<Scenario> scenarios = new ArrayList<>();
        List<Evidence> evidence = new ArrayList<>();
        List<Diagnostic> diagnostics = new ArrayList<>();
        boolean[] parseFailure = {false};

        Path root = request.workspaceRoot();
        Path specsRoot = root.resolve(MarkdownSpecificationProvider.ROOT);
        for (Path file : listMarkdownFiles(specsRoot)) {
            try {
                normalizeFile(
                        root,
                        file,
                        request,
                        identityResolver,
                        specifications,
                        requirements,
                        scenarios,
                        evidence);
            } catch (MarkdownFormatException exception) {
                parseFailure[0] = true;
                diagnostics.add(Diagnostic.error(
                        exception.code,
                        exception.getMessage(),
                        Map.of(
                                "provider", providerId().value(),
                                "source", root.relativize(file).toString().replace('\\', '/'))));
            } catch (RuntimeException exception) {
                parseFailure[0] = true;
                diagnostics.add(Diagnostic.error(
                        DiagnosticCode.INVALID_SOURCE,
                        "Structured Markdown source could not be normalized",
                        Map.of(
                                "provider", providerId().value(),
                                "source", root.relativize(file).toString().replace('\\', '/'),
                                "exception", exception.getClass().getSimpleName())));
            }
        }

        addUnsupportedDiagnostics(request.requestedCategories(), diagnostics);
        List<ReadCategoryReport> reports = ordered(request.requestedCategories()).stream()
                .map(category -> report(
                        category,
                        parseFailure[0],
                        specifications.size(),
                        requirements.size(),
                        scenarios.size()))
                .toList();

        String displayName = root.getFileName() == null ? root.toString() : root.getFileName().toString();
        ProjectSpecification project = new ProjectSpecification(
                request.projectId(),
                displayName,
                SourceLocator.file(root.toString()));
        NormalizedProjectContent content = new NormalizedProjectContent(
                project,
                specifications,
                requirements,
                scenarios,
                evidence,
                diagnostics);
        return new ProviderReadResult(providerId(), Optional.of(content), reports, diagnostics);
    }

    private ProviderReadResult absentResult(ProviderReadRequest request) {
        List<ReadCategoryReport> reports = ordered(request.requestedCategories()).stream()
                .map(category -> SUPPORTED.contains(category)
                        ? new ReadCategoryReport(
                                category,
                                ReadCategoryStatus.ABSENT,
                                0,
                                List.of(),
                                Optional.of("Structured Markdown source directory is absent"))
                        : unsupported(category))
                .toList();
        return new ProviderReadResult(providerId(), Optional.empty(), reports, List.of());
    }

    private void normalizeFile(
            Path workspaceRoot,
            Path file,
            ProviderReadRequest request,
            EntityIdentityResolver identityResolver,
            List<Specification> specifications,
            List<Requirement> requirements,
            List<Scenario> scenarios,
            List<Evidence> evidence) {
        List<String> lines = readAllLines(file);
        FrontMatter frontMatter = frontMatter(lines);
        if (!MarkdownSpecificationProvider.FORMAT_VERSION.equals(frontMatter.values().get("morpheus-format"))) {
            throw new MarkdownFormatException(
                    DiagnosticCode.UNSUPPORTED_FORMAT_VERSION,
                    "Structured Markdown file declares an unsupported morpheus-format");
        }
        String specificationKey = required(frontMatter.values(), "spec");
        String title = required(frontMatter.values(), "title");
        String specificationExternalId = "specification:" + specificationKey;
        SourceLocator source = SourceLocator.file(workspaceRoot.relativize(file).toString());

        Evidence specificationEvidence = evidence(
                identityResolver,
                specificationExternalId,
                source,
                lines,
                1,
                lines.size());
        evidence.add(specificationEvidence);
        SpecificationId specificationId = new SpecificationId(identityResolver.resolve(
                providerId(), "specification", specificationExternalId));
        specifications.add(new Specification(
                specificationId,
                request.projectId(),
                specificationKey,
                title,
                Optional.empty(),
                provenance(specificationExternalId, source, specificationEvidence.id())));

        List<Integer> requirementHeadings = headingIndexes(lines, frontMatter.bodyStart(), REQUIREMENT_HEADING);
        for (int index = 0; index < requirementHeadings.size(); index++) {
            int start = requirementHeadings.get(index);
            int endExclusive = index + 1 < requirementHeadings.size()
                    ? requirementHeadings.get(index + 1)
                    : lines.size();
            normalizeRequirement(
                    lines,
                    start,
                    endExclusive,
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
            SpecificationId specificationId,
            SourceLocator source,
            EntityIdentityResolver identityResolver,
            List<Requirement> requirements,
            List<Scenario> scenarios,
            List<Evidence> evidence) {
        Matcher heading = REQUIREMENT_HEADING.matcher(lines.get(start));
        if (!heading.matches()) {
            throw new IllegalStateException("requirement heading index is inconsistent");
        }
        String key = heading.group(1).trim();
        String title = heading.group(2).trim();
        String externalId = "requirement:" + key;

        int firstScenario = endExclusive;
        for (int index = start + 1; index < endExclusive; index++) {
            if (SCENARIO_HEADING.matcher(lines.get(index)).matches()) {
                firstScenario = index;
                break;
            }
        }
        String statement = joinStatement(lines, start + 1, firstScenario);
        if (statement.isBlank()) {
            throw new MarkdownFormatException(
                    DiagnosticCode.INVALID_SOURCE,
                    "Structured Markdown requirement has no statement: " + key);
        }

        Evidence requirementEvidence = evidence(
                identityResolver,
                externalId,
                source,
                lines,
                start + 1,
                endExclusive);
        evidence.add(requirementEvidence);
        RequirementId requirementId = new RequirementId(identityResolver.resolve(
                providerId(), "requirement", externalId));
        requirements.add(new Requirement(
                requirementId,
                specificationId,
                Optional.of(key),
                title,
                statement,
                provenance(externalId, source, requirementEvidence.id())));

        List<Integer> scenarioHeadings = headingIndexes(lines, firstScenario, SCENARIO_HEADING, endExclusive);
        for (int index = 0; index < scenarioHeadings.size(); index++) {
            int scenarioStart = scenarioHeadings.get(index);
            int scenarioEnd = index + 1 < scenarioHeadings.size()
                    ? scenarioHeadings.get(index + 1)
                    : endExclusive;
            normalizeScenario(
                    lines,
                    scenarioStart,
                    scenarioEnd,
                    key,
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
            throw new IllegalStateException("scenario heading index is inconsistent");
        }
        String title = heading.group(1).trim();
        String externalId = "scenario:" + requirementKey + "/" + slug(title);
        List<String> preconditions = new ArrayList<>();
        StringBuilder action = new StringBuilder();
        StringBuilder expected = new StringBuilder();

        for (int index = start + 1; index < endExclusive; index++) {
            Matcher step = SCENARIO_STEP.matcher(lines.get(index).trim());
            if (!step.matches()) {
                continue;
            }
            String keyword = step.group(1).toUpperCase(Locale.ROOT);
            String value = step.group(2).trim();
            switch (keyword) {
                case "GIVEN" -> preconditions.add(value);
                case "AND" -> {
                    if (action.isEmpty()) {
                        preconditions.add(value);
                    } else if (expected.isEmpty()) {
                        append(action, value);
                    } else {
                        append(expected, value);
                    }
                }
                case "WHEN" -> append(action, value);
                case "THEN" -> append(expected, value);
                default -> throw new IllegalStateException("unexpected scenario keyword: " + keyword);
            }
        }
        if (action.isEmpty() || expected.isEmpty()) {
            throw new MarkdownFormatException(
                    DiagnosticCode.INVALID_SOURCE,
                    "Structured Markdown scenario requires When: and Then: " + externalId);
        }

        Evidence scenarioEvidence = evidence(
                identityResolver,
                externalId,
                source,
                lines,
                start + 1,
                endExclusive);
        evidence.add(scenarioEvidence);
        ScenarioId scenarioId = new ScenarioId(identityResolver.resolve(providerId(), "scenario", externalId));
        scenarios.add(new Scenario(
                scenarioId,
                Optional.of(requirementId),
                title,
                preconditions,
                action.toString(),
                expected.toString(),
                provenance(externalId, source, scenarioEvidence.id())));
    }

    private ReadCategoryReport report(
            ReadCategory category,
            boolean parseFailure,
            int specificationCount,
            int requirementCount,
            int scenarioCount) {
        if (!SUPPORTED.contains(category)) {
            return unsupported(category);
        }
        int count = switch (category) {
            case CURRENT_SPECIFICATIONS -> specificationCount;
            case REQUIREMENTS -> requirementCount;
            case SCENARIOS -> scenarioCount;
            default -> 0;
        };
        if (parseFailure) {
            return new ReadCategoryReport(
                    category,
                    count == 0 ? ReadCategoryStatus.FAILED : ReadCategoryStatus.PARTIAL,
                    count,
                    List.of(DiagnosticCode.INVALID_SOURCE),
                    Optional.of("at least one Structured Markdown source was invalid"));
        }
        return ReadCategoryReport.of(category, count == 0 ? ReadCategoryStatus.ABSENT : ReadCategoryStatus.READ, count);
    }

    private ReadCategoryReport unsupported(ReadCategory category) {
        return new ReadCategoryReport(
                category,
                ReadCategoryStatus.UNSUPPORTED,
                0,
                List.of(DiagnosticCode.OPTIONAL_CAPABILITY_UNAVAILABLE),
                Optional.of("Structured Markdown does not define this category"));
    }

    private void addUnsupportedDiagnostics(Set<ReadCategory> categories, List<Diagnostic> diagnostics) {
        for (ReadCategory category : ordered(categories)) {
            if (!SUPPORTED.contains(category)) {
                diagnostics.add(Diagnostic.warning(
                        DiagnosticCode.OPTIONAL_CAPABILITY_UNAVAILABLE,
                        "Requested category is not defined by Structured Markdown",
                        Map.of("provider", providerId().value(), "category", category.name())));
            }
        }
    }

    private FrontMatter frontMatter(List<String> lines) {
        if (lines.isEmpty() || !lines.getFirst().trim().equals("---")) {
            throw new MarkdownFormatException(DiagnosticCode.INVALID_SOURCE, "Structured Markdown file requires front matter");
        }
        int end = -1;
        for (int index = 1; index < lines.size(); index++) {
            if (lines.get(index).trim().equals("---")) {
                end = index;
                break;
            }
        }
        if (end < 0) {
            throw new MarkdownFormatException(DiagnosticCode.INVALID_SOURCE, "Structured Markdown front matter is not closed");
        }
        Map<String, String> values = new LinkedHashMap<>();
        for (int index = 1; index < end; index++) {
            String line = lines.get(index).trim();
            if (line.isEmpty()) {
                continue;
            }
            int separator = line.indexOf(':');
            if (separator <= 0 || separator == line.length() - 1) {
                throw new MarkdownFormatException(DiagnosticCode.INVALID_SOURCE, "Invalid Structured Markdown front matter line");
            }
            String key = line.substring(0, separator).trim().toLowerCase(Locale.ROOT);
            String value = line.substring(separator + 1).trim();
            if (values.putIfAbsent(key, value) != null) {
                throw new MarkdownFormatException(DiagnosticCode.INVALID_SOURCE, "Duplicate Structured Markdown front matter key: " + key);
            }
        }
        return new FrontMatter(Map.copyOf(values), end + 1);
    }

    private String required(Map<String, String> values, String key) {
        String value = values.get(key);
        if (value == null || value.isBlank()) {
            throw new MarkdownFormatException(DiagnosticCode.INVALID_SOURCE, "Missing Structured Markdown front matter key: " + key);
        }
        return value.trim();
    }

    private Provenance provenance(String externalId, SourceLocator source, EvidenceId evidenceId) {
        return new Provenance(
                providerId(),
                Optional.of(MarkdownSpecificationProvider.PROVIDER_VERSION),
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
        int normalizedStart = Math.max(1, startLine);
        int normalizedEnd = Math.max(normalizedStart, Math.min(lines.size(), endLineExclusive));
        String excerpt = String.join("\n", lines.subList(normalizedStart - 1, normalizedEnd));
        EvidenceId evidenceId = new EvidenceId(identityResolver.resolve(
                providerId(), "evidence", "evidence:" + externalId));
        return new Evidence(
                evidenceId,
                source,
                Optional.of(new SourceRange(normalizedStart, normalizedEnd)),
                Optional.of(sha256(excerpt)));
    }

    private List<Path> listMarkdownFiles(Path specsRoot) {
        try (var paths = Files.walk(specsRoot)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".md"))
                    .sorted()
                    .toList();
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot enumerate Structured Markdown specifications", exception);
        }
    }

    private List<String> readAllLines(Path source) {
        try {
            return Files.readAllLines(source, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read Structured Markdown source " + source, exception);
        }
    }

    private List<Integer> headingIndexes(List<String> lines, int startInclusive, Pattern pattern) {
        return headingIndexes(lines, startInclusive, pattern, lines.size());
    }

    private List<Integer> headingIndexes(List<String> lines, int startInclusive, Pattern pattern, int endExclusive) {
        List<Integer> indexes = new ArrayList<>();
        for (int index = Math.max(0, startInclusive); index < Math.min(lines.size(), endExclusive); index++) {
            if (pattern.matcher(lines.get(index).trim()).matches()) {
                indexes.add(index);
            }
        }
        return indexes;
    }

    private String joinStatement(List<String> lines, int startInclusive, int endExclusive) {
        List<String> content = new ArrayList<>();
        for (int index = startInclusive; index < endExclusive; index++) {
            String value = lines.get(index).trim();
            if (!value.isEmpty() && !value.startsWith("#")) {
                content.add(value);
            }
        }
        return String.join(" ", content).trim();
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

    private String slug(String value) {
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        return normalized.isBlank() ? "scenario" : normalized;
    }

    private void append(StringBuilder target, String value) {
        if (!target.isEmpty()) {
            target.append(' ');
        }
        target.append(value);
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private record FrontMatter(Map<String, String> values, int bodyStart) {
        private FrontMatter {
            values = Map.copyOf(Objects.requireNonNull(values, "values"));
            if (bodyStart < 0) {
                throw new IllegalArgumentException("bodyStart must be >= 0");
            }
        }
    }

    private static final class MarkdownFormatException extends RuntimeException {
        private final DiagnosticCode code;

        private MarkdownFormatException(DiagnosticCode code, String message) {
            super(message);
            this.code = Objects.requireNonNull(code, "code");
        }
    }
}
