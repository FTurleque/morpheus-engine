package com.morpheus.application.read;

import com.morpheus.domain.source.SourceLocator;

import java.nio.file.Path;
import java.util.Objects;

/**
 * The project root a content reader publishes: the workspace root it received, normalized once, here.
 *
 * <p>Publication compares this locator, character for character, with the root the project was registered
 * under. A reader that publishes anything else -- the file it read, a parent, a differently normalized
 * spelling -- makes its own publication impossible. Every reader, built-in or plugin, goes through this
 * point so that no second normalization can drift from the first.</p>
 */
public final class ProviderProjectRoot {
    private ProviderProjectRoot() {
    }

    public static SourceLocator locator(Path workspaceRoot) {
        Path normalized = Objects.requireNonNull(workspaceRoot, "workspaceRoot").toAbsolutePath().normalize();
        return SourceLocator.file(normalized.toString());
    }
}
