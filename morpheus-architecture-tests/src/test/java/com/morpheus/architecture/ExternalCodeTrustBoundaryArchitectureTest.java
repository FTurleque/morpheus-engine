package com.morpheus.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MORPHEUS runs external code in two places, and they must not drift apart.
 *
 * <p>A provider plugin probe and an MCP peer are the same kind of thing seen from two adapters: a child process
 * carrying code that MORPHEUS did not write, started under the MORPHEUS operating-system account. They are
 * siblings, so neither may depend on the other and the invariants cannot be shared through a common class. What
 * can be shared is a contract that says the invariants are the same on both sides, which is what this test is.</p>
 *
 * <p>The second half of it is about vocabulary. These boundaries confine lifecycle and environment; they are not
 * an operating-system sandbox, and the day a document or a class comment starts implying otherwise, an operator
 * will deploy untrusted code behind them. The statement is asserted in the code and in SECURITY.md so that
 * removing it takes a deliberate act rather than an edit nobody notices.</p>
 */
class ExternalCodeTrustBoundaryArchitectureTest {
    private static final Path PROBE_PROCESS = Path.of(
            "morpheus-provider-sdk/src/main/java/com/morpheus/sdk/provider/ProviderPluginProbeProcess.java");
    private static final Path MCP_TRANSPORT = Path.of(
            "morpheus-mcp-transport/src/main/java/com/morpheus/integration/mcp/BoundedStdioClientTransport.java");

    /**
     * Both child-process boundaries inherit exactly the same environment.
     *
     * <p>They were written independently and happen to agree today. Nothing enforced that, so a variable added
     * to one allowlist for a plausible reason would silently widen only one of the two boundaries.</p>
     */
    @Test
    void bothExternalProcessBoundariesInheritTheSameEnvironmentAllowlist() throws IOException {
        Set<String> probe = safeEnvironmentKeys(PROBE_PROCESS);
        Set<String> mcp = safeEnvironmentKeys(MCP_TRANSPORT);

        assertFalse(probe.isEmpty(), "the provider probe must declare an explicit inherited-environment allowlist");
        assertEquals(probe, mcp,
                "the provider probe and the MCP peer transport must inherit the same environment; "
                        + "widening one boundary alone is how they drift");
    }

    /**
     * Neither boundary may inherit a MORPHEUS secret or a JVM injection variable.
     *
     * <p>{@code MORPHEUS_SERVER_TLS_PASSWORD} is the concrete case: an allowlist entry matching it would hand
     * the remote server's keystore password to every plugin probe and every configured peer. The JVM injection
     * variables are the other half -- they change what the child JVM executes before its first instruction.</p>
     */
    @Test
    void neitherBoundaryInheritsMorpheusSecretsOrJvmInjectionVariables() throws IOException {
        for (Path source : List.of(PROBE_PROCESS, MCP_TRANSPORT)) {
            Set<String> allowed = safeEnvironmentKeys(source);
            for (String key : allowed) {
                assertFalse(key.startsWith("MORPHEUS"),
                        () -> source + " must never inherit a MORPHEUS variable: " + key);
            }
            for (String forbidden : List.of(
                    "JAVA_TOOL_OPTIONS", "_JAVA_OPTIONS", "JDK_JAVA_OPTIONS", "CLASSPATH", "JAVA_OPTS",
                    "LD_PRELOAD", "LD_LIBRARY_PATH", "DYLD_INSERT_LIBRARIES")) {
                assertFalse(allowed.contains(forbidden),
                        () -> source + " must never inherit the JVM/loader injection variable " + forbidden);
            }
        }
    }

    /**
     * Both boundaries clear the inherited environment before rebuilding it.
     *
     * <p>Removing known-bad keys instead would fail open on every variable nobody thought of, which is every
     * variable a future JDK, a future MORPHEUS release or the host adds.</p>
     */
    @Test
    void bothBoundariesRebuildTheChildEnvironmentFromEmptyRatherThanRemovingKnownBadKeys() throws IOException {
        for (Path source : List.of(PROBE_PROCESS, MCP_TRANSPORT)) {
            String text = Files.readString(repositoryRoot().resolve(source));
            assertTrue(text.contains("environment.clear()"),
                    () -> source + " must clear the inherited environment before applying its allowlist");
            assertTrue(text.contains("SAFE_ENVIRONMENT_KEYS.contains("),
                    () -> source + " must rebuild the child environment from its allowlist");
            assertFalse(text.contains("environment.remove("),
                    () -> source + " must not fail open by removing known-bad keys from an inherited environment");
        }
    }

