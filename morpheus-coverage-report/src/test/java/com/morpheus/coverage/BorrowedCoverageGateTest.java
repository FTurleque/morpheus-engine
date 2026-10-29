package com.morpheus.coverage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What each module's coverage owes to tests that live in another module.
 *
 * <p>The per-module scale credits a line from the tests of the module that owns it; the aggregate scale from any
 * execution of the reactor. Both measure the same population, so for every module the two reports count the same
 * lines and branches, and the aggregate covered count is a superset of the module's own. The difference is the
 * module's <em>borrowed credit</em>, counted in lines and in branches. It is not a difference of two ratios and it
 * never sits beside a coverage threshold: this gate reads no coverage key, and neither coverage gate reads this
 * quantity's keys or evidence ({@code CoverageScaleSeparationTest}).</p>
 *
 * <p>Two properties hold the credit down. A class whose executable lines are covered only by other modules' tests
 * is <em>wholly borrowed</em>, and the gate carries the exact list of them: an unlisted one fails, a listed one that
 * stopped being wholly borrowed fails until its entry leaves. And the total credit has a maximum, read from
 * {@code config/m21-quality-ratchets.properties}, that sits above the highest qualified measurement of both
 * platforms by a margin, and is only ever lowered.</p>
 *
 * <p>This is the only module in which both reports exist during a build: the aggregate report is written by
 * {@code report-aggregate} in this module's {@code verify} phase, after every other module has written its own.</p>
 */
class BorrowedCoverageGateTest {
    private static final String OWN_REPORT = "target/site/jacoco/jacoco.xml";
    private static final String AGGREGATE_REPORT = "morpheus-coverage-report/target/site/jacoco-aggregate/jacoco.xml";
    private static final String EVIDENCE = "morpheus-architecture-tests/target/m21-borrowed-coverage-summary.txt";
    private static final String LINE_MAXIMUM_KEY = "borrowedLineCreditMaximum";
    private static final String BRANCH_MAXIMUM_KEY = "borrowedBranchCreditMaximum";

    // Qualified measurement of the total borrowed credit: 5966 lines / 1506 branches, the HIGHEST of four
    // exact-head runs, never the lowest -- a maximum one platform cannot meet would fail that platform's builds.
    //
    // Measured on 29/10/2026 at develop 6b3f6304 (push run 40961140381) and at 4b02a8e3 (pull_request run
    // 40955178602, the pull request whose merge 6b3f6304 is: the two trees are identical). Read from each run's
    // m21-integrity-<OS> artifact, every per-module jacoco.xml against its group of the aggregate jacoco.xml,
    // populationModules=16, 29 829 lines and 10 182 branches on all four:
    //     Linux    5966 / 5966 lines   1506 / 1506 branches   <- qualified on the highest
    //     Windows  5964 / 5965 lines   1505 / 1506 branches
    // Spread across the four: 2 lines and 1 branch, inside the 24-line / 11-branch reference spread of 15/09/2026
    // that the coverage ratchets are sized against. The maximum sits at most three times that reference above the
    // qualified measurement: one more line or branch of maximum needs a new measurement on both platforms.
    //
    // Lowering the maximum inside that window is a decision; lowering the qualified measurement needs a fresh
    // exact-head measurement of Linux AND Windows, cited here.
    private static final long QUALIFIED_BORROWED_LINES = 5966L;
    private static final long QUALIFIED_BORROWED_BRANCHES = 1506L;
    private static final long MAXIMUM_MARGIN_LINES = 3L * 24L;
    private static final long MAXIMUM_MARGIN_BRANCHES = 3L * 11L;

    /**
     * The borrowed credit of each module, the highest of the four qualifying runs, lines then branches. It is not
     * a threshold: when the total exceeds its maximum, the refusal names the modules whose credit grew past it.
     */
    private static final String QUALIFIED_BY_MODULE = """
            morpheus-api 13 19
            morpheus-application 4804 1152
            morpheus-cli 0 1
            morpheus-domain 343 63
            morpheus-integration-minos 16 2
            morpheus-integration-nexus 8 0
            morpheus-mcp 0 0
            morpheus-mcp-transport 6 3
            morpheus-provider-markdown 0 0
            morpheus-provider-openspec 3 8
            morpheus-provider-reference 0 0
            morpheus-provider-sdk 1 0
            morpheus-provider-synthetic 5 4
            morpheus-provider-testkit 0 0
            morpheus-store-memory 286 124
            morpheus-store-sqlite 481 132
            """;

