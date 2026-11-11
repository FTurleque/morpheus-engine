package com.morpheus.architecture.m22;

import com.morpheus.architecture.ClassesOfASource;
import com.morpheus.architecture.ProductionSources;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.assignableTo;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.belongToAnyOf;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.morpheus.architecture.RepositoryRoot;
import com.morpheus.sdk.provider.ProviderPluginService;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Locale;
import java.util.ServiceLoader;
import org.junit.jupiter.api.Test;

/**
 * The external-code trust boundary, stated as what it guarantees and as what it deliberately does not (ADR-0129).
 *
 * <p>MORPHEUS runs two kinds of code it did not write: a provider plugin an operator activated, and an MCP peer
 * whose launch command an operator configured. Both cross a real process and environment boundary. None of it is
 * an operating-system sandbox, and the accumulation of controls is exactly what makes that easy to misread -- so
 * the absence is asserted here alongside the guarantees. An absence cannot be deduced from the code, which is why
 * it needs a test of its own: the failure mode is an operator running code they do not trust, relying on a
 * guarantee nobody ever made.</p>
 */
class ProviderPluginTrustBoundaryContractTest {
    private static final String SDK = "morpheus-provider-sdk/src/main/java/com/morpheus/sdk/provider/";

    /**
     * Phrasings that assert containment MORPHEUS does not provide, in either documentation language.
     *
     * <p>Each one is unambiguous on its own, so no surrounding negation can rescue it -- which is the point: the
     * failure mode is a reader who stops at the sentence that sounds reassuring.</p>
     */
    private static final List<String> AFFIRMED_SANDBOX_CLAIMS = List.of(
            "runs in a sandbox",
            "run in a sandbox",
            "executed in a sandbox",
            "provides a sandbox",
            "sandboxes the plugin",
            "sandboxes untrusted",
            "is sandboxed",
            "are sandboxed",
            "sandbox guarantees",
            "s'exécute dans un sandbox",
            "s'exécutent dans un sandbox",
            "dans une sandbox",
            "fournit un sandbox",
            "fournit une sandbox");

    @Test
    void executingApprovedCodeRequiresAPinAndTheChildVerifiesItAgain() throws IOException {
        String activator = read(SDK + "ProviderPluginActivator.java");
        String worker = read(SDK + "ProviderPluginProbeWorker.java");
        String probe = read(SDK + "ProviderPluginProbeProcess.java");

        assertTrue(activator.contains("ExternalJarIntegrity.normalizeSha256(expectedSha256)"));
        assertTrue(activator.contains(
                        "Path stagedJar = ExternalJarIntegrity.stageVerifiedCopy(candidate.jarPath(), trustedSha256);"),
                "the verified copy must be what is loaded, closing the verify-to-load window on the original path");
        assertTrue(activator.contains("new java.net.URL[] {stagedJar.toUri().toURL()}"),
                "the classloader must only ever see the staged copy");

        assertTrue(probe.contains("command(candidate, workspaceRoot, trustedSha256, resultFile)"),
                "the parent must hand the trusted pin to the child rather than a trust decision");
        assertTrue(worker.contains("new ProviderPluginActivator().activate(candidate, sha256)"),
                "the child must verify the pin itself instead of trusting the parent's word");
        assertTrue(worker.contains("expected: JAR WORKSPACE SHA256 RESULT_FILE"),
                "the pin must stay a required argument of the worker");
    }

