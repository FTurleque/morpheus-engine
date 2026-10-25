package com.morpheus.cli.command;

import com.morpheus.application.operability.OperationalRecorder;
import com.morpheus.application.quality.AcceptanceQualityService;
import com.morpheus.application.quality.ChangeCompletenessService;
import com.morpheus.application.quality.CoverageRatioStatus;
import com.morpheus.application.quality.DecisionReferenceQualityService;
import com.morpheus.application.quality.QualityReport;
import com.morpheus.application.quality.QualityReportService;
import com.morpheus.application.quality.RequirementQualityService;
import com.morpheus.application.quality.TaskQualityService;
import com.morpheus.application.quality.compact.CompactQualityReportService;
import com.morpheus.domain.project.ProjectSpecificationId;

import java.io.PrintStream;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;

/** The quality family of the CLI: {@code quality}, the report of the ACTIVE snapshot of one project. */
public final class MorpheusQualityCli {
    private static final String COMMAND = "quality";
    private static final String OPT_PROJECT = "project";

    private final OperationalRecorder recorder;

    /** Every command counts what it puts on the database on {@code recorder}, the one the launcher handed. */
    public MorpheusQualityCli(OperationalRecorder recorder) {
        this.recorder = Objects.requireNonNull(recorder, "recorder");
    }

    public static boolean handles(String[] args) {
        return GlobalArgs.command(args).equals(COMMAND);
    }

    public int run(
            String[] args,
            PrintStream out,
            PrintStream err,
            Map<String, String> environment,
            Properties properties) {
        return CliCommandRun.run(args, out, err, environment, properties,
                (tokens, run) -> quality(tokens.subList(1, tokens.size()), run));
    }

    private int quality(List<String> tokens, CliCommandRun run) {
        CommandOptions options = CommandOptions.parse(tokens, Set.of());
        options.rejectUnknown(Set.of(OPT_PROJECT));
        ProjectSpecificationId projectId = ProjectSpecificationId.parse(options.required(OPT_PROJECT));
        try (CliRuntime runtime = CliRuntime.recovering(run.databasePath(), recorder)) {
            QualityReportService service = new QualityReportService(
                    runtime.snapshots(),
                    new RequirementQualityService(runtime.snapshots(), runtime.requirements(), runtime.traceability()),
                    new TaskQualityService(runtime.snapshots(), runtime.content(), runtime.requirements(), runtime.traceability()),
                    new AcceptanceQualityService(runtime.snapshots(), runtime.content()),
                    new ChangeCompletenessService(runtime.snapshots(), runtime.content(), runtime.requirements(), runtime.traceability()),
                    new DecisionReferenceQualityService(
                            runtime.snapshots(), runtime.content(), runtime.requirements(), runtime.traceability(), runtime.externalReferences()));
            QualityReport report = service.assessActive(projectId)
                    .orElseThrow(() -> CliFailure.notFound("project has no ACTIVE snapshot: " + projectId));
            if (run.json()) {
                run.printLine(new CompactQualityReportService().toJson(report));
            } else {
                var metrics = report.metrics();
                run.printLine("snapshotId=" + report.snapshot().id());
                run.printLine("findings=" + metrics.totalFindings()
                        + " requirements=" + metrics.totalRequirements()
                        + " requirementCoverage="
                        + coverage(metrics.requirementCoverageStatus(), metrics.requirementCoverageRatio())
                        + " tasks=" + metrics.totalTasks()
                        + " taskCoverage=" + coverage(metrics.taskCoverageStatus(), metrics.taskCoverageRatio())
                        + " acceptance=" + metrics.acceptanceCoverageStatus());
            }
            return CliExitCode.SUCCESS.code();
        }
    }

    /** A ratio over an empty population is not a measurement, so it is not printed as a number. */
    private static String coverage(CoverageRatioStatus status, double ratio) {
        return status == CoverageRatioStatus.MEASURED ? Double.toString(ratio) : status.name();
    }
}