    /**
     * The wholly borrowed classes, exact: one {@code module class} per line, the class named by its source file.
     * A class enters this list only by measurement, and leaves it in the change that gives it a test of its own.
     * Measured identical on Linux and Windows, so no entry depends on the platform.
     */
    private static final String WHOLLY_BORROWED = """
            morpheus-application com.morpheus.application.composition.CompositionProviderState
            morpheus-application com.morpheus.application.composition.CompositionSnapshotState
            morpheus-application com.morpheus.application.composition.CompositionStateView
            morpheus-application com.morpheus.application.composition.MultiProviderReadService
            morpheus-application com.morpheus.application.composition.ObservedProviderContributionLoader
            morpheus-application com.morpheus.application.composition.ProviderCompositionSource
            morpheus-application com.morpheus.application.context.AugmentedContextResult
            morpheus-application com.morpheus.application.context.AugmentedContextService
            morpheus-application com.morpheus.application.context.AugmentedSnapshotView
            morpheus-application com.morpheus.application.context.DisabledTechnicalContextProvider
            morpheus-application com.morpheus.application.context.MorpheusIntentContext
            morpheus-application com.morpheus.application.context.TechnicalContextBundle
            morpheus-application com.morpheus.application.context.TechnicalContextItem
            morpheus-application com.morpheus.application.context.TechnicalContextRequest
            morpheus-application com.morpheus.application.delta.AppliedRequirementDelta
            morpheus-application com.morpheus.application.delta.RequirementDeltaApplicationException
            morpheus-application com.morpheus.application.delta.RequirementDeltaApplicationPlan
            morpheus-application com.morpheus.application.delta.RequirementDeltaApplicationResult
            morpheus-application com.morpheus.application.delta.RequirementDeltaApplicationService
            morpheus-application com.morpheus.application.delta.RequirementDeltaPromotionResult
            morpheus-application com.morpheus.application.delta.RequirementDeltaPromotionService
            morpheus-application com.morpheus.application.delta.RequirementPromotionEvidence
            morpheus-application com.morpheus.application.history.HistoricalRequirementQueryService
            morpheus-application com.morpheus.application.history.PublishedHistoryException
            morpheus-application com.morpheus.application.history.PublishedHistoryRetentionPolicy
            morpheus-application com.morpheus.application.history.PublishedSnapshotHistoryService
            morpheus-application com.morpheus.application.history.RequirementLogicalRollbackPlan
            morpheus-application com.morpheus.application.history.RequirementLogicalRollbackRequest
            morpheus-application com.morpheus.application.history.RequirementLogicalRollbackService
            morpheus-application com.morpheus.application.history.RequirementSnapshotChangeKind
            morpheus-application com.morpheus.application.history.RequirementSnapshotComparison
            morpheus-application com.morpheus.application.history.RequirementSnapshotComparisonService
            morpheus-application com.morpheus.application.history.RequirementSnapshotDifference
            morpheus-application com.morpheus.application.ingestion.ObservedProjectSnapshotPublisher
            morpheus-application com.morpheus.application.ingestion.ProjectSnapshotImportResult
            morpheus-application com.morpheus.application.lifecycle.mutation.ChangeLifecycleMutationCommand
            morpheus-application com.morpheus.application.lifecycle.mutation.ChangeLifecycleMutationPersistenceResult
            morpheus-application com.morpheus.application.lifecycle.mutation.ChangeLifecycleMutationPersistenceState
            morpheus-application com.morpheus.application.lifecycle.mutation.ChangeLifecycleMutationPolicy
            morpheus-application com.morpheus.application.lifecycle.mutation.ChangeWriteCapabilityObservation
            morpheus-application com.morpheus.application.lifecycle.mutation.ControlledChangeLifecycleMutationService
            morpheus-application com.morpheus.application.lifecycle.mutation.RegisteredProjectWriteCapabilityResolver
            morpheus-application com.morpheus.application.operability.LocalOperabilityService
            morpheus-application com.morpheus.application.operability.LocalOperationalRuntime
            morpheus-application com.morpheus.application.operability.OperationalRuntime
            morpheus-application com.morpheus.application.policy.PolicyAuditPage
            morpheus-application com.morpheus.application.policy.PolicyConflictException
            morpheus-application com.morpheus.application.portfolio.PortfolioOverview
            morpheus-application com.morpheus.application.portfolio.PortfolioQueryService
            morpheus-application com.morpheus.application.portfolio.PortfolioReferenceConflict
            morpheus-application com.morpheus.application.portfolio.PortfolioRegistryService
            morpheus-application com.morpheus.application.portfolio.PortfolioTraversalDirection
            morpheus-application com.morpheus.application.portfolio.PortfolioTraversalService
            morpheus-application com.morpheus.application.quality.DecisionJustificationStatus
            morpheus-application com.morpheus.application.quality.DesignDecisionQualityAssessment
            morpheus-application com.morpheus.application.quality.ExternalReferenceQualityAssessment
            morpheus-application com.morpheus.application.query.BusinessContentQueryService
            morpheus-application com.morpheus.application.query.ChangeContextQueryService
            morpheus-application com.morpheus.application.query.ChangeContextResult
            morpheus-application com.morpheus.application.query.RequirementQueryService
            morpheus-application com.morpheus.application.query.RequirementSearchPage
            morpheus-application com.morpheus.application.query.RequirementSearchQuery
            morpheus-application com.morpheus.application.query.SnapshotItemResult
            morpheus-application com.morpheus.application.query.SpecificationContextQueryService
            morpheus-application com.morpheus.application.query.SpecificationContextResult
            morpheus-application com.morpheus.application.query.TraceRequirementQueryService
            morpheus-application com.morpheus.application.query.compact.CompactChangeContextView
            morpheus-application com.morpheus.application.query.compact.CompactQueryViewService
            morpheus-application com.morpheus.application.query.compact.CompactRequirementSearchView
            morpheus-application com.morpheus.application.query.compact.CompactTraceRequirementView
            morpheus-application com.morpheus.application.query.compact.CompactWarningCode
            morpheus-application com.morpheus.application.query.saved.SavedViewConflictException
            morpheus-application com.morpheus.application.query.saved.SavedViewService
            morpheus-application com.morpheus.application.query.saved.SavedViewVersion
            morpheus-application com.morpheus.application.reference.LiveExternalReferenceResolutionResult
            morpheus-application com.morpheus.application.reference.LiveExternalReferenceResolutionService
            morpheus-application com.morpheus.application.snapshot.RuntimeSnapshotRecovery
            morpheus-application com.morpheus.application.snapshot.SnapshotRecoveryPolicy
            morpheus-application com.morpheus.application.snapshot.SnapshotRecoveryService
            morpheus-application com.morpheus.application.snapshot.SnapshotValidationException
            morpheus-application com.morpheus.application.snapshot.SnapshotValidationResult
            morpheus-application com.morpheus.application.store.EntityStateException
            morpheus-application com.morpheus.application.store.SnapshotConflictException
            morpheus-application com.morpheus.application.sync.SyncStateConflictException
            morpheus-application com.morpheus.application.temporal.CurrentRequirementQueryService
            morpheus-application com.morpheus.application.traceability.ExternalTraceabilityAvailability
            morpheus-application com.morpheus.application.traceability.ExternalTraceabilityView
            morpheus-application com.morpheus.application.traceability.TraceRequirementResult
            morpheus-application com.morpheus.application.traceability.TraceRequirementService
            morpheus-domain com.morpheus.domain.change.ChangeProposal
            morpheus-domain com.morpheus.domain.decision.DesignDecision
            morpheus-domain com.morpheus.domain.decision.DesignDecisionId
            morpheus-domain com.morpheus.domain.diagnostic.Diagnostic
            morpheus-domain com.morpheus.domain.diagnostic.DiagnosticCode
            morpheus-domain com.morpheus.domain.diagnostic.DiagnosticSeverity
            morpheus-domain com.morpheus.domain.evidence.Evidence
            morpheus-domain com.morpheus.domain.evidence.SourceRange
            morpheus-domain com.morpheus.domain.project.ProjectSpecification
            morpheus-domain com.morpheus.domain.provider.ProviderProbeResult
            morpheus-domain com.morpheus.domain.provider.ProviderProbeStatus
            morpheus-domain com.morpheus.domain.reference.ExternalReference
            morpheus-domain com.morpheus.domain.reference.ExternalReferenceResolutionEvent
            morpheus-domain com.morpheus.domain.reference.ExternalReferenceResolutionReason
            morpheus-domain com.morpheus.domain.reference.ExternalReferenceResolutionState
            morpheus-domain com.morpheus.domain.reference.ExternalReferenceTarget
            morpheus-domain com.morpheus.domain.reference.ResolvedExternalTarget
            morpheus-domain com.morpheus.domain.requirement.Requirement
            morpheus-domain com.morpheus.domain.requirement.RequirementDelta
            morpheus-domain com.morpheus.domain.requirement.RequirementDeltaKind
            morpheus-domain com.morpheus.domain.scenario.Scenario
            morpheus-domain com.morpheus.domain.scenario.ScenarioId
            morpheus-domain com.morpheus.domain.snapshot.KnowledgeSnapshotMetadata
            morpheus-domain com.morpheus.domain.snapshot.KnowledgeSnapshotState
            morpheus-domain com.morpheus.domain.specification.Specification
            morpheus-domain com.morpheus.domain.specification.SpecificationId
            morpheus-domain com.morpheus.domain.task.ImplementationTask
            morpheus-domain com.morpheus.domain.task.TaskId
            morpheus-integration-minos com.morpheus.integration.minos.MinosIntegrationRuntime
            morpheus-integration-nexus com.morpheus.integration.nexus.NexusIntegrationRuntime
            morpheus-store-memory com.morpheus.store.memory.MemoryCompositionStateStore
            morpheus-store-memory com.morpheus.store.memory.MemoryExternalReferenceStore
            morpheus-store-memory com.morpheus.store.memory.MemorySnapshotBusinessContentStore
            morpheus-store-memory com.morpheus.store.memory.MemoryTraceabilityStore
            """;

