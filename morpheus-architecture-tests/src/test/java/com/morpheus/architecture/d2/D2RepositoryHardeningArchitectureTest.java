package com.morpheus.architecture.d2;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class D2RepositoryHardeningArchitectureTest {

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
            assertTrue(script.contains("698"));
            assertTrue(script.contains("250"));
            assertTrue(script.contains("0.47"));
            assertTrue(script.contains("0.40"));
            assertTrue(script.contains("1.2.1"));
        }
    }

    @Test
    void mainBoundaryHasPinnedSecurityWorkflowAndDependencyUpdatePolicy() throws IOException {
        Path root = repoRoot();
        String security = Files.readString(root.resolve(".github/workflows/security.yml"));
        String dependabot = Files.readString(root.resolve(".github/dependabot.yml"));

        assertTrue(security.contains("branches: [main]"));
        assertTrue(security.contains("dependency-check-maven:12.2.2:aggregate"));
        assertTrue(security.contains("actions/checkout@11d5960a326750d5838078e36cf38b85af677262"));
        assertTrue(security.contains("actions/setup-java@cf277c60eb25467037889841efdb72551f06f6c3"));
        assertTrue(security.contains("actions/upload-artifact@ea165f8d65b6e75b540449e92b4886f43607fa02"));
        assertFalse(security.contains("uses: actions/checkout@v"));
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
