package com.morpheus.architecture;

import com.tngtech.archunit.base.DescribedPredicate;
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
 * <p>ADR-0103's amendment of 11/09/2026 sketched two groups: eight routers without a body, nine that read one.
 * Establishing the second group literal by literal (15/09/2026) showed it is two groups, not one:</p>
 *
 * <ul>
 *   <li><strong>without a body</strong> -- eight routers that touch neither {@code com.sun.net.httpserver}, nor
 *       Jackson, nor the shared decoder;</li>
 *   <li><strong>through the shared decoder</strong> -- five routers that carry {@code HttpExchange} and read their
 *       body through {@code MorpheusHttpRequestDecoder}, and never touch Jackson;</li>
 *   <li><strong>with their own mapper</strong> -- four routers, policy, policy-management, query and reasoning, that
 *       build a {@code JsonMapper}, serialize through {@code CanonicalJsonSerializer} and read their body through
 *       {@code HttpRequestBodyReader} instead of the decoder. They are the four routers no architecture test covered
 *       before the family rules. Their protections are the decoder's -- same body cap, same read deadline, the same
 *       two deserialization features, the same 415 -- so what they duplicate is configuration, and the rule over
 *       them is a configuration rule, not a ban. Routing them through the decoder would be a behaviour change on
 *       four write surfaces and is not what these rules do.</li>
 * </ul>
 *
 * <p>Every group is listed by name. A router added tomorrow belongs to no group until someone classifies it, and
 * {@link #everyRouterIsClassifiedIntoExactlyOneTransportGroup} refuses it until then.</p>
 *
 * <p>{@code MorpheusHttpServer} appears in no ban. Six routers mention it for two different reasons: four borrow
 * {@code API_PREFIX}, a compile-time constant javac inlines, so ArchUnit sees no dependency; two decode into records
 * nested in it, which ArchUnit does see. A bytecode ban would miss the first four and a text ban would refuse all
 * six. It is not the boundary. {@code HttpRequestBodyReader} shows the same split inside one file: it reads
 * {@code MorpheusHttpServer.MAX_REQUEST_BODY_BYTES} (inlined, invisible to a rule) and
 * {@code MorpheusHttpServer.REQUEST_BODY_READ_TIMEOUT} (a {@code Duration}, visible).</p>
 */
class HttpRoutesTransportBoundaryArchitectureTest {

    private static final String API_PACKAGE = "com.morpheus.api";
    private static final String ROUTER_SUFFIX = "HttpRoutes";

    private static final List<String> ROUTERS_WITHOUT_A_BODY = List.of(
            "MorpheusCompositionHttpRoutes",
            "MorpheusDiagnosticsHttpRoutes",
            "MorpheusExternalReferenceHttpRoutes",
            "MorpheusIntegrationStatusHttpRoutes",
            "MorpheusProviderPluginHttpRoutes",
            "MorpheusRootHttpRoutes",
            "MorpheusSpecificationsHttpRoutes",
            "MorpheusVersionsHttpRoutes");
    private static final List<String> ROUTERS_THROUGH_THE_SHARED_DECODER = List.of(
            "MorpheusChangesHttpRoutes",
            "MorpheusPortfolioHttpRoutes",
            "MorpheusProjectRootHttpRoutes",
            "MorpheusProjectSyncHttpRoutes",
            "MorpheusRequirementsHttpRoutes");
    private static final List<String> ROUTERS_THROUGH_THE_DECODER_WRITING_THEIR_OWN_RESPONSE = List.of(
            "MorpheusReasoningHttpRoutes");
    private static final List<String> ROUTERS_WITH_THEIR_OWN_MAPPER = List.of(
            "MorpheusPolicyHttpRoutes",
            "MorpheusPolicyManagementHttpRoutes",
            "MorpheusQueryHttpRoutes");

    private static final String REQUEST_DECODER = API_PACKAGE + ".MorpheusHttpRequestDecoder";
    private static final String REQUEST_BODY_READER = API_PACKAGE + ".HttpRequestBodyReader";
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
     * Each group is a list, so a router outside every list escapes every rule below; and a listed router that no
     * longer exists narrows its group's rule without {@code failOnEmptyShould} noticing, as long as one survives.
     * Both are refused by name.
     *
     * <p>Broken before acceptance, 15/09/2026: an empty {@code MorpheusProbeHttpRoutes} added to {@code morpheus-api}
     * failed this method, naming it, while {@code HttpRoutesFamilyArchitectureTest} passed -- the family rules see the
     * new router, but no transport rule would have.</p>
     */
    @Test
    void everyRouterIsClassifiedIntoExactlyOneTransportGroup() {
        TreeSet<String> routers = new TreeSet<>();
        for (JavaClass javaClass : classes) {
            if (javaClass.getSimpleName().endsWith(ROUTER_SUFFIX)) {
                routers.add(javaClass.getSimpleName());
            }
        }
        List<String> defects = classificationDefects(routers, transportGroups());
        assertTrue(defects.isEmpty(), () -> "every router must be classified into exactly one transport group of "
                + "this test, by name: " + defects);
    }

    @Test
    void theClassificationRefusesAnUnclassifiedADoublyClassifiedAndAVanishedRouter() {
        Map<String, List<String>> groups = new TreeMap<>(Map.of(
                "without a body", List.of("AlphaHttpRoutes", "BetaHttpRoutes"),
                "through the shared decoder", List.of("BetaHttpRoutes", "GoneHttpRoutes")));

        assertEquals(List.of(
                        "BetaHttpRoutes is classified in [through the shared decoder, without a body]",
                        "GammaHttpRoutes is classified in no group",
                        "GoneHttpRoutes is classified but no such router exists"),
                classificationDefects(Set.of("AlphaHttpRoutes", "BetaHttpRoutes", "GammaHttpRoutes"), groups));
        assertEquals(List.of(), classificationDefects(Set.of("AlphaHttpRoutes"),
                Map.of("without a body", List.of("AlphaHttpRoutes"))));
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
                .that(routersNamed(ROUTERS_WITHOUT_A_BODY))
                .should().dependOnClassesThat(resideInAnyPackage("com.sun.net.httpserver..", "tools.jackson..")
                        .or(name(REQUEST_DECODER)))
                .because("a router that reads no request body decides a route and returns a value; the exchange, "
                        + "the decoding and the encoding belong to the server")
                .check(classes);

        assertNoSourceMentions(routerSources(repositoryRoot(), ROUTERS_WITHOUT_A_BODY), "tools.jackson");
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
                .that(routersNamed(ROUTERS_THROUGH_THE_SHARED_DECODER))
                .should().dependOnClassesThat(resideInAnyPackage("tools.jackson..")
                        .or(name(CANONICAL_JSON_SERIALIZER)))
                .because("a router that reads its body through MorpheusHttpRequestDecoder inherits its strict "
                        + "mapper; a mapper or serializer of its own is a second configuration nothing keeps in step")
                .check(classes);

        Map<String, Path> sources = routerSources(repositoryRoot(), ROUTERS_THROUGH_THE_SHARED_DECODER);
        assertNoSourceMentions(sources, "tools.jackson");
        assertNoSourceMentions(sources, "CanonicalJsonSerializer");
    }

    /**
     * A router that registers its own HTTP context and writes its own response still reads its body through the
     * server's decoder: it depends on {@code MorpheusHttpRequestDecoder}, on no body reader of its own, and on no
     * Jackson type.
     *
     * <p>These routers serialize their response envelope themselves, through {@code CanonicalJsonSerializer}; that
     * is the one difference with the routers above and it is why they are a group of their own. What they no longer
     * carry is a second request boundary -- a mapper, a reader, a failure type -- that nothing kept in step with the
     * decoder's. {@code HttpRequestBodyReader} and the decoder declare no static constant, so the rule alone covers
     * them; Jackson declares many, so its half keeps its text.</p>
     */
    @Test
    void routersWritingTheirOwnResponseReadTheirBodyThroughTheSharedDecoder() throws IOException {
        classes()
                .that(routersNamed(ROUTERS_THROUGH_THE_DECODER_WRITING_THEIR_OWN_RESPONSE))
                .should().dependOnClassesThat(name(REQUEST_DECODER))
                .because("the request boundary of a router that registers its own context is the server's decoder")
                .check(classes);
        noClasses()
                .that(routersNamed(ROUTERS_THROUGH_THE_DECODER_WRITING_THEIR_OWN_RESPONSE))
                .should().dependOnClassesThat(resideInAnyPackage("tools.jackson..").or(name(REQUEST_BODY_READER)))
                .because("a mapper or a body reader of its own is a second request boundary nothing keeps in step")
                .check(classes);

        assertNoSourceMentions(
                routerSources(repositoryRoot(), ROUTERS_THROUGH_THE_DECODER_WRITING_THEIR_OWN_RESPONSE), "tools.jackson");
    }

    /**
     * Every {@code JsonMapper.builder()} of {@code morpheus-api} enables {@code FAIL_ON_UNKNOWN_PROPERTIES} and
     * {@code FAIL_ON_TRAILING_TOKENS}, and no mapper is built any other way.
     *
     * <p>Five sites on 15/09/2026, all conforming: a gain of reach, not a correction. A sixth that forgot one flag
     * would accept unknown fields or trailing tokens silently.</p>
     *
     * <p>This is text, not bytecode, by ADR-0103: its first section reserves rules for an intention of the form "no
     * dependency", and which feature a builder received is no dependency at all -- it is an argument, the same kind
     * of fact as the wiring expressions its second section keeps textual.</p>
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

    private static Map<String, List<String>> transportGroups() {
        return Map.of(
                "without a body", ROUTERS_WITHOUT_A_BODY,
                "through the shared decoder", ROUTERS_THROUGH_THE_SHARED_DECODER,
                "through the decoder, writing their own response", ROUTERS_THROUGH_THE_DECODER_WRITING_THEIR_OWN_RESPONSE,
                "with their own mapper", ROUTERS_WITH_THEIR_OWN_MAPPER);
    }

    private static List<String> classificationDefects(Set<String> routers, Map<String, List<String>> groups) {
        Map<String, TreeSet<String>> groupsByRouter = new TreeMap<>();
        groups.forEach((group, members) -> members.forEach(
                router -> groupsByRouter.computeIfAbsent(router, ignored -> new TreeSet<>()).add(group)));
        TreeSet<String> named = new TreeSet<>(routers);
        named.addAll(groupsByRouter.keySet());

        List<String> defects = new ArrayList<>();
        for (String router : named) {
            TreeSet<String> memberships = groupsByRouter.getOrDefault(router, new TreeSet<>());
            if (!routers.contains(router)) {
                defects.add(router + " is classified but no such router exists");
            } else if (memberships.isEmpty()) {
                defects.add(router + " is classified in no group");
            } else if (memberships.size() > 1) {
                defects.add(router + " is classified in " + memberships);
            }
        }
        return defects;
    }

    private static DescribedPredicate<JavaClass> routersNamed(List<String> routers) {
        return DescribedPredicate.describe("routers " + routers,
                javaClass -> javaClass.getPackageName().equals(API_PACKAGE)
                        && routers.contains(javaClass.getSimpleName()));
    }

    private static Map<String, Path> routerSources(Path repository, List<String> routers) {
        Map<String, Path> sources = new TreeMap<>();
        for (String router : routers) {
            sources.put(router, repository.resolve(
                    "morpheus-api/src/main/java/com/morpheus/api/" + router + ".java"));
        }
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
