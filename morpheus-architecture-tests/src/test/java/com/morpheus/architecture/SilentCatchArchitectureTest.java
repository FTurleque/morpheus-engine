package com.morpheus.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A catch that says nothing is either explained or absent.
 *
 * <p>The product swallowed failures without a trace: a catch held a comment, or returned a default no caller could
 * tell from "nothing there". Read one by one on 13/10/2026, 224 catch blocks of the main sources did not rethrow; 23
 * of them lost a failure. Those are being made to record it ({@code OperationalRecorder.suppressed}). This rule keeps
 * the next one from arriving unseen.</p>
 *
 * <p>It is textual, and it reads a shape rather than an intention: a catch whose body holds no statement, or whose
 * variable is named {@code ignored}, is silent. Each silent catch is named in one of two closed lists - explained, or
 * not yet recorded - by its file and the types it catches. The rule cannot see a catch that returns a default under a
 * better-named variable; the classification of 13/10/2026 did that by reading, and a review still has to.</p>
 */
class SilentCatchArchitectureTest {
    private static final Pattern CATCH = Pattern.compile("catch \\(([^)]*)\\)\\s*\\{");
    private static final Set<String> SILENT_NAMES = Set.of("ignored", "ignore", "unused");

    /**
     * Silent catches that lose nothing: the exception is the answer to a question, or a clean-up fails on a path whose
     * outcome is already decided. Keyed by {@code File.java#caught types}, with how many and why.
     */
    private static final Map<String, Explained> EXPLAINED = Map.ofEntries(
            Map.entry("QueryValidator.java#NumberFormatException", new Explained(1,
                    "a probe: 'is this literal a number?'; the 'no' becomes a QUERY_VALUE_INVALID diagnostic")),
            Map.entry("SensitiveValueRedactor.java#InvalidPathException", new Explained(1,
                    "a probe: 'is this string a syntactically valid absolute path?'")),
            Map.entry("SqliteConnectionScope.java#IllegalArgumentException", new Explained(1,
                    "a probe: 'is this connection a proxy of this scope?'")),
            Map.entry("LocalWritePermissionHardener.java#IOException | UnsupportedOperationException", new Explained(3,
                    "probes of accounts that may not exist on this machine or this file system, all fail-closed: an "
                            + "identity that cannot be resolved is not trusted, and the refusal that follows is named")),
            Map.entry("TimedBoundedInputReader.java#IOException", new Explained(1,
                    "closing the request body after a read that already timed out or failed; that failure is raised")),
            Map.entry("UpdateDiscoveryService.java#IOException", new Explained(1,
                    "closing the manifest stream after a timeout or an interruption that is raised just after")),
            Map.entry("LocalSourceWatcher.java#IOException", new Explained(1,
                    "closing the watch service when the constructor fails; the constructor's failure propagates")),
            Map.entry("SqliteServerMaintenance.java#IOException", new Explained(1,
                    "closing the lease channel when acquiring the server lease failed; that failure propagates")),
            Map.entry("BoundedStdioServerTransportProvider.java#IOException", new Explained(1,
                    "closing standard input to unblock a read while the transport shuts down; nothing is lost")));

    /**
     * Silent catches that do lose a failure and do not record it yet, with the family they will be counted under.
     * This list only shrinks: an entry leaves it when its catch records, and nothing is added to it.
     */
    private static final Map<String, Explained> NOT_YET_RECORDED = Map.ofEntries(
            Map.entry("MinosMcpCodeGateway.java#RuntimeException", new Explained(1, "mcp-peer-shutdown")),
            Map.entry("NexusMcpContextGateway.java#RuntimeException", new Explained(1, "mcp-peer-shutdown")),
            Map.entry("PeerOperationDeadline.java#RuntimeException", new Explained(1, "mcp-peer-shutdown")),
            Map.entry("BoundedStdioClientTransport.java#RuntimeException", new Explained(1, "mcp-peer-process-tree")),
            Map.entry("MinosMcpCodeGateway.java#IOException", new Explained(1, "staged-artifact-cleanup")),
            Map.entry("NexusMcpContextGateway.java#IOException", new Explained(1, "staged-artifact-cleanup")),
            Map.entry("ExternalJarIntegrity.java#IOException", new Explained(1, "staged-artifact-cleanup")),
            Map.entry("ProviderPluginProbeProcess.java#IOException", new Explained(1, "staged-artifact-cleanup")),
            Map.entry("ProviderPluginProbeProcess.java#RuntimeException", new Explained(2,
                    "plugin-probe-process-tree and plugin-probe-shutdown")),
            Map.entry("ProviderPluginDescendantTermination.java#RuntimeException", new Explained(1,
                    "plugin-probe-shutdown; runs in the probe worker, not in the MORPHEUS process")),
            Map.entry("SqliteDatabaseLease.java#IOException", new Explained(2,
                    "sqlite-lease-release: a failed close on the normal release can leave the operating-system lock "
                            + "held while the lease is reported as returned")));

    private record Explained(int count, String reason) {
    }

