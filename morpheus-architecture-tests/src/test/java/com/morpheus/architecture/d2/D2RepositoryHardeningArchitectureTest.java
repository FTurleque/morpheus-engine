package com.morpheus.architecture.d2;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class D2RepositoryHardeningArchitectureTest {

    private static final Pattern CHECKOUT_NODE24 = Pattern.compile(
            "(?m)^\\s*uses: actions/checkout@[0-9a-f]{40} # v(?:[6-9]|[1-9][0-9])(?:\\.[0-9]+){0,2}\\s*$");
    private static final Pattern SETUP_JAVA_NODE24 = Pattern.compile(
            "(?m)^\\s*uses: actions/setup-java@[0-9a-f]{40} # v(?:[5-9]|[1-9][0-9])(?:\\.[0-9]+){0,2}\\s*$");
    private static final Pattern UPLOAD_ARTIFACT_NODE24 = Pattern.compile(
            "(?m)^\\s*uses: actions/upload-artifact@[0-9a-f]{40} # v(?:[6-9]|[1-9][0-9])(?:\\.[0-9]+){0,2}\\s*$");

    @Test
    void dependencyAndQualityBaselineIsPinned() throws IOException {
        String pom = Files.readString(repoRoot().resolve("pom.xml"));
        assertTrue(pom.contains("<jackson.version>3.1.5</jackson.version>"));
        assertTrue(pom.contains("<sqlite-jdbc.version>3.53.2.0</sqlite-jdbc.version>"));
        assertTrue(pom.contains("<dependency-check.maven.plugin.version>12.2.2</dependency-check.maven.plugin.version>"));
        assertTrue(pom.contains("<failOnWarning>true</failOnWarning>"));
        assertTrue(pom.contains("<id>d2-security</id>"));
        assertTrue(pom.contains("<failBuildOnCVSS>7.0</failBuildOnCVSS>"));
    }

    @Test
    void coverageRatchetCannotSilentlyReturnToTheM21Floor() throws IOException {
        String coverage = Files.readString(repoRoot().resolve(
                "morpheus-architecture-tests/src/test/java/com/morpheus/architecture/m21/CoverageQualityGateTest.java"));
        assertTrue(coverage.contains("MIN_LINE_RATIO = 0.40d"));
        assertTrue(coverage.contains("MIN_BRANCH_RATIO = 0.35d"));
        assertFalse(coverage.contains("MIN_LINE_RATIO = 0.25d"));
        assertFalse(coverage.contains("MIN_BRANCH_RATIO = 0.20d"));
    }

    @Test
    void durableM21ScriptsKeepQualifiedPresenceAndCoverageRatchets() throws IOException {
        Path root = repoRoot();
        String linux = Files.readString(root.resolve("scripts/validate-m21.sh"));
        String windows = Files.readString(root.resolve("scripts/validate-m21.ps1"));
        for (String script : java.util.List.of(linux, windows)) {
            assertTrue(script.contains("711"));
            assertTrue(script.contains("253"));
            assertTrue(script.contains("0.47"));
            assertTrue(script.contains("0.40"));
            assertTrue(script.contains("1.2.1"));
        }
    }

    @Test
    void d2ScriptsKeepQualifiedPresenceRatchets() throws IOException {
        Path root = repoRoot();
        String linux = Files.readString(root.resolve("scripts/validate-d2.sh"));
        String windows = Files.readString(root.resolve("scripts/validate-d2.ps1"));
        for (String script : java.util.List.of(linux, windows)) {
            assertTrue(script.contains("711"));
            assertTrue(script.contains("253"));
            assertTrue(script.contains("1.2.1"));
        }
    }

    @Test
    void activeWorkflowsUsePinnedNode24GenerationActions() throws IOException {
        Path root = repoRoot();
        for (String workflow : java.util.List.of("ci.yml", "security.yml")) {
            String text = Files.readString(root.resolve(".github/workflows").resolve(workflow));
            assertPinnedNode24(text, CHECKOUT_NODE24, "checkout", workflow);
            assertPinnedNode24(text, SETUP_JAVA_NODE24, "setup-java", workflow);
            assertPinnedNode24(text, UPLOAD_ARTIFACT_NODE24, "upload-artifact", workflow);
        }
    }

    @Test
    void historicalPreflightsAvoidDeprecatedSetupJavaV4() throws IOException {
        Path root = repoRoot().resolve(".github/workflows");
        for (String workflow : java.util.List.of("m10-preflight.yml", "m11-preflight.yml", "m12-preflight.yml")) {
            String text = Files.readString(root.resolve(workflow));
            assertPinnedNode24(text, CHECKOUT_NODE24, "checkout", workflow);
            assertPinnedNode24(text, SETUP_JAVA_NODE24, "setup-java", workflow);
            assertFalse(text.contains("cf277c60eb25467037889841efdb72551f06f6c3"));
        }
    }

    @Test
    void mainBoundaryHasDependencyUpdatePolicy() throws IOException {
        Path root = repoRoot();
        String security = Files.readString(root.resolve(".github/workflows/security.yml"));
        String dependabot = Files.readString(root.resolve(".github/dependabot.yml"));

        assertTrue(security.contains("branches: [main]"));
        assertTrue(security.contains("dependency-check-maven:12.2.2:aggregate"));
        assertTrue(dependabot.contains("package-ecosystem: maven"));
        assertTrue(dependabot.contains("package-ecosystem: github-actions"));
        assertTrue(dependabot.contains("target-branch: develop"));
    }

    @Test
    void untrustedJsonRemainsStrictAndDefaultTypingIsNotActivated() throws IOException {
        Path root = repoRoot();
        String server = Files.readString(root.resolve(
                "morpheus-api/src/main/java/com/morpheus/api/MorpheusHttpServer.java"));
        assertTrue(server.contains("MAX_REQUEST_BODY_BYTES = 65_536"));
        assertTrue(server.contains("FAIL_ON_UNKNOWN_PROPERTIES"));
        assertTrue(server.contains("FAIL_ON_TRAILING_TOKENS"));
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
    void d2IsLocalOnlyAndHasDualPlatformValidationArtifacts() {
        Path root = repoRoot();
        assertTrue(Files.isRegularFile(root.resolve("scripts/validate-d2.ps1")));
        assertTrue(Files.isRegularFile(root.resolve("scripts/validate-d2.sh")));
        assertTrue(Files.isRegularFile(root.resolve("docs/roadmap/D2_EXECUTION.md")));
        assertTrue(Files.isRegularFile(root.resolve("docs/validation/VALIDATION_D2.md")));
    }

    private void assertPinnedNode24(String workflow, Pattern pattern, String action, String file) {
        assertTrue(pattern.matcher(workflow).find(),
                () -> file + " must pin actions/" + action + " to a 40-char SHA from the Node 24 generation or newer");
        assertFalse(workflow.contains("uses: actions/" + action + "@v"),
                () -> file + " must not use a mutable tag for actions/" + action);
    }

    private Path repoRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        if (Files.isRegularFile(current.resolve("pom.xml")) && Files.isDirectory(current.resolve("distribution"))) {
            return current;
        }
        Path parent = current.getParent();
        if (parent != null && Files.isRegularFile(parent.resolve("pom.xml")) && Files.isDirectory(parent.resolve("distribution"))) {
            return parent;
        }
        throw new IllegalStateException("MORPHEUS repository root not found from " + current);
    }
}