    @Test
    void borrowedCreditIsMeasuredPerModuleAndStaysUnderItsMaximumWithEveryWhollyBorrowedClassNamed() throws Exception {
        Path root = RepositoryRoot.locate();
        Properties properties = new Properties();
        try (var reader = Files.newBufferedReader(root.resolve("config/m21-quality-ratchets.properties"))) {
            properties.load(reader);
        }
        Maximum maximum = Maximum.of(properties);
        assertMaximumWithinQualifiedWindow(maximum);

        Measurement measurement = measure(root);
        Files.createDirectories(root.resolve(EVIDENCE).getParent());
        Files.writeString(root.resolve(EVIDENCE), measurement.evidence(maximum));

        assertListedExactly(measurement.whollyBorrowed(), listed(WHOLLY_BORROWED));
        assertWithinMaximum(measurement, maximum, qualifiedByModule());
    }

    @Test
    void theListIsSortedAndCarriesNoDuplicate() {
        List<String> entries = WHOLLY_BORROWED.lines().map(String::strip).filter(line -> !line.isEmpty()).toList();
        assertEquals(new ArrayList<>(new TreeSet<>(entries)), entries,
                "the wholly borrowed list must be sorted and hold each class once, so that a diff reads as a change");
    }

    /** A partial reactor, a stale report, two reports of other classes, a negative credit: each refused by name. */
    @Test
    void theMeasurementRefusesTwoReportsThatDoNotDescribeTheSameCode(@TempDir Path reactor) throws Exception {
        FileTime reportTime = FileTime.from(Instant.parse("2026-01-01T12:00:00Z"));
        FileTime older = FileTime.from(reportTime.toInstant().minus(Duration.ofHours(1)));
        FileTime newer = FileTime.from(reportTime.toInstant().plus(Duration.ofSeconds(1)));
        Files.writeString(reactor.resolve("pom.xml"), """
                <project>
                  <modules>
                    <module>product</module>
                    <module>tooling</module>
                    <module>without-main-classes</module>
                  </modules>
                </project>
                """);
        Files.createDirectories(reactor.resolve("without-main-classes/src/test/java"));
        for (String module : List.of("product", "tooling")) {
            Path source = reactor.resolve(module).resolve("src/main/java/Placeholder.java");
            Files.createDirectories(source.getParent());
            Files.writeString(source, "class Placeholder {}");
            Files.setLastModifiedTime(source, older);
        }
        Path aggregate = reactor.resolve(AGGREGATE_REPORT);
        Files.createDirectories(aggregate.getParent());
        Files.writeString(aggregate, aggregate(group("product", file("p", "A", 4, 6, 1, 1)),
                group("tooling", file("t", "B", 2, 0, 0, 0))));
        Files.setLastModifiedTime(aggregate, reportTime);

        AssertionError neverBuilt = assertThrows(AssertionError.class, () -> measure(reactor));
        assertTrue(neverBuilt.getMessage().contains("never built") && neverBuilt.getMessage().contains("product")
                        && neverBuilt.getMessage().contains("./mvnw clean verify"),
                () -> "a module without its own report must be named under its cause: " + neverBuilt.getMessage());
        assertFalse(neverBuilt.getMessage().contains("without-main-classes"),
                () -> "a module without main classes owes no report: " + neverBuilt.getMessage());

        Files.createDirectories(reactor.resolve("product/target/classes"));
        writeOwn(reactor, "tooling", reportTime, file("t", "B", 2, 0, 0, 0));
        AssertionError withoutReport = assertThrows(AssertionError.class, () -> measure(reactor));
        assertTrue(withoutReport.getMessage().contains("built but emitted no report")
                        && withoutReport.getMessage().contains("product"),
                () -> "a built module without a report is a regression of that module: " + withoutReport.getMessage());

        writeOwn(reactor, "product", reportTime, file("p", "A", 1, 9, 1, 1));
        Measurement whole = measure(reactor);
        assertEquals(3, whole.lines(), "the credit is the aggregate covered count minus the module's own");
        assertEquals(0, whole.branches(), "the same branch covered on both scales is not borrowed");
        assertEquals(Set.of(), whole.whollyBorrowed(), "a class the module covers itself is not wholly borrowed");

        writeOwn(reactor, "product", reportTime, file("p", "A", 0, 10, 0, 2));
        assertEquals(Set.of("product p.A"), measure(reactor).whollyBorrowed(),
                "a class covered only by other modules' tests is wholly borrowed, named by module and class");

        writeOwn(reactor, "product", reportTime, file("p", "A", 0, 11, 0, 2));
        AssertionError otherLines = assertThrows(AssertionError.class, () -> measure(reactor));
        assertTrue(otherLines.getMessage().contains("do not count the same lines")
                        && otherLines.getMessage().contains("product"),
                () -> "two reports of different classes must be refused by module: " + otherLines.getMessage());

        writeOwn(reactor, "product", reportTime, file("p", "A", 5, 5, 1, 1));
        AssertionError negative = assertThrows(AssertionError.class, () -> measure(reactor));
        assertTrue(negative.getMessage().contains("covers more than the aggregate")
                        && negative.getMessage().contains("product"),
                () -> "a negative credit is refused, never clamped to zero: " + negative.getMessage());

        writeOwn(reactor, "product", reportTime, file("p", "A", 1, 9, 1, 1));
        Path test = reactor.resolve("product/src/test/java/PlaceholderTest.java");
        Files.createDirectories(test.getParent());
        Files.writeString(test, "class PlaceholderTest {}");
        Files.setLastModifiedTime(test, newer);
        AssertionError stale = assertThrows(AssertionError.class, () -> measure(reactor));
        assertTrue(stale.getMessage().contains("stale") && stale.getMessage().contains("product")
                        && stale.getMessage().contains("./mvnw clean verify"),
                () -> "a report older than the sources it measures is refused by name: " + stale.getMessage());
        assertFalse(stale.getMessage().contains("tooling"),
                () -> "a module whose sources predate both reports is not named: " + stale.getMessage());

        Files.setLastModifiedTime(test, older);
        Files.writeString(aggregate, aggregate(group("product", file("p", "A", 4, 6, 1, 1))));
        Files.setLastModifiedTime(aggregate, reportTime);
        AssertionError amputated = assertThrows(AssertionError.class, () -> measure(reactor));
        assertTrue(amputated.getMessage().contains("absent from the aggregate report: tooling"),
                () -> "a module missing on the aggregate scale is named: " + amputated.getMessage());
    }

