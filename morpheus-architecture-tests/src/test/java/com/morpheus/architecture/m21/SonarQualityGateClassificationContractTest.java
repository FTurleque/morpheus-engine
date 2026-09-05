package com.morpheus.architecture.m21;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SonarQualityGateClassificationContractTest {

    @Test
    void trustedMainSonarJobDistinguishesARealGateFailureFromNotComputed() throws IOException {
        Path root = repoRoot();
        String workflow = Files.readString(root.resolve(".github/workflows/ci.yml"));
        String classifier = Files.readString(root.resolve("scripts/classify-sonar-quality-gate.sh"));

        assertTrue(workflow.contains("id: sonar_scan"),
                "The scanner outcome must be available to the explicit classifier step");
        assertTrue(workflow.contains("continue-on-error: true"),
                "The scanner's generic non-zero status must not pre-empt explicit gate classification");
        assertTrue(workflow.contains("if: steps.sonar_scan.outcome == 'failure'"));
        assertTrue(workflow.contains("bash ./scripts/classify-sonar-quality-gate.sh"));
        assertTrue(workflow.contains("-Dsonar.qualitygate.wait=true"),
                "The scanner must still request a quality-gate verdict rather than becoming upload-only");

        assertTrue(classifier.contains("report-task.txt"),
                "Classification must be tied to the analysis produced by this scanner invocation");
        assertTrue(classifier.contains("ceTaskUrl"));
        assertTrue(classifier.contains("analysisId"));
        assertTrue(classifier.contains("qualitygates/project_status?analysisId="));
        assertTrue(classifier.contains("ERROR)"),
                "A genuinely failed Sonar quality gate must still fail the trusted-main job");
        assertTrue(classifier.contains("NONE)"),
                "Sonar's indeterminate/not-computed state must be handled explicitly");
        assertTrue(classifier.contains("refusing to hide a technical scanner failure"),
                "Scanner failures before an analysis is created must remain fail-closed");
    }

    private Path repoRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        if (Files.isRegularFile(current.resolve("pom.xml")) && Files.isDirectory(current.resolve("distribution"))) {
            return current;
        }
        Path parent = current.getParent();
        if (parent != null && Files.isRegularFile(parent.resolve("pom.xml"))
                && Files.isDirectory(parent.resolve("distribution"))) {
            return parent;
        }
        throw new IllegalStateException("MORPHEUS repository root not found from " + current);
    }
}