    /**
     * Executable plugin activation stays pinned and staged.
     *
     * <p>The pin is what makes the JAR identifiable and the verified staging copy is what makes the identity
     * survive until the classloader has it: verifying the original path and then loading that same path leaves
     * a window in which the file can be replaced between the two.</p>
     */
    @Test
    void executablePluginActivationStaysPinnedStagedAndFailClosed() throws IOException {
        Path root = repositoryRoot();
        String activator = Files.readString(root.resolve(
                "morpheus-provider-sdk/src/main/java/com/morpheus/sdk/provider/ProviderPluginActivator.java"));
        String discovery = Files.readString(root.resolve(
                "morpheus-provider-sdk/src/main/java/com/morpheus/sdk/provider/ProviderPluginDiscovery.java"));

        assertTrue(activator.contains("provider plugin activation requires a trusted SHA-256 pin"),
                "unpinned executable activation must keep failing closed");
        assertTrue(activator.contains("ExternalJarIntegrity.stageVerifiedCopy("),
                "the classloader must only ever see the verified staging copy");
        assertTrue(activator.contains("releaseLoaderAndStaging("),
                "a failed activation must close the classloader and delete its staging copy");
        assertTrue(discovery.contains("LinkOption.NOFOLLOW_LINKS") && discovery.contains("isSymbolicLink"),
                "discovery must stay metadata-only and refuse symbolic entries");
        assertFalse(discovery.contains("URLClassLoader"),
                "discovery must never load classes; only explicit activation may");
    }

    /**
     * Every source that runs external code says, in its own file, that it is not a sandbox.
     *
     * <p>Someone reading only one of these classes must not be able to conclude that MORPHEUS confines what the
     * external code can reach.</p>
     */
    @Test
    void everyExternalExecutionBoundaryStatesItIsNotAnOperatingSystemSandbox() throws IOException {
        Path root = repositoryRoot();
        for (Path source : List.of(PROBE_PROCESS, MCP_TRANSPORT)) {
            String text = Files.readString(root.resolve(source)).toLowerCase(Locale.ROOT);
            assertTrue(text.contains("not") && text.contains("sandbox"),
                    () -> source + " must state that its child-process boundary is not an OS sandbox");
            assertTrue(text.contains("trusted"),
                    () -> source + " must state that the code it starts is trusted code");
        }

        String security = Files.readString(root.resolve("SECURITY.md"));
        assertTrue(security.contains("not an operating-system sandbox"),
                "SECURITY.md must keep refusing the sandbox claim");
        assertTrue(security.contains("must therefore be treated as trusted code"),
                "SECURITY.md must keep naming approved plugins and peers as trusted code");
        assertTrue(security.contains("least-privilege account"),
                "SECURITY.md must keep telling operators what to do when the code is genuinely untrusted");
    }

    /**
     * No source claims MORPHEUS sandboxes anything.
     *
     * <p>Any occurrence of "sandbox" in production sources is scanned so that an affirmative claim cannot be
     * introduced next to the denials that exist today.</p>
     */
    @Test
    void noProductionSourceClaimsToSandboxExternalCode() throws IOException {
        Pattern claim = Pattern.compile("(?i)\\b(sandboxe[sd]?|sandboxing)\\b");
        List<Path> sources;
        try (var walk = Files.walk(repositoryRoot())) {
            sources = walk.filter(path -> path.toString().replace('\\', '/').contains("/src/main/java/"))
                    .filter(path -> path.toString().endsWith(".java"))
                    .toList();
        }
        assertFalse(sources.isEmpty(), "the production source scan must actually find sources");

        for (Path source : sources) {
            String text = Files.readString(source);
            Matcher matcher = claim.matcher(text);
            while (matcher.find()) {
                String context = text.substring(Math.max(0, matcher.start() - 80), matcher.start());
                assertTrue(context.toLowerCase(Locale.ROOT).contains("not"),
                        () -> source + " uses \"" + matcher.group()
                                + "\" without denying it; MORPHEUS must never claim to sandbox external code");
            }
        }
    }

    private Set<String> safeEnvironmentKeys(Path source) throws IOException {
        String text = Files.readString(repositoryRoot().resolve(source));
        Matcher declaration = Pattern
                .compile("SAFE_ENVIRONMENT_KEYS\\s*=\\s*Set\\.of\\((.*?)\\);", Pattern.DOTALL)
                .matcher(text);
        assertTrue(declaration.find(), () -> source + " must declare SAFE_ENVIRONMENT_KEYS as an explicit Set.of");
        Matcher keys = Pattern.compile("\"([^\"]+)\"").matcher(declaration.group(1));
        Set<String> allowed = new java.util.TreeSet<>();
        while (keys.find()) {
            allowed.add(keys.group(1).toUpperCase(Locale.ROOT));
        }
        return allowed;
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
