package com.morpheus.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.core.domain.properties.HasName.Predicates.name;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The two roles of {@code morpheus-api} at the bottom of its layering, each selected by its package.
 *
 * <p>The application services reside in the parent package {@code com.morpheus.api}, with {@code ApiRuntime} and the
 * request records they read: that package is their role, and every {@code *ApiService} class resides in it. The
 * transport plumbing resides in {@code com.morpheus.api.transport}, under every other package of the adapter.
 * {@code ApiPackageCyclesArchitectureTest} refuses a dependency that closes a cycle; these rules refuse a dependency
 * back up the layering whether or not the other direction exists today, and name the role they protect.</p>
 *
 * <p>Rule and text both, for one reason each. The rules see a dependency, spelled or not. The local server
 * ({@code DEFAULT_HOST}, {@code DEFAULT_PORT}, {@code MAX_REQUEST_BODY_BYTES}), the remote server
 * ({@code MAX_CONCURRENT_REQUESTS}) and the facade {@code MorpheusApiService} ({@code DEFAULT_LIMIT},
 * {@code MAX_LIMIT} and four more) declare public compile-time constants, which javac inlines; reading one from
 * another package spells that package, which the text scans refuse.</p>
 */
class ApiServicesAndTransportPackagesArchitectureTest {

    private static final String API = "com.morpheus.api";
    private static final String TRANSPORT = API + ".transport";
    private static final String SERVICE_SUFFIX = "ApiService";
    private static final String RESPONSE_WRITER = TRANSPORT + ".MorpheusHttpResponseWriter";

    /** A mention of a package of the adapter other than the transport plumbing. */
    private static final Pattern ANOTHER_ADAPTER_PACKAGE = Pattern.compile("com\\.morpheus\\.api\\.(?!transport\\b)\\w+");
    /** A mention of a package of the adapter above the services. */
    private static final Pattern A_PACKAGE_ABOVE_THE_SERVICES =
            Pattern.compile("com\\.morpheus\\.api\\.(?:local|remote|routes)\\b");

    private static final JavaClasses PRODUCTION = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(API);

    /**
     * The transport plumbing depends on no other package of the adapter: it is what every other package builds on.
     *
     * <p>Broken before acceptance, 30/10/2026: a {@code MorpheusApiService} parameter added to
     * {@code MorpheusHttpQuery} failed the rule; {@code MorpheusHttpServer.DEFAULT_PORT} read in
     * {@code MorpheusHttpRouteGuards} passed the rule and failed the text scan alone.</p>
     */
    @Test
    void theTransportPlumbingDependsOnNoOtherPackageOfTheAdapter() throws IOException {
        noClasses()
                .that().resideInAPackage(TRANSPORT + "..")
                .should().dependOnClassesThat(resideInAnyPackage(API, API + ".local..", API + ".remote..",
                        API + ".routes.."))
                .because("the transport plumbing sits under the services, the routers and both servers; a "
                        + "dependency back up makes the decoder, the writer or the parser depend on what they serve")
                .check(PRODUCTION);

        assertEquals(List.of(), sourcesMentioning(packageSources(TRANSPORT), ANOTHER_ADAPTER_PACKAGE),
                "a transport source names another package of the adapter, as an import or a constant it reads");
    }

    /**
     * The services depend on no router, on neither server, and not on the response writer: a service returns a value
     * the routers carry, and never touches the exchange.
     *
     * <p>Broken before acceptance, 30/10/2026: a {@code MorpheusHttpResponseWriter} field added to
     * {@code MorpheusSpecificationQueryApiService} failed the rule;
     * {@code MorpheusRemoteHttpServer.MAX_CONCURRENT_REQUESTS} read in {@code MorpheusDiagnosticsApiService} passed the
     * rule and failed the text scan alone.</p>
     */
    @Test
    void theServicesDependOnNoRouterNoServerAndNotTheResponseWriter() throws IOException {
        noClasses()
                .that().resideInAPackage(API)
                .should().dependOnClassesThat(resideInAnyPackage(API + ".local..", API + ".remote..",
                        API + ".routes..", "com.sun.net.httpserver..").or(name(RESPONSE_WRITER)))
                .because("the services answer the application's questions for every transport; the routers and the "
                        + "servers call them, and the exchange and the response belong to the transport")
                .check(PRODUCTION);

        assertEquals(List.of(), sourcesMentioning(packageSources(API), A_PACKAGE_ABOVE_THE_SERVICES),
                "a source of the services package names a router, server or remote package");
    }

    /**
     * The services are a package, not a suffix: an {@code *ApiService} class anywhere else is refused by name, with
     * the package it resides in, and the package holds at least one.
     *
     * <p>Broken before acceptance, 30/10/2026: an empty {@code PlantedApiService} in {@code com.morpheus.api.transport}
     * failed this method, named with its package.</p>
     */
    @Test
    void everyApiServiceResidesInTheServicesPackage() {
        List<String> misplaced = new ArrayList<>();
        int services = 0;
        for (JavaClass javaClass : PRODUCTION) {
            if (javaClass.isNestedClass() || !javaClass.getSimpleName().endsWith(SERVICE_SUFFIX)) {
                continue;
            }
            if (javaClass.getPackageName().equals(API)) {
                services++;
            } else {
                misplaced.add(javaClass.getSimpleName() + " resides in " + javaClass.getPackageName());
            }
        }
        assertTrue(misplaced.isEmpty(), () -> "every *ApiService class resides in " + API + ": " + misplaced);
        assertTrue(services > 0, "no *ApiService class found in " + API + ": a rule over the package holds over nothing");
    }

    /** The sources of the top-level classes the rules see in the package, read by their class name. */
    private static Map<String, String> packageSources(String packageName) throws IOException {
        Map<String, String> sources = new TreeMap<>();
        for (JavaClass javaClass : PRODUCTION) {
            if (javaClass.getPackageName().equals(packageName) && javaClass.isTopLevelClass()) {
                sources.put(javaClass.getSimpleName(),
                        Files.readString(ProductionSources.mainSource("morpheus-api", javaClass.getSimpleName())));
            }
        }
        assertTrue(!sources.isEmpty(), () -> "no class found in " + packageName);
        return sources;
    }

    private static List<String> sourcesMentioning(Map<String, String> sources, Pattern mention) {
        List<String> found = new ArrayList<>();
        sources.forEach((file, text) -> mention.matcher(text).results()
                .forEach(match -> found.add(file + ": " + match.group())));
        return found;
    }
}
