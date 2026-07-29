package com.morpheus.cli;

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

        assertEquals(CliExitCode.SUCCESS.code(), dryRun.exitCode(), dryRun.err());
        assertTrue(dryRun.out().contains("\"dryRun\":true"), dryRun.out());
        assertTrue(auditBefore.out().contains("\"action\":\"CREATE\""), auditBefore.out());
        assertFalse(auditBefore.out().contains("ACTIVATE"), auditBefore.out());
        assertEquals(CliExitCode.SUCCESS.code(), activation.exitCode(), activation.err());
        assertEquals(CliExitCode.SUCCESS.code(), override.exitCode(), override.err());
        assertTrue(evaluated.out().contains("\"originalDecision\":\"UNKNOWN\""), evaluated.out());
        assertTrue(evaluated.out().contains("\"effectiveDecision\":\"BLOCK\""), evaluated.out());
        assertTrue(evaluated.out().contains("explicit exception"), evaluated.out());
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