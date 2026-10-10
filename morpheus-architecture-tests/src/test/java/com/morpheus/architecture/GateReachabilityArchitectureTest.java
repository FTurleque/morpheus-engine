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
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A gate the repository declares has to be reachable by an invocation the repository actually performs.
 *
 * <p>Four things made gates unreachable without anything failing: five performance tests whose class names match no
 * Surefire pattern and that no workflow ran, two Maven profiles no workflow named, four validators pinned to branches
 * that no longer exist and with no shell twin, and documentation that cites a wrapper script which was removed. Each
 * rule below reads text, because the targets are {@code .sh}, {@code .ps1}, {@code .yml}, {@code .xml} and {@code .md},
 * which a bytecode rule cannot see.</p>
 *
 * <p>Every rule reads <em>executable</em> text: YAML and shell comments are removed first, otherwise a header comment
 * that mentions a validator would satisfy "a workflow runs it". Every rule also asserts that the population it reads is
 * not empty, since a rule that matches nothing passes too ({@code archRule.failOnEmptyShould} does not apply to
 * filesystem rules).</p>
 */
class GateReachabilityArchitectureTest {
    private static final Pattern VALIDATOR_FILE = Pattern.compile("validate-(.+)\\.(ps1|sh)");
    private static final Pattern SUREFIRE_DEFAULT_NAME = Pattern.compile("Test.*|.*Test|.*Tests|.*TestCase");
    private static final Pattern TEST_METHOD = Pattern.compile("@(?:Test|ParameterizedTest|RepeatedTest)\\b");
    private static final Pattern PROFILE_ID = Pattern.compile("<profile>\\s*<id>([^<]+)</id>", Pattern.DOTALL);
    private static final Pattern PROFILE_ARGUMENT = Pattern.compile("(?:^|[\\s\"'])-P\\s*([A-Za-z0-9_.,!-]+)");
    private static final Pattern SUREFIRE_PLUGIN = Pattern.compile(
            "<artifactId>maven-surefire-plugin</artifactId>(.*?)</plugin>", Pattern.DOTALL);
    private static final Pattern SUREFIRE_INCLUDE = Pattern.compile("<include>([^<]+)</include>");
    private static final Pattern REMOVED_WRAPPER = Pattern.compile("validate-[a-z0-9]+\\.cmd");
    private static final Pattern DISPATCHER_TARGET = Pattern.compile("validate\\.cmd\\s+([a-z]\\d+)\\b");

    /**
     * Profiles that no workflow runs on purpose, each with the reason. Empty: both audit profiles are run by the
     * nightly workflow. An entry without a reason, or naming a profile the POM no longer declares, fails the suite.
     */
    private static final Map<String, String> MANUAL_ONLY_PROFILES = Map.of();

    /** Documentation that states what to run now. Dated records (validation proofs, execution plans, ADRs) are not here. */
    private static final List<String> CURRENT_DOCUMENT_FILES =
            List.of("README.md", "scripts/README.md", "docs/README.md", "distribution/README.md");

    @Test
    void everyValidatorExistsOnBothPlatforms() throws IOException {
        Path scripts = repositoryRoot().resolve("scripts");
        Map<String, Set<String>> platformsByTarget = new TreeMap<>();
        for (Path file : list(scripts)) {
            Matcher matcher = VALIDATOR_FILE.matcher(file.getFileName().toString());
            if (matcher.matches()) {
                platformsByTarget.computeIfAbsent(matcher.group(1), key -> new TreeSet<>()).add(matcher.group(2));
            }
        }
        assertFalse(platformsByTarget.isEmpty(), "no validator found under scripts/: the rule would pass on nothing");

        List<String> lonely = new ArrayList<>();
        platformsByTarget.forEach((target, platforms) -> {
            if (!platforms.contains("ps1")) {
                lonely.add(target + " has no scripts/validate-" + target + ".ps1");
            }
            if (!platforms.contains("sh")) {
                lonely.add(target + " has no scripts/validate-" + target + ".sh");
            }
        });
        assertTrue(lonely.isEmpty(), () -> "a validator must exist on both platforms or on neither: " + lonely);
    }

    @Test
    void noValidatorSwitchesToANamedBranch() throws IOException {
        Path scripts = repositoryRoot().resolve("scripts");
        Pattern movesTheCheckout = Pattern.compile("\\bgit\\s+(?:switch|checkout)\\b");
        List<String> offenders = new ArrayList<>();
        int validators = 0;
        for (Path file : list(scripts)) {
            if (!VALIDATOR_FILE.matcher(file.getFileName().toString()).matches()) {
                continue;
            }
            validators++;
            String[] lines = executableText(file).split("\\R");
            for (int index = 0; index < lines.length; index++) {
                if (movesTheCheckout.matcher(lines[index]).find()) {
                    offenders.add(file.getFileName() + ":" + (index + 1));
                }
            }
        }
        assertTrue(validators > 0, "no validator found: the rule would pass on nothing");
        assertTrue(offenders.isEmpty(),
                () -> "a validator qualifies the checkout it runs from and must not move it to a branch: " + offenders);
    }