    @Test
    void anUnlistedWhollyBorrowedClassAndAListedOneThatGainedATestAreBothRefused() {
        AssertionError refusal = assertThrows(AssertionError.class, () -> assertListedExactly(
                Set.of("m a.Kept", "m a.New"), Set.of("m a.Kept", "m a.Tested")));
        String message = refusal.getMessage();
        assertTrue(message.contains("m a.New") && message.contains("write a test in m"),
                () -> "an unlisted wholly borrowed class names itself and its module: " + message);
        assertTrue(message.contains("m a.Tested") && message.contains("remove its entry"),
                () -> "a listed class that is no longer wholly borrowed must leave the list: " + message);
        assertFalse(message.contains("a.Kept"), () -> "a listed wholly borrowed class is not named: " + message);
        assertListedExactly(Set.of("m a.Kept"), Set.of("m a.Kept"));
    }

    @Test
    void creditOverTheMaximumIsRefusedWithTheModulesWhoseCreditGrew() {
        Map<String, long[]> qualified = Map.of("growing", new long[] {10, 4}, "steady", new long[] {5, 1});
        Measurement measured = new Measurement(new TreeMap<>(Map.of(
                "growing", new Row(100, 20, 10, 2, 22, 7),
                "steady", new Row(50, 10, 20, 3, 25, 4))), Set.of());
        AssertionError refusal = assertThrows(AssertionError.class,
                () -> assertWithinMaximum(measured, new Maximum(16, 6), qualified));
        String message = refusal.getMessage();
        assertTrue(message.contains("17 lines") && message.contains("maximum 16"),
                () -> "the refusal states the measured and the allowed counts: " + message);
        assertTrue(message.contains("growing (") && !message.contains("steady"),
                () -> "the refusal names the module whose credit grew and only it: " + message);
        assertTrue(message.contains("write a test in that module"),
                () -> "the refusal says the remedy: " + message);
        AssertionError branches = assertThrows(AssertionError.class,
                () -> assertWithinMaximum(measured, new Maximum(17, 5), qualified));
        assertTrue(branches.getMessage().contains("6 branches") && branches.getMessage().contains("maximum 5"),
                () -> "the branch maximum is enforced on its own: " + branches.getMessage());
        assertWithinMaximum(measured, new Maximum(17, 6), qualified);
    }

