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
import static org.junit.jupiter.api.Assertions.assertTrue;

class MorpheusPolicyManagementCliTest {
    private static final Pattern UUID_V7 = Pattern.compile(
            "[0-9a-f]{8}-[0-9a-f]{4}-7[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}");

    @TempDir
    Path temporaryDirectory;

    @Test
    void activationRevisionCanBeReadAndOverrideCanBeRemoved() {
        Result created = run(
                "--json", "policy", "pack", "create",
                "--name", "Governance",
                "--rules", "new|No findings|QUALITY_THRESHOLD|BLOCKER|FINDINGS|LTE|0",
                "--actor", "alice", "--reason", "baseline");
        String packId = uuids(created.out()).getFirst();
        Result versions = run("--json", "policy", "pack", "versions", "--id", packId);
        List<String> ids = uuids(versions.out());
        String versionId = ids.get(1);
        String ruleId = ids.get(2);
        String projectId = ProjectSpecificationId.generate().toString();

        Result activated = run(
                "--json", "policy", "activate", "--id", packId, "--version", versionId,
                "--project", projectId, "--expected-revision", "0",
                "--actor", "alice", "--reason", "enable");
        Result activations = run("--json", "policy", "activations", "--project", projectId);
        Result override = run(
                "--json", "policy", "override", "put", "--id", packId, "--rule", ruleId,
                "--mode", "FORCE_BLOCK", "--project", projectId, "--expected-revision", "0",
                "--actor", "security", "--reason", "temporary");
        Result removed = run(
                "--json", "policy", "override", "remove", "--id", packId, "--rule", ruleId,
                "--project", projectId, "--expected-revision", "1",
                "--actor", "security", "--reason", "waiver expired");
        Result evaluated = run("--json", "policy", "evaluate", "--id", packId, "--project", projectId);
        Result audit = run("--json", "policy", "audit", "--id", packId);

        assertEquals(CliExitCode.SUCCESS.code(), activated.exitCode(), activated.err());
        assertEquals(CliExitCode.SUCCESS.code(), activations.exitCode(), activations.err());
        assertTrue(activations.out().contains("\"revision\":1"), activations.out());
        assertTrue(activations.out().contains(versionId), activations.out());
        assertEquals(CliExitCode.SUCCESS.code(), override.exitCode(), override.err());
        assertEquals(CliExitCode.SUCCESS.code(), removed.exitCode(), removed.err());
        assertTrue(removed.out().contains("\"removed\":true"), removed.out());
        assertTrue(evaluated.out().contains("\"originalDecision\":\"UNKNOWN\""), evaluated.out());
        assertTrue(evaluated.out().contains("\"effectiveDecision\":\"UNKNOWN\""), evaluated.out());
        assertTrue(audit.out().contains("REMOVE_OVERRIDE"), audit.out());
        assertTrue(audit.out().contains("waiver expired"), audit.out());
    }

    private Result run(String... rawArgs) {
        List<String> args = new ArrayList<>();
        args.add("--db");
        args.add(temporaryDirectory.resolve("morpheus.db").toString());
        args.addAll(List.of(rawArgs));
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ByteArrayOutputStream errors = new ByteArrayOutputStream();
        int exit;
        try (PrintStream out = new PrintStream(output, true, StandardCharsets.UTF_8);
             PrintStream err = new PrintStream(errors, true, StandardCharsets.UTF_8)) {
            Properties properties = new Properties();
            properties.setProperty("user.home", temporaryDirectory.resolve("home").toString());
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