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
import static org.junit.jupiter.api.Assertions.assertFalse;
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
    void observesFreshnessAndTraversesWithExplicitDirection() {
        Result created = run("--json", "portfolio", "create", "--name", "Freshness");
        assertEquals(CliExitCode.SUCCESS.code(), created.exitCode(), created.err());
        String portfolioId = firstUuid(created.out());
        ProjectSpecificationId projectId = ProjectSpecificationId.generate();
        run("--json", "portfolio", "add-project",
                "--portfolio", portfolioId, "--project", projectId.toString(), "--name", "Alpha");

        Result freshness = run(
                "--json", "portfolio", "freshness",
                "--portfolio", portfolioId, "--project", projectId.toString(), "--state", "stale");
        assertEquals(CliExitCode.SUCCESS.code(), freshness.exitCode(), freshness.err());
        assertTrue(freshness.out().contains("\"STALE\""), freshness.out());

        Result traversal = run(
                "portfolio", "traverse", "--portfolio", portfolioId,
                "--start-project", projectId.toString(), "--start-type", "PROJECT",
                "--start-id", com.morpheus.domain.identity.DomainIdentity.generate().toString(),
                "--direction", "outgoing");
        assertEquals(CliExitCode.SUCCESS.code(), traversal.exitCode(), traversal.err());
    }

    @Test
    void explicitDataAndConfigDirectoriesAreBothConsumedBeforeActionValidation() {
        Result result = run(
                "--data-dir", tempDirectory.resolve("data").toString(),
                "--config-dir", tempDirectory.resolve("config").toString(),
                "--db", tempDirectory.resolve("morpheus.db").toString(),
                "portfolio");

        assertEquals(CliExitCode.USAGE.code(), result.exitCode());
        assertTrue(result.err().contains("portfolio requires an action"), result.err());
    }

    @Test
    void traversalRequiresExplicitStartIdentity() {
        Result created = run("--json", "portfolio", "create", "--name", "Traversal");
        assertEquals(CliExitCode.SUCCESS.code(), created.exitCode(), created.err());
        String portfolioId = firstUuid(created.out());

        Result result = run("portfolio", "traverse", "--portfolio", portfolioId);

        assertEquals(CliExitCode.USAGE.code(), result.exitCode());
        assertTrue(result.err().contains("--start-project is required"), result.err());
    }

    @Test
    void aMisspelledOptionOnAWriteActionIsRefusedAndNamed() {
        String portfolioId = firstUuid(run("--json", "portfolio", "create", "--name", "Typos").out());
        String projectId = ProjectSpecificationId.generate().toString();

        Result addProject = run("--json", "portfolio", "add-project",
                "--portfolio", portfolioId, "--project", projectId, "--name", "Alpha", "--workspac", "/src");
        Result freshness = run("--json", "portfolio", "freshness",
                "--portfolio", portfolioId, "--project", projectId, "--state", "stale", "--revison", "r1");
        Result addReference = run("--json", "portfolio", "add-reference",
                "--portfolio", portfolioId,
                "--source-project", projectId, "--source-type", "REQUIREMENT",
                "--source-id", com.morpheus.domain.identity.DomainIdentity.generate().toString(),
                "--target-project", projectId, "--target-type", "REQUIREMENT",
                "--target-id", com.morpheus.domain.identity.DomainIdentity.generate().toString(),
                "--relation", "DEPENDS_ON", "--provider", "openspec", "--evidences", "E");

        assertEquals(CliExitCode.USAGE.code(), addProject.exitCode(), addProject.err());
        assertTrue(addProject.err().contains("unknown option: --workspac"), addProject.err());
        assertEquals(CliExitCode.USAGE.code(), freshness.exitCode(), freshness.err());
        assertTrue(freshness.err().contains("unknown option: --revison"), freshness.err());
        assertEquals(CliExitCode.USAGE.code(), addReference.exitCode(), addReference.err());
        assertTrue(addReference.err().contains("unknown option: --evidences"), addReference.err());
        Result members = run("--json", "portfolio", "members", "--portfolio", portfolioId);
        assertFalse(members.out().contains(projectId), "a refused write must not persist the membership");
    }

    @Test
    void referencesRefusesAMisspelledProjectFilterInsteadOfListingEverything() {
        String portfolioId = firstUuid(run("--json", "portfolio", "create", "--name", "Filter").out());
        String projectId = ProjectSpecificationId.generate().toString();
        run("--json", "portfolio", "add-project", "--portfolio", portfolioId, "--project", projectId, "--name", "A");

        Result misspelled = run("--json", "portfolio", "references", "--portfolio", portfolioId, "--projet", projectId);
        Result spelled = run("--json", "portfolio", "references", "--portfolio", portfolioId, "--project", projectId);

        assertEquals(CliExitCode.USAGE.code(), misspelled.exitCode(), misspelled.err());
        assertTrue(misspelled.err().contains("unknown option: --projet"), misspelled.err());
        assertEquals(CliExitCode.SUCCESS.code(), spelled.exitCode(), spelled.err());
    }

    /**
     * An option given empty used to be read as an option not given: the project filter vanished and every reference
     * of the portfolio came back, and add-project persisted a membership without a workspace, exit code 0 each time.
     */
    @Test
    void anEmptyOrBlankValueIsRefusedAndNamedInsteadOfReadAsAnAbsentOption() {
        String portfolioId = firstUuid(run("--json", "portfolio", "create", "--name", "Blank").out());
        String projectId = ProjectSpecificationId.generate().toString();

        Result emptyFilter = run("--json", "portfolio", "references", "--portfolio", portfolioId, "--project", "");
        Result blankFilter = run("--json", "portfolio", "references", "--portfolio", portfolioId, "--project", "   ");
        Result emptyWorkspace = run("--json", "portfolio", "add-project",
                "--portfolio", portfolioId, "--project", projectId, "--name", "Alpha", "--workspace", "");

        for (Result refused : java.util.List.of(emptyFilter, blankFilter)) {
            assertEquals(CliExitCode.USAGE.code(), refused.exitCode(), refused.err());
            assertTrue(refused.err().contains("--project requires a non-blank value"), refused.err());
        }
        assertEquals(CliExitCode.USAGE.code(), emptyWorkspace.exitCode(), emptyWorkspace.err());
        assertTrue(emptyWorkspace.err().contains("--workspace requires a non-blank value"), emptyWorkspace.err());
        Result members = run("--json", "portfolio", "members", "--portfolio", portfolioId);
        assertEquals(CliExitCode.SUCCESS.code(), members.exitCode(), members.err());
        assertFalse(members.out().contains(projectId), "a refused write must not persist the membership");

        Result withoutWorkspace = run("--json", "portfolio", "add-project",
                "--portfolio", portfolioId, "--project", projectId, "--name", "Alpha");
        assertEquals(CliExitCode.SUCCESS.code(), withoutWorkspace.exitCode(), withoutWorkspace.err());
        assertTrue(run("--json", "portfolio", "members", "--portfolio", portfolioId).out().contains(projectId),
                "omitting the option still registers the membership without a workspace");
    }

    @Test
    void anUnknownActionIsReportedAsSuchEvenWithOptions() {
        Result result = run("portfolio", "frobnicate", "--anything", "x");

        assertEquals(CliExitCode.USAGE.code(), result.exitCode());
        assertTrue(result.err().contains("unknown portfolio action: frobnicate"), result.err());
    }

    @Test
    void everyActionRefusesAnOptionNoActionReads() {
        String portfolioId = firstUuid(run("--json", "portfolio", "create", "--name", "Bogus").out());
        String projectId = ProjectSpecificationId.generate().toString();

        for (String action : java.util.List.of("create", "add-project", "missing", "freshness", "add-reference",
                "list", "overview", "members", "references", "conflicts", "traverse")) {
            Result result = run("portfolio", action, "--portfolio", portfolioId, "--project", projectId,
                    "--bogus", "x");

            assertEquals(CliExitCode.USAGE.code(), result.exitCode(), action + ": " + result.err());
            assertTrue(result.err().contains("unknown option: --"), action + ": " + result.err());
        }
    }

    @Test
    void everyActionAcceptsEachOptionItReads() {
        String portfolioId = firstUuid(run("--json", "portfolio", "create", "--name", "Reads").out());
        String projectId = ProjectSpecificationId.generate().toString();
        run("--json", "portfolio", "add-project", "--portfolio", portfolioId, "--project", projectId, "--name", "A");

        assertEquals(0, run("portfolio", "list", "--offset", "0", "--limit", "5").exitCode());
        assertEquals(0, run("portfolio", "members", "--portfolio", portfolioId, "--offset", "0", "--limit", "5")
                .exitCode());
        assertEquals(0, run("portfolio", "references", "--portfolio", portfolioId, "--offset", "0", "--limit", "5")
                .exitCode());
        assertEquals(0, run("portfolio", "conflicts", "--portfolio", portfolioId).exitCode());
        assertEquals(0, run("portfolio", "missing", "--portfolio", portfolioId, "--project", projectId).exitCode());
        assertEquals(0, run("portfolio", "traverse", "--portfolio", portfolioId, "--start-project", projectId,
                "--start-type", "PROJECT", "--start-id",
                com.morpheus.domain.identity.DomainIdentity.generate().toString(),
                "--depth", "2", "--nodes", "10", "--links", "10", "--direction", "both").exitCode());
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