    @Test
    void aMaximumUnderTheQualifiedMeasurementOrRaisedPastItsMarginIsRefused() {
        AssertionError under = assertThrows(AssertionError.class, () -> assertMaximumWithinQualifiedWindow(
                new Maximum(QUALIFIED_BORROWED_LINES - 1, QUALIFIED_BORROWED_BRANCHES + MAXIMUM_MARGIN_BRANCHES)));
        assertTrue(under.getMessage().contains(LINE_MAXIMUM_KEY)
                        && under.getMessage().contains("needs a new measurement on both platforms"),
                () -> "a maximum one platform cannot meet is refused: " + under.getMessage());
        AssertionError raised = assertThrows(AssertionError.class, () -> assertMaximumWithinQualifiedWindow(
                new Maximum(QUALIFIED_BORROWED_LINES, QUALIFIED_BORROWED_BRANCHES + MAXIMUM_MARGIN_BRANCHES + 1)));
        assertTrue(raised.getMessage().contains(BRANCH_MAXIMUM_KEY) && raised.getMessage().contains("only lowered"),
                () -> "a maximum raised past its margin is refused: " + raised.getMessage());
        assertMaximumWithinQualifiedWindow(new Maximum(QUALIFIED_BORROWED_LINES, QUALIFIED_BORROWED_BRANCHES));
    }

