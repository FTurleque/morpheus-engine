package com.morpheus.application.composition;

import com.morpheus.application.identity.EntityIdentityResolver;
import com.morpheus.application.read.NormalizedProjectContent;
import com.morpheus.application.operability.OperationalEventSink;
import com.morpheus.application.operability.OperationalExecution;
import com.morpheus.application.operability.OperationalMetrics;
import com.morpheus.application.operability.OperationalRecorder;
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
import com.morpheus.domain.snapshot.KnowledgeSnapshotId;
import com.morpheus.domain.source.SourceLocator;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reading several providers is coordination only: each registered reader is observed, an unregistered one becomes an
 * explicit unavailable contribution, and every merge decision is left to the composition service.
 */
class MultiProviderReadServiceTest {
    private static final ProviderId HIGH = new ProviderId("high");
    private static final ProviderId LOW = new ProviderId("low");
    private static final ProviderId ABSENT = new ProviderId("absent");
    private static final ProjectSpecificationId PROJECT = ProjectSpecificationId.generate();
    private static final EntityIdentityResolver RESOLVER = (provider, type, externalId) -> DomainIdentity.generate();

    private final OperationalMetrics metrics = new OperationalMetrics();
    private final OperationalExecution execution =
            new OperationalExecution(new OperationalRecorder(OperationalEventSink.noop(), metrics));

    @Test
    void twoReadersOfOneProviderAreRefusedByName() {
        List<SpecificationContentReader> readers = List.of(reader(HIGH, "One"), reader(HIGH, "Two"));
        MultiProviderCompositionService composition = new MultiProviderCompositionService();

        IllegalArgumentException refusal = assertThrows(IllegalArgumentException.class,
                () -> new MultiProviderReadService(readers, composition, execution));

        assertEquals("duplicate content reader: " + HIGH, refusal.getMessage());
    }

    @Test
    void aReadWithoutAnySourceIsRefused() {
        MultiProviderReadService service = service(reader(HIGH, "Project"));
        ProviderReadRequest request = request();

        IllegalArgumentException refusal = assertThrows(IllegalArgumentException.class,
                () -> service.read(request, RESOLVER, List.of()));

        assertEquals("at least one composition source is required", refusal.getMessage());
    }

    @Test
    void everyRegisteredReaderIsReadAndObservedAndTheDivergenceIsComposed() {
        MultiProviderReadService service = service(reader(HIGH, "High name"), reader(LOW, "Low name"));

        MultiProviderCompositionResult result = service.read(request(), RESOLVER, List.of(
                new ProviderCompositionSource(LOW, 10, false),
                new ProviderCompositionSource(HIGH, 90, true)));

        assertEquals(HIGH, result.primaryProviderId());
        assertEquals(List.of(HIGH, LOW), result.contributions().stream().map(ProviderContribution::providerId).toList());
        assertTrue(result.conflicts().stream().anyMatch(conflict ->
                conflict.entityType() == CompositionEntityType.PROJECT
                        && conflict.field().equals("displayName")
                        && conflict.selectedProviderId().equals(Optional.of(HIGH))));
        assertEquals(2L, metrics.snapshot().counters().get("provider.read.success"));
    }

    @Test
    void anOptionalProviderWithoutAReaderIsAnExplicitlyAbsentContribution() {
        MultiProviderReadService service = service(reader(HIGH, "Project"));

        MultiProviderCompositionResult result = service.read(request(), RESOLVER, List.of(
                new ProviderCompositionSource(HIGH, 90, true),
                new ProviderCompositionSource(ABSENT, 10, false)));

        ProviderContribution absent = result.contributions().stream()
                .filter(contribution -> contribution.providerId().equals(ABSENT)).findFirst().orElseThrow();
        assertFalse(absent.available());
        assertFalse(absent.required());
        ProviderReadResult read = absent.readResult();
        assertEquals(List.of(ReadCategory.CURRENT_SPECIFICATIONS, ReadCategory.REQUIREMENTS),
                read.categoryReports().stream().map(ReadCategoryReport::category).toList());
        assertTrue(read.categoryReports().stream().allMatch(report -> report.status() == ReadCategoryStatus.ABSENT
                && report.diagnosticCodes().equals(List.of(DiagnosticCode.OPTIONAL_CAPABILITY_UNAVAILABLE))
                && report.detail().equals(Optional.of("content reader is not registered"))));
        Diagnostic diagnostic = read.diagnostics().getFirst();
        assertEquals(DiagnosticCode.OPTIONAL_CAPABILITY_UNAVAILABLE, diagnostic.code());
        assertEquals("false", diagnostic.details().get("required"));
        assertEquals("absent", diagnostic.details().get("provider"));
    }

    @Test
    void aRequiredProviderWithoutAReaderFailsTheComposition() {
        MultiProviderReadService service = service(reader(HIGH, "Project"));
        ProviderReadRequest request = request();
        List<ProviderCompositionSource> sources = List.of(
                new ProviderCompositionSource(HIGH, 90, true),
                new ProviderCompositionSource(ABSENT, 10, true));

        IllegalStateException refusal = assertThrows(IllegalStateException.class,
                () -> service.read(request, RESOLVER, sources));

        assertEquals("required provider contribution is unavailable: " + ABSENT, refusal.getMessage());
    }