    /**
     * The only public way into third-party provider code is the probe, and it takes the pin.
     *
     * <p>The property is one of the declared API, read on the compiled classes: an overload without a pin -- even
     * one that only throws today -- is a second entry whose body can change without any gate reading it again. The
     * activator and its handle are not public, so no host activates plugin code in its own process: plugin code runs
     * in the probe worker, the child process of ADR-0129.</p>
     */
    @Test
    void theOnlyPublicEntryToPluginCodeIsThePinnedProbe() {
        var sdk = new ClassFileImporter().importPackages("com.morpheus.sdk.provider");
        JavaClass activator = sdk.get("com.morpheus.sdk.provider.ProviderPluginActivator");
        JavaClass activation = sdk.get("com.morpheus.sdk.provider.ProviderPluginActivation");
        JavaClass service = sdk.get(ProviderPluginService.class);

        for (JavaClass hidden : List.of(activator, activation)) {
            assertFalse(hidden.getModifiers().contains(JavaModifier.PUBLIC),
                    () -> hidden.getSimpleName() + " must not be public: a public activator loads plugin code in the "
                            + "caller's process, outside the probe worker");
        }
        assertEquals(
                List.of("com.morpheus.sdk.provider.ProviderPluginActivator.activate("
                        + "com.morpheus.sdk.provider.ProviderPluginCandidate, java.lang.String)"),
                activator.getMethods().stream()
                        .filter(method -> method.getName().equals("activate"))
                        .map(JavaMethod::getFullName)
                        .toList(),
                "activation must have exactly one entry, and it must take the trusted SHA-256 pin");
        assertEquals(
                List.of("com.morpheus.sdk.provider.ProviderPluginService.probe("
                        + "java.nio.file.Path, java.lang.String, java.nio.file.Path, java.lang.String)"),
                publicMethodsNamed(service, "probe"),
                "the probe must have exactly one public entry, and it must take the trusted SHA-256 pin");
        for (JavaClass entry : List.of(activator, service)) {
            assertEquals(
                    List.of(),
                    entry.getMethods().stream()
                            .filter(method -> method.isAnnotatedWith(Deprecated.class))
                            .map(JavaMethod::getFullName)
                            .sorted()
                            .toList(),
                    () -> entry.getSimpleName() + " must not keep a deprecated entry waiting for its removal");
        }
    }

    private static List<String> publicMethodsNamed(JavaClass owner, String name) {
        return owner.getMethods().stream()
                .filter(method -> method.getName().equals(name))
                .filter(method -> method.getModifiers().contains(JavaModifier.PUBLIC))
                .map(JavaMethod::getFullName)
                .sorted()
                .toList();
    }

    /**
     * Discovery stays metadata-only: it reads the plugin directory and the JAR manifests, and nothing else.
     *
     * <p>A class loader of any kind, {@code ServiceLoader}, {@code ProcessBuilder} and {@code Process} declare no
     * compile-time constant, so the rule alone covers them, and it sees a dependency of any kind where the text saw
     * only {@code new URLClassLoader}, {@code ServiceLoader.load(} and {@code new ProcessBuilder}. Broken before
     * acceptance, 30/10/2026: a {@code URLClassLoader} field added to {@code ProviderPluginDiscovery} failed the rule,
     * and so did a {@code ProcessBuilder} one.</p>
     */
    @Test
    void discoveryStaysMetadataOnly() throws IOException {
        String discovery = read(SDK + "ProviderPluginDiscovery.java");

        assertTrue(discovery.contains("LinkOption.NOFOLLOW_LINKS"));
        assertTrue(discovery.contains("Files.isSymbolicLink"));
        assertTrue(discovery.contains(".toRealPath()") && discovery.contains("\"PLUGIN_DIRECTORY_PATH_RESOLVED\""),
                "a linked ancestor must be resolved and declared, so the operator sees which tree was scanned");
        assertTrue(discovery.contains("This class never creates a ClassLoader or ServiceLoader"),
                "the metadata-only contract must stay stated where it is implemented");
        noClasses()
                .that(ClassesOfASource.declaredIn("ProviderPluginDiscovery"))
                .should().dependOnClassesThat(assignableTo(ClassLoader.class)
                        .or(belongToAnyOf(ServiceLoader.class, ProcessBuilder.class, Process.class)))
                .because("discovery reads metadata only: it never loads a class, resolves a service or starts a "
                        + "process; only the pinned probe runs plugin code")
                .check(new ClassFileImporter().importPackages("com.morpheus.sdk.provider"));
    }

    @Test
    void theChildInheritsAnAllowlistedEnvironmentAndNeverASecret() throws IOException {
        String probe = read(SDK + "ProviderPluginProbeProcess.java");

        assertTrue(probe.contains("static void sanitizeEnvironment(Map<String, String> environment)"));
        assertTrue(probe.contains("environment.clear();"),
                "the child environment must be rebuilt from an allowlist, never filtered from the parent's");
        assertTrue(probe.contains("SAFE_ENVIRONMENT_KEYS.contains(entry.getKey().toUpperCase(Locale.ROOT))"));
        for (String secret : List.of(
                "MORPHEUS_SERVER_TLS_PASSWORD",
                "JAVA_TOOL_OPTIONS",
                "_JAVA_OPTIONS",
                "JDK_JAVA_OPTIONS",
                "CLASSPATH")) {
            assertFalse(probe.contains("\"" + secret + "\""),
                    () -> secret + " must never appear in the launch allowlist");
        }
    }

