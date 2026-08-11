package com.morpheus.provider.openspec;

import com.morpheus.application.files.SafeWorkspaceFileResolver;
import com.morpheus.application.read.ProviderIngestionBudget;

import java.io.IOException;
import java.nio.file.Path;

/** Creates fail-closed provider ingestion sessions without leaking filesystem exceptions. */
final class OpenSpecIngestionBudgets {
    private OpenSpecIngestionBudgets() {
    }

    static ProviderIngestionBudget.Session open(Path workspaceRoot) {
        try {
            return ProviderIngestionBudget.DEFAULT.open(SafeWorkspaceFileResolver.rootedAt(workspaceRoot));
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot resolve OpenSpec workspace root " + workspaceRoot, exception);
        }
    }
}
