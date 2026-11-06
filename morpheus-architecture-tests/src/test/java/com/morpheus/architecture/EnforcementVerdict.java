package com.morpheus.architecture;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * The verdict of one run of the architecture suite: a failure of a coherence check is documentation drift, any other
 * failure is an architecture violation.
 *
 * <p>A node of the run - a test, a test class, another container - is a coherence check when it or one of its
 * ancestors carries the tag {@value CoherenceCheck#TAG}: a method or a generated test inherits the tag of its class.
 * A failed container is named under the verdict of its nature, with the failed tests. Only tests are counted as
 * executions. Names are sorted, so the proof reads the same for the same failures.</p>
 *
 * <p>The verdict decides nothing: Surefire still fails the build on any failure. It names what failed, in
 * {@value #PROOF_FILE} and on the output of the build. The run is the one Surefire made, selection included: a run
 * restricted by {@code -Dtest} or {@code -Dgroups} gives the verdict of that selection.</p>
 */
final class EnforcementVerdict {

    static final String PROOF_FILE = "morpheus-architecture-tests/target/enforcement-verdict.txt";

    static final String METHOD = "method=executions of the JUnit platform in this Surefire run of "
            + "morpheus-architecture-tests; selection: the one the run applied, a -Dtest or -Dgroups run gives a "
            + "partial verdict; a node is a coherence check when it or an ancestor carries the tag coherence; a failed "
            + "coherence check is documentation drift, any other failure an architecture violation; executions count "
            + "tests only";

    private static final String CONSOLE_PREFIX = "[enforcement verdict] ";

    /** What a node of the run is. */
    enum Kind {
        TEST,
        TEST_CLASS,
        OTHER_CONTAINER
    }

    /** A finished node of the run: its readable name, its kind, its nature and whether it failed. */
    record Outcome(String name, Kind kind, boolean coherence, boolean failed) {
    }

    private final List<Outcome> outcomes;

    EnforcementVerdict(List<Outcome> outcomes) {
        this.outcomes = List.copyOf(outcomes);
    }

    /** True when {@code node} or one of its ancestors carries {@code tag}. */
    static <T> boolean carriesTheTag(T node, Function<T, Optional<T>> parent, Function<T, Set<String>> tags,
                                     String tag) {
        for (Optional<T> current = Optional.of(node); current.isPresent(); current = parent.apply(current.get())) {
            if (tags.apply(current.get()).contains(tag)) {
                return true;
            }
        }
        return false;
    }

    List<String> documentationDrift() {
        return failedNames(true);
    }

    List<String> architectureViolations() {
        return failedNames(false);
    }

    boolean failed() {
        return outcomes.stream().anyMatch(Outcome::failed);
    }

    /** The lines of {@value #PROOF_FILE}: the method, the counts, then each verdict with its tests. */
    List<String> proofLines() {
        List<String> lines = new ArrayList<>();
        lines.add(METHOD);
        lines.add("testClasses=" + count(Kind.TEST_CLASS));
        lines.add("executions.coherence=" + executions(true));
        lines.add("executions.code=" + executions(false));
        addVerdict(lines, "documentationDrift", documentationDrift());
        addVerdict(lines, "architectureViolations", architectureViolations());
        return lines;
    }

    /** The two lines the build prints. */
    List<String> consoleLines() {
        return List.of(
                consoleLine("documentation drift", documentationDrift()),
                consoleLine("architecture violation", architectureViolations()));
    }

    private List<String> failedNames(boolean coherence) {
        return outcomes.stream()
                .filter(Outcome::failed)
                .filter(outcome -> outcome.coherence() == coherence)
                .map(Outcome::name)
                .sorted(Comparator.naturalOrder())
                .toList();
    }

    private long count(Kind kind) {
        return outcomes.stream().filter(outcome -> outcome.kind() == kind).count();
    }

    private long executions(boolean coherence) {
        return outcomes.stream()
                .filter(outcome -> outcome.kind() == Kind.TEST)
                .filter(outcome -> outcome.coherence() == coherence)
                .count();
    }

    private static void addVerdict(List<String> lines, String key, List<String> names) {
        lines.add(key + "=" + names.size());
        names.forEach(name -> lines.add("  " + name));
    }

    private static String consoleLine(String verdict, List<String> names) {
        String line = CONSOLE_PREFIX + verdict + ": " + names.size() + " test(s)";
        return names.isEmpty() ? line : line + " - " + String.join(", ", names);
    }
}