    @Test
    void everySilentCatchOfTheProductIsNamedWithItsReason() throws IOException {
        Map<String, Integer> found = silentCatches(mainSources(repositoryRoot()));
        assertTrue(found.values().stream().mapToInt(Integer::intValue).sum() > 5,
                "too few silent catches found: the rule would pass on nothing - " + found);

        Map<String, Integer> named = new TreeMap<>();
        EXPLAINED.forEach((key, explained) -> named.merge(key, explained.count(), Integer::sum));
        NOT_YET_RECORDED.forEach((key, explained) -> named.merge(key, explained.count(), Integer::sum));

        assertEquals(named, found, "a catch that holds no statement, or whose variable is named 'ignored', loses a "
                + "failure unless it says why. Record the failure (OperationalRecorder.suppressed), or name the catch "
                + "in EXPLAINED with its reason; an entry whose catch is gone or now records is removed");
    }

    @Test
    void everyNamedCatchCarriesAReasonAndNoneIsNamedTwice() {
        EXPLAINED.forEach((key, explained) -> {
            assertFalse(explained.reason().isBlank(), key);
            assertFalse(NOT_YET_RECORDED.containsKey(key), () -> key + " is both explained and not yet recorded");
        });
        NOT_YET_RECORDED.forEach((key, explained) -> assertFalse(explained.reason().isBlank(), key));
    }

    @Test
    void theShapeOfASilentCatchIsRecognised() {
        assertEquals(Map.of("A.java#IOException", 1), silentIn("A.java",
                "try { x(); } catch (IOException failure) {\n    // nothing to do\n}\n"));
        assertEquals(Map.of("A.java#IOException | RuntimeException", 1), silentIn("A.java",
                "try { x(); } catch (IOException | RuntimeException ignored) {\n    return false;\n}\n"));
        assertEquals(Map.of("A.java#IOException", 2), silentIn("A.java",
                "try { x(); } catch (IOException a) {\n}\ntry { y(); } catch (final IOException b) {\n}\n"));
        assertEquals(Map.of(), silentIn("A.java",
                "try { x(); } catch (IOException failure) {\n    recorder.suppressed(FAMILY, failure);\n}\n"));
        assertEquals(Map.of(), silentIn("A.java",
                "try { x(); } catch (IOException failure) {\n    throw new IllegalStateException(failure);\n}\n"));
    }

    private static Map<String, Integer> silentIn(String fileName, String source) {
        Map<String, Integer> found = new TreeMap<>();
        collect(fileName, List.of(source.split("\n", -1)), found);
        return found;
    }

    private static Map<String, Integer> silentCatches(List<Path> sources) throws IOException {
        Map<String, Integer> found = new TreeMap<>();
        for (Path source : sources) {
            collect(source.getFileName().toString(), Files.readAllLines(source), found);
        }
        return found;
    }

    private static void collect(String fileName, List<String> lines, Map<String, Integer> found) {
        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index);
            Matcher clause = CATCH.matcher(line);
            if (!clause.find()) {
                continue;
            }
            String[] parameter = clause.group(1).replace("final ", "").trim().split("\\s+");
            String variable = parameter[parameter.length - 1];
            String types = String.join(" ", List.of(parameter).subList(0, parameter.length - 1));
            if (SILENT_NAMES.contains(variable) || holdsNoStatement(lines, index)) {
                found.merge(fileName + "#" + types, 1, Integer::sum);
            }
        }
    }

    /** The body of the catch opened on {@code catchLine} holds nothing but comments, up to the brace that closes it. */
    private static boolean holdsNoStatement(List<String> lines, int catchLine) {
        String opening = lines.get(catchLine);
        String closing = " ".repeat(opening.length() - opening.stripLeading().length()) + "}";
        for (int index = catchLine + 1; index < lines.size(); index++) {
            String line = lines.get(index);
            if (line.startsWith(closing)) {
                return true;
            }
            String statement = line.strip();
            if (!statement.isEmpty() && !statement.startsWith("//") && !statement.startsWith("*")
                    && !statement.startsWith("/*")) {
                return false;
            }
        }
        return true;
    }

    private static List<Path> mainSources(Path root) throws IOException {
        List<Path> sources = new ArrayList<>();
        try (Stream<Path> modules = Files.list(root)) {
            for (Path module : modules.filter(Files::isDirectory).sorted().toList()) {
                Path main = module.resolve("src/main/java");
                if (!module.getFileName().toString().startsWith("morpheus-") || !Files.isDirectory(main)) {
                    continue;
                }
                try (Stream<Path> files = Files.walk(main)) {
                    files.filter(path -> path.toString().endsWith(".java")).sorted().forEach(sources::add);
                }
            }
        }
        return sources;
    }

    private static Path repositoryRoot() throws IOException {
        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null) {
            if (Files.isRegularFile(current.resolve("contracts/public-surfaces.tsv"))
                    && Files.isRegularFile(current.resolve("pom.xml"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IOException("cannot locate MORPHEUS repository root from " + Path.of("").toAbsolutePath());
    }
}
