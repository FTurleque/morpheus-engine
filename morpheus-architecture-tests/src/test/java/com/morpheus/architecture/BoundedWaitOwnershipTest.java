package com.morpheus.architecture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Bounded waiting stays in one place per module, and every expiry says what it saw.
 *
 * <p>Twelve helpers had grown across the test sources, three of them near-identical copies of one
 * {@code awaitCondition(Duration, BooleanSupplier)} differing only in their poll interval. Nothing was wrong
 * with any single copy the day it was written; what went wrong is that they were edited separately afterwards,
 * until two helpers with the same name did opposite things -- one copy of {@code awaitSettledStatus} threw on
 * expiry while the other returned the unsettled status and let a later assertion fail for it.</p>
 *
 * <p>They also all expired with the same sentence: {@code "condition was not satisfied within PT60S"}. That
 * names nothing that was observed, so every one of these failures on a loaded runner had to be reconstructed
 * by hand. The polling was never the defect -- for a state that lives in another process or is only readable
 * over HTTP there is nothing to await, and a bounded poll is the honest shape -- the silence was.</p>
 *
 * <p>Extracting {@code BoundedWait} fixed the copies that existed. This refuses the next one, scanning text
 * the way {@code CoverageScaleSeparationTest} does, because a helper that compiles perfectly well and simply
 * should not exist cannot be caught any other way.</p>
 */
class BoundedWaitOwnershipTest {

    /** The per-module owners. Deliberately two, not one: see {@link #theTwoOwnersStayInStep()}. */
    private static final List<Path> OWNERS = List.of(
            Path.of("morpheus-api/src/test/java/com/morpheus/api/BoundedWait.java"),
            Path.of("morpheus-mcp-transport/src/test/java/com/morpheus/integration/mcp/BoundedWait.java"));

    /**
     * A method declaration whose name opens with {@code await}: an indented line carrying at least one
     * modifier, a return type, then the name. A call site never opens a line that way, so
     * {@code BoundedWait.until(...)} inside a test body is left alone -- which is the point.
     */
    private static final Pattern AWAIT_DECLARATION = Pattern.compile(
            "(?m)^ +(?:(?:private|protected|public|static|final|abstract)[ ]+)+"
                    + "[A-Za-z0-9_<>,.\\[\\]? ]+[ ]+(await[A-Za-z0-9_]*)[ ]*\\(");

    private static final Pattern SLEEP_CALL = Pattern.compile("\\.sleep\\(|Thread\\.sleep\\(");

    private static final Pattern SUPPRESSION = Pattern.compile("@SuppressWarnings\\(\"java:S2925\"\\)");

    private static final List<String> CATEGORIES = List.of(
            "category one of three", "category two of three", "category three of three");

    /**
     * A wait helper that sleeps is a wait helper: it must delegate rather than roll its own deadline.
     *
     * <p>An {@code await} method that does not sleep is left alone on purpose. {@code awaitClosed} and
     * {@code awaitRelease} block on a {@code CountDownLatch}, and {@code awaitChildReady} blocks on a read --
     * those wait on an event that really exists, which is the one case where a poll would be the wrong answer.
     * The rule catches the case that actually recurred: someone writing the deadline loop again.</p>
     */
    @Test
    void noTestClassRollsItsOwnBoundedWait() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (Path source : testSources()) {
            if (isOwner(source)) {
                continue;
            }
            String text = Files.readString(source);
            Matcher declaration = AWAIT_DECLARATION.matcher(text);
            while (declaration.find()) {
                String body = methodBody(text, declaration.end());
                if (SLEEP_CALL.matcher(body).find()) {
                    offenders.add(source.getFileName() + " declares " + declaration.group(1)
                            + "(...) with its own sleep loop");
                }
            }
        }

