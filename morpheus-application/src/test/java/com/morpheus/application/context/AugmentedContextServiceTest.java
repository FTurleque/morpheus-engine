package com.morpheus.application.context;

import com.morpheus.application.operability.OperationalEventSink;
import com.morpheus.application.operability.OperationalExecution;
import com.morpheus.application.operability.OperationalMetrics;
import com.morpheus.application.operability.OperationalRecorder;
import com.morpheus.application.reference.ExternalIntegrationStatus;
import com.morpheus.application.store.ExternalReferenceStore;
import com.morpheus.application.store.InMemorySnapshotStores;
import com.morpheus.application.store.KnowledgeStoreException;
import com.morpheus.application.store.RequirementVersionRecord;
import com.morpheus.application.store.SnapshotBusinessContent;
import com.morpheus.domain.change.ChangeId;
import com.morpheus.domain.change.ChangeProposal;
import com.morpheus.domain.constraint.Constraint;
import com.morpheus.domain.constraint.ConstraintId;
import com.morpheus.domain.decision.DesignDecision;
import com.morpheus.domain.decision.DesignDecisionId;
import com.morpheus.domain.evidence.Evidence;
import com.morpheus.domain.evidence.EvidenceId;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.provenance.Provenance;
import com.morpheus.domain.provider.ProviderId;
import com.morpheus.domain.requirement.Requirement;
import com.morpheus.domain.requirement.RequirementId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotMetadata;
import com.morpheus.domain.snapshot.KnowledgeSnapshotState;
import com.morpheus.domain.source.SourceLocator;
import com.morpheus.domain.specification.SpecificationId;
import com.morpheus.domain.task.ImplementationTask;
import com.morpheus.domain.task.TaskId;
import com.morpheus.domain.temporal.TemporalState;
import com.morpheus.domain.traceability.TraceabilityEntityKind;
import com.morpheus.domain.traceability.TraceabilityEntityRef;
import com.morpheus.domain.traceability.TraceabilityLink;
import com.morpheus.domain.traceability.TraceabilityLinkId;
import com.morpheus.domain.traceability.TraceabilityLinkOrigin;
import com.morpheus.domain.traceability.TraceabilityRelationType;
import com.morpheus.domain.traceability.TraceabilityResolutionState;
import com.morpheus.domain.version.EntityVersion;
import com.morpheus.domain.version.EntityVersionId;
import com.morpheus.domain.version.SpecificationVersionId;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MORPHEUS owns the intent seed and nothing else: it states the requirement or the change from the ACTIVE snapshot,
 * hands the bounded query to the external engine through an observed call, and never persists what comes back.
 */
class AugmentedContextServiceTest {
    private static final ExternalIntegrationStatus AVAILABLE =
            new ExternalIntegrationStatus("engine", "AVAILABLE", true, "ready", Map.of());

    private final ProjectSpecificationId projectId = ProjectSpecificationId.generate();
    private final InMemorySnapshotStores stores = new InMemorySnapshotStores();
    private final OperationalMetrics metrics = new OperationalMetrics();
    private final RecordingProvider provider = new RecordingProvider();
    private final AugmentedContextService service = new AugmentedContextService(
            stores, stores, stores, stores, unusedExternalReferences(), provider,
            new OperationalExecution(new OperationalRecorder(OperationalEventSink.noop(), metrics)));
    private final Evidence evidence = new Evidence(
            EvidenceId.generate(), SourceLocator.file("specs/change.md"), Optional.empty(), Optional.of("sha256:e"));
    private final KnowledgeSnapshotMetadata snapshot = new KnowledgeSnapshotMetadata(
            KnowledgeSnapshotId.generate(), projectId, Optional.empty(), KnowledgeSnapshotState.ACTIVE,
            Optional.of("rev-1"), Instant.parse("2026-10-30T08:00:00Z"));
    private final SpecificationVersionId versionId = SpecificationVersionId.generate();

