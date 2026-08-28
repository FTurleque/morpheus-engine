package com.morpheus.architecture.d2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** D2 production-hardening architecture contracts. */
class D2RepositoryHardeningArchitectureTest {
    private static final Set<String> REQUIRED_PINNED_ACTIONS = Set.of(
            "actions/checkout@3d3c42e5aac5ba805825da76410c181273ba90b1",
            "actions/setup-java@b6effb05e454b25005698d916606bdc6ffcbf961",
            "actions/upload-artifact@043fb46d1a93c77aae656e7c1c64a875d1fc6a0a",
            "github/codeql-action/init@b8c2a30d1ec7ba27e3e6a61bf314ee669d75db07",
            "github/codeql-action/analyze@b8c2a30d1ec7ba27e3e6a61bf314ee669d75db07",
            "dependency-check/Dependency-Check_Action@f9b6fbda9b96c07bd43262ef794667461cb83622");

    @Test
    void workflowsPinThirdPartyActionsToImmutableShas() throws IOException {
        Path workflows = repoRoot().resolve(".github/workflows");
        assertTrue(Files.isDirectory(workflows));
        try (Stream<Path> files = Files.list(workflows)) {
            List<Path> workflowFiles = files.filter(path -> path.toString().endsWith(".yml")).toList();
            assertFalse(workflowFiles.isEmpty());
            for (Path workflow : workflowFiles) {
                for (String line : Files.readAllLines(workflow, StandardCharsets.UTF_8)) {
                    String trimmed = line.trim();
                    if (!trimmed.startsWith("uses:")) {
                        continue;
                    }
                    String action = trimmed.substring("uses:".length()).trim();
                    if (action.startsWith("./")) {
                        continue;
                    }
                    assertTrue(action.matches("[^@]+@[0-9a-f]{40}(?:\\s+#.*)?"),
                            "workflow action must be pinned to a full commit SHA: " + action);
                }
            }
        }
    }

    @Test
    void requiredSecurityActionsRemainPinnedToAuditedCommits() throws IOException {
        String workflows = readTree(repoRoot().resolve(".github/workflows"), ".yml");
        for (String required : REQUIRED_PINNED_ACTIONS) {
            assertTrue(workflows.contains(required), "missing audited workflow pin: " + required);
        }
    }

    @Test
    void permissionsStayLeastPrivilegeAndSecurityUploadsAreExplicit() throws IOException {
        Path root = repoRoot();
        String ci = Files.readString(root.resolve(".github/workflows/ci.yml"));
        String security = Files.readString(root.resolve(".github/workflows/security.yml"));
        String codeql = Files.readString(root.resolve(".github/workflows/codeql.yml"));
        assertTrue(ci.contains("contents: read"));
        assertFalse(ci.contains("contents: write"));
        assertTrue(security.contains("contents: read"));
        assertFalse(security.contains("contents: write"));
        assertTrue(codeql.contains("contents: read"));
        assertTrue(codeql.contains("security-events: write"));
    }

    @Test
    void dependencyHygieneAndConvergenceAreEnforced() throws IOException {
        String pom = Files.readString(repoRoot().resolve("pom.xml"));
        assertTrue(pom.contains("maven-enforcer-plugin"));
        assertTrue(pom.contains("dependencyConvergence"));
        assertTrue(pom.contains("maven-dependency-plugin"));
        assertTrue(pom.contains("analyze-only"));
        assertTrue(pom.contains("failOnWarning"));
    }

    @Test
    void sbomAndVulnerabilityScanningAreVersioned() throws IOException {
        Path root = repoRoot();
        String pom = Files.readString(root.resolve("pom.xml"));
        String security = Files.readString(root.resolve(".github/workflows/security.yml"));
        assertTrue(pom.contains("cyclonedx-maven-plugin"));
        assertTrue(pom.contains("makeAggregateBom"));
        assertTrue(security.contains("Dependency-Check_Action"));
        assertTrue(security.contains("failOnCVSS: 7"));
        assertTrue(security.contains("format: ALL"));
    }

