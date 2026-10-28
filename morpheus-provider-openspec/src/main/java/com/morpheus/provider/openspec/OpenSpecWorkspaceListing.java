package com.morpheus.provider.openspec;

import com.morpheus.application.read.ProviderIngestionBudget;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Enumerates the directories an OpenSpec read walks and checks each batch against the budget before it is collected
 * in full. One read enumerates a directory once: the orchestrator hands the change roots to every reader that needs
 * them.
 */
final class OpenSpecWorkspaceListing {
    private OpenSpecWorkspaceListing() {
    }

    /** The active change directories under {@code openspec/changes}, sorted; {@code archive} is history, not a change. */
    static List<Path> changeRoots(Path workspaceRoot, ProviderIngestionBudget.Session budget) {
        Path changesRoot = workspaceRoot.resolve("openspec/changes");
        if (!Files.isDirectory(changesRoot)) {
            return List.of();
        }
        try (var paths = Files.list(changesRoot)) {
            List<Path> roots = paths
                    .filter(Files::isDirectory)
                    .filter(path -> !path.getFileName().toString().equals("archive"))
                    .limit(budget.remainingFiles() + 1)
                    .toList();
            budget.requireAdditionalFiles(roots.size(), "openspec/changes");
            return roots.stream().sorted().toList();
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot enumerate OpenSpec changes", exception);
        }
    }

    /** Every {@code spec.md} under {@code specsRoot}, sorted; a refusal names {@code source}. */
    static List<Path> specificationFiles(Path specsRoot, ProviderIngestionBudget.Session budget, String source) {
        if (!Files.isDirectory(specsRoot)) {
            return List.of();
        }
        try (var paths = OpenSpecBoundedTraversal.walk(specsRoot)) {
            List<Path> files = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().equals("spec.md"))
                    .limit(budget.remainingFiles() + 1)
                    .toList();
            budget.requireAdditionalFiles(files.size(), source);
            return files.stream().sorted().toList();
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot enumerate OpenSpec specifications for " + source, exception);
        }
    }
}
