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
import com.morpheus.domain.identity.DomainIdentity;
import com.morpheus.domain.project.ProjectSpecification;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.provider.ProviderId;
import com.morpheus.domain.source.SourceLocator;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectContentIngestionTest {
    private static final ProviderId PROVIDER = new ProviderId("fake");
    private static final Path WORKSPACE = Path.of("workspace").toAbsolutePath();
    private static final ProjectSpecificationId PROJECT = ProjectSpecificationId.generate();
    private static final EntityIdentityResolver RESOLVER = (providerId, entityType, externalId) ->
            DomainIdentity.generate();

    @Test
    void aReadWhoseCategoriesAreReadOrAbsentIsPublishedAsTheProviderReturnedIt() {
        NormalizedProjectContent content = content(List.of());
        Map<ReadCategory, ReadCategoryStatus> statuses = allPublished(ReadCategoryStatus.READ);
        statuses.put(ReadCategory.CHANGES, ReadCategoryStatus.ABSENT);
        AtomicReference<ProviderReadRequest> asked = new AtomicReference<>();

        NormalizedProjectContent published = new ProjectContentIngestion(reader(Optional.of(content), statuses, asked))
                .read(WORKSPACE, PROJECT, RESOLVER);

        assertSame(content, published);
        assertEquals(ProjectContentIngestion.PUBLISHED_CATEGORIES, asked.get().requestedCategories());
        assertEquals(WORKSPACE.normalize(), asked.get().workspaceRoot());
        assertEquals(PROJECT, asked.get().projectId());
    }

    @Test
    void aFailedCategoryRefusesThePublicationAndNamesIt() {
        Map<ReadCategory, ReadCategoryStatus> statuses = allPublished(ReadCategoryStatus.READ);
        statuses.put(ReadCategory.CHANGES, ReadCategoryStatus.FAILED);
        statuses.put(ReadCategory.CONSTRAINTS, ReadCategoryStatus.FAILED);

        IncompleteProjectReadException refused = assertThrows(IncompleteProjectReadException.class,
                () -> new ProjectContentIngestion(reader(Optional.of(content(List.of())), statuses, null))
                        .read(WORKSPACE, PROJECT, RESOLVER));

        assertEquals("project read is incomplete, nothing was published: provider fake did not read "
                + "CHANGES=FAILED, CONSTRAINTS=FAILED", refused.getMessage());
        assertEquals(List.of(
                new IncompleteProjectReadException.UnreadCategory(ReadCategory.CHANGES, Optional.of(ReadCategoryStatus.FAILED)),
                new IncompleteProjectReadException.UnreadCategory(ReadCategory.CONSTRAINTS, Optional.of(ReadCategoryStatus.FAILED))),
                refused.unreadCategories());
        assertFalse(refused.getMessage().contains(WORKSPACE.toString()));
    }

    @Test
    void aCategoryPartialBecauseASourceWasRejectedRefusesThePublicationAndRelaysTheProviderErrors() {
        Map<ReadCategory, ReadCategoryStatus> statuses = allPublished(ReadCategoryStatus.READ);
        statuses.put(ReadCategory.CHANGES, ReadCategoryStatus.PARTIAL);
        List<Diagnostic> errors = List.of(
                Diagnostic.error(DiagnosticCode.INVALID_SOURCE, "openspec/changes/a/proposal.md: no intent", Map.of()),
                Diagnostic.warning(DiagnosticCode.PARTIAL_INGESTION, "a warning is not relayed", Map.of()));

        IncompleteProjectReadException refused = assertThrows(IncompleteProjectReadException.class,
                () -> new ProjectContentIngestion(reader(Optional.of(content(List.of())), statuses,
                        Map.of(ReadCategory.CHANGES, List.of(DiagnosticCode.PARTIAL_INGESTION, DiagnosticCode.INVALID_SOURCE)),
                        errors, null))
                        .read(WORKSPACE, PROJECT, RESOLVER));

        assertEquals("project read is incomplete, nothing was published: provider fake did not read CHANGES=PARTIAL; "
                + "errors: openspec/changes/a/proposal.md: no intent", refused.getMessage());
    }

    @Test
    void aCategoryPartialForAnotherReasonIsPublishedWithItsDiagnostics() {
        Map<ReadCategory, ReadCategoryStatus> statuses = allPublished(ReadCategoryStatus.READ);
        statuses.put(ReadCategory.REQUIREMENT_DELTAS, ReadCategoryStatus.PARTIAL);
        Diagnostic skipped = Diagnostic.warning(DiagnosticCode.PARTIAL_INGESTION, "skipped requirement", Map.of());
        NormalizedProjectContent content = content(List.of(skipped));

        NormalizedProjectContent published = new ProjectContentIngestion(reader(Optional.of(content), statuses,
                Map.of(ReadCategory.REQUIREMENT_DELTAS, List.of(DiagnosticCode.PARTIAL_INGESTION)), List.of(), null))
                .read(WORKSPACE, PROJECT, RESOLVER);

        assertSame(content, published);
    }

    @Test
    void theRefusalRelaysAtMostFiveDistinctErrorsAndCountsTheRest() {
        List<Diagnostic> errors = new ArrayList<>();
        for (int index = 1; index <= 7; index++) {
            errors.add(Diagnostic.error(DiagnosticCode.INVALID_SOURCE, "error " + index, Map.of()));
        }
        errors.add(Diagnostic.error(DiagnosticCode.INVALID_SOURCE, "error 1", Map.of("again", "yes")));

        IncompleteProjectReadException refused = assertThrows(IncompleteProjectReadException.class,
                () -> new ProjectContentIngestion(reader(Optional.empty(), allPublished(ReadCategoryStatus.ABSENT),
                        Map.of(), errors, null)).read(WORKSPACE, PROJECT, RESOLVER));

        assertEquals("project read is incomplete, nothing was published: provider fake returned no content; "
                + "errors: error 1; error 2; error 3; error 4; error 5; and 2 more", refused.getMessage());
    }

    @Test
    void aReadWithoutContentRefusesThePublicationEvenWhenEveryCategoryWasReported() {
        IncompleteProjectReadException refused = assertThrows(IncompleteProjectReadException.class,
                () -> new ProjectContentIngestion(reader(Optional.empty(), allPublished(ReadCategoryStatus.ABSENT), null))
                        .read(WORKSPACE, PROJECT, RESOLVER));

        assertEquals("project read is incomplete, nothing was published: provider fake returned no content",
                refused.getMessage());
        assertEquals(List.of(), refused.unreadCategories());
    }

    @Test
    void aReadWithoutContentNamesTheCategoriesItFailed() {
        Map<ReadCategory, ReadCategoryStatus> statuses = allPublished(ReadCategoryStatus.FAILED);

        IncompleteProjectReadException refused = assertThrows(IncompleteProjectReadException.class,
                () -> new ProjectContentIngestion(reader(Optional.empty(), statuses, null))
                        .read(WORKSPACE, PROJECT, RESOLVER));

        assertTrue(refused.getMessage().startsWith("project read is incomplete, nothing was published: provider fake "
                + "returned no content; unread categories CURRENT_SPECIFICATIONS=FAILED, "), refused.getMessage());
        assertEquals(ProjectContentIngestion.PUBLISHED_CATEGORIES.size(), refused.unreadCategories().size());
    }

    @Test
    void aCategoryTheProviderDidNotReportIsUnread() {
        Map<ReadCategory, ReadCategoryStatus> statuses = allPublished(ReadCategoryStatus.READ);
        statuses.remove(ReadCategory.REQUIREMENT_DELTAS);

        IncompleteProjectReadException refused = assertThrows(IncompleteProjectReadException.class,
                () -> new ProjectContentIngestion(reader(Optional.of(content(List.of())), statuses, null))
                        .read(WORKSPACE, PROJECT, RESOLVER));

        assertEquals("project read is incomplete, nothing was published: provider fake did not read "
                + "REQUIREMENT_DELTAS=UNREPORTED", refused.getMessage());
        assertEquals(List.of(new IncompleteProjectReadException.UnreadCategory(
                ReadCategory.REQUIREMENT_DELTAS, Optional.empty())), refused.unreadCategories());
    }

    @Test
    void anUnsupportedCategoryIsPublishedAndNamedByAWarning() {
        Map<ReadCategory, ReadCategoryStatus> statuses = allPublished(ReadCategoryStatus.READ);
        statuses.put(ReadCategory.REQUIREMENT_DELTAS, ReadCategoryStatus.UNSUPPORTED);
        Diagnostic own = Diagnostic.warning(DiagnosticCode.PARTIAL_INGESTION, "unrelated", Map.of());

        NormalizedProjectContent published = new ProjectContentIngestion(
                reader(Optional.of(content(List.of(own))), statuses, null)).read(WORKSPACE, PROJECT, RESOLVER);

        assertEquals(List.of(own, Diagnostic.warning(
                DiagnosticCode.OPTIONAL_CAPABILITY_UNAVAILABLE,
                "Requested read category is unavailable from this provider contract",
                Map.of("provider", "fake", "category", "REQUIREMENT_DELTAS"))), published.diagnostics());
    }

    @Test
    void anUnsupportedCategoryTheProviderAlreadyNamedIsNotNamedTwice() {
        Map<ReadCategory, ReadCategoryStatus> statuses = allPublished(ReadCategoryStatus.READ);
        statuses.put(ReadCategory.DESIGN_DECISIONS, ReadCategoryStatus.UNSUPPORTED);
        Diagnostic named = Diagnostic.warning(DiagnosticCode.OPTIONAL_CAPABILITY_UNAVAILABLE,
                "the provider's own words", Map.of("category", "DESIGN_DECISIONS"));
        NormalizedProjectContent content = content(List.of(named));

        NormalizedProjectContent published = new ProjectContentIngestion(reader(Optional.of(content), statuses, null))
                .read(WORKSPACE, PROJECT, RESOLVER);

        assertSame(content, published);
    }

    private static Map<ReadCategory, ReadCategoryStatus> allPublished(ReadCategoryStatus status) {
        Map<ReadCategory, ReadCategoryStatus> statuses = new EnumMap<>(ReadCategory.class);
        ProjectContentIngestion.PUBLISHED_CATEGORIES.forEach(category -> statuses.put(category, status));
        return statuses;
    }

    private static NormalizedProjectContent content(List<Diagnostic> diagnostics) {
        return NormalizedProjectContent.currentSpecificationsOnly(
                new ProjectSpecification(PROJECT, "workspace", SourceLocator.file("/srv/workspace")),
                List.of(), List.of(), List.of(), List.of(), diagnostics);
    }

    private static SpecificationContentReader reader(
            Optional<NormalizedProjectContent> content,
            Map<ReadCategory, ReadCategoryStatus> statuses,
            AtomicReference<ProviderReadRequest> asked) {
        return reader(content, statuses, Map.of(), List.of(), asked);
    }

    private static SpecificationContentReader reader(
            Optional<NormalizedProjectContent> content,
            Map<ReadCategory, ReadCategoryStatus> statuses,
            Map<ReadCategory, List<DiagnosticCode>> codes,
            List<Diagnostic> diagnostics,
            AtomicReference<ProviderReadRequest> asked) {
        return new SpecificationContentReader() {
            @Override
            public ProviderId providerId() {
                return PROVIDER;
            }

            @Override
            public ProviderReadResult read(ProviderReadRequest request, EntityIdentityResolver identityResolver) {
                if (asked != null) {
                    asked.set(request);
                }
                List<ReadCategoryReport> reports = new ArrayList<>();
                statuses.forEach((category, status) -> reports.add(new ReadCategoryReport(
                        category, status, 0, codes.getOrDefault(category, List.of()), Optional.empty())));
                return new ProviderReadResult(PROVIDER, content, reports, diagnostics);
            }
        };
    }
}
