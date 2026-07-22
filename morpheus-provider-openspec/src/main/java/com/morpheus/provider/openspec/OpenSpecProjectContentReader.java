package com.morpheus.provider.openspec;

import com.morpheus.application.identity.EntityIdentityResolver;
import com.morpheus.application.ingestion.NormalizedProjectContent;
import com.morpheus.domain.diagnostic.Diagnostic;
import com.morpheus.domain.project.ProjectSpecificationId;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Aggregates the M2 OpenSpec readers into one provider-neutral normalized project content graph. */
public final class OpenSpecProjectContentReader {
    private final OpenSpecCurrentSpecificationReader currentReader;
    private final OpenSpecChangeMetadataReader changeReader;

    public OpenSpecProjectContentReader() {
        this(new OpenSpecCurrentSpecificationReader(), new OpenSpecChangeMetadataReader());
    }

    OpenSpecProjectContentReader(
            OpenSpecCurrentSpecificationReader currentReader,
            OpenSpecChangeMetadataReader changeReader) {
        this.currentReader = Objects.requireNonNull(currentReader, "currentReader");
        this.changeReader = Objects.requireNonNull(changeReader, "changeReader");
    }

    public NormalizedProjectContent read(
            Path workspaceRoot,
            ProjectSpecificationId projectId,
            EntityIdentityResolver identityResolver) {
        var current = currentReader.read(workspaceRoot, projectId, identityResolver);
        var changes = changeReader.read(workspaceRoot, projectId, identityResolver);

        if (!current.project().equals(changes.project())) {
            throw new IllegalStateException("OpenSpec readers produced inconsistent project descriptors");
        }

        var evidence = new ArrayList<>(current.evidence());
        evidence.addAll(changes.evidence());

        List<Diagnostic> diagnostics = new ArrayList<>();
        diagnostics.addAll(current.diagnostics());
        changes.diagnostics().stream().filter(item -> !diagnostics.contains(item)).forEach(diagnostics::add);

        return new NormalizedProjectContent(
                current.project(),
                current.specifications(),
                current.requirements(),
                current.scenarios(),
                changes.changes(),
                changes.constraints(),
                changes.designDecisions(),
                changes.tasks(),
                evidence,
                diagnostics);
    }
}
