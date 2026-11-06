package com.morpheus.architecture;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * The life cycle of an MCP peer client exists once, in {@code McpPeerSession} of the shared transport.
 *
 * <p>The MINOS and NEXUS gateways carried two copies of it: building the bounded client, arming the operation deadline,
 * checking the required tools, calling a tool, closing, deleting the verified copy of the JAR and counting what fails
 * without failing the operation. A gateway now keeps what is its peer's own -- its tools, its payloads, the shape of
 * its launch command -- and opens its peer through the session.</p>
 *
 * <p>Both rules read compiled dependencies and calls: none of their targets declares a compile-time constant a text ban
 * would have to catch, and both integration modules are in the imported set. The gateways are selected by package and
 * by their dependency on {@code McpPeerLaunch}, never by a list of names, so a third gateway is held the same way.</p>
 */
class McpPeerGatewayLifecycleArchitectureTest {
    private static final String INTEGRATIONS = "com.morpheus.integration..";
    private static final String PEER_LAUNCH = "com.morpheus.transport.mcp.McpPeerLaunch";
    private static final String PEER_SESSION = "com.morpheus.transport.mcp.McpPeerSession";

    private static final JavaClasses PRODUCTION = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.morpheus.integration", "com.morpheus.transport.mcp");

    @Test
    void noIntegrationBuildsItsOwnPeerClientArmsItsDeadlineOrCleansItsCopy() {
        noClasses()
                .that().resideInAPackage(INTEGRATIONS)
                .should().dependOnClassesThat().haveFullyQualifiedName("io.modelcontextprotocol.client.McpClient")
                .orShould().dependOnClassesThat().haveFullyQualifiedName("io.modelcontextprotocol.client.McpSyncClient")
                .orShould().dependOnClassesThat()
                .haveFullyQualifiedName("com.morpheus.transport.mcp.BoundedStdioClientTransport")
                .orShould().callMethod("com.morpheus.transport.mcp.PeerOperationDeadline", "arm",
                        "java.time.Duration", "java.lang.Runnable")
                .orShould().callMethod("java.nio.file.Files", "deleteIfExists", "java.nio.file.Path")
                .because("the life cycle of an MCP peer client -- the bounded client, the operation deadline and the "
                        + "clean-up of the verified copy -- exists once, in McpPeerSession; a gateway hands it its peer "
                        + "and keeps only its operations")
                .check(PRODUCTION);
    }

    /**
     * Attaching a clean-up failure to the primary one is part of the session's life cycle. The ban is held on the
     * gateways only -- the classes that describe a peer launch -- because {@code javac} compiles every
     * try-with-resources into a call of {@code Throwable.addSuppressed}, and the runtimes and providers of the
     * integrations close their gateway that way.
     */
    @Test
    void noGatewayAttachesACleanUpFailureToThePrimaryOneItself() {
        noClasses()
                .that().resideInAPackage(INTEGRATIONS)
                .and(dependOn(PEER_LAUNCH))
                .should().callMethod("java.lang.Throwable", "addSuppressed", "java.lang.Throwable")
                .because("a failure to close the peer or delete its verified copy is attached to the primary failure "
                        + "by McpPeerSession, the one implementation of the peer client's life cycle")
                .check(PRODUCTION);
    }

    @Test
    void everyClassThatDescribesAPeerLaunchOpensItThroughTheSession() {
        classes()
                .that().resideInAPackage(INTEGRATIONS)
                .and(dependOn(PEER_LAUNCH))
                .should().dependOnClassesThat().haveFullyQualifiedName(PEER_SESSION)
                .because("a gateway that describes how its peer is launched opens it through McpPeerSession, the one "
                        + "implementation of the peer client's life cycle")
                .check(PRODUCTION);
    }

    private static DescribedPredicate<JavaClass> dependOn(String targetName) {
        return DescribedPredicate.describe("depend on " + targetName,
                candidate -> candidate.getDirectDependenciesFromSelf().stream()
                        .anyMatch(dependency -> dependency.getTargetClass().getName().equals(targetName)));
    }
}
