package com.morpheus.architecture.m21;

import com.morpheus.architecture.CoherenceCheck;
import com.morpheus.architecture.RepositoryRoot;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@CoherenceCheck
class SonarQualityGateClassificationContractTest {

    @Test
    void trustedMainSonarJobDistinguishesARealGateFailureFromNotComputed() throws IOException {
        Path root = RepositoryRoot.locate();
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

    /**
     * Only {@code main} is analysed, and it names no branch, so a refusal to read its quality gate -- which would
     * mean the token lost its rights -- is an error that names its status. No verdict is ever reported as unknown
     * because of the branch it was read for.
     */
    @Test
    void anUnreadableGateIsAnErrorBecauseOnlyMainIsAnalysed() throws IOException {
        Path root = RepositoryRoot.locate();
        String classifier = Files.readString(root.resolve("scripts/classify-sonar-quality-gate.sh"));

        assertFalse(classifier.contains("UNREADABLE_OFF_MAIN"),
                "no branch other than main is analysed, so no refusal to read a gate may become an unknown verdict");
        assertFalse(classifier.contains("SONAR_BRANCH_NAME"),
                "the classifier must not depend on a branch name that no workflow passes");
        assertTrue(classifier.contains("(HTTP ${gate_http})"),
                "every refusal to read the gate must stay an error that names its status");

        try (var workflows = Files.list(root.resolve(".github/workflows"))) {
            for (Path workflow : workflows.filter(path -> path.toString().endsWith(".yml")).sorted().toList()) {
                assertFalse(Files.readString(workflow).contains("SONAR_BRANCH_NAME"),
                        workflow.getFileName() + " names a SonarQube Cloud branch, but only main is analysed");
            }
        }
    }
}