    @Test
    void aRequirementIntentIsStatedFromTheActiveSnapshotAndSentThroughAnObservedCall() {
        stores.putSnapshot(snapshot);
        Requirement requirement = requirement("Session expiry", "Sessions expire after ten minutes.");
        putRequirement(requirement);

        AugmentedContextResult result = service.requirement(
                projectId, requirement.id(), TechnicalContextOptions.defaults("engine-project")).orElseThrow();

        MorpheusIntentContext intent = result.intentContext();
        assertEquals("REQUIREMENT", intent.subjectType());
        assertEquals(requirement.id().toString(), intent.subjectId());
        assertEquals(Optional.of("R-1"), intent.key());
        assertEquals("Requirement: R-1 Session expiry\nStatement: Sessions expire after ten minutes.", intent.query());
        assertEquals(List.of(intent.query()), provider.queries);
        assertEquals(Optional.of("rev-1"), result.snapshot().sourceRevision());
        assertEquals("ACTIVE", result.snapshot().state());
        assertEquals(snapshot.id().toString(), result.snapshot().id());
        assertEquals("2026-10-30T08:00:00Z", result.snapshot().createdAt());
        assertTrue(result.technicalContext().bundle().isPresent());
        assertFalse(result.persisted());
        assertEquals(1L, metrics.snapshot().counters().get("external.technical_context.success"));
    }

    @Test
    void aProjectWithoutAnActiveSnapshotHasNoContextAndCallsNoEngine() {
        RequirementId requirementId = RequirementId.generate();

        Optional<AugmentedContextResult> result = service.requirement(
                projectId, requirementId, TechnicalContextOptions.defaults("engine-project"));

        assertEquals(Optional.empty(), result);
        assertEquals(List.of(), provider.queries);
    }

    @Test
    void aRequirementAbsentFromTheActiveSnapshotIsRefusedByName() {
        stores.putSnapshot(snapshot);
        RequirementId missing = RequirementId.generate();
        TechnicalContextOptions options = TechnicalContextOptions.defaults("engine-project");

        KnowledgeStoreException refusal = assertThrows(KnowledgeStoreException.class,
                () -> service.requirement(projectId, missing, options));

        assertEquals("requirement not found in ACTIVE snapshot: " + missing, refusal.getMessage());
    }

    @Test
    void anOverlongIntentIsTrimmedToTheQueryBudgetAndSaysSo() {
        stores.putSnapshot(snapshot);
        Requirement requirement = requirement("Long", "x".repeat(AugmentedContextService.MAX_INTENT_QUERY_CHARS));
        putRequirement(requirement);

        String query = service.requirement(projectId, requirement.id(), TechnicalContextOptions.defaults("p"))
                .orElseThrow().intentContext().query();

        assertEquals(AugmentedContextService.MAX_INTENT_QUERY_CHARS, query.length());
        assertTrue(query.endsWith("\n[context trimmed]"), query.substring(query.length() - 40));
    }

    @Test
    void aChangeIntentGathersItsAffectedRequirementsConstraintsDecisionsAndTasks() {
        stores.putSnapshot(snapshot);
        Requirement requirement = requirement("Session expiry", "Sessions expire.");
        putRequirement(requirement);
        ChangeProposal change = new ChangeProposal(
                ChangeId.generate(), projectId, Optional.of("C-1"), "Shorter sessions", "Reduce exposure",
                List.of("auth"), List.of(), List.of(), provenance());
        Constraint constraint = Constraint.withUnknownSemantics(
                ConstraintId.generate(), change.id(), "No downtime", provenance());
        DesignDecision decision = new DesignDecision(
                DesignDecisionId.generate(), change.id(), "Token TTL", "Use a ten minute TTL", provenance());
        ImplementationTask pending = new ImplementationTask(
                TaskId.generate(), change.id(), Optional.of("T-1"), "Shorten TTL", false, provenance());
        ImplementationTask done = new ImplementationTask(
                TaskId.generate(), change.id(), Optional.empty(), "Document TTL", true, provenance());
        stores.putSnapshotContent(new SnapshotBusinessContent(
                snapshot.id(), versionId, List.of(), List.of(), List.of(change), List.of(constraint),
                List.of(decision), List.of(pending, done), List.of(), List.of(evidence)));
        stores.putLink(snapshot.id(), new TraceabilityLink(
                TraceabilityLinkId.generate(),
                new TraceabilityEntityRef(TraceabilityEntityKind.CHANGE, change.id().value()),
                TraceabilityRelationType.AFFECTS,
                new TraceabilityEntityRef(TraceabilityEntityKind.REQUIREMENT, requirement.id().value()),
                TraceabilityLinkOrigin.EXPLICIT,
                TraceabilityResolutionState.RESOLVED,
                Optional.empty(),
                Set.of(evidence.id()),
                Instant.parse("2026-10-30T08:00:00Z")));

        AugmentedContextResult result = service.change(
                projectId, change.id(), TechnicalContextOptions.defaults("engine-project")).orElseThrow();

        MorpheusIntentContext intent = result.intentContext();
        assertEquals("CHANGE", intent.subjectType());
        assertEquals("Reduce exposure", intent.intent());
        assertEquals(List.of("auth"), intent.scope());
        assertEquals(List.of("R-1 Session expiry: Sessions expire."), intent.affectedRequirements());
        assertEquals(List.of("No downtime"), intent.constraints());
        assertEquals(List.of("Token TTL: Use a ten minute TTL"), intent.designDecisions());
        assertEquals(2, intent.implementationTasks().size());
        assertTrue(intent.implementationTasks().contains("T-1 Shorten TTL [pending]"));
        assertTrue(intent.implementationTasks().contains("Document TTL [completed]"));
        assertTrue(intent.query().startsWith("Change: C-1 Shorter sessions\nIntent: Reduce exposure\nScope: auth\n"
                + "Affected requirement: R-1 Session expiry: Sessions expire.\nConstraint: No downtime\n"
                + "Design decision: Token TTL: Use a ten minute TTL\n"), intent.query());
        assertEquals(List.of(intent.query()), provider.queries);
    }

