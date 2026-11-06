package com.morpheus.architecture;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A {@code com.morpheus} package an active OpenSpec change cites exists in the sources of the reactor.
 *
 * <p>An active change is read by whoever picks it up as a description of the code today. One kept naming, in the
 * present tense, the package the shared MCP transport left when it was renamed, and nothing read
 * {@code openspec/changes/}. The cited name is the longest run of lower-case segments after {@code com.morpheus.}, so
 * {@code com.morpheus.api..} reads as {@code com.morpheus.api} and a qualified class name as its package. A package
 * exists when a {@code .java} file of a module of the root POM sits in it, or below it, under {@code src/main/java} or
 * {@code src/test/java}. The archive is a dated record and is not read.</p>
 *
 * <p>Two absent names are legitimate, and each is admitted by a mark written right after the name on the same line,
 * where the reader sees it: {@value #FORMER_NAME} for a renaming told as history, {@value #TO_CREATE} for a package a
 * change proposes. A package marked {@value #TO_CREATE} that exists is refused, so the mark does not outlive the
 * implementation. No list of exceptions is kept here.</p>
 */
class OpenSpecChangePackageCitationTest {

    static final String FORMER_NAME = "(ancien nom)";
    static final String TO_CREATE = "(à créer)";

    private static final Path CHANGES = Path.of("openspec", "changes");
    private static final Path ARCHIVE = CHANGES.resolve("archive");
    private static final Pattern MODULE = Pattern.compile("<module>([^<]+)</module>");
    private static final Pattern CITED_PACKAGE = Pattern.compile("(?<![\\w.])com\\.morpheus(?:\\.[a-z][a-z0-9_]*)*");
    /** What may sit between a cited name and its mark: the rest of a qualified name, a closing code span, spaces. */
    private static final Pattern MARK = Pattern.compile("^[\\w.*]*`*\\s*(\\(ancien nom\\)|\\(à créer\\))");
    private static final List<String> SOURCE_ROOTS = List.of("src/main/java", "src/test/java");

    @Test
    void everyPackageAnActiveChangeCitesExistsInTheReactorSources() throws IOException {
        Path root = RepositoryRoot.locate();
        List<Path> files = RepositoryTree.files(root);
        Set<String> packages = declaredPackages(root, files);
        assertTrue(packages.contains("com.morpheus.application"),
                "the reactor sources must declare com.morpheus.application: the package scan reads nothing");
        assertEquals(List.of(), refusals(root, files, packages),
                "an active OpenSpec change cites a com.morpheus package the reactor sources do not declare; write "
                        + "its current name, or mark it " + FORMER_NAME + " or " + TO_CREATE + " right after it");
    }

    @Test
    void anAbsentPackageIsRefusedWhereverItIsCitedAndAnExistingOneUnderEveryForm(@TempDir Path root)
            throws IOException {
        plantReactor(root);
        plant(root, "openspec/changes/a-change/design.md", """
                - `morpheus-mcp-transport` ships the package `com.morpheus.integration.mcp`, which holds a type.
                - Rules name `com.morpheus.application..`, `com.morpheus.application.security.PeerLaunchSettingsResolver`
                  and `com.morpheus.transport.mcp`; the root `com.morpheus` is a prefix of every package.
                - The class com.morpheus.gone.Thing is cited in prose.
                """);
        plant(root, "openspec/changes/archive/2026-01-01-old/design.md", "`com.morpheus.integration.mcp`\n");

        List<String> refusals = refusals(root, RepositoryTree.files(root), declaredPackages(root,
                RepositoryTree.files(root)));
        assertEquals(List.of(
                "openspec/changes/a-change/design.md:1 com.morpheus.integration.mcp is no package of the reactor sources",
                "openspec/changes/a-change/design.md:4 com.morpheus.gone is no package of the reactor sources"),
                refusals, "the archive is not read and an existing package is admitted under every form");
    }

    @Test
    void onlyTheTwoMarksAdmitAnAbsentPackageAndTheCreationMarkDoesNotOutliveTheCreation(@TempDir Path root)
            throws IOException {
        plantReactor(root);
        plant(root, "openspec/changes/a-change/proposal.md", """
                The transport left `com.morpheus.integration.mcp` (ancien nom) for its own package.
                The change adds `com.morpheus.application.reasoning.trace` (à créer).
                The class `com.morpheus.application.planned.Thing` (à créer) holds it.
                The transport was renamed from `com.morpheus.integration.mcp`, formerly its name.
                It moves to `com.morpheus.transport.mcp` (à créer).
                """);

        List<String> refusals = refusals(root, RepositoryTree.files(root), declaredPackages(root,
                RepositoryTree.files(root)));
        assertEquals(List.of(
                "openspec/changes/a-change/proposal.md:4 com.morpheus.integration.mcp is no package of the reactor "
                        + "sources",
                "openspec/changes/a-change/proposal.md:5 com.morpheus.transport.mcp exists: remove the mark "
                        + TO_CREATE),
                refusals, "a mark admits an absent package, another wording does not, and a stale creation mark fails");
    }

    @Test
    void noActiveChangeIsReadAsNoCitationWhileTheCorpusProvesTheRefusal(@TempDir Path root) throws IOException {
        plantReactor(root);
        plant(root, "openspec/changes/archive/2026-01-01-old/design.md", "`com.morpheus.integration.mcp`\n");
        Set<String> packages = declaredPackages(root, RepositoryTree.files(root));
        assertFalse(packages.isEmpty(), "the corpus declares packages");
        assertEquals(List.of(), refusals(root, RepositoryTree.files(root), packages),
                "with no active change nothing is read");
    }

    /**
     * Each refusal names the file, the line and the package. A package is absent unless it, or a package below it, is
     * declared; it is admitted when absent and marked, and refused when present and marked {@value #TO_CREATE}.
     */
    private static List<String> refusals(Path root, List<Path> files, Set<String> packages) throws IOException {
        List<String> refusals = new ArrayList<>();
        for (Path file : files) {
            Path relative = root.relativize(file);
            if (!relative.startsWith(CHANGES) || relative.startsWith(ARCHIVE) || !file.toString().endsWith(".md")) {
                continue;
            }
            String name = relative.toString().replace('\\', '/');
            List<String> lines = Files.readAllLines(file);
            for (int index = 0; index < lines.size(); index++) {
                String line = lines.get(index);
                Matcher cited = CITED_PACKAGE.matcher(line);
                while (cited.find()) {
                    String cite = cited.group();
                    Matcher mark = MARK.matcher(line.substring(cited.end()));
                    String marked = mark.find() ? mark.group(1) : "";
                    boolean exists = packages.contains(cite);
                    if (!exists && marked.isEmpty()) {
                        refusals.add(name + ":" + (index + 1) + " " + cite + " is no package of the reactor sources");
                    } else if (exists && marked.equals(TO_CREATE)) {
                        refusals.add(name + ":" + (index + 1) + " " + cite + " exists: remove the mark " + TO_CREATE);
                    }
                }
            }
        }
        return refusals;
    }

    /** Every package a module of the root POM declares in its main or test sources, with all its prefixes. */
    private static Set<String> declaredPackages(Path root, List<Path> files) throws IOException {
        Set<String> modules = new TreeSet<>();
        Matcher module = MODULE.matcher(Files.readString(root.resolve("pom.xml")));
        while (module.find()) {
            modules.add(module.group(1).trim());
        }
        Set<String> packages = new TreeSet<>();
        for (Path file : files) {
            String relative = root.relativize(file).toString().replace('\\', '/');
            if (!relative.endsWith(".java")) {
                continue;
            }
            for (String sources : SOURCE_ROOTS) {
                int at = relative.indexOf("/" + sources + "/");
                if (at > 0 && modules.contains(relative.substring(0, at))) {
                    String directory = relative.substring(at + sources.length() + 2, relative.lastIndexOf('/') + 1);
                    String declared = directory.replace('/', '.');
                    for (int dot = declared.indexOf('.'); dot >= 0; dot = declared.indexOf('.', dot + 1)) {
                        packages.add(declared.substring(0, dot));
                    }
                }
            }
        }
        return packages;
    }

    private static void plantReactor(Path root) throws IOException {
        plant(root, "pom.xml", "<project><modules><module>m</module></modules></project>\n");
        plant(root, "m/src/main/java/com/morpheus/transport/mcp/Transport.java", "package com.morpheus.transport.mcp;\n");
        plant(root, "m/src/test/java/com/morpheus/application/security/ResolverTest.java",
                "package com.morpheus.application.security;\n");
        plant(root, "outside/src/main/java/com/morpheus/gone/Thing.java", "package com.morpheus.gone;\n");
    }

    private static void plant(Path root, String relative, String content) throws IOException {
        Path file = root.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }
}