        assertEquals(List.of(), offenders,
                "a bounded wait belongs to the module's BoundedWait; a private copy is how two helpers with "
                        + "the same name came to behave in opposite ways the first time");
    }

    /** The message that named nothing must not come back, in any of its dialects. */
    @Test
    void noExpiryReportsOnlyThatTimePassed() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (Path source : testSources()) {
            String text = Files.readString(source);
            if (text.contains("\"condition was not satisfied within \"")) {
                offenders.add(source.getFileName() + " reports an expiry without naming what it observed");
            }
        }
        assertEquals(List.of(), offenders,
                "an expiry must name the last observed state; \"condition was not satisfied within <budget>\" "
                        + "is exactly the sentence that made these failures unreadable");

        for (Path owner : OWNERS) {
            String text = Files.readString(repoRoot().resolve(owner));
            assertTrue(text.contains("Last observed state: "),
                    () -> owner + " must report the last observed state on expiry");
            assertTrue(text.contains("Actually elapsed: "),
                    () -> owner + " must report how long really elapsed, not only the budget");
            assertTrue(text.contains(" samples."),
                    () -> owner + " must report how many samples it actually took");
            assertTrue(text.contains("the observed state never changed across")
                            && text.contains("the observed state was still changing"),
                    () -> owner + " must tell a condition that never progressed apart from a budget that ran "
                            + "out while it still was: the two have opposite fixes");
        }
    }

    /**
     * Three different things wear one suppression, and each must say which it is.
     *
     * <p>A bounded poll of external state is legitimate and is the majority. A deliberately slow actor is a
     * fixture -- the sleep is the device under test, and removing it removes the test. A fixed stabilisation
     * delay is the one where the risk actually sits, because a runner slower than the guess passes without
     * establishing the precondition. Filed under a single {@code java:S2925} they are indistinguishable, and
     * the third hides behind the first two.</p>
     */
    @Test
    void everySleepSuppressionNamesWhichOfTheThreeNaturesItJustifies() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (Path source : testSources()) {
            String text = Files.readString(source);
            Matcher suppression = SUPPRESSION.matcher(text);
            while (suppression.find()) {
                String context = text.substring(Math.max(0, suppression.start() - 1600), suppression.start());
                if (CATEGORIES.stream().noneMatch(context::contains)) {
                    offenders.add(source.getFileName() + " suppresses java:S2925 without naming which of the "
                            + "three natures it justifies");
                }
            }
        }
        assertEquals(List.of(), offenders,
                "each java:S2925 suppression must name its nature: a bounded poll, a deliberately slow actor, "
                        + "or a fixed stabilisation delay");
    }

    /**
     * Two copies, kept identical where it counts.
     *
     * <p>One shared artifact would have been better, and was rejected for a concrete reason:
     * {@code morpheus-mcp-transport} declares no MORPHEUS dependency at all, and giving it one so that two test
     * classes could share a helper is a real architectural change made for a cosmetic gain -- the reactor
     * module count is itself gated by {@code AuditRemediationContractTest}. Two copies are the honest cost of
     * that choice, and this is what stops them drifting the way the twelve helpers did.</p>
     */
    @Test
    void theTwoOwnersStayInStep() throws IOException {
        Path root = repoRoot();
        List<String> cores = new ArrayList<>();
        for (Path owner : OWNERS) {
            String text = Files.readString(root.resolve(owner));
            int coreStart = text.indexOf("    static void until(");
            assertTrue(coreStart > 0, () -> owner + " must declare the shared bounded-wait entry point");
            cores.add(text.substring(coreStart));
        }
        assertEquals(cores.get(0), cores.get(1),
                "the two BoundedWait copies must stay byte-identical from `static void until(` onwards; only "
                        + "their poll-interval constants and their javadoc may differ by module");
    }

    /** The scan is worth nothing if it is pointed at the wrong tree or the owners have moved. */
    @Test
    void theScanSeesTheOwnersItDeliberatelySkips() throws IOException {
        List<Path> sources = testSources();
        assertFalse(sources.isEmpty(), "the scan must find test sources to be worth anything");
        assertTrue(sources.size() > 200,
                () -> "the scan must cover the whole test tree, found only " + sources.size() + " sources");

        Path root = repoRoot();
        for (Path owner : OWNERS) {
            assertTrue(Files.isRegularFile(root.resolve(owner)),
                    () -> "owner " + owner + " must exist where this test expects it");
            assertTrue(sources.stream().anyMatch(source -> isOwner(source) && source.endsWith(owner.getFileName())),
                    () -> "the scan must actually reach " + owner + ", otherwise it skips nothing");
        }

        // The rule only bites if an await method with its own sleep loop is really detected as one.
        String planted = "    private void awaitSomething(java.time.Duration timeout) throws Exception {\n"
                + "        while (true) {\n"
                + "            Thread.sleep(10);\n"
                + "        }\n"
                + "    }\n";
        Matcher declaration = AWAIT_DECLARATION.matcher(planted);
        assertTrue(declaration.find(), "the declaration pattern must match a hand-rolled await helper");
        assertTrue(SLEEP_CALL.matcher(methodBody(planted, declaration.end())).find(),
                "the body scan must see the sleep inside a hand-rolled await helper");
    }

    /** Returns the source from the first {@code {} after {@code from} to its matching close. */
    private static String methodBody(String text, int from) {
        int open = text.indexOf('{', from);
        if (open < 0) {
            return "";
        }
        int depth = 0;
        for (int index = open; index < text.length(); index++) {
            char character = text.charAt(index);
            if (character == '{') {
                depth++;
            } else if (character == '}') {
                depth--;
                if (depth == 0) {
                    return text.substring(open, index + 1);
                }
            }
        }
        return text.substring(open);
    }

    private static boolean isOwner(Path source) {
        return OWNERS.stream().anyMatch(owner -> source.endsWith(owner));
    }

    private static List<Path> testSources() throws IOException {
        Path root = repoRoot();
        try (Stream<Path> tree = Files.walk(root)) {
            return tree.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".java"))
                    .filter(path -> path.toString().replace('\\', '/').contains("/src/test/java/"))
                    .filter(path -> !path.toString().replace('\\', '/').contains("/target/"))
                    .sorted()
                    .toList();
        }
    }

    private static Path repoRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        if (Files.isRegularFile(current.resolve("pom.xml")) && Files.isDirectory(current.resolve("distribution"))) {
            return current;
        }
        Path parent = current.getParent();
        if (parent != null && Files.isRegularFile(parent.resolve("pom.xml"))
                && Files.isDirectory(parent.resolve("distribution"))) {
            return parent;
        }
        throw new IllegalStateException("MORPHEUS repository root not found from " + current);
    }
}