    @Test
    void everyTestClassOutsideSurefireSelectionIsNamedByAValidatorAWorkflowRuns() throws IOException {
        Path root = repositoryRoot();
        List<String> unselected = new ArrayList<>();
        int scanned = 0;
        for (String module : declaredModules(root)) {
            Path tests = root.resolve(module).resolve("src/test/java");
            if (!Files.isDirectory(tests)) {
                continue;
            }
            List<Pattern> moduleIncludes = surefireIncludes(root.resolve(module).resolve("pom.xml"));
            try (Stream<Path> files = Files.walk(tests)) {
                for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                    scanned++;
                    String simpleName = file.getFileName().toString().replaceFirst("\\.java$", "");
                    if (isSelected(simpleName, moduleIncludes)
                            || !TEST_METHOD.matcher(Files.readString(file)).find()) {
                        continue;
                    }
                    unselected.add(simpleName);
                }
            }
        }
        assertTrue(scanned > 100, "too few test sources scanned (" + scanned + "): the rule would pass on nothing");

        List<Path> shellValidators = validators(root, "sh");
        List<Path> powerShellValidators = validators(root, "ps1");
        String workflows = workflowsExecutableText(root);
        assertFalse(workflows.isBlank(), "no workflow text read: the rule would pass on nothing");

