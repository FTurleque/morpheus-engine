package com.morpheus.provider.reference;

import com.morpheus.application.identity.EntityIdentityResolver;
import com.morpheus.application.ingestion.NormalizedProjectContent;
import com.morpheus.application.read.ProviderReadRequest;
import com.morpheus.application.read.ProviderReadResult;
import com.morpheus.application.read.ReadCategory;
import com.morpheus.application.read.ReadCategoryReport;
import com.morpheus.application.read.ReadCategoryStatus;
import com.morpheus.application.read.SpecificationContentReader;
import com.morpheus.domain.evidence.Evidence;
import com.morpheus.domain.evidence.EvidenceId;
import com.morpheus.domain.project.ProjectSpecification;
import com.morpheus.domain.provenance.Provenance;
import com.morpheus.domain.provider.ProviderId;
import com.morpheus.domain.source.SourceLocator;
import com.morpheus.domain.specification.Specification;
import com.morpheus.domain.specification.SpecificationId;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Minimal real provider-neutral content reader used as the M22 external-provider template. */
public final class ReferenceSpecificationContentReader implements SpecificationContentReader {
    @Override
    public ProviderId providerId() {
        return ReferenceSpecificationProvider.ID;
    }

    @Override
    public ProviderReadResult read(ProviderReadRequest request, EntityIdentityResolver identityResolver) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(identityResolver, "identityResolver");

        Path workspace = request.workspaceRoot();
        Path marker = workspace.resolve(ReferenceSpecificationProvider.MARKER_FILE);
        boolean present = Files.isRegularFile(marker);
        SourceLocator markerSource = SourceLocator.file(ReferenceSpecificationProvider.MARKER_FILE);

        List<Evidence> evidence;
        List<Specification> specifications;
        if (present) {
            EvidenceId evidenceId = new EvidenceId(identityResolver.resolve(
                    providerId(), "evidence", ReferenceSpecificationProvider.MARKER_FILE));
            Evidence item = new Evidence(evidenceId, markerSource, Optional.empty(), Optional.empty());
            SpecificationId specificationId = new SpecificationId(identityResolver.resolve(
                    providerId(), "specification", "reference-current"));
            Provenance provenance = new Provenance(
                    providerId(),
                    Optional.of(ReferenceSpecificationProvider.VERSION),
                    markerSource,
                    Optional.of("reference-current"),
                    Optional.empty(),
                    evidenceId);
            Specification specification = new Specification(
                    specificationId,
                    request.projectId(),
                    "reference-current",
                    "MORPHEUS Reference Specification",
                    Optional.of("Reference provider content emitted through the provider-neutral M22 SDK read contract."),
                    provenance);
            evidence = List.of(item);
            specifications = List.of(specification);
        } else {
            evidence = List.of();
            specifications = List.of();
        }

        ProjectSpecification project = new ProjectSpecification(
                request.projectId(),
                "MORPHEUS Reference Project",
                SourceLocator.file(workspace.toString()));
        NormalizedProjectContent content = new NormalizedProjectContent(
                project,
                specifications,
                List.of(),
                List.of(),
                evidence,
                List.of());

        List<ReadCategoryReport> reports = request.requestedCategories().stream()
                .sorted(Comparator.comparingInt(Enum::ordinal))
                .map(category -> report(category, present))
                .toList();

        return new ProviderReadResult(providerId(), Optional.of(content), reports, List.of());
    }

    private static ReadCategoryReport report(ReadCategory category, boolean markerPresent) {
        if (category == ReadCategory.CURRENT_SPECIFICATIONS) {
            return ReadCategoryReport.of(
                    category,
                    markerPresent ? ReadCategoryStatus.READ : ReadCategoryStatus.ABSENT,
                    markerPresent ? 1 : 0);
        }
        return ReadCategoryReport.of(category, ReadCategoryStatus.UNSUPPORTED, 0);
    }
}
