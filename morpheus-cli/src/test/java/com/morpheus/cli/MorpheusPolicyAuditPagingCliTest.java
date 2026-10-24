package com.morpheus.cli;

import com.morpheus.application.operability.OperationalLogMode;
import com.morpheus.application.operability.OperationalRuntime;
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

/** {@code policy audit} answers with a page, as the HTTP route and the MCP tool do (AUD-PRF-04, ADR-0131). */
class MorpheusPolicyAuditPagingCliTest {
    private static final Pattern UUID_V7 = Pattern.compile(
            "[0-9a-f]{8}-[0-9a-f]{4}-7[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}");
    private static final String RULE = "new|No findings|QUALITY_THRESHOLD|BLOCKER|FINDINGS|LTE|0";

    @TempDir
    Path tempDirectory;

    @Test
    void theAuditIsAPageWithTheDefaultTheNextPageAndTheLastOne() {
        String packId = packWithFiveRecords();

        String everything = page(run("--json", "policy", "audit", "--id", packId));
        String firstTwo = page(run("--json", "policy", "audit", "--id", packId, "--limit", "2"));
        String next = page(run("--json", "policy", "audit", "--id", packId, "--offset", "2", "--limit", "2"));
        String last = page(run("--json", "policy", "audit", "--id", packId, "--offset", "4", "--limit", "2"));

        assertTrue(everything.startsWith("{\"offset\":0,\"limit\":50,\"totalMatches\":5,\"hasMore\":false,\"items\":[{"), everything);
                assertEquals(5, occurrences(everything, "\"action\":"));
        assertTrue(everything.contains("\"action\":\"CREATE\""), everything);
        assertTrue(firstTwo.startsWith("{\"offset\":0,\"limit\":2,\"totalMatches\":5,\"hasMore\":true,"), firstTwo);
        assertEquals(2, occurrences(firstTwo, "\"action\":"));
                assertTrue(next.startsWith("{\"offset\":2,\"limit\":2,\"totalMatches\":5,\"hasMore\":true,"), next);
        assertEquals(2, occurrences(next, "\"action\":"));
        assertFalse(next.contains("\"action\":\"CREATE\""), "the creation record is on the first page only: " + next);
        assertTrue(last.startsWith("{\"offset\":4,\"limit\":2,\"totalMatches\":5,\"hasMore\":false,"), last);
        assertEquals(1, occurrences(last, "\"action\":"));
    }

    @Test
    void aPageOutsideTheBoundsAndAnUnknownPackAreRefused() {
        String packId = packWithFiveRecords();

        for (String limit : List.of("0", String.valueOf(com.morpheus.application.query.PageRequest.MAX_LIMIT + 1))) {
            Result refused = run("--json", "policy", "audit", "--id", packId, "--limit", limit);
            assertEquals(CliExitCode.USAGE.code(), refused.exitCode(), refused.err());
            assertTrue(refused.err().contains("--limit must be between"), refused.err());
        }
        Result negative = run("--json", "policy", "audit", "--id", packId, "--offset", "-1");
        assertEquals(CliExitCode.USAGE.code(), negative.exitCode(), negative.err());
        Result notANumber = run("--json", "policy", "audit", "--id", packId, "--limit", "many");
        assertEquals(CliExitCode.USAGE.code(), notANumber.exitCode(), notANumber.err());
        assertTrue(notANumber.err().contains("--limit must be an integer"), notANumber.err());
        Result unknownOption = run("--json", "policy", "audit", "--id", packId, "--cursor", "x");
        assertEquals(CliExitCode.USAGE.code(), unknownOption.exitCode(), unknownOption.err());
        Result unknownPack = run("--json", "policy", "audit", "--id", "00000000-0000-7000-8000-000000000000");
        assertEquals(CliExitCode.NOT_FOUND.code(), unknownPack.exitCode(), unknownPack.err());
    }

    private String packWithFiveRecords() {
        Result created = run(
                "--json", "policy", "pack", "create", "--name", "Governance", "--rules", RULE,
                "--actor", "alice", "--reason", "baseline");
        assertEquals(CliExitCode.SUCCESS.code(), created.exitCode(), created.err());
        String packId = uuids(created.out()).getFirst();
        String ruleId = uuids(run("--json", "policy", "pack", "versions", "--id", packId).out()).get(2);
        for (int revision = 1; revision <= 4; revision++) {
            Result updated = run(
                    "--json", "policy", "pack", "update", "--id", packId, "--expected-revision",
                    String.valueOf(revision), "--name", "Governance " + revision,
                    "--rules", ruleId + "|No findings|QUALITY_THRESHOLD|BLOCKER|FINDINGS|LTE|0",
                    "--actor", "alice", "--reason", "update " + revision);
            assertEquals(CliExitCode.SUCCESS.code(), updated.exitCode(), updated.err());
        }
        return packId;
    }

    private static String page(Result result) {
        assertEquals(CliExitCode.SUCCESS.code(), result.exitCode(), result.err());
        return result.out().trim();
    }

    private static int occurrences(String text, String needle) {
        int count = 0;
        for (int at = text.indexOf(needle); at >= 0; at = text.indexOf(needle, at + needle.length())) {
            count++;
        }
        return count;
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
            exit = MorpheusMain.run(OperationalRuntime.of(OperationalLogMode.OFF), args.toArray(String[]::new), out, err,
                    Map.of(), properties);
        }
        return new Result(exit, output.toString(StandardCharsets.UTF_8), errors.toString(StandardCharsets.UTF_8));
    }

    private static List<String> uuids(String text) {
        Matcher matcher = UUID_V7.matcher(text);
        List<String> values = new ArrayList<>();
        while (matcher.find()) {
            values.add(matcher.group());
        }
        if (values.isEmpty()) {
            throw new AssertionError("UUIDv7 not found in " + text);
        }
        return List.copyOf(values);
    }

    private record Result(int exitCode, String out, String err) {
    }
}
