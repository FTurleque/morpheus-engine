package com.morpheus.cli.command;

import com.morpheus.application.operability.OperationalRecorder;
import com.morpheus.application.query.BusinessContentQueryService;
import com.morpheus.application.query.PageRequest;
import com.morpheus.application.query.SnapshotPage;
import com.morpheus.application.query.compact.CanonicalJsonSerializer;
import com.morpheus.domain.acceptance.AcceptanceCriterion;
import com.morpheus.domain.change.ChangeId;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.requirement.RequirementId;

import java.io.PrintStream;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;

/** M15 CLI surface for explicit, snapshot-scoped acceptance criteria. */
public final class MorpheusAcceptanceCriteriaCli {
    private final CanonicalJsonSerializer json = new CanonicalJsonSerializer();
    private final OperationalRecorder recorder;

    /** Every command counts what it puts on the database on {@code recorder}, the one the launcher handed. */
    public MorpheusAcceptanceCriteriaCli(OperationalRecorder recorder) {
        this.recorder = Objects.requireNonNull(recorder, "recorder");
    }

    public static boolean handles(String[] args) {
        return "acceptance-criteria".equals(GlobalArgs.command(args));
    }

    public int run(
            String[] args,
            PrintStream out,
            PrintStream err,
            Map<String, String> environment,
            Properties properties) {
        try {
            Parsed parsed = Parsed.parse(args, environment, properties);
            if (parsed.tokens().isEmpty() || !"list".equals(parsed.tokens().getFirst())) {
                throw new IllegalArgumentException("acceptance-criteria requires action: list");
            }
            CommandOptions options = CommandOptions.parse(parsed.tokens().subList(1, parsed.tokens().size()),
                    CommandOptions.Declared.values("project", "change", "requirement", "offset", "limit"));
            ProjectSpecificationId projectId = ProjectSpecificationId.parse(options.required("project"));
            Optional<ChangeId> changeId = options.optional("change").map(ChangeId::parse);
            Optional<RequirementId> requirementId = options.optional("requirement").map(RequirementId::parse);
            if (changeId.isPresent() && requirementId.isPresent()) {
                throw new IllegalArgumentException("--change and --requirement are mutually exclusive");
            }
            PageRequest pageRequest = options.page();

            try (CliRuntime runtime = CliRuntime.recovering(parsed.layout().databasePath(), recorder)) {
                if (runtime.snapshots().findProject(projectId).isEmpty()) {
                    throw new IllegalStateException("project not found: " + projectId);
                }
                BusinessContentQueryService query = new BusinessContentQueryService(runtime.snapshots(), runtime.content());
                SnapshotPage<AcceptanceCriterion> page = changeId.isPresent()
                        ? query.activeAcceptanceCriteriaForChange(projectId, changeId.orElseThrow(), pageRequest)
                                .orElseThrow(() -> new IllegalStateException("project has no ACTIVE snapshot: " + projectId))
                        : requirementId.isPresent()
                                ? query.activeAcceptanceCriteriaForRequirement(projectId, requirementId.orElseThrow(), pageRequest)
                                        .orElseThrow(() -> new IllegalStateException("project has no ACTIVE snapshot: " + projectId))
                                : query.activeAcceptanceCriteria(projectId, pageRequest)
                                        .orElseThrow(() -> new IllegalStateException("project has no ACTIVE snapshot: " + projectId));
                write(page, parsed.json(), out);
                return CliExitCode.SUCCESS.code();
            }
        } catch (IllegalArgumentException failure) {
            return CliErrorLine.report(err, CliExitCode.USAGE, failure);
        } catch (RuntimeException failure) {
            return CliErrorLine.report(err, CliExitCode.STATE_ERROR, failure);
        }
    }

    private void write(SnapshotPage<AcceptanceCriterion> page, boolean jsonOutput, PrintStream out) {
        List<AcceptanceCriterionView> items = page.items().stream().map(this::view).toList();
        AcceptancePageView result = new AcceptancePageView(
                page.snapshot().id().toString(),
                page.totalMatches(),
                page.hasMore(),
                items);
        if (jsonOutput) {
            out.println(json.toJson(result));
            return;
        }
        out.println("snapshotId=" + result.snapshotId()
                + " total=" + result.totalMatches()
                + " hasMore=" + result.hasMore());
        result.items().forEach(item -> out.println(
                item.id() + "\t" + item.verificationStatus() + "\t" + item.title()));
    }

    private AcceptanceCriterionView view(AcceptanceCriterion criterion) {
        return new AcceptanceCriterionView(
                criterion.id().toString(),
                criterion.requirementId().map(Object::toString),
                criterion.changeId().map(Object::toString),
                criterion.title(),
                criterion.condition(),
                criterion.verificationStatus().name(),
                criterion.verificationEvidenceIds().stream().map(Object::toString).toList(),
                criterion.provenance().evidenceId().toString());
    }

    private record Parsed(boolean json, CliLayout layout, List<String> tokens) {
        private static Parsed parse(String[] args, Map<String, String> environment, Properties properties) {
            GlobalArgs.Parsed global = GlobalArgs.parse(args);
            List<String> remaining = global.remaining();
            if (remaining.isEmpty() || !"acceptance-criteria".equals(remaining.getFirst())) {
                throw new IllegalArgumentException("acceptance-criteria command is required");
            }
            return new Parsed(
                    global.json(),
                    CliLayout.resolve(global.dataDirectory(), global.configDirectory(), global.databasePath(),
                            environment, properties),
                    List.copyOf(remaining.subList(1, remaining.size())));
        }
    }

    private record AcceptancePageView(
            String snapshotId,
            int totalMatches,
            boolean hasMore,
            List<AcceptanceCriterionView> items) {
    }

    private record AcceptanceCriterionView(
            String id,
            Optional<String> requirementId,
            Optional<String> changeId,
            String title,
            String condition,
            String verificationStatus,
            List<String> verificationEvidenceIds,
            String sourceEvidenceId) {
    }
}