        List<String> unreachable = new ArrayList<>();
        for (String gate : unselected) {
            Pattern name = Pattern.compile("\\b" + Pattern.quote(gate) + "\\b");
            List<Path> shellNamers = namingValidators(shellValidators, name);
            List<Path> powerShellNamers = namingValidators(powerShellValidators, name);
            if (shellNamers.isEmpty() || powerShellNamers.isEmpty()) {
                unreachable.add(gate + " is not named by a validator on "
                        + (shellNamers.isEmpty() ? "Linux (.sh)" : "Windows (.ps1)"));
                continue;
            }
            boolean run = Stream.concat(shellNamers.stream(), powerShellNamers.stream())
                    .map(path -> path.getFileName().toString().replaceFirst("\\.(?:ps1|sh)$", ""))
                    .anyMatch(validator -> Pattern.compile(
                            "(?<![A-Za-z0-9-])" + Pattern.quote(validator) + "(?![A-Za-z0-9-])")
                            .matcher(workflows).find());
            if (!run) {
                unreachable.add(gate + " is named by a validator that no workflow runs");
            }
        }
        assertTrue(unreachable.isEmpty(),
                () -> "Surefire does not select these test classes, so only a validator a workflow runs can: "
                        + unreachable);
    }

    @Test
    void everyRootPomProfileIsNamedByAWorkflowOrExcusedWithAReason() throws IOException {
        Path root = repositoryRoot();
        String pom = Files.readString(root.resolve("pom.xml"));
        Set<String> declared = new TreeSet<>();
        Matcher profiles = PROFILE_ID.matcher(pom);
        while (profiles.find()) {
            declared.add(profiles.group(1).trim());
        }
        assertFalse(declared.isEmpty(), "no profile found in the root POM: the rule would pass on nothing");

        Set<String> named = new TreeSet<>();
        Matcher arguments = PROFILE_ARGUMENT.matcher(workflowsExecutableText(root));
        while (arguments.find()) {
            for (String profile : arguments.group(1).split(",")) {
                named.add(profile.startsWith("!") ? profile.substring(1) : profile);
            }
        }

        MANUAL_ONLY_PROFILES.forEach((profile, reason) -> {
            assertTrue(declared.contains(profile), "an excuse names a profile the root POM no longer declares: " + profile);
            assertFalse(reason.isBlank(), "a manual-only profile needs the reason it is manual: " + profile);
        });
        Set<String> unreached = new TreeSet<>(declared);
        unreached.removeAll(named);
        unreached.removeAll(MANUAL_ONLY_PROFILES.keySet());
        assertTrue(unreached.isEmpty(),
                () -> "these Maven profiles are run by no workflow and are not listed as manual-only: " + unreached);
    }

    @Test
    void currentDocumentationNamesOnlyResolvingCommands() throws IOException {
        Path root = repositoryRoot();
        List<Path> documents = new ArrayList<>();
        for (String file : CURRENT_DOCUMENT_FILES) {
            if (Files.isRegularFile(root.resolve(file))) {
                documents.add(root.resolve(file));
            }
        }
        Path developerGuides = root.resolve("docs/developer");
        for (Path guide : list(developerGuides)) {
            if (guide.getFileName().toString().endsWith(".md")) {
                documents.add(guide);
            }
        }
        assertTrue(documents.size() > 5, "too few current documents scanned: the rule would pass on nothing");

        Set<String> targets = new TreeSet<>();
        for (Path file : list(root.resolve("scripts"))) {
            Matcher matcher = VALIDATOR_FILE.matcher(file.getFileName().toString());
            if (matcher.matches()) {
                targets.add(matcher.group(1));
            }
        }

        List<String> offenders = new ArrayList<>();
        for (Path document : documents) {
            String[] lines = Files.readString(document).split("\\R");
            for (int index = 0; index < lines.length; index++) {
                String where = root.relativize(document).toString().replace('\\', '/') + ":" + (index + 1);
                if (REMOVED_WRAPPER.matcher(lines[index]).find()) {
                    offenders.add(where + " names a validate-<target>.cmd wrapper, which does not exist");
                }
                Matcher dispatched = DISPATCHER_TARGET.matcher(lines[index]);
                while (dispatched.find()) {
                    if (!targets.contains(dispatched.group(1))) {
                        offenders.add(where + " runs scripts\\validate.cmd " + dispatched.group(1)
                                + ", which is not a target");
                    }
                }
            }
        }
        assertTrue(offenders.isEmpty(),
                () -> "current documentation must name a command that resolves: " + offenders);
    }

    private static boolean isSelected(String simpleName, List<Pattern> moduleIncludes) {
        if (!moduleIncludes.isEmpty()) {
            return moduleIncludes.stream().anyMatch(include -> include.matcher(simpleName).matches());
        }
        return SUREFIRE_DEFAULT_NAME.matcher(simpleName).matches();
    }

    /** The class-name patterns of the {@code <include>} entries of a module POM, which replace Surefire's defaults. */
    private static List<Pattern> surefireIncludes(Path pom) throws IOException {
        List<Pattern> includes = new ArrayList<>();
        if (!Files.isRegularFile(pom)) {
            return includes;
        }
        Matcher plugin = SUREFIRE_PLUGIN.matcher(Files.readString(pom));
        while (plugin.find()) {
            Matcher matcher = SUREFIRE_INCLUDE.matcher(plugin.group(1));
            while (matcher.find()) {
                String glob = matcher.group(1).trim();
                String lastSegment = glob.substring(glob.lastIndexOf('/') + 1).replaceFirst("\\.java$", "");
                includes.add(Pattern.compile(Pattern.quote(lastSegment).replace("*", "\\E.*\\Q")));
            }
        }
        return includes;
    }

    private static List<Path> validators(Path root, String extension) throws IOException {
        List<Path> result = new ArrayList<>();
        for (Path file : list(root.resolve("scripts"))) {
            Matcher matcher = VALIDATOR_FILE.matcher(file.getFileName().toString());
            if (matcher.matches() && matcher.group(2).equals(extension)) {
                result.add(file);
            }
        }
        return result;
    }

    private static List<Path> namingValidators(List<Path> validators, Pattern name) throws IOException {
        List<Path> result = new ArrayList<>();
        for (Path validator : validators) {
            if (name.matcher(executableText(validator)).find()) {
                result.add(validator);
            }
        }
        return result;
    }

    private static String workflowsExecutableText(Path root) throws IOException {
        StringBuilder text = new StringBuilder();
        for (Path workflow : list(root.resolve(".github/workflows"))) {
            if (workflow.getFileName().toString().endsWith(".yml")) {
                text.append(executableText(workflow)).append('\n');
            }
        }
        return text.toString();
    }

    /** The file without its comments: whole-line {@code #} comments and trailing ones preceded by whitespace. */
    private static String executableText(Path file) throws IOException {
        StringBuilder text = new StringBuilder();
        for (String line : Files.readString(file).split("\\R", -1)) {
            if (line.stripLeading().startsWith("#")) {
                continue;
            }
            text.append(line.replaceFirst("\\s#.*$", "")).append('\n');
        }
        return text.toString();
    }

    private static List<String> declaredModules(Path root) throws IOException {
        String pom = Files.readString(root.resolve("pom.xml"));
        Matcher block = Pattern.compile("<modules>(.*?)</modules>", Pattern.DOTALL).matcher(pom);
        assertTrue(block.find(), "root POM declares no modules");
        List<String> modules = new ArrayList<>();
        Matcher module = Pattern.compile("<module>([^<]+)</module>").matcher(block.group(1));
        while (module.find()) {
            modules.add(module.group(1).trim());
        }
        assertEquals(modules.size(), new TreeSet<>(modules).size(), "a module is declared twice");
        return modules;
    }

    private static List<Path> list(Path directory) throws IOException {
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        try (Stream<Path> entries = Files.list(directory)) {
            return entries.sorted().toList();
        }
    }

    private static Path repositoryRoot() throws IOException {
        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null) {
            Path pom = current.resolve("pom.xml");
            if (Files.isRegularFile(pom)) {
                String content = Files.readString(pom);
                if (content.contains("<artifactId>morpheus-engine</artifactId>") && content.contains("<modules>")) {
                    return current;
                }
            }
            current = current.getParent();
        }
        throw new IOException("cannot locate MORPHEUS repository root from " + Path.of("").toAbsolutePath());
    }
}
