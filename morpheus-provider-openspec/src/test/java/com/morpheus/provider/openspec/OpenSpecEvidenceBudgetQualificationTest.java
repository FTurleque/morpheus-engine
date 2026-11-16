package com.morpheus.provider.openspec;

import com.morpheus.application.files.SafeWorkspaceFileResolver;
import com.morpheus.application.read.ProviderIngestionBudget;
import com.morpheus.application.read.ProviderReadRequest;
import com.morpheus.application.read.ProviderReadResult;
import com.morpheus.application.read.ReadCategory;
import com.morpheus.application.read.ReadCategoryReport;
import com.morpheus.application.read.ReadCategoryStatus;
import com.morpheus.domain.diagnostic.Diagnostic;
import com.morpheus.domain.diagnostic.DiagnosticCode;
import com.morpheus.domain.identity.DomainIdentity;
import com.morpheus.domain.project.ProjectSpecificationId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The default evidence bound is qualified on corpora larger than the projects it was measured on, and still refuses a
 * corpus beyond it. The measurement and the declared margin are written beside the bound, in
 * {@code ProviderIngestionBudget.DEFAULT}: the largest measured project sits under a third of the bound, and the margin is
 * half of it. The thresholds below are fractions of the bound that lie above that measurement, so the bound is read, not
 * restated.
 */
class OpenSpecEvidenceBudgetQualificationTest {
    private static final Set<ReadCategory> EVERY_READ_GROUP = EnumSet.of(
            ReadCategory.CURRENT_SPECIFICATIONS,
            ReadCategory.REQUIREMENTS,
            ReadCategory.SCENARIOS,
            ReadCategory.CHANGES,
            ReadCategory.REQUIREMENT_DELTAS,
            ReadCategory.CONSTRAINTS,
            ReadCategory.DESIGN_DECISIONS,
            ReadCategory.IMPLEMENTATION_TASKS);

    private static final int REQUIREMENTS_PER_SPECIFICATION = 20;
    private static final int SCENARIOS_PER_REQUIREMENT = 2;

    /**
     * A workspace whose current specifications exceed a quarter of the bound and whose fully cited files (specifications
     * and proposals) exceed two fifths of it: both lie above the largest measured project. Each change also carries a
     * design decision, tasks and a delta, so every reader cites, and the scenarios are cited inside their requirement and
     * their file as the reader cites them.
     */
    @Test
    void aCorpusLargerThanTheLargestMeasuredProjectIsRead(@TempDir Path workspace) throws Exception {
        long bound = ProviderIngestionBudget.DEFAULT.maxEvidenceBytes();
        // The margin the bound's comment declares: half of the bound stays free on a qualified corpus.
        long declaredMargin = bound / 2;
        write(workspace, "openspec/config.yaml", "schema: spec-driven\n");
        long specificationBytes = 0;
        for (int index = 0; specificationBytes < bound / 4; index++) {
            specificationBytes += write(workspace,
                    "openspec/specs/capability-" + index + "/spec.md", specification("Capability " + index));
        }
        long fullyCitedBytes = specificationBytes;
        for (int index = 0; fullyCitedBytes < bound * 2 / 5; index++) {
            String change = "openspec/changes/change-" + index + "/";
            fullyCitedBytes += write(workspace, change + "proposal.md", proposal("Change " + index));
            write(workspace, change + "design.md", design("Change " + index));
            write(workspace, change + "tasks.md", tasks("Change " + index));
            write(workspace, change + "specs/capability-" + index + "/spec.md", delta("Change " + index));
        }
        ProviderIngestionBudget.Session budget =
                ProviderIngestionBudget.DEFAULT.open(SafeWorkspaceFileResolver.rootedAt(workspace));

        ProviderReadResult result = read(workspace, budget);

        for (ReadCategory category : EVERY_READ_GROUP) {
            ReadCategoryReport report = result.report(category).orElseThrow();
            assertEquals(ReadCategoryStatus.READ, report.status(), report + ": " + result.diagnostics());
        }
        assertTrue(result.diagnostics().stream().noneMatch(OpenSpecEvidenceBudgetQualificationTest::isBudgetRefusal),
                () -> "no budget diagnostic: " + result.diagnostics());
        assertTrue(budget.evidenceBytes() >= fullyCitedBytes,
                "every line of a specification and a proposal is cited: " + budget.evidenceBytes());
        assertTrue(budget.evidenceBytes() <= bound - declaredMargin,
                "the evidence counted stays under the bound by the declared margin: " + budget.evidenceBytes()
                        + " of " + bound);
    }

