package com.morpheus.provider.openspec;

import com.morpheus.application.identity.EntityIdentityResolver;
import com.morpheus.application.ingestion.NormalizedProjectContent;
import com.morpheus.application.read.ProviderIngestionBudget;
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
    private final OpenSpecRequirementDeltaReader requirementDeltaReader;

    public OpenSpecProjectContentReader() {
        this(
                new OpenSpecCurrentSpecificationReader(),
                new OpenSpecChangeMetadataReader(),
                new OpenSpecRequirementDeltaReader());
    }

    OpenSpecProjectContentReader(
            OpenSpecCurrentSpecificationReader currentReader,
            OpenSpecChangeMetadataReader changeReader) {
        this(currentReader, changeReader, new OpenSpecRequirementDeltaReader());
    }

    OpenSpecProjectContentReader(
            OpenSpecCurrentSpecificationReader currentReader,
            OpenSpecChangeMetadataReader changeReader,
            OpenSpecRequirementDeltaReader requirementDeltaReader) {
        this.currentReader = Objects.requireNonNull(currentReader, "currentReader");
        this.changeReader = Objects.requireNonNull(changeReader, "changeReader");
        this.requirementDeltaReader = Objects.requireNonNull(requirementDeltaReader, "requirementDeltaReader");
    }

    public NormalizedProjectContent read(
            Path workspaceRoot,
            ProjectSpecificationId projectId,
            EntityIdentityResolver identityResolver) {
        Path root = Objects.requireNonNull(workspaceRoot, "workspaceRoot").toAbsolutePath().normalize();
        ProviderIngestionBudget.Session budget = OpenSpecIngestionBudgets.open(root);
        var current = currentReader.read(root, projectId, identityResolver, budget);
        var changes = changeReader.read(root, projectId, identityResolver, budget);
        var deltas = requirementDeltaReader.read(root, identityResolver, budget);

        if (!current.project().equals(changes.project())) {
            throw new IllegalStateException("OpenSpec readers produced inconsistent project descriptors");
        }

        var evidence = new ArrayList<>(current.evidence());
        evidence.addAll(changes.evidence());
        evidence.addAll(deltas.evidence());

        List<Diagnostic> diagnostics = new ArrayList<>();
        diagnostics.addAll(current.diagnostics());
        changes.diagnostics().stream().filter(item -> !diagnostics.contains(item)).forEach(diagnostics::add);
        deltas.diagnostics().stream().filter(item -> !diagnostics.contains(item)).forEach(diagnostics::add);

        return new NormalizedProjectContent(
                current.project(),
                current.specifications(),
                current.requirements(),
                current.scenarios(),
                changes.changes(),
                deltas.requirementDeltas(),
                changes.constraints(),
                changes.designDecisions(),
                changes.tasks(),
                evidence,
                diagnostics);
    }
}