    @Test
    void aChangeAbsentFromTheActiveSnapshotIsRefusedByName() {
        stores.putSnapshot(snapshot);
        stores.putSnapshotContent(new SnapshotBusinessContent(
                snapshot.id(), versionId, List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of()));
        ChangeId missing = ChangeId.generate();
        TechnicalContextOptions options = TechnicalContextOptions.defaults("engine-project");

        KnowledgeStoreException refusal = assertThrows(KnowledgeStoreException.class,
                () -> service.change(projectId, missing, options));

        assertEquals("change not found in ACTIVE snapshot: " + missing, refusal.getMessage());
    }

    @Test
    void aDisabledEngineAnswersUnavailableWithoutInventingContext() {
        DisabledTechnicalContextProvider disabled = new DisabledTechnicalContextProvider(" nexus ", "not configured");

        TechnicalContextObservation observation = disabled.build(
                new TechnicalContextRequest(" query ", TechnicalContextOptions.defaults("p")));

        assertEquals("nexus", disabled.system());
        assertEquals(new ExternalIntegrationStatus("nexus", "DISABLED", false, "not configured", Map.of()),
                observation.status());
        assertEquals(Optional.empty(), observation.bundle());
        IllegalArgumentException blank = assertThrows(IllegalArgumentException.class,
                () -> new DisabledTechnicalContextProvider("nexus", " "));
        assertEquals("message must not be blank", blank.getMessage());
    }

    @Test
    void aLiveResultCannotClaimToBePersisted() {
        AugmentedSnapshotView view = AugmentedSnapshotView.from(snapshot);
        MorpheusIntentContext intent = new MorpheusIntentContext(
                "REQUIREMENT", "id", Optional.of("  "), "Title", "Intent",
                List.of(), List.of(), List.of(), List.of(), List.of(), "query");
        TechnicalContextObservation technical = TechnicalContextObservation.unavailable(AVAILABLE);

        IllegalArgumentException refusal = assertThrows(IllegalArgumentException.class,
                () -> new AugmentedContextResult(view, intent, technical, true));

        assertEquals("M13 augmented context is a live non-persisted observation", refusal.getMessage());
        assertEquals(Optional.empty(), intent.key(), "a blank key is no key");
    }

    @Test
    void aSnapshotViewAndAnIntentRefuseBlankText() {
        IllegalArgumentException view = assertThrows(IllegalArgumentException.class,
                () -> new AugmentedSnapshotView("id", "project", Optional.empty(), " ", Optional.empty(), "now"));
        IllegalArgumentException intent = assertThrows(IllegalArgumentException.class,
                () -> new MorpheusIntentContext("CHANGE", "id", Optional.empty(), "Title", "Intent",
                        List.of(), List.of(), List.of(), List.of(), List.of(), " "));
        IllegalArgumentException request = assertThrows(IllegalArgumentException.class,
                () -> new TechnicalContextRequest("  ", TechnicalContextOptions.defaults("p")));

        assertEquals("state must not be blank", view.getMessage());
        assertEquals("query must not be blank", intent.getMessage());
        assertEquals("query must not be blank", request.getMessage());
    }

