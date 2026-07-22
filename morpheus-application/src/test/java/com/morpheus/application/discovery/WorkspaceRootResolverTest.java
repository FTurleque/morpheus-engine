package com.morpheus.application.discovery;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkspaceRootResolverTest {

    @TempDir
    Path tempDir;

    private final WorkspaceRootResolver resolver = new WorkspaceRootResolver();

    @Test
    void keepsExplicitPathFirstAndAddsDistinctGitAncestor() throws IOException {
        Path repo = Files.createDirectories(tempDir.resolve("repo"));
        Files.createDirectory(repo.resolve(".git"));
        Path nested = Files.createDirectories(repo.resolve("service/src"));

        var candidates = resolver.candidates(nested.resolve("..").resolve("src"));

        assertEquals(2, candidates.size());
        assertEquals(nested.toAbsolutePath().normalize(), candidates.get(0).root());
        assertEquals(WorkspaceRootKind.EXPLICIT, candidates.get(0).kind());
        assertEquals(repo.toAbsolutePath().normalize(), candidates.get(1).root());
        assertEquals(WorkspaceRootKind.GIT_ANCESTOR, candidates.get(1).kind());
    }

    @Test
    void acceptsGitFileMarkerUsedByWorktrees() throws IOException {
        Path repo = Files.createDirectories(tempDir.resolve("worktree"));
        Files.writeString(repo.resolve(".git"), "gitdir: ../metadata");
        Path nested = Files.createDirectories(repo.resolve("module"));

        var candidates = resolver.candidates(nested);

        assertEquals(2, candidates.size());
        assertEquals(repo.toAbsolutePath().normalize(), candidates.get(1).root());
        assertEquals(WorkspaceRootKind.GIT_ANCESTOR, candidates.get(1).kind());
    }

    @Test
    void nonGitWorkspaceKeepsOnlyExplicitCandidate() throws IOException {
        Path workspace = Files.createDirectories(tempDir.resolve("plain-workspace"));

        var candidates = resolver.candidates(workspace);

        assertEquals(1, candidates.size());
        assertEquals(workspace.toAbsolutePath().normalize(), candidates.getFirst().root());
        assertTrue(resolver.findGitRoot(workspace).isEmpty());
    }
}