    @Test
    void aMissingMaximumKeyNamesItself() {
        Properties incomplete = new Properties();
        incomplete.setProperty(LINE_MAXIMUM_KEY, "10");
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, () -> Maximum.of(incomplete));
        assertTrue(failure.getMessage().contains(BRANCH_MAXIMUM_KEY),
                () -> "a missing maximum must name its key: " + failure.getMessage());
    }

    private static Measurement measure(Path root) throws Exception {
        List<String> population = derivedPopulation(root);
        Path aggregatePath = root.resolve(AGGREGATE_REPORT);
        if (!Files.isRegularFile(aggregatePath)) {
            throw new AssertionError("the aggregate report is missing: " + aggregatePath
                    + " -- run ./mvnw clean verify over the whole reactor");
        }
        List<String> neverBuilt = new ArrayList<>();
        List<String> builtWithoutReport = new ArrayList<>();
        List<String> stale = new ArrayList<>();
        Map<String, Element> own = new LinkedHashMap<>();
        for (String module : population) {
            Path directory = root.resolve(module);
            Path report = directory.resolve(OWN_REPORT);
            if (Files.isRegularFile(report)) {
                String staleness = newerSource(report, root, List.of(module));
                if (staleness != null) {
                    stale.add(module + " (" + staleness + ")");
                }
                own.put(module, parse(report).getDocumentElement());
            } else if (Files.isDirectory(directory.resolve("target/classes"))) {
                builtWithoutReport.add(module);
            } else {
                neverBuilt.add(module);
            }
        }
        List<String> aggregateSources = new ArrayList<>(population);
        aggregateSources.add("morpheus-architecture-tests");
        String aggregateStaleness = newerSource(aggregatePath, root, aggregateSources);
        if (aggregateStaleness != null) {
            stale.add("the aggregate report (" + aggregateStaleness + ")");
        }
        Map<String, Element> groups = new LinkedHashMap<>();
        for (Element group : children(parse(aggregatePath).getDocumentElement(), "group")) {
            groups.put(group.getAttribute("name"), group);
        }
        List<String> absent = population.stream().filter(module -> !groups.containsKey(module)).toList();

        List<String> refusals = new ArrayList<>();
        if (!neverBuilt.isEmpty()) {
            refusals.add("never built by this invocation, run ./mvnw clean verify over the whole reactor: "
                    + String.join(", ", neverBuilt));
        }
        if (!builtWithoutReport.isEmpty()) {
            refusals.add("built but emitted no report, a regression in the module: "
                    + String.join(", ", builtWithoutReport));
        }
        if (!absent.isEmpty()) {
            refusals.add("absent from the aggregate report: " + String.join(", ", absent));
        }
        if (!stale.isEmpty()) {
            refusals.add("stale, older than the sources it measures, run ./mvnw clean verify over the whole reactor: "
                    + String.join("; ", stale));
        }
        if (!refusals.isEmpty()) {
            throw new AssertionError("the borrowed coverage credit cannot be measured:" + System.lineSeparator()
                    + "  " + String.join(System.lineSeparator() + "  ", refusals));
        }

        TreeMap<String, Row> rows = new TreeMap<>();
        Set<String> whollyBorrowed = new TreeSet<>();
        List<String> mismatched = new ArrayList<>();
        for (String module : population) {
            Counts ownCounts = counts(own.get(module));
            Counts aggregateCounts = counts(groups.get(module));
            if (ownCounts.lineTotal() != aggregateCounts.lineTotal()
                    || ownCounts.branchTotal() != aggregateCounts.branchTotal()) {
                mismatched.add(module + " do not count the same lines or branches (own " + ownCounts.lineTotal()
                        + " lines " + ownCounts.branchTotal() + " branches, aggregate " + aggregateCounts.lineTotal()
                        + " lines " + aggregateCounts.branchTotal() + " branches)");
                continue;
            }
            if (ownCounts.lineCovered() > aggregateCounts.lineCovered()
                    || ownCounts.branchCovered() > aggregateCounts.branchCovered()) {
                mismatched.add(module + " covers more than the aggregate report credits it (own "
                        + ownCounts.lineCovered() + " lines " + ownCounts.branchCovered() + " branches, aggregate "
                        + aggregateCounts.lineCovered() + " lines " + aggregateCounts.branchCovered() + " branches)");
                continue;
            }
            rows.put(module, new Row(ownCounts.lineTotal(), ownCounts.branchTotal(), ownCounts.lineCovered(),
                    ownCounts.branchCovered(), aggregateCounts.lineCovered(), aggregateCounts.branchCovered()));
            Map<String, Long> ownByClass = coveredLinesByClass(own.get(module));
            coveredLinesByClass(groups.get(module)).forEach((type, covered) -> {
                if (covered > 0 && ownByClass.getOrDefault(type, 0L) == 0L) {
                    whollyBorrowed.add(module + " " + type);
                }
            });
        }
        if (!mismatched.isEmpty()) {
            throw new AssertionError("the two reports of a module do not describe the same classes, so no credit can "
                    + "be read from them -- a negative credit is refused, never clamped to zero:"
                    + System.lineSeparator() + "  " + String.join(System.lineSeparator() + "  ", mismatched));
        }
        return new Measurement(rows, whollyBorrowed);
    }

    private static void assertListedExactly(Set<String> measured, Set<String> listed) {
        Set<String> unlisted = new TreeSet<>(measured);
        unlisted.removeAll(listed);
        Set<String> outlived = new TreeSet<>(listed);
        outlived.removeAll(measured);
        if (unlisted.isEmpty() && outlived.isEmpty()) {
            return;
        }
        StringBuilder refusal = new StringBuilder("the list of wholly borrowed classes is not the measured set");
        for (String entry : unlisted) {
            String module = entry.substring(0, entry.indexOf(' '));
            refusal.append(System.lineSeparator()).append("  covered only by other modules' tests, unlisted: ")
                    .append(entry).append(" -- write a test in ").append(module).append(" that covers it");
        }
        for (String entry : outlived) {
            refusal.append(System.lineSeparator()).append("  listed, but no longer wholly borrowed: ").append(entry)
                    .append(" -- remove its entry from WHOLLY_BORROWED");
        }
        throw new AssertionError(refusal.toString());
    }

    private static void assertWithinMaximum(Measurement measurement, Maximum maximum, Map<String, long[]> qualified) {
        long lines = measurement.lines();
        long branches = measurement.branches();
        if (lines <= maximum.lines() && branches <= maximum.branches()) {
            return;
        }
        List<String> grew = new ArrayList<>();
        measurement.rows().forEach((module, row) -> {
            long[] baseline = qualified.getOrDefault(module, new long[] {0, 0});
            if (row.borrowedLines() > baseline[0] || row.borrowedBranches() > baseline[1]) {
                grew.add(module + " (" + row.borrowedLines() + " lines, " + row.borrowedBranches()
                        + " branches; qualified " + baseline[0] + " lines, " + baseline[1] + " branches)");
            }
        });
        throw new AssertionError("the coverage borrowed from other modules' tests is " + lines + " lines (maximum "
                + maximum.lines() + ") and " + branches + " branches (maximum " + maximum.branches()
                + "); write a test in that module rather than raising the maximum. Credit grew in: "
                + String.join("; ", grew));
    }

    private static void assertMaximumWithinQualifiedWindow(Maximum maximum) {
        assertWindow(LINE_MAXIMUM_KEY, maximum.lines(), QUALIFIED_BORROWED_LINES, MAXIMUM_MARGIN_LINES);
        assertWindow(BRANCH_MAXIMUM_KEY, maximum.branches(), QUALIFIED_BORROWED_BRANCHES, MAXIMUM_MARGIN_BRANCHES);
    }

    private static void assertWindow(String key, long maximum, long qualified, long margin) {
        assertTrue(maximum >= qualified, () -> key + "=" + maximum + " is under the qualified measurement "
                + qualified + ": one platform could not meet it, so it needs a new measurement on both platforms");
        assertTrue(maximum <= qualified + margin, () -> key + "=" + maximum + " is more than " + margin
                + " over the qualified measurement " + qualified + ": a maximum is only lowered");
    }

    private static Set<String> listed(String block) {
        Set<String> entries = new TreeSet<>();
        block.lines().map(String::strip).filter(line -> !line.isEmpty()).forEach(entries::add);
        return entries;
    }

    private static Map<String, long[]> qualifiedByModule() {
        Map<String, long[]> qualified = new TreeMap<>();
        QUALIFIED_BY_MODULE.lines().map(String::strip).filter(line -> !line.isEmpty()).forEach(line -> {
            String[] parts = line.split(" ");
            qualified.put(parts[0], new long[] {Long.parseLong(parts[1]), Long.parseLong(parts[2])});
        });
        return qualified;
    }

    private static String newerSource(Path report, Path root, List<String> modules) throws IOException {
        FileTime reportTime = Files.getLastModifiedTime(report);
        for (String module : modules) {
            for (String sources : List.of("src/main/java", "src/test/java")) {
                Path directory = root.resolve(module).resolve(sources);
                if (!Files.isDirectory(directory)) {
                    continue;
                }
                try (var files = Files.walk(directory)) {
                    for (Path file : files.filter(Files::isRegularFile).toList()) {
                        if (Files.getLastModifiedTime(file).compareTo(reportTime) > 0) {
                            return "report " + reportTime + " is older than "
                                    + root.relativize(file).toString().replace('\\', '/') + " "
                                    + Files.getLastModifiedTime(file);
                        }
                    }
                }
            }
        }
        return null;
    }

    private static List<String> derivedPopulation(Path root) throws Exception {
        List<String> population = new ArrayList<>();
        for (Element modules : children(parse(root.resolve("pom.xml")).getDocumentElement(), "modules")) {
            for (Element module : children(modules, "module")) {
                String name = module.getTextContent().strip();
                Path sources = root.resolve(name).resolve("src/main/java");
                if (Files.isDirectory(sources)) {
                    try (var files = Files.walk(sources)) {
                        if (files.anyMatch(path -> path.toString().endsWith(".java") && Files.isRegularFile(path))) {
                            population.add(name);
                        }
                    }
                }
            }
        }
        assertFalse(population.isEmpty(), () -> "no reactor module with main classes is declared under " + root);
        return List.copyOf(population);
    }

    /** Covered lines per source file, named {@code package.Type} after the file, nested types included. */
    private static Map<String, Long> coveredLinesByClass(Element report) {
        Map<String, Long> covered = new TreeMap<>();
        for (Element pkg : descendants(report, "package")) {
            for (Element source : children(pkg, "sourcefile")) {
                String file = source.getAttribute("name");
                String type = pkg.getAttribute("name").replace('/', '.') + "."
                        + file.substring(0, file.length() - ".java".length());
                for (Element counter : children(source, "counter")) {
                    if (counter.getAttribute("type").equals("LINE")) {
                        covered.merge(type, Long.parseLong(counter.getAttribute("covered")), Long::sum);
                    }
                }
            }
        }
        return covered;
    }

    private static Counts counts(Element report) {
        long[] values = new long[4];
        for (Element counter : children(report, "counter")) {
            long covered = Long.parseLong(counter.getAttribute("covered"));
            long missed = Long.parseLong(counter.getAttribute("missed"));
            switch (counter.getAttribute("type")) {
                case "LINE" -> {
                    values[0] = covered;
                    values[1] = covered + missed;
                }
                case "BRANCH" -> {
                    values[2] = covered;
                    values[3] = covered + missed;
                }
                default -> {
                }
            }
        }
        return new Counts(values[0], values[1], values[2], values[3]);
    }

    private static List<Element> children(Element parent, String tag) {
        List<Element> found = new ArrayList<>();
        for (Node child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element element && element.getTagName().equals(tag)) {
                found.add(element);
            }
        }
        return found;
    }

    private static List<Element> descendants(Element parent, String tag) {
        List<Element> found = new ArrayList<>();
        var nodes = parent.getElementsByTagName(tag);
        for (int index = 0; index < nodes.getLength(); index++) {
            found.add((Element) nodes.item(index));
        }
        return found;
    }

    private static org.w3c.dom.Document parse(Path report) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        factory.setExpandEntityReferences(false);
        var builder = factory.newDocumentBuilder();
        builder.setEntityResolver((publicId, systemId) -> new InputSource(new StringReader("")));
        return builder.parse(report.toFile());
    }

    private static void writeOwn(Path reactor, String module, FileTime time, String files) throws IOException {
        Path report = reactor.resolve(module).resolve(OWN_REPORT);
        Files.createDirectories(report.getParent());
        Files.writeString(report, "<report name=\"" + module + "\">" + files + totals(files) + "</report>");
        Files.setLastModifiedTime(report, time);
    }

    private static String aggregate(String... groups) {
        return "<report name=\"aggregate\">" + String.join("", groups) + "</report>";
    }

    private static String group(String module, String files) {
        return "<group name=\"" + module + "\">" + files + totals(files) + "</group>";
    }

    /** One package holding one source file, with its LINE and BRANCH counters. */
    private static String file(String pkg, String type, int linesCovered, int linesMissed, int branchesCovered,
            int branchesMissed) {
        return "<package name=\"" + pkg + "\"><sourcefile name=\"" + type + ".java\">"
                + "<counter type=\"LINE\" missed=\"" + linesMissed + "\" covered=\"" + linesCovered + "\"/>"
                + "<counter type=\"BRANCH\" missed=\"" + branchesMissed + "\" covered=\"" + branchesCovered + "\"/>"
                + "</sourcefile></package>";
    }

    /** The report-level counters of a fixture holding one source file are that file's counters. */
    private static String totals(String files) {
        int start = files.indexOf("<counter type=\"LINE\"");
        int end = files.indexOf("</sourcefile>");
        return files.substring(start, end);
    }

    private record Counts(long lineCovered, long lineTotal, long branchCovered, long branchTotal) {
    }

    private record Row(long lines, long branches, long ownLines, long ownBranches, long aggregateLines,
            long aggregateBranches) {
        private long borrowedLines() {
            return aggregateLines - ownLines;
        }

        private long borrowedBranches() {
            return aggregateBranches - ownBranches;
        }
    }

    private record Measurement(TreeMap<String, Row> rows, Set<String> whollyBorrowed) {
        private long lines() {
            return rows.values().stream().mapToLong(Row::borrowedLines).sum();
        }

        private long branches() {
            return rows.values().stream().mapToLong(Row::borrowedBranches).sum();
        }

        private String evidence(Maximum maximum) {
            String newline = System.lineSeparator();
            StringBuilder evidence = new StringBuilder("coverageScope=borrowed-credit").append(newline)
                    .append("borrowedSource=jacoco-report-aggregate minus each module's own target/site/jacoco")
                    .append(newline)
                    .append("populationModules=").append(rows.size()).append(newline)
                    .append("population=").append(String.join(",", rows.keySet())).append(newline)
                    .append("borrowedLines=").append(lines()).append(newline)
                    .append("borrowedBranches=").append(branches()).append(newline)
                    .append(LINE_MAXIMUM_KEY).append('=').append(maximum.lines()).append(newline)
                    .append(BRANCH_MAXIMUM_KEY).append('=').append(maximum.branches()).append(newline)
                    .append("qualifiedBorrowedLines=").append(QUALIFIED_BORROWED_LINES).append(newline)
                    .append("qualifiedBorrowedBranches=").append(QUALIFIED_BORROWED_BRANCHES).append(newline)
                    .append("whollyBorrowedClasses=").append(whollyBorrowed.size()).append(newline);
            rows.forEach((module, row) -> evidence.append("module.").append(module).append('=')
                    .append("lines=").append(row.lines()).append(";branches=").append(row.branches())
                    .append(";ownLines=").append(row.ownLines()).append(";aggregateLines=").append(row.aggregateLines())
                    .append(";borrowedLines=").append(row.borrowedLines())
                    .append(";ownBranches=").append(row.ownBranches())
                    .append(";aggregateBranches=").append(row.aggregateBranches())
                    .append(";borrowedBranches=").append(row.borrowedBranches()).append(newline));
            whollyBorrowed.forEach(entry -> evidence.append("whollyBorrowed=").append(entry).append(newline));
            return evidence.toString();
        }
    }

    private record Maximum(long lines, long branches) {
        private static Maximum of(Properties properties) {
            return new Maximum(required(properties, LINE_MAXIMUM_KEY), required(properties, BRANCH_MAXIMUM_KEY));
        }

        private static long required(Properties properties, String key) {
            String value = properties.getProperty(key);
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("missing M21 borrowed coverage maximum: " + key);
            }
            try {
                return Long.parseLong(value.strip());
            } catch (NumberFormatException failure) {
                throw new IllegalArgumentException("invalid M21 borrowed coverage maximum: " + key, failure);
            }
        }
    }
}
