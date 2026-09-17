package com.morpheus.architecture.m22;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

/**
 * The external-code trust boundary, stated as what it guarantees and as what it deliberately does not (ADR-0101).
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

        assertTrue(activator.contains("provider plugin activation requires a trusted SHA-256 pin"),
                "unpinned executable activation must stay fail-closed");
        assertTrue(activator.contains("ExternalJarIntegrity.normalizeSha256(expectedSha256)"));
        assertTrue(activator.contains("ExternalJarIntegrity.stageVerifiedCopy(candidate.jarPath(), expected)"),
                "the verified copy must be what is loaded, closing the verify-to-load window on the original path");
        assertTrue(activator.contains("new java.net.URL[] {loadJar.toUri().toURL()}"),
                "the classloader must only ever see the staged copy");

        assertTrue(probe.contains("command(candidate, workspaceRoot, trustedSha256, resultFile)"),
                "the parent must hand the trusted pin to the child rather than a trust decision");
        assertTrue(worker.contains("new ProviderPluginActivator().activate(candidate, sha256)"),
                "the child must verify the pin itself instead of trusting the parent's word");
        assertTrue(worker.contains("expected: JAR WORKSPACE SHA256 RESULT_FILE"),
                "the pin must stay a required argument of the worker");
    }

    @Test
    void discoveryStaysMetadataOnly() throws IOException {
        String discovery = read(SDK + "ProviderPluginDiscovery.java");

        assertTrue(discovery.contains("LinkOption.NOFOLLOW_LINKS"));
        assertTrue(discovery.contains("Files.isSymbolicLink"));
        assertTrue(discovery.contains("This class never creates a ClassLoader or ServiceLoader"),
                "the metadata-only contract must stay stated where it is implemented");
        assertFalse(discovery.contains("new URLClassLoader"), "discovery must never load a class");
        assertFalse(discovery.contains("ServiceLoader.load("), "discovery must never resolve a service");
        assertFalse(discovery.contains("new ProcessBuilder"), "discovery must never start a process");
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
        String mcpTools = read("morpheus-mcp/src/main/java/com/morpheus/mcp/MorpheusProviderPluginMcpTools.java");
        String httpRoutes = read(
                "morpheus-api/src/main/java/com/morpheus/api/MorpheusProviderPluginHttpRoutes.java");

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
        assertTrue(sdkGuide.contains("0101-external-code-is-trusted-code-not-sandboxed-code.md"),
                "the SDK guide must point at the trust model rather than restate it");
        assertFalse(sdkGuide.contains("différée au-delà de M22"),
                "the process boundary exists since M22; only the sandbox does not");

        String adr = read("docs/adr/0101-external-code-is-trusted-code-not-sandboxed-code.md");
        assertTrue(adr.contains("PAS une sandbox du système d'exploitation"));
        assertTrue(adr.contains("= code de confiance"));

        String probe = read(SDK + "ProviderPluginProbeProcess.java");
        assertTrue(probe.contains("deliberately not described as an OS security sandbox"));

        for (String page : List.of(
                "SECURITY.md",
                "docs/developer/PROVIDER_SDK.md",
                "docs/adr/0101-external-code-is-trusted-code-not-sandboxed-code.md",
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
        return Files.readString(repositoryRoot().resolve(relative));
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
        throw new IllegalStateException("MORPHEUS repository root not found");
    }
}