    @Test
    void aCorpusBeyondTheLimitIsRefusedAndPublishesNothing(@TempDir Path workspace) throws Exception {
        long bound = ProviderIngestionBudget.DEFAULT.maxEvidenceBytes();
        write(workspace, "openspec/config.yaml", "schema: spec-driven\n");
        long specificationBytes = 0;
        for (int index = 0; specificationBytes <= bound; index++) {
            specificationBytes += write(workspace,
                    "openspec/specs/capability-" + index + "/spec.md", specification("Capability " + index));
        }

        ProviderReadResult result = read(
                workspace, ProviderIngestionBudget.DEFAULT.open(SafeWorkspaceFileResolver.rootedAt(workspace)));

        assertTrue(result.content().isEmpty(), "nothing is published");
        for (ReadCategory category : EVERY_READ_GROUP) {
            assertEquals(ReadCategoryStatus.FAILED, result.report(category).orElseThrow().status(), category::name);
        }
        List<Diagnostic> refusals = result.diagnostics().stream()
                .filter(OpenSpecEvidenceBudgetQualificationTest::isBudgetRefusal)
                .toList();
        assertEquals(1, refusals.size(), result.diagnostics()::toString);
        String message = refusals.getFirst().message();
        Matcher refusal = Pattern.compile("provider ingestion evidence bytes for the whole read exceeds the budget: "
                + "(\\d+) > " + bound + ", reached while reading openspec/specs/capability-\\d+/spec\\.md")
                .matcher(message);
        assertTrue(refusal.find(), message);
        assertTrue(Long.parseLong(refusal.group(1)) > bound, message);
    }

    private static boolean isBudgetRefusal(Diagnostic diagnostic) {
        return diagnostic.code() == DiagnosticCode.INVALID_SOURCE
                && "ingestion-budget".equals(diagnostic.details().get("group"));
    }

    private static ProviderReadResult read(Path workspace, ProviderIngestionBudget.Session budget) {
        // Stable per external identity: a delta names its change by the identity the change reader resolved.
        Map<String, DomainIdentity> identities = new TreeMap<>();
        return new OpenSpecSpecificationContentReader().read(
                new ProviderReadRequest(workspace, ProjectSpecificationId.generate(), EVERY_READ_GROUP),
                (provider, entityType, externalId) -> identities.computeIfAbsent(
                        provider.value() + "|" + entityType + "|" + externalId, ignored -> DomainIdentity.generate()),
                budget);
    }

    private static String specification(String name) {
        StringBuilder text = new StringBuilder()
                .append("# Specification ").append(name).append("\n\n")
                .append("## Purpose\n\n").append(sentence(name + " purpose")).append("\n\n")
                .append("## Requirements\n\n");
        for (int requirement = 0; requirement < REQUIREMENTS_PER_SPECIFICATION; requirement++) {
            String title = name + " requirement " + requirement;
            text.append("### Requirement: ").append(title).append('\n')
                    .append("The system SHALL ").append(sentence(title)).append("\n\n");
            for (int scenario = 0; scenario < SCENARIOS_PER_REQUIREMENT; scenario++) {
                String scenarioTitle = title + " scenario " + scenario;
                text.append("#### Scenario: ").append(scenarioTitle).append('\n')
                        .append("- **GIVEN** ").append(sentence(scenarioTitle + " given")).append('\n')
                        .append("- **WHEN** ").append(sentence(scenarioTitle + " when")).append('\n')
                        .append("- **THEN** ").append(sentence(scenarioTitle + " then")).append('\n')
                        .append("- **AND** ").append(sentence(scenarioTitle + " and")).append("\n\n");
            }
        }
        return text.toString();
    }

    private static String proposal(String name) {
        StringBuilder text = new StringBuilder()
                .append("# Proposal: ").append(name).append("\n\n")
                .append("## Intent\n\n");
        for (int line = 0; line < 100; line++) {
            text.append(sentence(name + " intent " + line)).append('\n');
        }
        text.append("\n## Scope\n\n");
        for (int item = 0; item < 10; item++) {
            text.append("- ").append(sentence(name + " scope " + item)).append('\n');
        }
        text.append("\n## Constraints\n\n");
        for (int item = 0; item < 3; item++) {
            text.append("- ").append(sentence(name + " constraint " + item)).append('\n');
        }
        return text.toString();
    }

    private static String design(String name) {
        return "# Design: " + name + "\n\n## Decisions\n\n### Decision for " + name + "\n"
                + sentence(name + " decision") + "\n";
    }

    private static String tasks(String name) {
        return "# Tasks: " + name + "\n\n- [x] " + sentence(name + " first task") + "\n- [ ] "
                + sentence(name + " second task") + "\n";
    }

    private static String delta(String name) {
        String title = name + " added requirement";
        return "# Delta\n\n## ADDED Requirements\n\n### Requirement: " + title + "\nThe system SHALL "
                + sentence(title) + "\n\n#### Scenario: " + title + " scenario\n- **WHEN** " + sentence(title + " when")
                + "\n- **THEN** " + sentence(title + " then") + "\n";
    }

    /** A long, deterministic line, so that the corpus grows in bytes faster than in lines. */
    private static String sentence(String subject) {
        return subject.toLowerCase(Locale.ROOT) + " keeps a deterministic and bounded trace of what was read, "
                + "so that the qualification corpus grows in bytes rather than in lines.";
    }

    private static long write(Path workspace, String relative, String content) throws IOException {
        Path file = workspace.resolve(relative);
        Files.createDirectories(file.getParent());
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        Files.write(file, bytes);
        return bytes.length;
    }
}