    @Test
    void aBundleStaysWithinItsTokenBudgetAndItsItemsHaveCoherentLines() {
        TechnicalContextItem item = item(3, 7);

        TechnicalContextBundle bundle = new TechnicalContextBundle(
                " project ", "Project", "query", true, 12L, 100, 40, List.of(item), List.of("tests"), Map.<String, Object>of("k", 1));

        assertEquals("project", bundle.projectId());
        assertEquals(List.of(item), bundle.items());
        assertEquals("", new TechnicalContextItem("FILE", "a.java", null, 0, 0, null, 0.5,
                Map.of(), List.of(), 0, false).content());
        IllegalArgumentException overBudget = assertThrows(IllegalArgumentException.class,
                () -> new TechnicalContextBundle("p", "P", "q", false, 0L, 10, 11, List.of(), List.of(), Map.of()));
        IllegalArgumentException noBudget = assertThrows(IllegalArgumentException.class,
                () -> new TechnicalContextBundle("p", "P", "q", false, 0L, 0, 0, List.of(), List.of(), Map.of()));
        IllegalArgumentException negativeDuration = assertThrows(IllegalArgumentException.class,
                () -> new TechnicalContextBundle("p", "P", "q", false, -1L, 10, 0, List.of(), List.of(), Map.of()));
        assertEquals("estimatedTokens must be between 0 and tokenBudget", overBudget.getMessage());
        assertEquals("tokenBudget must be > 0", noBudget.getMessage());
        assertEquals("durationMs must be >= 0", negativeDuration.getMessage());
    }

    @Test
    void anItemRefusesIncoherentLineRangesAndNegativeTokens() {
        IllegalArgumentException negative = assertThrows(IllegalArgumentException.class, () -> item(-1, 2));
        IllegalArgumentException half = assertThrows(IllegalArgumentException.class, () -> item(0, 2));
        IllegalArgumentException reversed = assertThrows(IllegalArgumentException.class, () -> item(5, 2));
        IllegalArgumentException tokens = assertThrows(IllegalArgumentException.class,
                () -> new TechnicalContextItem("FILE", "a.java", "A", 1, 1, "x", 1.0, Map.of(), List.of(), -1, false));

        assertEquals("line numbers must not be negative", negative.getMessage());
        assertEquals("startLine and endLine must both be zero or both be positive", half.getMessage());
        assertEquals("endLine must be >= startLine", reversed.getMessage());
        assertEquals("estimatedTokens must be >= 0", tokens.getMessage());
    }

    private static TechnicalContextItem item(int startLine, int endLine) {
        return new TechnicalContextItem("SYMBOL", "src/A.java", "A#run", startLine, endLine, "code", 0.9,
                Map.of("lexical", 0.4), List.of("named in the intent"), 12, false);
    }

    private Requirement requirement(String title, String statement) {
        return new Requirement(RequirementId.generate(), SpecificationId.generate(), Optional.of("R-1"),
                title, statement, provenance());
    }

    private void putRequirement(Requirement requirement) {
        stores.putRequirementVersion(new RequirementVersionRecord(snapshot.id(), new EntityVersion<>(
                EntityVersionId.generate(), requirement.id().value(), versionId, TemporalState.CURRENT, requirement)));
    }

    private Provenance provenance() {
        return new Provenance(new ProviderId("test-provider"), Optional.of("1"), SourceLocator.file("specs/change.md"),
                Optional.of("ext"), Optional.of("rev-1"), evidence.id());
    }

    /** No fixture here holds an external reference: reaching this port would mean a link the test did not write. */
    private static ExternalReferenceStore unusedExternalReferences() {
        return (ExternalReferenceStore) Proxy.newProxyInstance(
                AugmentedContextServiceTest.class.getClassLoader(),
                new Class<?>[]{ExternalReferenceStore.class},
                (proxy, method, args) -> {
                    throw new AssertionError("unexpected call ExternalReferenceStore." + method.getName());
                });
    }

    private static final class RecordingProvider implements TechnicalContextProvider {
        private final List<String> queries = new ArrayList<>();

        @Override
        public String system() {
            return "engine";
        }

        @Override
        public ExternalIntegrationStatus status() {
            return AVAILABLE;
        }

        @Override
        public TechnicalContextObservation build(TechnicalContextRequest request) {
            queries.add(request.query());
            return TechnicalContextObservation.available(AVAILABLE, new TechnicalContextBundle(
                    "engine-project", "Engine project", request.query(), request.options().explain(),
                    3L, request.options().tokenBudget(), 0, List.of(), List.of(), Map.of()));
        }
    }
}
