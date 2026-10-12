package com.morpheus.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The HTTP adapter reads a request path in one place.
 *
 * <p>Three copies of a segment splitter used to decode a path the JDK had already decoded, with a form decoder, while
 * the remote role policy matched the same path decoded once: the code that authorized a request and the code that
 * routed it read it two ways. {@code MorpheusHttpPathParser} is now the only reader, and it reads the path as it was
 * sent. The rules below keep a second reader from coming back under any of the three shapes it took.</p>
 */
class HttpPathReadingArchitectureTest {
    private static final String API_SOURCES = "morpheus-api/src/main/java/com/morpheus/api";

    /** The classes that decode a form, where {@code +} is a space by definition, each with what it decodes. */
    private static final Map<String, String> FORM_DECODERS = Map.of(
            "MorpheusHttpQuery", "the query parameters of a local request",
            "MorpheusRemoteProxyTargetResolver", "the query parameters the remote proxy rewrites before forwarding");

    /** The files that split on a slash something other than a request path, each with what it splits. */
    private static final Map<String, String> SLASH_SPLITTERS = Map.of(
            "MorpheusHttpPathParser.java", "the request path, and the API prefix it is matched against",
            "MorpheusHttpRouteTable.java", "the route templates written in the table itself");

    /** The files that call a {@code getPath()} which is not the decoded path of a request. */
    private static final Map<String, String> OTHER_GET_PATH_CALLERS = Map.of(
            "RequestProtectedHttpServer.java", "HttpContext.getPath(), the name a context was registered under");

    private final JavaClasses classes = new ClassFileImporter().importPackages("com.morpheus.api");

    @Test
    void noClassOfTheAdapterFormDecodesAnythingButAQuery() {
        noClasses()
                .that().resideInAPackage("com.morpheus.api..")
                .and().haveSimpleNameNotEndingWith("Test")
                .and(com.tngtech.archunit.base.DescribedPredicate.describe("are not the named form decoders",
                        type -> !FORM_DECODERS.containsKey(type.getSimpleName())))
                .should().dependOnClassesThat().haveFullyQualifiedName("java.net.URLDecoder")
                .because("URLDecoder is a form decoder: it turns + into a space and, applied to a path the JDK "
                        + "already decoded, decodes it a second time; a path is read by MorpheusHttpPathParser")
                .check(classes);
    }

    @Test
    void everyNamedFormDecoderStillDecodesAForm() {
        for (String decoder : FORM_DECODERS.keySet()) {
            classes()
                    .that().haveFullyQualifiedName("com.morpheus.api." + decoder)
                    .should().dependOnClassesThat().haveFullyQualifiedName("java.net.URLDecoder")
                    .because("an exception that no longer applies is removed from FORM_DECODERS, not kept")
                    .check(classes);
        }
    }

    @Test
    void noSourceOfTheAdapterReadsTheDecodedPathOfARequest() throws IOException {
        assertEquals(new TreeSet<>(OTHER_GET_PATH_CALLERS.keySet()), sourcesMentioning(".getPath()"),
                "getRequestURI().getPath() is the path decoded once by the JDK: splitting it treats an encoded "
                        + "slash as a separator. The path is read by MorpheusHttpPathParser from getRawPath(); a "
                        + "getPath() that is not a request path is named in OTHER_GET_PATH_CALLERS with its reason");
    }

    @Test
    void onlyTheParserSplitsARequestPath() throws IOException {
        assertEquals(new TreeSet<>(SLASH_SPLITTERS.keySet()), sourcesMentioning(".split(\"/\""),
                "a second segment splitter is a second reading of the path; a split that is not a request path is "
                        + "named in SLASH_SPLITTERS with its reason");
    }

    @Test
    void theLocalServerTheRoutersAndTheRemotePolicyObtainTheirSegmentsFromTheParser() throws IOException {
        assertTrue(source("MorpheusHttpServer.java")
                        .contains("pathSegments(exchange.getRequestURI().getRawPath())"),
                "the local server hands the parser the path as it was sent");
        assertTrue(source("MorpheusHttpRequestDecoder.java")
                        .contains("pathParser.segments(exchange.getRequestURI().getRawPath())"),
                "the routers that register their own context obtain their segments from the parser");
        assertTrue(source("MorpheusRemoteRoutePolicy.java").contains("return PATHS.segments(rawPath);"),
                "the remote role policy obtains its segments from the parser");
        assertTrue(source("MorpheusRemoteHttpServer.java")
                        .contains("authorize(exchange.getRequestMethod(), exchange.getRequestURI().getRawPath())"),
                "the remote server authorizes the path as it was sent, which is also the path it forwards");
    }

    @Test
    void theAdapterHoldsOneParserInstance() throws IOException {
        assertEquals(Set.of("MorpheusHttpPathParser.java"), sourcesMentioning("new MorpheusHttpPathParser("),
                "every reader of a path shares MorpheusHttpPathParser.api(); a second instance could be given "
                        + "another prefix and read the same path differently");
    }

    @Test
    void theRemoteServerAsksTheAuthorizationNotThePathForWhatFollows() throws IOException {
        String server = source("MorpheusRemoteHttpServer.java");

        assertEquals(1, occurrences(server, "MorpheusRemoteRoutePolicy.authorize("),
                "a request is authorized once; the route and the role are then read from that result");
        assertTrue(server.contains("route.names(\"server/status\")")
                        && server.contains("route.names(\"server/backups\")")
                        && server.contains("route.names(\"provider-plugins/probe\")")
                        && server.contains("route.usesBoundedUpstreamTimeout()"),
                "the routes the remote server treats itself are named by the authorization of the request");
    }

    @Test
    void theTextualRulesReadEverySourceBelowTheAdapterPackage(@TempDir Path directory) throws IOException {
        Path nested = Files.createDirectories(directory.resolve("remote/deeper"));
        Files.writeString(directory.resolve("Top.java"), "class Top { String p = uri.getPath(); }");
        Files.writeString(nested.resolve("Nested.java"), "class Nested { String p = uri.getPath(); }");
        Files.writeString(nested.resolve("notes.txt"), "uri.getPath()");

        assertEquals(Set.of("Top.java", "remote/deeper/Nested.java"), sourcesMentioning(directory, ".getPath()"));
    }

    private static Set<String> sourcesMentioning(String text) throws IOException {
        return sourcesMentioning(repositoryRoot().resolve(API_SOURCES), text);
    }

    /** The Java sources at or below {@code directory} that contain {@code text}, by their path under it. */
    private static Set<String> sourcesMentioning(Path directory, String text) throws IOException {
        Set<String> mentioning = new TreeSet<>();
        try (Stream<Path> sources = Files.walk(directory)) {
            for (Path source : sources.filter(path -> path.toString().endsWith(".java")).toList()) {
                if (Files.readString(source).contains(text)) {
                    mentioning.add(directory.relativize(source).toString().replace('\\', '/'));
                }
            }
        }
        return mentioning;
    }

    private static int occurrences(String text, String sought) {
        int count = 0;
        for (int index = text.indexOf(sought); index >= 0; index = text.indexOf(sought, index + 1)) {
            count++;
        }
        return count;
    }

    private static String source(String name) throws IOException {
        return Files.readString(repositoryRoot().resolve(API_SOURCES).resolve(name));
    }

    private static Path repositoryRoot() {
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
