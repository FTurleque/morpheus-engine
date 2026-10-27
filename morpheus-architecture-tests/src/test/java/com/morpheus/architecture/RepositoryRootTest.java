package com.morpheus.architecture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RepositoryRootTest {

    private static final String REACTOR_POM = "<project>\n  <modules>\n    <module>child</module>\n  </modules>\n</project>\n";

    private static final String MODULE_POM = "<project>\n  <parent>\n    <artifactId>morpheus-engine</artifactId>\n"
            + "  </parent>\n</project>\n";

    @Test
    void theRootIsFoundFromAModuleDirectory(@TempDir Path temporary) throws IOException {
        Path root = reactor(temporary.resolve("checkout"), true);

        assertEquals(root, RepositoryRoot.from(root.resolve("child")));
    }

    @Test
    void theRootIsFoundFromItself(@TempDir Path temporary) throws IOException {
        Path root = reactor(temporary.resolve("checkout"), true);

        assertEquals(root, RepositoryRoot.from(root));
    }

    @Test
    void theRootIsFoundFromADirectoryNestedBelowAModule(@TempDir Path temporary) throws IOException {
        Path root = reactor(temporary.resolve("checkout"), true);
        Path nested = Files.createDirectories(root.resolve("child/src/test/java/com/example"));

        assertEquals(root, RepositoryRoot.from(nested));
    }

    /**
     * A worktree under {@code .claude/worktrees/} is a complete checkout inside another one, and its {@code .git}
     * is a file where the outer checkout has a directory. The search must stop at the worktree.
     */
    @Test
    void aWorktreeNestedInAnotherCheckoutIsItsOwnRoot(@TempDir Path temporary) throws IOException {
        Path outer = reactor(temporary.resolve("checkout"), true);
        Path worktree = reactor(outer.resolve(".claude/worktrees/lot"), false);

        assertEquals(worktree, RepositoryRoot.from(worktree.resolve("child")));
        assertEquals(outer, RepositoryRoot.from(outer.resolve("child")));
    }

    @Test
    void aDirectoryWithNoRepositoryAboveItIsRefusedNamingTheStartAndTheAnchor(@TempDir Path temporary) throws IOException {
        Path start = Files.createDirectories(temporary.resolve("elsewhere/deeper"));
        Files.writeString(temporary.resolve("elsewhere/pom.xml"), MODULE_POM);

        IllegalStateException refusal = assertThrows(IllegalStateException.class, () -> RepositoryRoot.from(start));

        assertEquals("no ancestor of " + start + " has " + RepositoryRoot.ANCHOR, refusal.getMessage());
    }

    @Test
    void theRepositoryTheseTestsRunInIsTheNearestReactorAboveTheWorkingDirectory() {
        Path workingDirectory = Path.of("").toAbsolutePath().normalize();
        Path root = RepositoryRoot.locate();

        assertTrue(workingDirectory.startsWith(root), () -> root + " must be an ancestor of " + workingDirectory);
        assertTrue(Files.isDirectory(root.resolve("morpheus-architecture-tests")),
                () -> root + " must hold the module these tests belong to");
        assertTrue(Files.exists(root.resolve(".git")), () -> root + " must be a Git checkout or worktree");
    }

    @Test
    void aFixtureIsResolvedUnderTheRootAndAMissingOneIsRefusedByItsPath() {
        assertEquals(RepositoryRoot.locate().resolve("experiments/m0/fixtures/openspec-basic"),
                RepositoryRoot.fixture("openspec-basic"));

        IllegalStateException refusal = assertThrows(IllegalStateException.class,
                () -> RepositoryRoot.fixture("no-such-fixture"));

        assertEquals("no M0 fixture no-such-fixture at "
                        + RepositoryRoot.locate().resolve("experiments/m0/fixtures/no-such-fixture"),
                refusal.getMessage());
    }

    private static Path reactor(Path root, boolean gitDirectory) throws IOException {
        Files.createDirectories(root.resolve("child"));
        Files.writeString(root.resolve("pom.xml"), REACTOR_POM);
        Files.writeString(root.resolve("child/pom.xml"), MODULE_POM);
        if (gitDirectory) {
            Files.createDirectories(root.resolve(".git"));
        } else {
            Files.writeString(root.resolve(".git"), "gitdir: ../../../.git/worktrees/lot\n");
        }
        return root;
    }
}
