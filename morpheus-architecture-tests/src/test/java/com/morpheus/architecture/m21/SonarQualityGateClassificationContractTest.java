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

    /**
     * SonarQube Cloud does not serve the quality gate of a branch other than main to this organization. The nightly
     * analysis of develop is uploaded and processed, and its verdict cannot be read: an unknown verdict, which the
     * lane must say rather than turn into a failure or a pass. The exemption is bound to a branch the caller names,
     * so that the same refusal on main -- where it would mean the token lost its rights -- stays an error.
     */
    @Test
    void anUnreadableGateIsUnknownOnlyForABranchOtherThanMainThatTheCallerNames() throws IOException {
        Path root = repoRoot();
        String classifier = Files.readString(root.resolve("scripts/classify-sonar-quality-gate.sh"));
        String nightly = Files.readString(root.resolve(".github/workflows/nightly.yml"));
        String trustedMain = Files.readString(root.resolve(".github/workflows/ci.yml"));

        assertTrue(classifier.contains("\"$gate_http\" == \"403\" && -n \"${SONAR_BRANCH_NAME:-}\""
                        + " && \"${SONAR_BRANCH_NAME}\" != \"main\""),
                "the unreadable-gate exemption must require HTTP 403 and a named branch other than main");
        assertTrue(classifier.contains("SONAR_QUALITY_GATE_UNREADABLE_OFF_MAIN"),
                "an unreadable gate must be announced under its own name");
        assertTrue(classifier.contains("The verdict is unknown, not failed"),
                "the lane must say that no verdict was read, not that the gate passed");
        assertTrue(classifier.contains("(HTTP ${gate_http})"),
                "every other refusal to read the gate must stay an error that names its status");

        assertTrue(nightly.contains("SONAR_BRANCH_NAME: develop"),
                "the nightly lane analyses develop and must name it to the classifier");
        assertTrue(nightly.contains("-Dsonar.qualitygate.wait=true"),
                "the nightly lane must still ask for a verdict: the exemption is not an upload-only mode");
        assertTrue(!trustedMain.contains("SONAR_BRANCH_NAME"),
                "the analysis of main names no branch, so a refusal to read its gate stays an error");
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