    @Test
    void aReaderAnsweringForAnotherProviderIsRefusedByTheLoader() {
        SpecificationContentReader impostor = new SpecificationContentReader() {
            @Override
            public ProviderId providerId() {
                return HIGH;
            }

            @Override
            public ProviderReadResult read(ProviderReadRequest request, EntityIdentityResolver identityResolver) {
                return result(LOW, "Project");
            }
        };
        MultiProviderReadService service = service(impostor);
        ProviderReadRequest request = request();
        List<ProviderCompositionSource> sources = List.of(new ProviderCompositionSource(HIGH, 90, true));

        IllegalArgumentException refusal = assertThrows(IllegalArgumentException.class,
                () -> service.read(request, RESOLVER, sources));

        assertEquals("provider read result identity does not match contribution provider: " + HIGH,
                refusal.getMessage());
    }

    @Test
    void aFailingReadIsCountedAsAProviderFailureAndPropagated() {
        ObservedProviderContributionLoader loader = new ObservedProviderContributionLoader(execution);

        IllegalStateException failure = assertThrows(IllegalStateException.class, () -> loader.load(
                HIGH, 1, true, () -> {
                    throw new IllegalStateException("provider unreadable");
                }));

        assertEquals("provider unreadable", failure.getMessage());
        assertEquals(1L, metrics.snapshot().counters().get("provider.read.failure"));
    }

    @Test
    void theCompositionStateIsOrderedAndItsPublicViewCarriesEveryProviderAndConflict() {
        MultiProviderReadService service = service(reader(HIGH, "High name"), reader(LOW, "Low name"));
        MultiProviderCompositionResult result = service.read(request(), RESOLVER, List.of(
                new ProviderCompositionSource(LOW, 10, false),
                new ProviderCompositionSource(HIGH, 90, true)));
        KnowledgeSnapshotId snapshotId = KnowledgeSnapshotId.generate();

        CompositionSnapshotState state = CompositionSnapshotState.from(snapshotId, result);
        CompositionStateView view = CompositionStateView.from(state);

        assertEquals(List.of(HIGH, LOW), state.providers().stream().map(CompositionProviderState::providerId).toList());
        assertEquals(new CompositionProviderState(LOW, 10, false, true, 0), state.providers().get(1));
        assertEquals(snapshotId.toString(), view.snapshotId());
        assertEquals("high", view.primaryProviderId());
        assertEquals(new CompositionStateView.ProviderView("high", 90, true, true, 0), view.providers().getFirst());
        assertEquals(state.conflicts().size(), view.conflicts().size());
        CompositionStateView.ConflictView displayName = view.conflicts().stream()
                .filter(conflict -> conflict.field().equals("displayName")).findFirst().orElseThrow();
        assertEquals("PROJECT", displayName.entityType());
        assertEquals("PRECEDENCE_RECORDED", displayName.resolution());
        assertEquals(Optional.of("high"), displayName.selectedProviderId());
        assertEquals(List.of("High name", "Low name"),
                displayName.candidates().stream().map(CompositionStateView.CandidateView::value).toList());
        assertEquals(List.of(90, 10),
                displayName.candidates().stream().map(CompositionStateView.CandidateView::priority).toList());
    }

    @Test
    void aStateWhosePrimaryProviderDidNotTakePartIsRefused() {
        KnowledgeSnapshotId snapshotId = KnowledgeSnapshotId.generate();
        List<CompositionProviderState> providers = List.of(new CompositionProviderState(LOW, 1, false, true, 0));

        IllegalArgumentException refusal = assertThrows(IllegalArgumentException.class,
                () -> new CompositionSnapshotState(snapshotId, HIGH, providers, List.of()));

        assertEquals("primary provider must be present in providers", refusal.getMessage());
    }

    @Test
    void aNegativeDiagnosticCountAndABlankViewIdentityAreRefused() {
        IllegalArgumentException negative = assertThrows(IllegalArgumentException.class,
                () -> new CompositionProviderState(HIGH, 1, true, true, -1));
        IllegalArgumentException blank = assertThrows(IllegalArgumentException.class,
                () -> new CompositionStateView(" ", "high", List.of(), List.of()));

        assertEquals("diagnosticCount must be non-negative", negative.getMessage());
        assertEquals("snapshotId must not be blank", blank.getMessage());
    }

    @Test
    void aStateCountsTheDiagnosticsOfItsContribution() {
        Diagnostic warning = Diagnostic.warning(DiagnosticCode.UNSUPPORTED_SOURCE, "ignored file", Map.of());
        ProviderReadResult read = new ProviderReadResult(
                HIGH, Optional.of(content("Project")), List.of(), List.of(warning, warning));

        CompositionProviderState state = CompositionProviderState.from(new ProviderContribution(HIGH, 5, true, read));

        assertEquals(new CompositionProviderState(HIGH, 5, true, true, 2), state);
    }

    private MultiProviderReadService service(SpecificationContentReader... readers) {
        return new MultiProviderReadService(List.of(readers), new MultiProviderCompositionService(), execution);
    }

    private static ProviderReadRequest request() {
        return new ProviderReadRequest(Path.of("workspace"), PROJECT,
                Set.of(ReadCategory.REQUIREMENTS, ReadCategory.CURRENT_SPECIFICATIONS));
    }

    private static SpecificationContentReader reader(ProviderId provider, String projectName) {
        return new SpecificationContentReader() {
            @Override
            public ProviderId providerId() {
                return provider;
            }

            @Override
            public ProviderReadResult read(ProviderReadRequest request, EntityIdentityResolver identityResolver) {
                return result(provider, projectName);
            }
        };
    }

    private static ProviderReadResult result(ProviderId provider, String projectName) {
        return new ProviderReadResult(provider, Optional.of(content(projectName)), List.of(), List.of());
    }

    private static NormalizedProjectContent content(String projectName) {
        return NormalizedProjectContent.currentSpecificationsOnly(
                new ProjectSpecification(PROJECT, projectName, SourceLocator.file("workspace")),
                List.of(), List.of(), List.of(), List.of(), List.of());
    }
}
