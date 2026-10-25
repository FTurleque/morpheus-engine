package com.morpheus.application.ingestion;

import com.morpheus.application.identity.EntityIdentityResolver;
import com.morpheus.application.read.ProviderReadRequest;
import com.morpheus.application.read.ProviderReadResult;
import com.morpheus.application.read.ReadCategory;
import com.morpheus.application.read.ReadCategoryReport;
import com.morpheus.application.read.ReadCategoryStatus;
import com.morpheus.application.read.SpecificationContentReader;
import com.morpheus.domain.diagnostic.Diagnostic;
import com.morpheus.domain.diagnostic.DiagnosticCode;
import com.morpheus.domain.diagnostic.DiagnosticSeverity;
import com.morpheus.domain.project.ProjectSpecificationId;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Reads the content of a project through the provider port, and keeps it only when a publication may store it.
 *
 * <p>The content is publishable when the provider returned it and no requested category was {@code FAILED}, or
 * {@code PARTIAL} because a source was rejected ({@link DiagnosticCode#INVALID_SOURCE} among the codes of its report);
 * a missing report counts as an unread category. A category read partially for another reason, such as a requirement
 * the provider could not normalize, is published with the diagnostics that name what was left out. A category the
 * provider cannot carry ({@code UNSUPPORTED}) is published too, and a warning names it when the provider's own
 * diagnostics do not.</p>
 */
public final class ProjectContentIngestion {
    /** The categories a publication stores. */
    public static final Set<ReadCategory> PUBLISHED_CATEGORIES = Collections.unmodifiableSet(EnumSet.of(
            ReadCategory.CURRENT_SPECIFICATIONS,
            ReadCategory.REQUIREMENTS,
            ReadCategory.SCENARIOS,
            ReadCategory.CHANGES,
            ReadCategory.REQUIREMENT_DELTAS,
            ReadCategory.CONSTRAINTS,
            ReadCategory.DESIGN_DECISIONS,
            ReadCategory.IMPLEMENTATION_TASKS));

    private final SpecificationContentReader reader;

    public ProjectContentIngestion(SpecificationContentReader reader) {
        this.reader = Objects.requireNonNull(reader, "reader");
    }

    /**
     * The publishable content of the workspace.
     *
     * @throws IncompleteProjectReadException when the provider returned no content, or a category was not read
     */
    public NormalizedProjectContent read(
            Path workspaceRoot,
            ProjectSpecificationId projectId,
            EntityIdentityResolver identityResolver) {
        Objects.requireNonNull(identityResolver, "identityResolver");
        ProviderReadResult result = reader.read(
                new ProviderReadRequest(workspaceRoot, projectId, PUBLISHED_CATEGORIES), identityResolver);
        List<IncompleteProjectReadException.UnreadCategory> unread = new ArrayList<>();
        List<ReadCategory> unsupported = new ArrayList<>();
        for (ReadCategory category : ReadCategory.values()) {
            if (!PUBLISHED_CATEGORIES.contains(category)) {
                continue;
            }
            Optional<ReadCategoryReport> report = result.report(category);
            if (report.isEmpty() || refuses(report.get())) {
                unread.add(new IncompleteProjectReadException.UnreadCategory(
                        category, report.map(ReadCategoryReport::status)));
            } else if (report.get().status() == ReadCategoryStatus.UNSUPPORTED) {
                unsupported.add(category);
            }
        }
        if (result.content().isEmpty() || !unread.isEmpty()) {
            throw new IncompleteProjectReadException(
                    result.providerId(), result.content().isPresent(), unread, errorsOf(result));
        }
        return namingUnsupported(result, result.content().get(), unsupported);
    }

    private static boolean refuses(ReadCategoryReport report) {
        return report.status() == ReadCategoryStatus.FAILED
                || report.status() == ReadCategoryStatus.PARTIAL
                && report.diagnosticCodes().contains(DiagnosticCode.INVALID_SOURCE);
    }

    private static List<String> errorsOf(ProviderReadResult result) {
        return result.diagnostics().stream()
                .filter(diagnostic -> diagnostic.severity() == DiagnosticSeverity.ERROR)
                .map(Diagnostic::message)
                .distinct()
                .toList();
    }

    private static NormalizedProjectContent namingUnsupported(
            ProviderReadResult result, NormalizedProjectContent content, List<ReadCategory> unsupported) {
        List<Diagnostic> diagnostics = new ArrayList<>(content.diagnostics());
        for (ReadCategory category : unsupported) {
            boolean named = diagnostics.stream().anyMatch(diagnostic ->
                    diagnostic.code() == DiagnosticCode.OPTIONAL_CAPABILITY_UNAVAILABLE
                            && category.name().equals(diagnostic.details().get("category")));
            if (!named) {
                diagnostics.add(Diagnostic.warning(
                        DiagnosticCode.OPTIONAL_CAPABILITY_UNAVAILABLE,
                        "Requested read category is unavailable from this provider contract",
                        Map.of("provider", result.providerId().value(), "category", category.name())));
            }
        }
        if (diagnostics.size() == content.diagnostics().size()) {
            return content;
        }
        return new NormalizedProjectContent(
                content.project(),
                content.specifications(),
                content.requirements(),
                content.scenarios(),
                content.changes(),
                content.requirementDeltas(),
                content.constraints(),
                content.designDecisions(),
                content.tasks(),
                content.acceptanceCriteria(),
                content.evidence(),
                diagnostics);
    }
}
