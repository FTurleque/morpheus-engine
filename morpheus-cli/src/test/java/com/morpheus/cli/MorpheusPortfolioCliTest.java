package com.morpheus.cli;

import com.morpheus.domain.project.ProjectSpecificationId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MorpheusPortfolioCliTest {
    @TempDir
    Path tempDirectory;

    @Test
    void createsPortfolioRegistersProjectsAndReturnsOverview() {
        Result created = run("--json", "portfolio", "create", "--name", "Platform");
        assertEquals(CliExitCode.SUCCESS.code(), created.exitCode(), created.err());
        String portfolioId = firstUuid(created.out());

        ProjectSpecificationId first = ProjectSpecificationId.generate();
        ProjectSpecificationId second = ProjectSpecificationId.generate();
        Result firstRegistration = run(
                "--json", "portfolio", "add-project",
                "--portfolio", portfolioId,
                "--project", first.toString(),
                "--name", "Alpha",
                "--workspace", tempDirectory.resolve("alpha").toString(),
                "--repository", "git:https://example.test/alpha.git",
                "--providers", "openspec,markdown");
        Result secondRegistration = run(
                "--json", "portfolio", "add-project",
                "--portfolio", portfolioId,
                "--project", second.toString(),
                "--name", "Beta",
                "--workspace", tempDirectory.resolve("beta").toString());
        Result overview = run("--json", "portfolio", "overview", "--portfolio", portfolioId);

        assertEquals(CliExitCode.SUCCESS.code(), firstRegistration.exitCode(), firstRegistration.err());
        assertEquals(CliExitCode.SUCCESS.code(), secondRegistration.exitCode(), secondRegistration.err());
        assertEquals(CliExitCode.SUCCESS.code(), overview.exitCode(), overview.err());
        assertTrue(overview.out().contains("\"memberships\""), overview.out());
        assertTrue(overview.out().contains(first.toString()), overview.out());
        assertTrue(overview.out().contains(second.toString()), overview.out());
        assertTrue(overview.out().contains("\"referenceCount\":0"), overview.out());
    }

    @Test
    void traversalRequiresExplicitStartIdentity() {
        Result result = run("portfolio", "traverse", "--portfolio", "missing");

        assertEquals(CliExitCode.USAGE.code(), result.exitCode());
        assertTrue(result.err().contains("--start-project is required"), result.err());
    }

    private Result run(String... args) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ByteArrayOutputStream errors = new ByteArrayOutputStream();
        int exit;
        try (PrintStream out = new PrintStream(output, true, StandardCharsets.UTF_8);
             PrintStream err = new PrintStream(errors, true, StandardCharsets.UTF_8)) {
            Properties properties = new Properties();
            properties.setProperty("user.home", tempDirectory.resolve("home").toString());
            properties.setProperty("os.name", "Linux");
            exit = MorpheusMain.run(args, out, err, Map.of(), properties);
        }
        return new Result(exit, output.toString(StandardCharsets.UTF_8), errors.toString(StandardCharsets.UTF_8));
    }

    private String firstUuid(String json) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("[0-9a-f]{8}-[0-9a-f]{4}-7[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")
                .matcher(json);
        if (!matcher.find()) {
            throw new AssertionError("UUIDv7 not found in " + json);
        }
        return matcher.group();
    }

    private record Result(int exitCode, String out, String err) {
    }
}
