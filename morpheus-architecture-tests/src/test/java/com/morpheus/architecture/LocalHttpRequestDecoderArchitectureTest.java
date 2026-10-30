package com.morpheus.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.simpleNameContaining;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalHttpRequestDecoderArchitectureTest {

    private static final JavaClasses PRODUCTION = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.morpheus.api");

    @Test
    void localHttpRequestDecodingStaysExtractedStrictAndBounded() throws IOException {
        String server = Files.readString(ProductionSources.mainSource("morpheus-api", "MorpheusHttpServer"));
        String projectRootRoutes = Files.readString(ProductionSources.mainSource("morpheus-api", "MorpheusProjectRootHttpRoutes"));
        String projectSyncRoutes = Files.readString(ProductionSources.mainSource("morpheus-api", "MorpheusProjectSyncHttpRoutes"));
        String decoder = Files.readString(ProductionSources.mainSource("morpheus-api", "MorpheusHttpRequestDecoder"));
        String localHttpConsumers = server + "\n" + projectRootRoutes + "\n" + projectSyncRoutes;

        assertTrue(server.contains("private final MorpheusHttpRequestDecoder requestDecoder;"));
        assertTrue(server.contains("new MorpheusProjectRootHttpRoutes(this.service, this.requestDecoder)"));
        assertTrue(projectRootRoutes.contains("MorpheusHttpRequestDecoder requestDecoder"));
        assertTrue(projectRootRoutes.contains("requestDecoder.readRequiredJson("));
        assertTrue(projectSyncRoutes.contains("MorpheusHttpRequestDecoder requestDecoder"));
        assertTrue(projectSyncRoutes.contains("requestDecoder.readOptionalJson("));
        assertFalse(server.contains("requestDecoder.readRequiredJson("));
        assertFalse(localHttpConsumers.contains("JsonMapper"),
                "the local server and the project routers name no Jackson mapper: MorpheusHttpRequestDecoder "
                + "holds the one strict request mapper");
        assertFalse(localHttpConsumers.contains("DeserializationFeature"),
                "the local server and the project routers name no Jackson deserialization feature: the strict "
                + "features are set once, on the decoder's mapper");
        assertFalse(localHttpConsumers.contains("requireJsonContentType("));
        assertTrue(decoder.contains("final class MorpheusHttpRequestDecoder"));
        assertTrue(decoder.contains("FAIL_ON_UNKNOWN_PROPERTIES"));
        assertTrue(decoder.contains("FAIL_ON_TRAILING_TOKENS"));
        assertTrue(decoder.contains("HttpRequestBodyReader.read("));
        assertTrue(Files.readString(ProductionSources.mainSource("morpheus-api", "HttpRequestBodyReader"))
                .contains("TimedBoundedInputReader.read("));
        assertTrue(decoder.contains("Content-Type application/json is required"));
    }

    /**
     * {@code MorpheusHttpServer} and {@code MorpheusProjectRootHttpRoutes} and {@code MorpheusProjectSyncHttpRoutes}
     * read no request body themselves: {@code MorpheusHttpRequestDecoder} reads it, bounded in size and time. The rule
     * bans a dependency on any class whose simple name contains {@code TimedBoundedInputReader}, as the text ban it
     * replaces matched the name.
     *
     * <p>None of these types declares a compile-time constant, so the rule alone covers them. Broken before acceptance,
     * 30/10/2026: a {@code TimedBoundedInputReader} field added to {@code MorpheusHttpServer} failed this rule.</p>
     */
    @Test
    void theServerAndTheProjectRoutersReadNoBodyThemselves() {
        noClasses()
                .that(ClassesOfASource.declaredIn("MorpheusHttpServer", "MorpheusProjectRootHttpRoutes", "MorpheusProjectSyncHttpRoutes"))
                .should().dependOnClassesThat(simpleNameContaining("TimedBoundedInputReader"))
                .because("a request body is read once, through the decoder, under the size and time bounds it applies")
                .check(PRODUCTION);
    }
}
