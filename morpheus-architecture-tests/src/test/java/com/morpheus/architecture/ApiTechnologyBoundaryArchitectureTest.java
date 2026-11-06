package com.morpheus.architecture;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.CompositeArchRule;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import static com.tngtech.archunit.lang.conditions.ArchConditions.never;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Which technology {@code com.morpheus.api} may reach, and through which classes.
 *
 * <p>The HTTP adapter serves the local and the remote server. Each family of the shared vocabulary
 * ({@link TechnologyFamily}) has a regime here: refused in every class of the adapter, confined to the classes named
 * for it with their reason, free, or held by another gate that is named. A confined list is exactly the set of classes
 * that reach the family, measured on the bytecode, so a new dependency on the network, on threads, on files or on a
 * JSON library is written here, with its reason, before it builds. The lists are tables of facts: the adapter is a
 * transport module, whose role is to carry these technologies, and no decision record expects them to shrink.</p>
 *
 * <p>The finer rules of the adapter stay where they are and are not repeated here: which class builds the pool of
 * database workers ({@link DatabaseWorkArchitectureTest}, ADR-0159), which router may decode a body, and the strict
 * features of the one {@code JsonMapper} ({@code HttpRoutesTransportBoundaryArchitectureTest}). This gate says which
 * classes reach a family at all.</p>
 */
class ApiTechnologyBoundaryArchitectureTest {
    private static final String API = "com.morpheus.api";

    private enum Regime { REFUSED, CONFINED, FREE, HELD_BY_ANOTHER_GATE }

    private record Decision(Regime regime, String reason) {
    }

    private static final Map<TechnologyFamily, Decision> REGIMES = Map.of(
            TechnologyFamily.SQL, new Decision(Regime.REFUSED,
                    "persistence goes through the SQLite operation runtime of the store, ARCH-0162"),
            TechnologyFamily.JSON_LIBRARY, new Decision(Regime.CONFINED,
                    "one strict decoder reads request bodies; responses are written by the canonical serializer of "
                            + "the application"),
            TechnologyFamily.PROCESS_EXECUTION, new Decision(Regime.REFUSED,
                    "the HTTP adapter starts no process"),
            TechnologyFamily.OUTBOUND_NETWORK, new Decision(Regime.CONFINED,
                    "the loopback policy, the TLS of the remote facade and its proxy to the local server"),
            TechnologyFamily.THREADS, new Decision(Regime.CONFINED,
                    "the executors of both servers, the database workers and the deadlines of body reads and "
                            + "response writes"),
            TechnologyFamily.FILE_ACCESS, new Decision(Regime.CONFINED,
                    "the workspace directories a request names and the remote identity file"),
            TechnologyFamily.PROCESS_ENVIRONMENT, new Decision(Regime.REFUSED,
                    "the servers are configured by their caller: the CLI reads the environment, the TLS password "
                            + "included, and hands values down"),
            TechnologyFamily.STANDARD_STREAMS, new Decision(Regime.REFUSED,
                    "the servers write to HTTP exchanges and log through the operational runtime, never to the "
                            + "streams of the process"),
            TechnologyFamily.WALL_CLOCK, new Decision(Regime.FREE,
                    "the adapter stamps the requests it serves and the records it writes; the application receiving "
                            + "a java.time.Clock is held by the application gate"),
            TechnologyFamily.REFLECTION_AND_CLASS_LOADING, new Decision(Regime.HELD_BY_ANOTHER_GATE,
                    "ReflectionAndClassLoadingBoundaryArchitectureTest confines it over the whole product"));

    /** Each confined family, with each class of the HTTP adapter that reaches it and why. */
    private static final Map<TechnologyFamily, Map<String, String>> CONFINED_FAMILIES = Map.of(
            TechnologyFamily.JSON_LIBRARY, Map.of(
                    API + ".transport.MorpheusHttpRequestDecoder",
                    "builds the one JsonMapper of the adapter, strict about unknown properties and trailing tokens"),
            TechnologyFamily.OUTBOUND_NETWORK, Map.ofEntries(
                    Map.entry(API + ".local.LoopbackHostPolicy", "resolves the bind host to a loopback InetAddress"),
                    Map.entry(API + ".local.LoopbackRequestPolicy", "checks that a Host header names a loopback "
                            + "address"),
                    Map.entry(API + ".local.MorpheusHttpServer", "reports the address it is bound to"),
                    Map.entry(API + ".local.MorpheusInternalCapability", "sets its header on a proxied "
                            + "java.net.http request"),
                    Map.entry(API + ".local.MorpheusLocalHttpServerBootstrap", "binds the local server to its "
                            + "resolved loopback address"),
                    Map.entry(API + ".remote.MorpheusRemoteHttpServer", "reports the address it is bound to"),
                    Map.entry(API + ".remote.MorpheusRemoteHttpServerBootstrap", "restricts the TLS protocols of "
                            + "the HTTPS server"),
                    Map.entry(API + ".remote.MorpheusRemoteProxyTransport", "forwards an authorized request to the "
                            + "local server with java.net.http")),
            TechnologyFamily.THREADS, Map.ofEntries(
                    Map.entry(API + ".local.DatabaseCallWorkers", "runs database work on platform threads, "
                            + "ADR-0159"),
                    Map.entry(API + ".local.MorpheusHttpServer", "owns and shuts down the executor of the local "
                            + "server"),
                    Map.entry(API + ".local.MorpheusLocalHttpServerBootstrap", "gives the local server its executor "
                            + "of virtual threads"),
                    Map.entry(API + ".remote.MorpheusRemoteHttpServer", "drains the executor of the remote facade on "
                            + "close"),
                    Map.entry(API + ".remote.MorpheusRemoteHttpServerBootstrap", "gives the remote facade its "
                            + "executor of virtual threads"),
                    Map.entry(API + ".transport.HttpRequestBodyReader", "receives the executor that bounds the read "
                            + "of a body"),
                    Map.entry(API + ".transport.MorpheusHttpRequestDecoder", "holds the executor that bounds the read "
                            + "of a body"),
                    Map.entry(API + ".transport.TimedBoundedInputReader", "reads a body under a deadline on the "
                            + "executor it receives"),
                    Map.entry(API + ".transport.TimedBoundedResponseWriter", "watches the deadline of a response "
                            + "write on its own thread")),
            TechnologyFamily.FILE_ACCESS, Map.of(
                    API + ".AllowedWorkspaceRoots", "checks that an allowed root is still the directory it was",
                    API + ".MorpheusProjectRegistryApiService", "checks that a registered workspace is a directory",
                    API + ".MorpheusProjectSyncApiService", "checks that a workspace to sync is a directory",
                    API + ".remote.MorpheusRemoteIdentitySnapshotCache", "stamps the remote identity file to reload "
                            + "it when it changes",
                    API + ".remote.RemoteIdentityFileStore", "reads, locks and atomically replaces the remote "
                            + "identity file"));

