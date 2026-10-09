package com.morpheus.cli;

import com.morpheus.application.policy.PolicyEvaluation;
import com.morpheus.application.query.dsl.ProjectQueryScope;
import com.morpheus.application.query.dsl.QueryDefinition;
import com.morpheus.application.query.dsl.QueryDefinitionCodec;
import com.morpheus.application.query.dsl.QueryEntityType;
import com.morpheus.application.query.dsl.QueryPage;
import com.morpheus.domain.change.ChangeId;
import com.morpheus.domain.project.ProjectSpecificationId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MorpheusPolicyCliTest {
    private static final Pattern UUID_V7 = Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-7[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}");

    @TempDir
    Path tempDirectory;

    @Test
    void policyPackVersioningAndStaleCasAreVisibleOnCli() {
        Result created = run(
                "--json", "policy", "pack", "create",
                "--name", "Governance",
                "--rules", "new|No findings|QUALITY_THRESHOLD|BLOCKER|FINDINGS|LTE|0",
                "--actor", "alice", "--reason", "baseline");
        assertEquals(CliExitCode.SUCCESS.code(), created.exitCode(), created.err());
        String packId = uuids(created.out()).getFirst();

        Result versions1 = run("--json", "policy", "pack", "versions", "--id", packId);
        List<String> identities = uuids(versions1.out());
        String ruleId = identities.get(2);

        Result updated = run(
                "--json", "policy", "pack", "update",
                "--id", packId, "--expected-revision", "1", "--name", "Governance v2",
                "--rules", ruleId + "|No findings|QUALITY_THRESHOLD|BLOCKER|FINDINGS|LTE|0",
                "--actor", "alice", "--reason", "version two");
        Result stale = run(
                "--json", "policy", "pack", "update",
                "--id", packId, "--expected-revision", "1", "--name", "stale",
                "--rules", ruleId + "|No findings|QUALITY_THRESHOLD|BLOCKER|FINDINGS|LTE|0",
                "--actor", "bob", "--reason", "stale write");
        Result versions2 = run("--json", "policy", "pack", "versions", "--id", packId);

        assertEquals(CliExitCode.SUCCESS.code(), updated.exitCode(), updated.err());
        assertTrue(updated.out().contains("\"revision\":2"), updated.out());
        assertEquals(CliExitCode.STATE_ERROR.code(), stale.exitCode(), stale.err());
        assertTrue(stale.err().contains("stale policy pack revision"), stale.err());
        assertTrue(versions2.out().contains("\"versionNumber\":1"), versions2.out());
        assertTrue(versions2.out().contains("\"versionNumber\":2"), versions2.out());
        assertFalse(stale.err().contains("\tat "), stale.err());
    }

    @Test
    void dryRunIsReadOnlyAndExplicitOverridePreservesOriginalDecision() {
        Result created = run(
                "--json", "policy", "pack", "create",
                "--name", "Governance",
                "--rules", "new|No findings|QUALITY_THRESHOLD|BLOCKER|FINDINGS|LTE|0",
                "--actor", "alice", "--reason", "baseline");
        String packId = uuids(created.out()).getFirst();
        Result versions = run("--json", "policy", "pack", "versions", "--id", packId);
        List<String> identities = uuids(versions.out());
        String versionId = identities.get(1);
        String ruleId = identities.get(2);
        String projectId = ProjectSpecificationId.generate().toString();

        Result dryRun = run(
                "--json", "policy", "dry-run", "--id", packId, "--version", versionId,
                "--project", projectId);
        Result auditBefore = run("--json", "policy", "audit", "--id", packId);
        Result activation = run(
                "--json", "policy", "activate", "--id", packId, "--version", versionId,
                "--project", projectId, "--expected-revision", "0",
                "--actor", "alice", "--reason", "enable");
        Result override = run(
                "--json", "policy", "override", "put", "--id", packId, "--rule", ruleId,
                "--mode", "FORCE_BLOCK", "--project", projectId, "--expected-revision", "0",
                "--actor", "security", "--reason", "explicit exception");
        Result evaluated = run("--json", "policy", "evaluate", "--id", packId, "--project", projectId);

        assertEquals(CliExitCode.STATE_ERROR.code(), dryRun.exitCode(), dryRun.err());
        assertTrue(dryRun.out().contains("\"dryRun\":true"), dryRun.out());
        assertTrue(auditBefore.out().contains("\"action\":\"CREATE\""), auditBefore.out());
        assertFalse(auditBefore.out().contains("ACTIVATE"), auditBefore.out());
        assertEquals(CliExitCode.SUCCESS.code(), activation.exitCode(), activation.err());
        assertEquals(CliExitCode.SUCCESS.code(), override.exitCode(), override.err());
        assertEquals(CliExitCode.STATE_ERROR.code(), evaluated.exitCode(), evaluated.err());
        assertTrue(evaluated.out().contains("\"originalDecision\":\"UNKNOWN\""), evaluated.out());
        assertTrue(evaluated.out().contains("\"effectiveDecision\":\"BLOCK\""), evaluated.out());
        assertTrue(evaluated.out().contains("explicit exception"), evaluated.out());
    }

    /** An empty --id used to be read as no pack, so evaluate ran every active pack of the scope instead. */
    @Test
    void anEmptyPackFilterIsRefusedInsteadOfEvaluatingEveryPack() {
        String projectId = com.morpheus.domain.project.ProjectSpecificationId.generate().toString();

        Result result = run("--json", "policy", "evaluate", "--project", projectId, "--id", "");

        assertEquals(CliExitCode.USAGE.code(), result.exitCode(), result.err());
        assertTrue(result.err().contains("--id requires a non-blank value"), result.err());
    }

    @Test
    void packCreateAcceptsConstraintAndLifecycleGuardRuleKinds() {
        String changeId = ChangeId.generate().toString();
        Result created = run(
                "--json", "policy", "pack", "create",
                "--name", "Guards",
                "--rules", "new|Constraint guard|CONSTRAINT_GUARD|WARNING|" + changeId + "|COMPLETED"
                        + ";;new|Lifecycle guard|LIFECYCLE_GUARD|INFO|" + changeId + "|DRAFT|VERIFYING",
                "--actor", "alice", "--reason", "guard baseline");
        assertEquals(CliExitCode.SUCCESS.code(), created.exitCode(), created.err());
        String packId = uuids(created.out()).getFirst();

        Result versions = run("--json", "policy", "pack", "versions", "--id", packId);

        assertEquals(CliExitCode.SUCCESS.code(), versions.exitCode(), versions.err());
        assertTrue(versions.out().contains("\"kind\":\"CONSTRAINT_GUARD\""), versions.out());
        assertTrue(versions.out().contains("\"kind\":\"LIFECYCLE_GUARD\""), versions.out());
    }

    @Test
    void packCreateAcceptsQueryAssertionRuleKind() {
        QueryDefinition query = QueryDefinition.all(
                new ProjectQueryScope(ProjectSpecificationId.generate()), QueryEntityType.REQUIREMENT, QueryPage.first(50));
        String encodedQuery = new QueryDefinitionCodec().encode(query);

        Result created = run(
                "--json", "policy", "pack", "create",
                "--name", "QueryGuard",
                "--rules", "new|Query assertion|QUERY_ASSERTION|WARNING|" + encodedQuery + "|GTE|0",
                "--actor", "alice", "--reason", "query baseline");
        assertEquals(CliExitCode.SUCCESS.code(), created.exitCode(), created.err());
        String packId = uuids(created.out()).getFirst();

        Result versions = run("--json", "policy", "pack", "versions", "--id", packId);

        assertEquals(CliExitCode.SUCCESS.code(), versions.exitCode(), versions.err());
        assertTrue(versions.out().contains("\"kind\":\"QUERY_ASSERTION\""), versions.out());
    }

    @Test
    void explicitDataAndConfigDirectoriesAreBothConsumedBeforeActionValidation() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ByteArrayOutputStream errors = new ByteArrayOutputStream();
        int exit;
        try (PrintStream out = new PrintStream(output, true, StandardCharsets.UTF_8);
             PrintStream err = new PrintStream(errors, true, StandardCharsets.UTF_8)) {
            Properties properties = new Properties();
            properties.setProperty("user.home", tempDirectory.resolve("home").toString());
            properties.setProperty("os.name", "Linux");
            exit = MorpheusMain.run(new String[]{
                    "--data-dir", tempDirectory.resolve("data").toString(),
                    "--config-dir", tempDirectory.resolve("config").toString(),
                    "--db", tempDirectory.resolve("morpheus.db").toString(),
                    "policy"}, out, err, Map.of(), properties);
        }
        assertEquals(CliExitCode.USAGE.code(), exit);
        assertTrue(errors.toString(StandardCharsets.UTF_8).contains("policy action is required"));
    }

    @Test
    void aPolicyDecisionReachesTheExitCodeAndTheJsonIsStillPrinted() {
        Scope pack = createPack();

        Result unknown = run("--json", "policy", "evaluate", "--id", pack.packId(), "--project", pack.activate());
        Result passed = run("--json", "policy", "evaluate", "--id", pack.packId(), "--project", pack.activateWith("DISABLE"));
        Result warned = run("--json", "policy", "evaluate", "--id", pack.packId(), "--project", pack.activateWith("FORCE_WARN"));
        Result blocked = run("--json", "policy", "evaluate", "--id", pack.packId(), "--project", pack.activateWith("FORCE_BLOCK"));

        assertEquals(CliExitCode.STATE_ERROR.code(), unknown.exitCode(), unknown.err());
        assertTrue(unknown.out().contains("\"effectiveDecision\":\"UNKNOWN\""), unknown.out());
        assertEquals(CliExitCode.SUCCESS.code(), passed.exitCode(), passed.err());
        assertTrue(passed.out().contains("\"effectiveDecision\":\"PASS\""), passed.out());
        assertEquals(CliExitCode.SUCCESS.code(), warned.exitCode(), warned.err());
        assertTrue(warned.out().contains("\"effectiveDecision\":\"WARN\""), warned.out());
        assertEquals(CliExitCode.STATE_ERROR.code(), blocked.exitCode(), blocked.err());
        assertTrue(blocked.out().contains("\"effectiveDecision\":\"BLOCK\""), blocked.out());
    }

    @Test
    void theGovernanceReportOverAllActivePacksMapsItsDecisionToTheExitCode() {
        Scope pack = createPack();
        String unknownProject = pack.activate();
        String blockedProject = pack.activateWith("FORCE_BLOCK");
        String passedProject = pack.activateWith("DISABLE");
        String emptyProject = ProjectSpecificationId.generate().toString();

        Result unknown = run("--json", "policy", "evaluate", "--project", unknownProject);
        Result blocked = run("--json", "policy", "evaluate", "--project", blockedProject);
        Result passed = run("--json", "policy", "evaluate", "--project", passedProject);
        Result nothingActive = run("--json", "policy", "evaluate", "--project", emptyProject);

        assertEquals(CliExitCode.STATE_ERROR.code(), unknown.exitCode(), unknown.err());
        assertTrue(unknown.out().contains("\"decision\":\"UNKNOWN\""), unknown.out());
        assertEquals(CliExitCode.STATE_ERROR.code(), blocked.exitCode(), blocked.err());
        assertTrue(blocked.out().contains("\"decision\":\"BLOCK\""), blocked.out());
        assertEquals(CliExitCode.SUCCESS.code(), passed.exitCode(), passed.err());
        assertEquals(CliExitCode.SUCCESS.code(), nothingActive.exitCode(), nothingActive.err());
    }

    @Test
    void aDryRunOfAnUnknownDecisionIsNotASuccess() {
        Scope pack = createPack();

        Result dryRun = run("--json", "policy", "dry-run", "--id", pack.packId(), "--version", pack.versionId(),
                "--project", ProjectSpecificationId.generate().toString());

        assertEquals(CliExitCode.STATE_ERROR.code(), dryRun.exitCode(), dryRun.err());
        assertTrue(dryRun.out().contains("\"decision\":\"UNKNOWN\""), dryRun.out());
    }

    @Test
    void aSuccessfulConfigurationActionStillExitsZeroWhateverTheDecisionWouldBe() {
        Scope pack = createPack();
        String project = pack.activateWith("FORCE_BLOCK");

        Result activations = run("--json", "policy", "activations", "--project", project);
        Result overrides = run("--json", "policy", "override", "list", "--project", project);
        Result audit = run("--json", "policy", "audit", "--id", pack.packId());
        Result list = run("--json", "policy", "pack", "list");

        assertEquals(CliExitCode.SUCCESS.code(), activations.exitCode(), activations.err());
        assertEquals(CliExitCode.SUCCESS.code(), overrides.exitCode(), overrides.err());
        assertEquals(CliExitCode.SUCCESS.code(), audit.exitCode(), audit.err());
        assertEquals(CliExitCode.SUCCESS.code(), list.exitCode(), list.err());
    }

    @Test
    void everyPolicyDecisionHasAnExplicitExitCode() {
        for (PolicyEvaluation.Decision decision : PolicyEvaluation.Decision.values()) {
            boolean letsThrough = decision == PolicyEvaluation.Decision.PASS
                    || decision == PolicyEvaluation.Decision.WARN;
            assertEquals(letsThrough ? CliExitCode.SUCCESS : CliExitCode.STATE_ERROR,
                    MorpheusPolicyCli.exitCodeOf(decision), decision.name());
        }
    }

    private Scope createPack() {
        Result created = run(
                "--json", "policy", "pack", "create", "--name", "Exit",
                "--rules", "new|No findings|QUALITY_THRESHOLD|BLOCKER|FINDINGS|LTE|0",
                "--actor", "alice", "--reason", "baseline");
        String packId = uuids(created.out()).getFirst();
        List<String> identities = uuids(run("--json", "policy", "pack", "versions", "--id", packId).out());
        return new Scope(packId, identities.get(1), identities.get(2));
    }

    private final class Scope {
        private final String packId;
        private final String versionId;
        private final String ruleId;

        private Scope(String packId, String versionId, String ruleId) {
            this.packId = packId;
            this.versionId = versionId;
            this.ruleId = ruleId;
        }

        String packId() {
            return packId;
        }

        String versionId() {
            return versionId;
        }

        String activate() {
            String project = ProjectSpecificationId.generate().toString();
            Result activation = run("--json", "policy", "activate", "--id", packId, "--version", versionId,
                    "--project", project, "--expected-revision", "0", "--actor", "alice", "--reason", "enable");
            assertEquals(CliExitCode.SUCCESS.code(), activation.exitCode(), activation.err());
            return project;
        }

        String activateWith(String mode) {
            String project = activate();
            Result override = run("--json", "policy", "override", "put", "--id", packId, "--rule", ruleId,
                    "--mode", mode, "--project", project, "--expected-revision", "0",
                    "--actor", "security", "--reason", "explicit");
            assertEquals(CliExitCode.SUCCESS.code(), override.exitCode(), override.err());
            return project;
        }
    }

    private Result run(String... rawArgs) {
        List<String> args = new ArrayList<>();
        args.add("--db");
        args.add(tempDirectory.resolve("morpheus.db").toString());
        args.addAll(List.of(rawArgs));
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ByteArrayOutputStream errors = new ByteArrayOutputStream();
        int exit;
        try (PrintStream out = new PrintStream(output, true, StandardCharsets.UTF_8);
             PrintStream err = new PrintStream(errors, true, StandardCharsets.UTF_8)) {
            Properties properties = new Properties();
            properties.setProperty("user.home", tempDirectory.resolve("home").toString());
            properties.setProperty("os.name", "Linux");
            exit = MorpheusMain.run(args.toArray(String[]::new), out, err, Map.of(), properties);
        }
        return new Result(exit, output.toString(StandardCharsets.UTF_8), errors.toString(StandardCharsets.UTF_8));
    }

    private List<String> uuids(String text) {
        Matcher matcher = UUID_V7.matcher(text);
        List<String> values = new ArrayList<>();
        while (matcher.find()) values.add(matcher.group());
        if (values.isEmpty()) throw new AssertionError("UUIDv7 not found in " + text);
        return List.copyOf(values);
    }

    private record Result(int exitCode, String out, String err) {}
}