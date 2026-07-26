package com.morpheus.cli;

import com.morpheus.application.lifecycle.mutation.ChangeWriteCapabilityObservation;
import com.morpheus.application.lifecycle.mutation.ChangeWriteCapabilityResolver;
import com.morpheus.application.lifecycle.mutation.RegisteredProjectWriteCapabilityResolver;
import com.morpheus.application.provider.ProviderSelectionPolicy;
import com.morpheus.application.provider.SpecificationProviderRegistry;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.provider.openspec.OpenSpecSpecificationProvider;
import com.morpheus.store.sqlite.SqliteSpecificationKnowledgeStore;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/** Composition-root resolver: only providers packaged by the launcher may authorize writes. */
final class CliProjectWriteCapabilityResolver implements ChangeWriteCapabilityResolver {
    private final Path databasePath;

    CliProjectWriteCapabilityResolver(Path databasePath) {
        this.databasePath = Objects.requireNonNull(databasePath, "databasePath");
    }

    @Override
    public ChangeWriteCapabilityObservation resolve(ProjectSpecificationId projectId) {
        try (var projects = new SqliteSpecificationKnowledgeStore(databasePath)) {
            var registry = new SpecificationProviderRegistry(
                    List.of(new OpenSpecSpecificationProvider()),
                    new ProviderSelectionPolicy());
            return new RegisteredProjectWriteCapabilityResolver(projects, registry).resolve(projectId);
        }
    }
}