    @Test
    void noProductionSourceContainsKnownUnsafeDeserializationActivation() throws IOException {
        Path root = repoRoot();
        try (var files = Files.walk(root, 16)) {
            for (Path source : files.filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> path.toString().contains("src" + java.io.File.separator + "main" + java.io.File.separator + "java"))
                    .toList()) {
                String text = Files.readString(source);
                assertFalse(text.contains("activateDefaultTyping("), "default typing activation forbidden: " + source);
                assertFalse(text.contains("enableDefaultTyping("), "default typing activation forbidden: " + source);
            }
        }
    }

    @Test
    void providerPluginProcessIsolationRemainsBounded() throws IOException {
        Path root = repoRoot();
        String process = Files.readString(root.resolve(
                "morpheus-provider-sdk/src/main/java/com/morpheus/sdk/provider/ProviderPluginProcess.java"));
        assertTrue(process.contains("MAX_RESPONSE_BYTES"));
        assertTrue(process.contains("timeout"));
        assertTrue(process.contains("destroyForcibly"));
    }

    @Test
    void remoteApiSecurityDefaultsRemainFailClosed() throws IOException {
        Path root = repoRoot();
        String remote = Files.readString(root.resolve(
                "morpheus-api/src/main/java/com/morpheus/api/MorpheusRemoteHttpServer.java"));
        assertTrue(remote.contains("MorpheusRemoteRole"));
        assertTrue(remote.contains("HttpsServer"));
        assertFalse(remote.toLowerCase(Locale.ROOT).contains("trustall"));
    }

    @Test
    void codeQlSastIsVersionedAndUsesExtendedJavaSecurityQueries() throws IOException {
        String codeql = Files.readString(repoRoot().resolve(".github/workflows/codeql.yml"));
        assertTrue(codeql.contains("branches: [main, develop]"));
        assertTrue(codeql.contains("security-events: write"));
        assertTrue(codeql.contains("languages: java-kotlin"));
        assertTrue(codeql.contains("build-mode: manual"));
        assertTrue(codeql.contains("queries: security-extended"));
        assertTrue(codeql.contains("./mvnw -DskipTests -Djacoco.skip=true clean package"));
    }

    @Test
    void untrustedJsonRemainsStrictAndDefaultTypingIsNotActivated() throws IOException {
        Path root = repoRoot();
        String server = Files.readString(root.resolve(
                "morpheus-api/src/main/java/com/morpheus/api/MorpheusHttpServer.java"));
        String projects = Files.readString(root.resolve(
                "morpheus-api/src/main/java/com/morpheus/api/MorpheusProjectsHttpRoutes.java"));
        String requestDecoder = Files.readString(root.resolve(
                "morpheus-api/src/main/java/com/morpheus/api/MorpheusHttpRequestDecoder.java"));
        assertTrue(server.contains("MAX_REQUEST_BODY_BYTES = 65_536"));
        assertTrue(server.contains("MorpheusHttpRequestDecoder requestDecoder = new MorpheusHttpRequestDecoder("));
        assertTrue(projects.contains("MorpheusHttpRequestDecoder requestDecoder"));
        assertTrue(requestDecoder.contains("FAIL_ON_UNKNOWN_PROPERTIES"));
        assertTrue(requestDecoder.contains("FAIL_ON_TRAILING_TOKENS"));
        assertTrue(requestDecoder.contains("TimedBoundedInputReader.read("));
        assertTrue(Files.isRegularFile(root.resolve(
                "morpheus-api/src/test/java/com/morpheus/api/JacksonSecurityRegressionTest.java")));

        try (var files = Files.walk(root, 16)) {
            for (Path source : files.filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> path.toString().contains("src" + java.io.File.separator + "main" + java.io.File.separator + "java"))
                    .toList()) {
                String text = Files.readString(source);
                assertFalse(text.contains("activateDefaultTyping("), "default typing activation forbidden: " + source);
                assertFalse(text.contains("enableDefaultTyping("), "default typing activation forbidden: " + source);
            }
        }
    }

    @Test
    void repositoryRootAndCoreBuildFilesRemainDiscoverable() throws IOException {
        Path root = repoRoot();
        assertNotNull(root);
        assertTrue(Files.isRegularFile(root.resolve("pom.xml")));
        assertTrue(Files.isRegularFile(root.resolve("contracts/public-surfaces.tsv")));
        assertEquals("morpheus-engine", root.getFileName().toString());
    }

    private String readTree(Path root, String suffix) throws IOException {
        StringBuilder content = new StringBuilder();
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : files.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(suffix))
                    .toList()) {
                content.append(Files.readString(file)).append('\n');
            }
        }
        return content.toString();
    }

    private Path repoRoot() {
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
