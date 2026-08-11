package com.morpheus.provider.openspec;

import com.morpheus.application.identity.EntityIdentityResolver;
import com.morpheus.application.files.SafeWorkspaceFileResolver;
import com.morpheus.application.ingestion.NormalizedProjectContent;
import com.morpheus.domain.change.ChangeId;
import com.morpheus.domain.change.ChangeProposal;
import com.morpheus.domain.constraint.Constraint;
import com.morpheus.domain.constraint.ConstraintId;
import com.morpheus.domain.decision.DesignDecision;
import com.morpheus.domain.decision.DesignDecisionId;
import com.morpheus.domain.evidence.Evidence;
import com.morpheus.domain.evidence.EvidenceId;
import com.morpheus.domain.evidence.SourceRange;
import com.morpheus.domain.project.ProjectSpecification;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.provenance.Provenance;
import com.morpheus.domain.provider.ProviderCapability;
import com.morpheus.domain.provider.ProviderProbeStatus;
import com.morpheus.domain.source.SourceLocator;
import com.morpheus.domain.task.ImplementationTask;
import com.morpheus.domain.task.TaskId;

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

/** Normalizes OpenSpec proposal/design/task metadata without applying M3 temporal semantics. */
public final class OpenSpecChangeMetadataReader {
    private static final Pattern PROPOSAL_TITLE = Pattern.compile("^#\\s+Proposal:\\s*(.+?)\\s*$");
    private static final Pattern DECISION_HEADING = Pattern.compile("^###\\s+(.+?)\\s*$");
    private static final Pattern TASK = Pattern.compile("^-\\s+\\[([ xX])\\]\\s+(.+?)\\s*$");

    private final OpenSpecSpecificationProvider provider;

    public OpenSpecChangeMetadataReader() {
        this(new OpenSpecSpecificationProvider());
    }

    OpenSpecChangeMetadataReader(OpenSpecSpecificationProvider provider) {
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
        if (!probe.capabilities().contains(ProviderCapability.READ_CHANGES)) {
            throw new IllegalArgumentException("OpenSpec workspace has no readable changes: " + root);
        }

        List<ChangeProposal> changes = new ArrayList<>();
        List<Constraint> constraints = new ArrayList<>();
        List<DesignDecision> decisions = new ArrayList<>();
        List<ImplementationTask> tasks = new ArrayList<>();
        List<Evidence> evidence = new ArrayList<>();

        for (Path changeRoot : listChangeRoots(root.resolve("openspec/changes"))) {
            normalizeChange(root, changeRoot, projectId, identityResolver, changes, constraints, decisions, tasks, evidence);
        }

        String displayName = root.getFileName() == null ? root.toString() : root.getFileName().toString();
        ProjectSpecification project = new ProjectSpecification(projectId, displayName, SourceLocator.file(root.toString()));
        return new NormalizedProjectContent(
                project,
                List.of(),
                List.of(),
                List.of(),
                changes,
                constraints,
                decisions,
                tasks,
                evidence,
                probe.diagnostics());
    }