    private final JavaClasses classes = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(API);

    @Test
    void everyFamilyOfTheVocabularyHasARegimeInTheHttpAdapter() {
        Set<String> vocabulary = new TreeSet<>();
        TechnologyFamily.VOCABULARY.forEach(family -> vocabulary.add(family.name()));
        Set<String> withRegime = new TreeSet<>();
        REGIMES.keySet().forEach(family -> withRegime.add(family.name()));
        assertEquals(vocabulary, withRegime, "every family of the shared vocabulary has a regime in the HTTP "
                + "adapter: refused, confined, free or held by another gate, with its reason");

        Set<String> confined = new TreeSet<>();
        REGIMES.forEach((family, decision) -> {
            if (decision.regime() == Regime.CONFINED) confined.add(family.name());
        });
        Set<String> listed = new TreeSet<>();
        CONFINED_FAMILIES.keySet().forEach(family -> listed.add(family.name()));
        assertEquals(confined, listed, "a family is confined in the HTTP adapter exactly when the gate names its "
                + "classes");
    }

    @Test
    void aFamilyTheHttpAdapterRefusesIsReachedByNoClassOfTheAdapter() {
        ArchRule rule = CompositeArchRule.of(REGIMES.entrySet().stream()
                .filter(entry -> entry.getValue().regime() == Regime.REFUSED)
                .sorted(Map.Entry.comparingByKey((left, right) -> left.name().compareTo(right.name())))
                .map(entry -> (ArchRule) classes().that().resideInAPackage(API + "..")
                        .should(never(entry.getKey().reach()))
                        .because("the HTTP adapter uses no " + entry.getKey().name() + ": "
                                + entry.getValue().reason()))
                .toList());
        rule.check(classes);
    }

    @Test
    void aConfinedFamilyIsReachedOnlyByTheClassesOfTheHttpAdapterNamedForIt() {
        ArchRule rule = CompositeArchRule.of(CONFINED_FAMILIES.entrySet().stream()
                .sorted(Map.Entry.comparingByKey((left, right) -> left.name().compareTo(right.name())))
                .map(entry -> (ArchRule) classes().that().resideInAPackage(API + "..")
                        .and(DescribedPredicate.not(namedFor(entry.getValue().keySet(), entry.getKey())))
                        .should(never(entry.getKey().reach()))
                        .because(entry.getKey().name() + " is reached in the HTTP adapter only by the classes this "
                                + "gate names for it, each with its reason"))
                .toList());
        rule.check(classes);
    }

    @Test
    void everyClassNamedForAConfinedFamilyOfTheHttpAdapterStillReachesIt() {
        Map<String, Set<String>> named = new TreeMap<>();
        Map<String, Set<String>> reached = new TreeMap<>();
        CONFINED_FAMILIES.forEach((family, reasons) -> {
            named.put(family.name(), new TreeSet<>(reasons.keySet()));
            reached.put(family.name(), classesReaching(family));
        });
        assertEquals(named, reached, "each list of the HTTP adapter gate names exactly the classes of the adapter "
                + "that reach its family: name a class that reaches it with its reason, remove one that no longer "
                + "does");
    }

    private Set<String> classesReaching(TechnologyFamily family) {
        Set<String> reaching = new TreeSet<>();
        for (JavaClass origin : classes) {
            if (family.reachedBy(origin)) reaching.add(TechnologyFamily.topLevelName(origin));
        }
        return reaching;
    }

    private static DescribedPredicate<JavaClass> namedFor(Set<String> names, TechnologyFamily family) {
        return DescribedPredicate.describe("named by the HTTP adapter gate for " + family.name(),
                javaClass -> names.contains(TechnologyFamily.topLevelName(javaClass)));
    }
}