    @Test
    void theChildIsBoundedInTimeAndCannotLeaveAProcessBehind() throws IOException {
        String probe = read(SDK + "ProviderPluginProbeProcess.java");
        String worker = read(SDK + "ProviderPluginProbeWorker.java");

        assertTrue(probe.contains("DEFAULT_TIMEOUT = Duration.ofSeconds(45)"));
        assertTrue(probe.contains("provider plugin probe exceeded the "),
                "an overrunning probe must be reported as a bound that was exceeded");
        assertTrue(probe.contains("private void terminate(Process process, Map<Long, ProcessHandle> observed)"));
        assertTrue(probe.contains("signalDescendants(root, observed, true)"),
                "the parent must force-terminate the descendants it observed, not only the worker");

        assertTrue(worker.contains("Runtime.getRuntime().addShutdownHook"),
                "the worker is MORPHEUS code and must reap its own subtree while it is still enumerable");
        assertTrue(worker.contains("ProviderPluginDescendantTermination.reapDescendantsOf("));
    }

    @Test
    void executingThirdPartyCodeIsNeverModelFacingAndTheDirectoryIsServerChosen() throws IOException {
        String manifest = read("contracts/public-surfaces.tsv");
        String mcpTools = ProductionSources.readMain("morpheus-mcp", "MorpheusProviderPluginMcpTools");
        String httpRoutes = ProductionSources.readMain("morpheus-api", "MorpheusProviderPluginHttpRoutes");

        assertTrue(manifest.contains(
                        "provider.plugins.probe\tWRITE\tprovider-plugins probe\tEXPLICITLY_NOT_EXPOSED\t"),
                "executable third-party code must stay absent from the model-facing transport");
        assertTrue(mcpTools.contains("this.pluginDirectory = Optional.empty()"),
                "a model must not choose which directory is searched for executable code");
        assertTrue(httpRoutes.contains("provider-plugin probe is remote-only"),
                "the probe must stay refused on the local facade");
    }

    /**
     * The controls above must never be sold as a sandbox on a surface an operator reads before deciding to run a
     * plugin. A stale claim here is not a documentation defect: it is the input to a wrong decision.
     *
     * <p>The denial is asserted as the exact sentence each surface owes its reader, and the affirmative phrasings
     * that would contradict it are refused outright -- rather than by guessing whether some negation elsewhere on
     * the page happens to qualify them.</p>
     */
    @Test
    void noSurfaceClaimsAnOperatingSystemSandbox() throws IOException {
        String security = read("SECURITY.md");
        assertTrue(security.contains("These controls are **not an operating-system sandbox**"));
        assertTrue(security.contains("must therefore be treated as trusted code"));
        assertTrue(security.contains("Neither guarantee is a sandbox."));
        assertTrue(security.contains("MCP peer descendants are best-effort"),
                "a best-effort cleanup must stay documented as best-effort, never as a guarantee");

        String sdkGuide = read("docs/developer/PROVIDER_SDK.md");
        assertTrue(sdkGuide.contains("classloader isolation != security sandbox"));
        assertTrue(sdkGuide.contains("SEC-0129-plugins-provider-code-de-confiance-hors-processus.md"),
                "the SDK guide must point at the trust model rather than restate it");
        assertFalse(sdkGuide.contains("différée au-delà de M22"),
                "the process boundary exists since M22; only the sandbox does not");

        String adr = read("docs/architecture/adr/SEC-0129-plugins-provider-code-de-confiance-hors-processus.md");
        assertTrue(adr.contains("PAS une sandbox du système d'exploitation"));
        assertTrue(adr.contains("= code de confiance"));

        String probe = read(SDK + "ProviderPluginProbeProcess.java");
        assertTrue(probe.contains("deliberately not described as an OS security sandbox"));

        for (String page : List.of(
                "SECURITY.md",
                "docs/developer/PROVIDER_SDK.md",
                "docs/architecture/adr/SEC-0129-plugins-provider-code-de-confiance-hors-processus.md",
                "docs/developer/MCP.md",
                SDK + "ProviderPluginProbeProcess.java",
                SDK + "ProviderPluginActivator.java")) {
            String content = read(page).toLowerCase(Locale.ROOT);
            for (String claim : AFFIRMED_SANDBOX_CLAIMS) {
                assertFalse(content.contains(claim),
                        () -> page + " claims a sandbox MORPHEUS does not provide: \"" + claim + "\"");
            }
        }
    }

    private static String read(String relative) throws IOException {
        return Files.readString(RepositoryRoot.locate().resolve(relative));
    }
}