    private void normalizeChange(
            Path workspaceRoot,
            Path changeRoot,
            ProjectSpecificationId projectId,
            EntityIdentityResolver identities,
            List<ChangeProposal> changes,
            List<Constraint> constraints,
            List<DesignDecision> decisions,
            List<ImplementationTask> tasks,
            List<Evidence> evidence) {
        String changeKey = changeRoot.getFileName().toString();
        Path proposalFile = changeRoot.resolve("proposal.md");
        if (!Files.isRegularFile(proposalFile)) {
            throw new IllegalArgumentException("OpenSpec change has no proposal.md: " + changeKey);
        }

        List<String> proposalLines = readAllLines(workspaceRoot, proposalFile);
        SourceLocator proposalSource = locator(workspaceRoot, proposalFile);
        String changeExternalId = "change:" + changeKey;
        String title = proposalTitle(proposalLines);
        String intent = sectionBody(proposalLines, "## Intent")
                .orElseThrow(() -> new IllegalArgumentException("OpenSpec change has no Intent: " + changeKey));
        List<String> scope = bulletSection(proposalLines, "## Scope");
        List<String> outOfScope = bulletSection(proposalLines, "## Out of scope");
        List<String> risks = bulletSection(proposalLines, "## Risks");

        Evidence changeEvidence = evidence(identities, changeExternalId, proposalSource, proposalLines, 1, proposalLines.size());
        evidence.add(changeEvidence);
        ChangeId changeId = new ChangeId(identities.resolve(
                OpenSpecSpecificationProvider.ID, "change", changeExternalId));
        changes.add(new ChangeProposal(
                changeId,
                projectId,
                Optional.of(changeKey),
                title,
                intent,
                scope,
                outOfScope,
                risks,
                provenance(changeExternalId, proposalSource, changeEvidence.id())));

        normalizeConstraints(changeKey, changeId, proposalLines, proposalSource, identities, constraints, evidence);
        normalizeDecisions(workspaceRoot, changeRoot, changeKey, changeId, identities, decisions, evidence);
        normalizeTasks(workspaceRoot, changeRoot, changeKey, changeId, identities, tasks, evidence);
    }

    private void normalizeConstraints(
            String changeKey,
            ChangeId changeId,
            List<String> lines,
            SourceLocator source,
            EntityIdentityResolver identities,
            List<Constraint> constraints,
            List<Evidence> evidence) {
        List<LineItem> items = bulletSectionItems(lines, "## Constraints");
        for (int index = 0; index < items.size(); index++) {
            LineItem item = items.get(index);
            String externalId = "constraint:" + changeKey + ":" + (index + 1);
            Evidence itemEvidence = evidence(identities, externalId, source, lines, item.lineNumber(), item.lineNumber());
            evidence.add(itemEvidence);
            constraints.add(new Constraint(
                    new ConstraintId(identities.resolve(OpenSpecSpecificationProvider.ID, "constraint", externalId)),
                    changeId,
                    item.text(),
                    provenance(externalId, source, itemEvidence.id())));
        }
    }

    private void normalizeDecisions(
            Path workspaceRoot,
            Path changeRoot,
            String changeKey,
            ChangeId changeId,
            EntityIdentityResolver identities,
            List<DesignDecision> decisions,
            List<Evidence> evidence) {
        Path designFile = changeRoot.resolve("design.md");
        if (!Files.isRegularFile(designFile)) {
            return;
        }
        List<String> lines = readAllLines(workspaceRoot, designFile);
        SourceLocator source = locator(workspaceRoot, designFile);
        List<Integer> headings = headingIndexes(lines, DECISION_HEADING);
        for (int index = 0; index < headings.size(); index++) {
            int start = headings.get(index);
            int end = index + 1 < headings.size() ? headings.get(index + 1) : nextLevelTwoHeading(lines, start + 1);
            Matcher matcher = DECISION_HEADING.matcher(lines.get(start));
            if (!matcher.matches()) {
                throw new IllegalStateException("design decision heading mismatch");
            }
            String title = matcher.group(1).trim();
            String decision = joinContent(lines, start + 1, end);
            if (decision.isBlank()) {
                throw new IllegalArgumentException("OpenSpec design decision has no body: " + title);
            }
            String externalId = "design-decision:" + changeKey + ":" + slug(title);
            Evidence itemEvidence = evidence(identities, externalId, source, lines, start + 1, end);
            evidence.add(itemEvidence);
            decisions.add(new DesignDecision(
                    new DesignDecisionId(identities.resolve(OpenSpecSpecificationProvider.ID, "design-decision", externalId)),
                    changeId,
                    title,
                    decision,
                    provenance(externalId, source, itemEvidence.id())));
        }
    }

