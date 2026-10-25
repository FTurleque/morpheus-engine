package com.morpheus.architecture;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaCall;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.core.domain.properties.HasName.Predicates.name;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a router may touch of the HTTP transport and of JSON depends on how it reads a request body, so these rules
 * are stated per capability group, not over the family.
 *
 * <p>The first classification by capability sketched two groups: eight routers without a body, nine that read one.
 * Establishing the second group literal by literal (15/09/2026) showed it is two groups, not one:</p>
 *
 * <ul>
 *   <li><strong>without a body</strong> -- eight routers that touch neither {@code com.sun.net.httpserver}, nor
 *       Jackson, nor the shared decoder;</li>
 *   <li><strong>through the shared decoder</strong> -- five routers that carry {@code HttpExchange} and read their
 *       body through {@code MorpheusHttpRequestDecoder}, and never touch Jackson;</li>
 *   <li><strong>registering their own context</strong> -- four routers, policy, policy-management, query and
 *       reasoning, that register their own HTTP context rather than being held as a field by the server, and receive
 *       both of the server's boundaries -- the decoder and the writer -- at {@code register(...)}.</li>
 * </ul>
 *
 * <p>Both of the last group's names described a duplication since removed. Until 15/09/2026 it was "with their own
 * mapper": each built a {@code JsonMapper}, read its body through {@code HttpRequestBodyReader} and threw a private
 * failure type; DT-16 gave them the server's decoder. Until 16/09/2026 it was "writing their own response": each
 * carried a copy of an eight-line send method, three private envelope records identical to the {@code public} ones on
 * {@code MorpheusHttpServer}, and a {@code CanonicalJsonSerializer} of its own; DT-17 gave them the server's writer.
 * {@code ExtensionRoutesRequestBoundaryParityTest} and {@code ExtensionRoutesResponseWritingParityTest} pin what they
 * read and what they write, byte for byte, across those two moves. Neither group was left empty when its reason
 * disappeared -- an empty list satisfies every rule written over it -- so the first was deleted and the second
 * renamed for the one thing still true of it.</p>
 *
 * <p>Every group is a package of {@code com.morpheus.api.routes}: {@code bodiless}, {@code shareddecoder},
 * {@code owncontext}. Placing a router in one of them is classifying it. A router added in any other package
 * belongs to no group, and {@link #everyRouterIsClassifiedIntoExactlyOneTransportGroup} refuses it until someone
 * places it.</p>
 *
 * <p>{@code MorpheusHttpServer} appears in no ban, and no router mentions it: the envelopes the routers build are
 * types of {@code com.morpheus.api.transport}, the request records they decode into live beside them, and
 * {@code API_PREFIX}, a compile-time constant javac inlines, is declared by {@code MorpheusHttpRouteTable}. The local
 * server depends on the routers, never the reverse; it is not their boundary.</p>
 */
class HttpRoutesTransportBoundaryArchitectureTest {

    private static final String API_PACKAGE = "com.morpheus.api";
    private static final String TRANSPORT_PACKAGE = API_PACKAGE + ".transport";
    private static final String ROUTER_SUFFIX = "HttpRoutes";

    private static final String ROUTES_PACKAGE = API_PACKAGE + ".routes";
    private static final String ROUTERS_WITHOUT_A_BODY = ROUTES_PACKAGE + ".bodiless";
    private static final String ROUTERS_THROUGH_THE_SHARED_DECODER = ROUTES_PACKAGE + ".shareddecoder";
    private static final String ROUTERS_REGISTERING_THEIR_OWN_CONTEXT = ROUTES_PACKAGE + ".owncontext";

    private static final String REQUEST_DECODER = TRANSPORT_PACKAGE + ".MorpheusHttpRequestDecoder";
    private static final DescribedPredicate<JavaCall<?>> ASKS_THE_DECODER_FOR_THE_PATH = DescribedPredicate.describe(
            "asks MorpheusHttpRequestDecoder for the segments of the path",
            call -> call.getTargetOwner().getName().equals(TRANSPORT_PACKAGE + ".MorpheusHttpRequestDecoder")
                    && call.getName().startsWith("pathSegments"));
    private static final String REQUEST_BODY_READER = TRANSPORT_PACKAGE + ".HttpRequestBodyReader";
    private static final String RESPONSE_WRITER = TRANSPORT_PACKAGE + ".MorpheusHttpResponseWriter";
    private static final String CANONICAL_JSON_SERIALIZER =
            "com.morpheus.application.query.compact.CanonicalJsonSerializer";
    private static final List<String> STRICT_FEATURES = List.of("FAIL_ON_UNKNOWN_PROPERTIES", "FAIL_ON_TRAILING_TOKENS");

    private static final Pattern MAPPER_BUILDER = Pattern.compile("JsonMapper\\s*\\.\\s*builder\\s*\\(\\s*\\)");
    private static final Pattern BUILD_CALL = Pattern.compile("\\.\\s*build\\s*\\(\\s*\\)");
    private static final Pattern ENABLE_CALL = Pattern.compile("\\.\\s*enable\\s*\\(([^)]*)\\)");
    private static final Pattern DISABLE_CALL = Pattern.compile("\\.\\s*disable\\s*\\(([^)]*)\\)");
    private static final Pattern CONFIGURE_OFF = Pattern.compile(
            "\\.\\s*configure\\s*\\(\\s*(?:DeserializationFeature\\s*\\.\\s*)?(FAIL_ON_\\w+)\\s*,\\s*false\\s*\\)");
    private static final Pattern FEATURE = Pattern.compile("FAIL_ON_\\w+");
    private static final Pattern MAPPER_OUTSIDE_THE_BUILDER = Pattern.compile(
            "new\\s+(?:Json|Object)Mapper\\s*\\(|JsonMapper\\s*\\.\\s*shared\\s*\\(|\\.\\s*rebuild\\s*\\(");

    private final JavaClasses classes = new ClassFileImporter().importPackages("com.morpheus");

    /**
     * Each group is a package, so a router classified twice or classified without existing cannot be written. What can
     * still go wrong is a router left outside every group package -- in the parent package, or in a package no rule
     * names -- where it escapes every rule below. It is refused by name, with the package it resides in.
     */
    @Test
    void everyRouterIsClassifiedIntoExactlyOneTransportGroup() {
        Map<String, String> packageByRouter = new TreeMap<>();
        List<String> defects = new ArrayList<>();
        for (JavaClass javaClass : classes) {
            if (javaClass.getSimpleName().endsWith(ROUTER_SUFFIX)) {
                String previous = packageByRouter.put(javaClass.getSimpleName(), javaClass.getPackageName());
                if (previous != null) {
                    defects.add(javaClass.getSimpleName() + " is declared in " + previous + " and in "
                            + javaClass.getPackageName());
                }
            }
        }
        defects.addAll(classificationDefects(packageByRouter, transportGroups().keySet()));
        assertTrue(defects.isEmpty(), () -> "every router must reside in exactly one transport group package of "
                + "this test: " + defects);
        for (String group : transportGroups().keySet()) {
            assertTrue(packageByRouter.containsValue(group), () -> "the transport group " + group + " holds no "
                    + "router: a rule over an empty group would hold over nothing");
        }
    }

    @Test
    void theClassificationRefusesARouterLeftInTheParentPackageAndOneInAnUnnamedPackage() {
        Map<String, String> packageByRouter = new TreeMap<>(Map.of(
                "AlphaHttpRoutes", ROUTERS_WITHOUT_A_BODY,
                "BetaHttpRoutes", API_PACKAGE,
                "GammaHttpRoutes", ROUTES_PACKAGE + ".unnamed"));

        assertEquals(List.of(
                        "BetaHttpRoutes resides in " + API_PACKAGE + ", which is no transport group",
                        "GammaHttpRoutes resides in " + ROUTES_PACKAGE + ".unnamed, which is no transport group"),
                classificationDefects(packageByRouter, transportGroups().keySet()));
        assertEquals(List.of(), classificationDefects(Map.of("AlphaHttpRoutes", ROUTERS_WITHOUT_A_BODY),
                transportGroups().keySet()));
    }

    /**
     * A router without a body touches no transport type, no JSON type and not the decoder.
     *
     * <p>Rule and text do not see the same things here, and each half was chosen by counting. Jackson 3.2.2 declares
     * public compile-time constants (110 in {@code jackson-core}, 44 in {@code jackson-databind}), so a router writing
     * one carries the value and no dependency: the text scan for {@code tools.jackson} stays beside the rule, and
     * catches it because such a constant cannot be reached from another package without spelling the package.
     * {@code jdk.httpserver} declares none (JDK 21.0.12, 29 classes; its only public static fields are three enum
     * constants, never inlined), and {@code MorpheusHttpRequestDecoder} declares no static constant: the rule alone
     * covers them.</p>
     *
     * <p>Broken before acceptance, 15/09/2026, each violation removed afterwards, with every router suite run beside
     * it. A {@code com.sun.net.httpserver.Headers} field in {@code MorpheusVersionsHttpRoutes}: the rule failed and
     * {@code LocalVersionsHttpRoutesArchitectureTest} passed -- nothing caught it before. A
     * {@code MorpheusHttpRequestDecoder} field in {@code MorpheusRootHttpRoutes}: the rule failed, and so did
     * {@code LocalRootHttpRoutesArchitectureTest}. {@code tools.jackson.core.StreamReadConstraints.DEFAULT_MAX_DEPTH}
     * read in {@code MorpheusCompositionHttpRoutes}: the rule passed, javac having written {@code 500} into the class,
     * and only this text scan failed; no router suite caught it.</p>
     */
    @Test
    void routersWithoutABodyTouchNeitherTheTransportNorJsonNorTheDecoder() throws IOException {
        noClasses()
                .that(routersIn(ROUTERS_WITHOUT_A_BODY))
                .should().dependOnClassesThat(resideInAnyPackage("com.sun.net.httpserver..", "tools.jackson..")
                        .or(name(REQUEST_DECODER)).or(name(RESPONSE_WRITER)))
                .because("a router that reads no request body decides a route and returns a value; the exchange, "
                        + "the decoding and the encoding belong to the server")
                .check(classes);

        assertNoSourceMentions(routerSources(ROUTERS_WITHOUT_A_BODY), "tools.jackson");
    }

    /**
     * A router that reads its body through the shared decoder touches neither Jackson nor the canonical serializer.
     *
     * <p>True of all five on 15/09/2026 and written nowhere until then: if you read a body, the decoder reads it for
     * you. {@code CanonicalJsonSerializer} declares a public compile-time constant, {@code DEFAULT_MAX_UTF8_BYTES},
     * and Jackson declares many, so both halves keep their text.</p>
     *
     * <p>Broken before acceptance, 15/09/2026, each violation removed afterwards, with every router suite run beside
     * it. A {@code JsonMapper} field in {@code MorpheusChangesHttpRoutes}: the rule failed, and so did
     * {@code LocalChangesHttpRoutesArchitectureTest}. {@code CanonicalJsonSerializer.class} in
     * {@code MorpheusPortfolioHttpRoutes}: the rule failed, and so did {@code LocalPortfolioHttpRoutesArchitectureTest}.
     * {@code CanonicalJsonSerializer.DEFAULT_MAX_UTF8_BYTES} read in {@code MorpheusRequirementsHttpRoutes}: the rule
     * passed and only this text scan failed; no router suite caught it.</p>
     */
    @Test
    void routersThroughTheSharedDecoderTouchNeitherJacksonNorTheCanonicalSerializer() throws IOException {
        noClasses()
                .that(routersIn(ROUTERS_THROUGH_THE_SHARED_DECODER))
                .should().dependOnClassesThat(resideInAnyPackage("tools.jackson..")
                        .or(name(CANONICAL_JSON_SERIALIZER)).or(name(REQUEST_BODY_READER)).or(name(RESPONSE_WRITER)))
                .because("a router that reads its body through MorpheusHttpRequestDecoder inherits its strict "
                        + "mapper; a mapper, reader or serializer of its own is a second configuration nothing keeps "
                        + "in step")
                .check(classes);

        Map<String, Path> sources = routerSources(ROUTERS_THROUGH_THE_SHARED_DECODER);
        assertNoSourceMentions(sources, "tools.jackson");
        assertNoSourceMentions(sources, "CanonicalJsonSerializer");
    }

    /**
     * A router that registers its own HTTP context receives both of the server's boundaries and builds neither: it
     * depends on {@code MorpheusHttpRequestDecoder} and on {@code MorpheusHttpResponseWriter}, on no body reader of
     * its own, on no Jackson type and on no serializer of its own.
     *
     * <p>Until DT-17 (16/09/2026) this group was named for what it did wrong: it wrote its own response. Each of the
     * four carried a copy of an eight-line send method and three private envelope records identical to the
     * {@code public} ones already on {@code MorpheusHttpServer}, and serialized through a
     * {@code CanonicalJsonSerializer} of its own. They now receive the server's writer at {@code register(...)},
     * exactly as DT-16 gave them its decoder, and {@code ExtensionRoutesResponseWritingParityTest} pins what they
     * write byte for byte across the move. What still sets them apart -- and what this group is now named for -- is
     * that they register their own HTTP context instead of being held as a field by the server.</p>
     *
     * <p>{@code HttpRequestBodyReader}, the decoder and the writer declare no static constant, so the rule alone
     * covers them; Jackson and {@code CanonicalJsonSerializer} declare compile-time constants javac would inline, so
     * those two halves keep their text.</p>
     *
     * <p>The writer is banned from the two groups the server holds, in their own methods above, and required here:
     * the family cannot state either, because since DT-17 the four routers below must depend on the writer and the
     * thirteen others must not. Broken before acceptance, 16/09/2026: a {@code MorpheusHttpResponseWriter} field
     * added to {@code MorpheusRootHttpRoutes} failed
     * {@link #routersWithoutABodyTouchNeitherTheTransportNorJsonNorTheDecoder} and
     * {@code LocalRootHttpRoutesArchitectureTest}, while {@code HttpRoutesFamilyArchitectureTest} passed -- a field
     * is not a call.</p>
     */
    @Test
    void routersRegisteringTheirOwnContextReceiveBothBoundariesAndBuildNeither() throws IOException {
        classes()
                .that(routersIn(ROUTERS_REGISTERING_THEIR_OWN_CONTEXT))
                .should().dependOnClassesThat(name(REQUEST_DECODER))
                .because("the request boundary of a router that registers its own context is the server's decoder")
                .check(classes);
        classes()
                .that(routersIn(ROUTERS_REGISTERING_THEIR_OWN_CONTEXT))
                .should().dependOnClassesThat(name(RESPONSE_WRITER))
                .because("the response boundary of a router that registers its own context is the server's writer")
                .check(classes);
        noClasses()
                .that(routersIn(ROUTERS_REGISTERING_THEIR_OWN_CONTEXT))
                .should().dependOnClassesThat(resideInAnyPackage("tools.jackson..")
                        .or(name(REQUEST_BODY_READER)).or(name(CANONICAL_JSON_SERIALIZER)))
                .because("a mapper, a body reader or a serializer of its own is a second boundary nothing keeps in "
                        + "step with the server's")
                .check(classes);

        Map<String, Path> sources = routerSources(ROUTERS_REGISTERING_THEIR_OWN_CONTEXT);
        assertNoSourceMentions(sources, "tools.jackson");
        assertNoSourceMentions(sources, "CanonicalJsonSerializer");
    }

    /**
     * Every {@code JsonMapper.builder()} of {@code morpheus-api} enables {@code FAIL_ON_UNKNOWN_PROPERTIES} and
     * {@code FAIL_ON_TRAILING_TOKENS}, and no mapper is built any other way.
     *
     * <p>Five sites when it was written, all conforming: a gain of reach, not a correction. One since the four
     * extension routers moved onto the decoder (15/09/2026) -- the decoder's own. A second that forgot one flag would
     * accept unknown fields or trailing tokens silently, which is why the rule outlived the duplication it was written
     * against.</p>
     *
     * <p>This is text, not bytecode, by {@code .claude/rules/architecture.md}: it reserves rules for an intention of
     * the form "no
     * dependency", and which feature a builder received is no dependency at all -- it is an argument, the same kind
     * of fact as the wiring expressions it keeps textual.</p>
     *
     * <p>Broken on the real tree, 15/09/2026, besides the resident proof below: removing
     * {@code FAIL_ON_TRAILING_TOKENS} from {@code MorpheusReasoningHttpRoutes} failed this method, naming the file and
     * the line of the builder, while {@code D2RepositoryHardeningArchitectureTest}, which checks the two features on the
     * decoder only, passed.</p>
     */
    @Test
    void everyApiJsonMapperIsStrictAboutUnknownPropertiesAndTrailingTokens() throws IOException {
        Path sources = repositoryRoot().resolve("morpheus-api/src/main/java");
        assertTrue(countMapperBuilders(sources) > 0, "no JsonMapper.builder() found under " + sources);
        List<String> lenient = lenientMappers(sources);
        assertTrue(lenient.isEmpty(), () -> "a morpheus-api JsonMapper must be built through JsonMapper.builder() "
                + "with both " + STRICT_FEATURES + " enabled:" + System.lineSeparator()
                + String.join(System.lineSeparator(), lenient));
    }

    /** The configuration rule, replayed on every build over mappers built to violate it one flag at a time. */
    @Test
    void theMapperRuleRefusesAMapperMissingOneStrictFeatureAndNamesIt(@TempDir Path sources) throws IOException {
        Path api = Files.createDirectories(sources.resolve("com/example"));
        Files.writeString(api.resolve("Strict.java"), """
                class Strict {
                    JsonMapper mapper = JsonMapper.builder()
                            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                            .build();
                    JsonMapper both = JsonMapper.builder().enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS,
                            DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();
                }
                """);
        Files.writeString(api.resolve("MissingTrailing.java"), """
                class MissingTrailing {
                    JsonMapper mapper = JsonMapper.builder()
                            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                            .build();
                }
                """);
        Files.writeString(api.resolve("DisabledUnknown.java"), """
                class DisabledUnknown {
                    JsonMapper mapper = JsonMapper.builder()
                            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                            .build();
                }
                """);
        Files.writeString(api.resolve("Shared.java"), "class Shared { JsonMapper mapper = new JsonMapper(); }\n");

        assertEquals(List.of(
                        "com/example/DisabledUnknown.java:2: FAIL_ON_UNKNOWN_PROPERTIES not enabled",
                        "com/example/MissingTrailing.java:2: FAIL_ON_TRAILING_TOKENS not enabled",
                        "com/example/Shared.java:1: mapper built outside JsonMapper.builder()"),
                lenientMappers(sources),
                "each lenient mapper must be named by file, line and missing feature; Strict.java passes");
    }

    /**
     * The family ban on the path parser holds a router away from the parser, not away from the path: the decoder
     * offers the segments of the request, for the four routers that register their own context and are handed no
     * segments by the server. A router of the two other groups receives its segments from the server and holds the
     * same decoder; asking it for the path would be the second reading the ban exists to prevent, under another name.
     *
     * <p>Broken before acceptance, 13/10/2026: {@code requestDecoder.pathSegments(exchange)} written into
     * {@code MorpheusProjectSyncHttpRoutes} failed this method and this method alone.</p>
     */
    @Test
    void onlyARouterThatRegistersItsOwnContextAsksTheDecoderForThePath() {
        noClasses()
                .that().resideInAPackage(ROUTES_PACKAGE + "..")
                .and(DescribedPredicate.not(routersIn(ROUTERS_REGISTERING_THEIR_OWN_CONTEXT)))
                .should().callMethodWhere(ASKS_THE_DECODER_FOR_THE_PATH)
                .because("the server already parsed the path of a router it holds; a second reading through the "
                        + "decoder is what the family ban on MorpheusHttpPathParser forbids")
                .check(classes);
        classes()
                .that(routersIn(ROUTERS_REGISTERING_THEIR_OWN_CONTEXT))
                .should().callMethodWhere(ASKS_THE_DECODER_FOR_THE_PATH)
                .because("a router that registers its own context is handed no segments; it obtains them from the "
                        + "decoder, never from a reading of its own")
                .check(classes);
    }

    /** The transport groups, each a package, with the name the rules above speak of. */
    private static Map<String, String> transportGroups() {
        return new TreeMap<>(Map.of(
                ROUTERS_WITHOUT_A_BODY, "without a body",
                ROUTERS_THROUGH_THE_SHARED_DECODER, "through the shared decoder",
                ROUTERS_REGISTERING_THEIR_OWN_CONTEXT, "registering their own context"));
    }

    private static List<String> classificationDefects(Map<String, String> packageByRouter, Set<String> groups) {
        List<String> defects = new ArrayList<>();
        new TreeMap<>(packageByRouter).forEach((router, packageName) -> {
            if (!groups.contains(packageName)) {
                defects.add(router + " resides in " + packageName + ", which is no transport group");
            }
        });
        return defects;
    }

    private static DescribedPredicate<JavaClass> routersIn(String group) {
        return DescribedPredicate.describe("routers of " + group,
                javaClass -> javaClass.getPackageName().equals(group)
                        && javaClass.getSimpleName().endsWith(ROUTER_SUFFIX));
    }

    private Map<String, Path> routerSources(String group) {
        Map<String, Path> sources = new TreeMap<>();
        for (JavaClass javaClass : classes) {
            if (routersIn(group).test(javaClass) && !javaClass.isNestedClass()) {
                sources.put(javaClass.getSimpleName(), ProductionSources.mainSource("morpheus-api", javaClass.getSimpleName()));
            }
        }
        assertTrue(!sources.isEmpty(), () -> "no router source found for " + group);
        return sources;
    }

    private static void assertNoSourceMentions(Map<String, Path> sources, String literal) throws IOException {
        List<String> violations = new ArrayList<>();
        for (Map.Entry<String, Path> source : sources.entrySet()) {
            if (Files.readString(source.getValue()).contains(literal)) {
                violations.add(source.getKey());
            }
        }
        assertTrue(violations.isEmpty(), () -> "routers mentioning " + literal + ": " + violations);
    }

    private static long countMapperBuilders(Path sourcesRoot) throws IOException {
        long builders = 0;
        for (Path source : javaSources(sourcesRoot)) {
            builders += MAPPER_BUILDER.matcher(Files.readString(source)).results().count();
        }
        return builders;
    }

    private static List<String> lenientMappers(Path sourcesRoot) throws IOException {
        List<String> lenient = new ArrayList<>();
        for (Path source : javaSources(sourcesRoot)) {
            String text = Files.readString(source);
            String file = sourcesRoot.relativize(source).toString().replace('\\', '/');
            Matcher outside = MAPPER_OUTSIDE_THE_BUILDER.matcher(text);
            while (outside.find()) {
                lenient.add(file + ":" + lineOf(text, outside.start()) + ": mapper built outside JsonMapper.builder()");
            }
            Matcher builder = MAPPER_BUILDER.matcher(text);
            while (builder.find()) {
                Matcher build = BUILD_CALL.matcher(text);
                String chain = build.find(builder.end())
                        ? text.substring(builder.end(), build.end())
                        : text.substring(builder.end());
                Set<String> effective = new TreeSet<>(features(ENABLE_CALL, chain));
                effective.removeAll(features(DISABLE_CALL, chain));
                effective.removeAll(features(CONFIGURE_OFF, chain));
                for (String feature : STRICT_FEATURES) {
                    if (!effective.contains(feature)) {
                        lenient.add(file + ":" + lineOf(text, builder.start()) + ": " + feature + " not enabled");
                    }
                }
            }
        }
        return lenient;
    }

    private static Set<String> features(Pattern call, String chain) {
        Set<String> features = new TreeSet<>();
        Matcher matcher = call.matcher(chain);
        while (matcher.find()) {
            Matcher feature = FEATURE.matcher(matcher.group(1));
            while (feature.find()) {
                features.add(feature.group());
            }
        }
        return features;
    }

    private static int lineOf(String text, int index) {
        return (int) text.substring(0, index).chars().filter(character -> character == '\n').count() + 1;
    }

    private static List<Path> javaSources(Path sourcesRoot) throws IOException {
        try (var files = Files.walk(sourcesRoot)) {
            return files.filter(path -> path.getFileName().toString().endsWith(".java")).sorted().toList();
        }
    }

    private Path repositoryRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null) {
            if (Files.isRegularFile(current.resolve("contracts/public-surfaces.tsv"))
                    && Files.isRegularFile(current.resolve("pom.xml"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("cannot locate MORPHEUS repository root");
    }
}
