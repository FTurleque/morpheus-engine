package com.morpheus.cli;

import com.morpheus.application.analysis.ChangeAnalysisWarning;
import com.morpheus.application.analysis.ChangeAnalysisWarningCode;
import com.morpheus.domain.diagnostic.DiagnosticSeverity;
import com.morpheus.domain.evidence.EvidenceId;
import com.morpheus.domain.identity.DomainIdentity;
import com.morpheus.domain.snapshot.KnowledgeSnapshotId;
import com.morpheus.domain.traceability.TraceabilityEntityKind;
import com.morpheus.domain.traceability.TraceabilityEntityRef;
import com.morpheus.domain.traceability.TraceabilityLink;
import com.morpheus.domain.traceability.TraceabilityLinkId;
import com.morpheus.domain.traceability.TraceabilityLinkOrigin;
import com.morpheus.domain.traceability.TraceabilityRelationType;
import com.morpheus.domain.traceability.TraceabilityResolutionState;
import com.morpheus.store.sqlite.SqliteTraceabilityStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MorpheusCliTest {
    @TempDir
    Path tempDir;

    @Test
    void helpVersionPathsAndUsageHaveStableStreamsAndExitCodes() {
        Invocation help = invoke("help");
        assertEquals(0, help.exitCode());
        assertTrue(help.stdout().contains("Usage:"));
        assertTrue(help.stderr().isEmpty());

        Invocation version = invoke("--json", "version");
        assertEquals(0, version.exitCode());
        assertTrue(version.stdout().contains("\"version\""));
        assertTrue(version.stderr().isEmpty());

        Invocation paths = invoke("--data-dir", tempDir.resolve("data").toString(), "--json", "paths");
        assertEquals(0, paths.exitCode());
        assertTrue(paths.stdout().contains("morpheus.db"));
        assertTrue(paths.stdout().contains("\"configDirectory\""));

        Invocation invalid = invoke("--json", "does-not-exist");
        assertEquals(CliExitCode.USAGE.code(), invalid.exitCode());
        assertTrue(invalid.stdout().isEmpty());
        assertTrue(invalid.stderr().contains("\"exitCode\":2"));
        assertTrue(invalid.stderr().contains("USAGE"));
    }

    @Test
    void explicitDataAndConfigDirectoriesAreBothConsumedBeforeCommandDispatch() {
        Invocation invocation = invoke(
                "--data-dir", tempDir.resolve("data").toString(),
                "--config-dir", tempDir.resolve("config").toString(),
                "--db", tempDir.resolve("morpheus.db").toString(),
                "does-not-exist");

        assertEquals(CliExitCode.USAGE.code(), invocation.exitCode());
        assertTrue(invocation.stderr().contains("unknown command: does-not-exist"), invocation.stderr());
    }

    @Test
    void layoutUsesProductionDefaultsAndExplicitDataKeepsPortableStateTogether() {
        Path home = tempDir.resolve("home");
        Properties linux = properties("Linux", home);
        CliLayout defaultLinux = CliLayout.resolve(
                java.util.Optional.empty(), java.util.Optional.empty(), java.util.Optional.empty(), Map.of(), linux);
        assertTrue(defaultLinux.dataDirectory().endsWith(Path.of(".local", "share", "morpheus")));
        assertTrue(defaultLinux.configDirectory().endsWith(Path.of(".config", "morpheus")));
        assertTrue(defaultLinux.logsDirectory().endsWith(Path.of(".local", "state", "morpheus", "logs")));
        assertTrue(defaultLinux.backupsDirectory().endsWith(Path.of(".local", "state", "morpheus", "backups")));

        Path localAppData = tempDir.resolve("LocalAppData");
        Properties windows = properties("Windows 10", home);
        CliLayout defaultWindows = CliLayout.resolve(
                java.util.Optional.empty(), java.util.Optional.empty(), java.util.Optional.empty(),
                Map.of("LOCALAPPDATA", localAppData.toString()), windows);
        Path productRoot = localAppData.resolve("MORPHEUS").toAbsolutePath().normalize();
        assertEquals(productRoot.resolve("data"), defaultWindows.dataDirectory());
        assertEquals(productRoot.resolve("config"), defaultWindows.configDirectory());
        assertEquals(productRoot.resolve("logs"), defaultWindows.logsDirectory());
        assertEquals(productRoot.resolve("backups"), defaultWindows.backupsDirectory());
        assertEquals(productRoot.resolve("data").resolve("morpheus.db"), defaultWindows.databasePath());

        Path portable = tempDir.resolve("portable-data");
        CliLayout explicit = CliLayout.resolve(
                java.util.Optional.of(portable), java.util.Optional.empty(), java.util.Optional.empty(), Map.of(), linux);
        assertEquals(portable.toAbsolutePath().normalize(), explicit.dataDirectory());
        assertEquals(portable.resolve("config").toAbsolutePath().normalize(), explicit.configDirectory());
        assertEquals(portable.resolve("logs").toAbsolutePath().normalize(), explicit.logsDirectory());
        assertEquals(portable.resolve("backups").toAbsolutePath().normalize(), explicit.backupsDirectory());
        assertEquals(portable.resolve("morpheus.db").toAbsolutePath().normalize(), explicit.databasePath());
    }

    @Test
    void layoutHonorsXdgAndExplicitEnvironmentOverrides() {
        Path home = tempDir.resolve("xdg-home");
        Properties linux = properties("Linux", home);
        Map<String, String> environment = Map.of(
                "XDG_DATA_HOME", tempDir.resolve("xdg-data").toString(),
                "XDG_CONFIG_HOME", tempDir.resolve("xdg-config").toString(),
                "XDG_STATE_HOME", tempDir.resolve("xdg-state").toString());
        CliLayout layout = CliLayout.resolve(
                java.util.Optional.empty(), java.util.Optional.empty(), java.util.Optional.empty(), environment, linux);
        assertEquals(tempDir.resolve("xdg-data/morpheus").toAbsolutePath().normalize(), layout.dataDirectory());
        assertEquals(tempDir.resolve("xdg-config/morpheus").toAbsolutePath().normalize(), layout.configDirectory());
        assertEquals(tempDir.resolve("xdg-state/morpheus/logs").toAbsolutePath().normalize(), layout.logsDirectory());
        assertEquals(tempDir.resolve("xdg-state/morpheus/backups").toAbsolutePath().normalize(), layout.backupsDirectory());
    }

    @Test
    void registersOpenSpecWorkspaceSyncsAndQueriesThroughPersistentSqliteState() {
        Path data = tempDir.resolve("data");
        Path fixture = fixture("openspec-basic");

        Invocation add = invokeWithData(data, "projects", "add", "--workspace", fixture.toString());
        assertEquals(0, add.exitCode(), add.stderr());
        String projectId = value(add.stdout(), "projectId");
        assertFalse(projectId.isBlank());

        Invocation sync = invokeWithData(data, "sync", "--project", projectId);
        assertEquals(0, sync.exitCode(), sync.stderr());
        assertTrue(sync.stdout().contains("published=true"));
        assertTrue(sync.stdout().contains("requirements=2"));
        assertTrue(sync.stdout().contains("mode=FULL_REBUILD"));

        Invocation status = invokeWithData(data, "--json", "sync-status", "--project", projectId);
        assertEquals(0, status.exitCode(), status.stderr());
        assertTrue(status.stdout().contains("\"state\":\"FRESH\""));
        assertTrue(status.stdout().contains("\"currentSourceCount\""));
        assertTrue(status.stdout().contains("\"lastSuccessfulMode\":\"FULL_REBUILD\""));

        Invocation requirements = invokeWithData(
                data, "--json", "requirements", "find", "--project", projectId, "--query", "session");
        assertEquals(0, requirements.exitCode(), requirements.stderr());
        assertTrue(requirements.stdout().contains("session-expiration"));
        assertTrue(requirements.stdout().contains("totalMatches"));

        Invocation changes = invokeWithData(data, "changes", "list", "--project", projectId);
        assertEquals(0, changes.exitCode(), changes.stderr());
        String changeId = firstIdLine(changes.stdout());
        assertFalse(changeId.isBlank());

        Invocation context = invokeWithData(
                data, "--json", "change-context", "--project", projectId, "--change", changeId, "--depth", "2");
        assertEquals(0, context.exitCode(), context.stderr());
        assertTrue(context.stdout().contains("get_change_context"));

        Invocation analysis = invokeWithData(
                data, "--json", "analyze-change", "--project", projectId, "--change", changeId, "--depth", "2");
        assertEquals(0, analysis.exitCode(), analysis.stderr());
        assertTrue(analysis.stdout().contains("analyze_change"));
        assertTrue(analysis.stdout().contains("UNAVAILABLE_IN_NORMALIZED_MODEL"));

        Invocation quality = invokeWithData(data, "--json", "quality", "--project", projectId);
        assertEquals(0, quality.exitCode(), quality.stderr());
        assertTrue(quality.stdout().contains("get_quality_report"));

        Invocation listAfterReopen = invokeWithData(data, "--json", "projects", "list");
        assertEquals(0, listAfterReopen.exitCode());
        assertTrue(listAfterReopen.stdout().contains(projectId));
        assertTrue(Files.exists(data.resolve("morpheus.db")));
    }

    /** A ratio over an empty population is 1.0 by convention; the quality output used to publish it as a coverage. */
    @Test
    void aProjectWithoutRequirementsOrTasksHasNoCoverageMeasurement() throws Exception {
        Path workspace = tempDir.resolve("empty-workspace");
        Files.createDirectories(workspace.resolve("openspec/specs"));
        Files.copy(fixture("openspec-basic").resolve("openspec/config.yaml"), workspace.resolve("openspec/config.yaml"));
        Files.createDirectories(workspace.resolve("openspec/changes/describe-only"));
        Files.copy(fixture("openspec-basic").resolve("openspec/changes/add-remember-me/proposal.md"),
                workspace.resolve("openspec/changes/describe-only/proposal.md"));
        Path data = tempDir.resolve("empty-quality-data");
        Invocation add = invokeWithData(data, "projects", "add", "--workspace", workspace.toString());
        assertEquals(0, add.exitCode(), add.stderr());
        String projectId = value(add.stdout(), "projectId");
        Invocation sync = invokeWithData(data, "sync", "--project", projectId);
        assertEquals(0, sync.exitCode(), sync.stderr());
        assertTrue(sync.stdout().contains("requirements=0"), sync.stdout());

        Invocation text = invokeWithData(data, "quality", "--project", projectId);
        Invocation json = invokeWithData(data, "--json", "quality", "--project", projectId);

        assertEquals(0, text.exitCode(), text.stderr());
        assertTrue(text.stdout().contains(" requirementCoverage=UNDEFINED_EMPTY_POPULATION "), text.stdout());
        assertTrue(text.stdout().contains(" taskCoverage=UNDEFINED_EMPTY_POPULATION "), text.stdout());
        assertFalse(text.stdout().contains("Coverage=1.0"), text.stdout());
        assertEquals(0, json.exitCode(), json.stderr());
        assertTrue(json.stdout().contains("\"requirementCoverageStatus\":\"UNDEFINED_EMPTY_POPULATION\""), json.stdout());
        assertTrue(json.stdout().contains("\"taskCoverageStatus\":\"UNDEFINED_EMPTY_POPULATION\""), json.stdout());
        assertTrue(json.stdout().contains("\"requirementCoverageRatio\":1.0"),
                "the ratio keeps its wire type and value; the status says it is not a measurement: " + json.stdout());
    }

    /**
     * For a project that has requirements, the quality JSON keeps every field it had, with the same type; the two
     * statuses are the only addition.
     */
    @Test
    void aProjectWithRequirementsKeepsItsQualityFieldsAndGainsOnlyTheStatuses() {
        Path data = tempDir.resolve("measured-quality-data");
        String projectId = value(invokeWithData(
                data, "projects", "add", "--workspace", fixture("openspec-basic").toString()).stdout(), "projectId");
        assertEquals(0, invokeWithData(data, "sync", "--project", projectId).exitCode());

        Invocation json = invokeWithData(data, "--json", "quality", "--project", projectId);
        Invocation text = invokeWithData(data, "quality", "--project", projectId);

        assertEquals(0, json.exitCode(), json.stderr());
        java.util.regex.Matcher metrics = java.util.regex.Pattern.compile("\"metrics\":\\{(.*?)\\},\"findings\"")
                .matcher(json.stdout());
        assertTrue(metrics.find(), json.stdout());
        java.util.Set<String> keys = new java.util.TreeSet<>();
        java.util.regex.Matcher key = java.util.regex.Pattern.compile("\"(\\w+)\":").matcher(
                metrics.group(1).replaceAll("\"findingsBy\\w+\":\\{[^}]*\\}", "\"findingsBy\":0"));
        while (key.find()) {
            keys.add(key.group(1));
        }
        assertEquals(new java.util.TreeSet<>(List.of("acceptanceCoverageStatus", "coveredTasks", "findingsBy",
                        "lifecycleAggregationStatus", "linkedRequirements", "orphanRequirements", "requirementCoverageRatio",
                        "requirementCoverageStatus", "taskCoverageRatio", "taskCoverageStatus", "totalChanges",
                        "totalDesignDecisions", "totalExternalReferences", "totalFindings", "totalRequirements", "totalTasks",
                        "uncoveredTasks")), keys);
        assertTrue(json.stdout().matches("(?s).*\"requirementCoverageRatio\":[0-9.]+,.*"), json.stdout());
        assertTrue(json.stdout().contains("\"requirementCoverageStatus\":\"MEASURED\""), json.stdout());
        assertTrue(json.stdout().matches("(?s).*\"taskCoverageRatio\":[0-9.]+,\"taskCoverageStatus\":\"MEASURED\".*"),
                json.stdout());
        assertTrue(text.stdout().matches("(?s).* requirementCoverage=[0-9.]+ .*"), text.stdout());
    }

    /**
     * No provider derives DEPENDS_ON links, so the test writes a two-requirement cycle into the published snapshot:
     * at depth 1 the link back from the frontier is never recorded, and the traversal reports its depth budget.
     */
    @Test
    void aTruncatedAnalysisNamesItsTruncationInTextAsInJsonAndStillSucceeds() {
        Path data = tempDir.resolve("truncation-data");
        String projectId = value(invokeWithData(
                data, "projects", "add", "--workspace", fixture("openspec-basic").toString()).stdout(), "projectId");
        assertEquals(0, invokeWithData(data, "sync", "--project", projectId).exitCode());
        Invocation requirements = invokeWithData(data, "requirements", "find", "--project", projectId);
        String snapshotId = value(requirements.stdout(), "snapshotId").split(" ")[0];
        List<String> requirementIds = requirements.stdout().lines()
                .filter(line -> !line.startsWith("snapshotId=") && !line.isBlank())
                .map(line -> line.split("\\t", 2)[0])
                .toList();
        assertEquals(2, requirementIds.size(), requirements.stdout());
        try (SqliteTraceabilityStore store = new SqliteTraceabilityStore(data.resolve("morpheus.db"))) {
            store.putLinks(KnowledgeSnapshotId.parse(snapshotId), List.of(
                    dependsOn(requirementIds.get(0), requirementIds.get(1)),
                    dependsOn(requirementIds.get(1), requirementIds.get(0))));
        }
        String changeId = firstIdLine(invokeWithData(data, "changes", "list", "--project", projectId).stdout());

        Invocation text = invokeWithData(
                data, "analyze-change", "--project", projectId, "--change", changeId, "--depth", "1");
        Invocation json = invokeWithData(
                data, "--json", "analyze-change", "--project", projectId, "--change", changeId, "--depth", "1");

        assertEquals(0, text.exitCode(), "a truncated traversal is a partial observation, not a refusal: " + text.stderr());
        assertEquals(0, json.exitCode(), json.stderr());
        assertTrue(json.stdout().contains("\"truncationReason\":\"DEPTH_BUDGET_REACHED:1\""), json.stdout());
        assertEquals(List.of("DEPTH_BUDGET_REACHED:1"), text.stdout().lines()
                .filter(line -> line.startsWith("truncationReason="))
                .map(line -> line.substring("truncationReason=".length()))
                .toList(), text.stdout());
        assertTrue(value(text.stdout(), "warningCodes").contains("TRACEABILITY_TRAVERSAL_TRUNCATED"), text.stdout());
    }

    @Test
    void aCompleteAnalysisNamesItsWarningsAndPrintsNoTruncation() {
        Path data = tempDir.resolve("complete-analysis-data");
        String projectId = value(invokeWithData(
                data, "projects", "add", "--workspace", fixture("openspec-basic").toString()).stdout(), "projectId");
        assertEquals(0, invokeWithData(data, "sync", "--project", projectId).exitCode());
        String changeId = firstIdLine(invokeWithData(data, "changes", "list", "--project", projectId).stdout());

        Invocation text = invokeWithData(
                data, "analyze-change", "--project", projectId, "--change", changeId, "--depth", "2");

        assertEquals(0, text.exitCode(), text.stderr());
        assertFalse(text.stdout().contains("truncationReason="), text.stdout());
        String codes = value(text.stdout(), "warningCodes");
        assertTrue(codes.contains("ACCEPTANCE_CRITERIA_UNAVAILABLE"), text.stdout());
        assertFalse(codes.contains("TRACEABILITY_TRAVERSAL_TRUNCATED"), text.stdout());
        String count = text.stdout().lines().filter(line -> line.startsWith("dependencies=")).findFirst().orElseThrow();
        assertEquals(count.substring(count.indexOf("warnings=") + "warnings=".length()),
                Integer.toString(codes.split(",").length), "one code per counted warning: " + text.stdout());
    }

    @Test
    void theWarningLinesListOneCodePerWarningAndEachDistinctTruncationReasonOnce() {
        List<ChangeAnalysisWarning> warnings = List.of(
                warning(ChangeAnalysisWarningCode.ACCEPTANCE_CRITERIA_UNAVAILABLE, Map.of("status", "UNAVAILABLE")),
                warning(ChangeAnalysisWarningCode.TRACEABILITY_TRAVERSAL_TRUNCATED,
                        Map.of("direction", "DEPENDENCY", "truncationReason", "NODE_BUDGET_REACHED:1000")),
                warning(ChangeAnalysisWarningCode.TRACEABILITY_TRAVERSAL_TRUNCATED,
                        Map.of("direction", "DEPENDENT", "truncationReason", "NODE_BUDGET_REACHED:1000")),
                warning(ChangeAnalysisWarningCode.TRACEABILITY_TRAVERSAL_TRUNCATED,
                        Map.of("direction", "DEPENDENT", "truncationReason", "DEPTH_BUDGET_REACHED:2")));

        assertEquals(List.of(
                        "warningCodes=ACCEPTANCE_CRITERIA_UNAVAILABLE,TRACEABILITY_TRAVERSAL_TRUNCATED,"
                                + "TRACEABILITY_TRAVERSAL_TRUNCATED,TRACEABILITY_TRAVERSAL_TRUNCATED",
                        "truncationReason=DEPTH_BUDGET_REACHED:2",
                        "truncationReason=NODE_BUDGET_REACHED:1000"),
                MorpheusCli.analysisWarningLines(warnings));
        assertEquals(List.of("warningCodes="), MorpheusCli.analysisWarningLines(List.of()));
    }

    private static ChangeAnalysisWarning warning(ChangeAnalysisWarningCode code, Map<String, String> details) {
        return new ChangeAnalysisWarning(code, DiagnosticSeverity.WARNING, Optional.empty(), "message", details);
    }

    private static TraceabilityLink dependsOn(String source, String target) {
        return new TraceabilityLink(
                TraceabilityLinkId.generate(),
                new TraceabilityEntityRef(TraceabilityEntityKind.REQUIREMENT, DomainIdentity.parse(source)),
                TraceabilityRelationType.DEPENDS_ON,
                new TraceabilityEntityRef(TraceabilityEntityKind.REQUIREMENT, DomainIdentity.parse(target)),
                TraceabilityLinkOrigin.EXPLICIT, TraceabilityResolutionState.RESOLVED, Optional.empty(),
                Set.of(EvidenceId.generate()), Instant.parse("2026-09-26T00:00:00Z"));
    }

    @Test
    void missingEntitiesAndInvalidOptionsRemainDistinct() {
        Path data = tempDir.resolve("errors-data");
        Invocation missing = invokeWithData(
                data, "changes", "list", "--project", "01900000-0000-7000-8000-000000000001");
        assertEquals(CliExitCode.NOT_FOUND.code(), missing.exitCode());
        assertTrue(missing.stderr().contains("ACTIVE snapshot"));

        Invocation badDepth = invokeWithData(
                data, "trace-requirement", "--project", "x", "--requirement", "y", "--depth", "0");
        assertEquals(CliExitCode.USAGE.code(), badDepth.exitCode());
    }

    @Test
    void firstSyncBootstrapsThenRepeatedSyncsStayIncrementalUntilForceIsRequested() {
        Path data = tempDir.resolve("data");
        Path fixture = fixture("openspec-basic");

        Invocation add = invokeWithData(data, "projects", "add", "--workspace", fixture.toString());
        assertEquals(0, add.exitCode(), add.stderr());
        String projectId = value(add.stdout(), "projectId");

        Invocation first = invokeWithData(data, "sync", "--project", projectId);
        assertEquals(0, first.exitCode(), first.stderr());
        assertTrue(first.stdout().contains("mode=FULL_REBUILD"), first.stdout());
        assertTrue(first.stdout().contains("published=true"), first.stdout());
        String bootstrapSnapshot = value(first.stdout(), "snapshotId");

        Invocation unchanged = invokeWithData(data, "sync", "--project", projectId);
        assertEquals(0, unchanged.exitCode(), unchanged.stderr());
        assertTrue(unchanged.stdout().contains("mode=INCREMENTAL"), unchanged.stdout());
        assertTrue(unchanged.stdout().contains("fullRebuildReason=none"), unchanged.stdout());
        assertTrue(unchanged.stdout().contains("published=false"), unchanged.stdout());
        assertTrue(unchanged.stdout().contains("requirements=0"), unchanged.stdout());
        assertEquals(bootstrapSnapshot, value(unchanged.stdout(), "snapshotId"));

        Invocation forced = invokeWithData(data, "sync", "--project", projectId, "--force");
        assertEquals(0, forced.exitCode(), forced.stderr());
        assertTrue(forced.stdout().contains("mode=FULL_REBUILD"), forced.stdout());
        assertTrue(forced.stdout().contains("fullRebuildReason=FORCED"), forced.stdout());
        assertTrue(forced.stdout().contains("published=true"), forced.stdout());

        Invocation status = invokeWithData(data, "--json", "sync-status", "--project", projectId);
        assertEquals(0, status.exitCode(), status.stderr());
        assertTrue(status.stdout().contains("\"state\":\"FRESH\""), status.stdout());
        assertTrue(status.stdout().contains("\"lastSuccessfulMode\":\"FULL_REBUILD\""), status.stdout());
    }

    @Test
    void aSyncRefusedForInvalidContentIsAUsageErrorThatNamesTheFileRelativeToTheWorkspace() throws Exception {
        Path data = tempDir.resolve("invalid-content-data");
        Path workspace = Files.createDirectories(tempDir.resolve("untitled-openspec"));
        Files.createDirectories(workspace.resolve("openspec/specs/broken"));
        Files.writeString(workspace.resolve("openspec/config.yaml"), "schema: spec-driven\n");
        Files.writeString(
                workspace.resolve("openspec/specs/broken/spec.md"),
                "## Requirements\n\n### Requirement: Untitled\nThe system SHALL reject an untitled specification.\n");
        Invocation add = invokeWithData(data, "projects", "add", "--workspace", workspace.toString());
        assertEquals(0, add.exitCode(), add.stderr());

        Invocation sync = invokeWithData(data, "sync", "--project", value(add.stdout(), "projectId"));

        assertEquals(CliExitCode.USAGE.code(), sync.exitCode(), sync.stderr());
        assertTrue(sync.stderr().contains("openspec/specs/broken/spec.md: OpenSpec specification has no title"),
                sync.stderr());
        assertFalse(sync.stderr().contains(workspace.toString()), sync.stderr());
    }

    private Invocation invokeWithData(Path data, String... command) {
        String[] args = new String[command.length + 2];
        args[0] = "--data-dir";
        args[1] = data.toString();
        System.arraycopy(command, 0, args, 2, command.length);
        return invoke(args);
    }

    private Invocation invoke(String... args) {
        ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
        ByteArrayOutputStream errBytes = new ByteArrayOutputStream();
        try (PrintStream out = new PrintStream(outBytes, true, StandardCharsets.UTF_8);
             PrintStream err = new PrintStream(errBytes, true, StandardCharsets.UTF_8)) {
            int exitCode = MorpheusMain.run(
                    args,
                    out,
                    err,
                    Map.of(),
                    properties(System.getProperty("os.name"), tempDir.resolve("home")));
            return new Invocation(
                    exitCode,
                    outBytes.toString(StandardCharsets.UTF_8).replace("\r\n", "\n"),
                    errBytes.toString(StandardCharsets.UTF_8).replace("\r\n", "\n"));
        }
    }

    private Properties properties(String osName, Path home) {
        Properties properties = new Properties();
        properties.setProperty("os.name", osName);
        properties.setProperty("user.home", home.toString());
        return properties;
    }

    private Path fixture(String name) {
        Path current = Path.of("").toAbsolutePath().normalize();
        Path fromRoot = current.resolve("experiments/m0/fixtures").resolve(name);
        if (Files.isDirectory(fromRoot)) {
            return fromRoot;
        }
        Path fromModule = current.resolve("../experiments/m0/fixtures").normalize().resolve(name);
        if (Files.isDirectory(fromModule)) {
            return fromModule;
        }
        throw new IllegalStateException("M0 fixture not found: " + name + " from " + current);
    }

    private String value(String output, String key) {
        String prefix = key + "=";
        return output.lines().filter(line -> line.startsWith(prefix)).findFirst()
                .map(line -> line.substring(prefix.length()).trim())
                .orElseThrow(() -> new AssertionError("missing " + key + " in output: " + output));
    }

    private String firstIdLine(String output) {
        return output.lines()
                .filter(line -> !line.startsWith("snapshotId="))
                .filter(line -> !line.isBlank())
                .findFirst()
                .map(line -> line.split("\\t", 2)[0])
                .orElseThrow(() -> new AssertionError("missing item line in output: " + output));
    }

    private record Invocation(int exitCode, String stdout, String stderr) {}
}