    private void normalizeTasks(
            Path workspaceRoot,
            Path changeRoot,
            String changeKey,
            ChangeId changeId,
            EntityIdentityResolver identities,
            List<ImplementationTask> tasks,
            List<Evidence> evidence) {
        Path tasksFile = changeRoot.resolve("tasks.md");
        if (!Files.isRegularFile(tasksFile)) {
            return;
        }
        List<String> lines = readAllLines(workspaceRoot, tasksFile);
        SourceLocator source = locator(workspaceRoot, tasksFile);
        int ordinal = 0;
        for (int index = 0; index < lines.size(); index++) {
            Matcher matcher = TASK.matcher(lines.get(index));
            if (!matcher.matches()) {
                continue;
            }
            ordinal++;
            String externalId = "task:" + changeKey + ":" + ordinal;
            Evidence itemEvidence = evidence(identities, externalId, source, lines, index + 1, index + 1);
            evidence.add(itemEvidence);
            tasks.add(new ImplementationTask(
                    new TaskId(identities.resolve(OpenSpecSpecificationProvider.ID, "task", externalId)),
                    changeId,
                    Optional.of(changeKey + "/task-" + ordinal),
                    matcher.group(2).trim(),
                    !matcher.group(1).isBlank(),
                    provenance(externalId, source, itemEvidence.id())));
        }
    }

    private List<Path> listChangeRoots(Path changesRoot) {
        if (!Files.isDirectory(changesRoot)) {
            return List.of();
        }
        try (var paths = Files.list(changesRoot)) {
            return paths
                    .filter(Files::isDirectory)
                    .filter(path -> !path.getFileName().toString().equals("archive"))
                    .sorted()
                    .toList();
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot enumerate OpenSpec changes", exception);
        }
    }

    private String proposalTitle(List<String> lines) {
        return lines.stream()
                .map(PROPOSAL_TITLE::matcher)
                .filter(Matcher::matches)
                .map(matcher -> matcher.group(1).trim())
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("OpenSpec proposal has no Proposal title"));
    }

    private Optional<String> sectionBody(List<String> lines, String heading) {
        int start = sectionStart(lines, heading);
        if (start < 0) {
            return Optional.empty();
        }
        int end = nextLevelTwoHeading(lines, start + 1);
        String body = joinContent(lines, start + 1, end);
        return body.isBlank() ? Optional.empty() : Optional.of(body);
    }

    private List<String> bulletSection(List<String> lines, String heading) {
        return bulletSectionItems(lines, heading).stream().map(LineItem::text).toList();
    }

    private List<LineItem> bulletSectionItems(List<String> lines, String heading) {
        int start = sectionStart(lines, heading);
        if (start < 0) {
            return List.of();
        }
        int end = nextLevelTwoHeading(lines, start + 1);
        List<LineItem> items = new ArrayList<>();
        for (int index = start + 1; index < end; index++) {
            String line = lines.get(index).trim();
            if (line.startsWith("- ")) {
                items.add(new LineItem(index + 1, line.substring(2).trim()));
            }
        }
        return List.copyOf(items);
    }

    private int sectionStart(List<String> lines, String heading) {
        for (int index = 0; index < lines.size(); index++) {
            if (lines.get(index).trim().equals(heading)) {
                return index;
            }
        }
        return -1;
    }

    private int nextLevelTwoHeading(List<String> lines, int from) {
        for (int index = from; index < lines.size(); index++) {
            if (lines.get(index).startsWith("## ")) {
                return index;
            }
        }
        return lines.size();
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

    private SourceLocator locator(Path workspaceRoot, Path source) {
        return SourceLocator.file(workspaceRoot.relativize(source).toString());
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
            int endLine) {
        int normalizedEnd = Math.max(startLine, endLine);
        String excerpt = String.join("\n", lines.subList(startLine - 1, normalizedEnd));
        EvidenceId evidenceId = new EvidenceId(identities.resolve(
                OpenSpecSpecificationProvider.ID, "evidence", "evidence:" + externalId));
        return new Evidence(
                evidenceId,
                source,
                Optional.of(new SourceRange(startLine, normalizedEnd)),
                Optional.of(sha256(excerpt)));
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

    private record LineItem(int lineNumber, String text) {
    }
}
