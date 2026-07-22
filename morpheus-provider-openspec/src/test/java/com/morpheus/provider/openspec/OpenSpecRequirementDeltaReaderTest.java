package com.morpheus.provider.openspec;

import com.morpheus.application.identity.EntityIdentityResolver;
import com.morpheus.domain.identity.DomainIdentity;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.provider.ProviderId;
import com.morpheus.domain.requirement.RequirementDeltaKind;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenSpecRequirementDeltaReaderTest {

    @Test
    void normalizesModifiedAndAddedRequirementDeltasFromM0Fixture() {
        var result = new OpenSpecRequirementDeltaReader().read(
                fixture("openspec-basic"),
                new StableTestIdentityResolver());

        assertEquals(3, result.requirementDeltas().size());
        assertEquals(1, result.requirementDeltas().stream()
                .filter(delta -> delta.kind() == RequirementDeltaKind.MODIFIED)
                .count());
        assertEquals(2, result.requirementDeltas().stream()
                .filter(delta -> delta.kind() == RequirementDeltaKind.ADDED)
                .count());
        assertEquals(0, result.requirementDeltas().stream()
                .filter(delta -> delta.kind() == RequirementDeltaKind.REMOVED)
                .count());
        assertEquals(5, result.requirementDeltas().stream().mapToInt(delta -> delta.scenarios().size()).sum());
        assertEquals(8, result.evidence().size());
        assertTrue(result.diagnostics().isEmpty());

        var modified = result.requirementDeltas().stream()
                .filter(delta -> delta.kind() == RequirementDeltaKind.MODIFIED)
                .findFirst()
                .orElseThrow();
        assertEquals("auth-session/session-expiration", modified.key().orElseThrow());
        assertEquals("Session expiration", modified.title());
        assertEquals(2, modified.scenarios().size());

        var addedKeys = result.requirementDeltas().stream()
                .filter(delta -> delta.kind() == RequirementDeltaKind.ADDED)
                .map(delta -> delta.key().orElseThrow())
                .sorted()
                .toList();
        assertEquals(
                java.util.List.of(
                        "auth-session/explicit-remember-me-opt-in",
                        "auth-session/persistent-credential-revocation"),
                addedKeys);
    }

    @Test
    void modifiedDeltaReusesCurrentLogicalRequirementIdentityWithoutReplacingBaselineContent() {
        ProjectSpecificationId projectId = ProjectSpecificationId.generate();
        StableTestIdentityResolver identities = new StableTestIdentityResolver();
        Path workspace = fixture("openspec-basic");

        var current = new OpenSpecCurrentSpecificationReader().read(workspace, projectId, identities);
        var deltas = new OpenSpecRequirementDeltaReader().read(workspace, identities);

        var currentExpiration = current.requirements().stream()
                .filter(requirement -> requirement.key().orElseThrow().equals("auth-session/session-expiration"))
                .findFirst()
                .orElseThrow();
        var modifiedExpiration = deltas.requirementDeltas().stream()
                .filter(delta -> delta.key().orElseThrow().equals("auth-session/session-expiration"))
                .findFirst()
                .orElseThrow();

        assertEquals(currentExpiration.id(), modifiedExpiration.requirementId());
        assertNotEquals(currentExpiration.statement(), modifiedExpiration.statement().orElseThrow());
        assertTrue(currentExpiration.statement().contains("30 minutes of inactivity"));
        assertTrue(modifiedExpiration.statement().orElseThrow().contains("remember-me session"));
    }

    @Test
    void deltaOccurrenceIdentityIsDistinctFromLogicalRequirementIdentityAndHasSourceEvidence() {
        var result = new OpenSpecRequirementDeltaReader().read(
                fixture("openspec-basic"),
                new StableTestIdentityResolver());

        var modified = result.requirementDeltas().stream()
                .filter(delta -> delta.kind() == RequirementDeltaKind.MODIFIED)
                .findFirst()
                .orElseThrow();

        assertNotEquals(modified.id().value(), modified.requirementId().value());
        assertEquals(
                "requirement-delta:add-remember-me:modified:auth-session/session-expiration",
                modified.provenance().externalId().orElseThrow());
        assertEquals(
                "file:openspec/changes/add-remember-me/specs/auth-session/spec.md",
                modified.provenance().source().toString());
        assertTrue(result.evidence().stream()
                .anyMatch(item -> item.id().equals(modified.provenance().evidenceId())));
    }

    @Test
    void supportsRemovedRequirementWithoutInventingStatement(@TempDir Path workspace) throws Exception {
        Path openspec = workspace.resolve("openspec");
        Path deltaFile = openspec.resolve("changes/remove-legacy/specs/auth-session/spec.md");
        Files.createDirectories(deltaFile.getParent());
        Files.writeString(openspec.resolve("config.yaml"), "schema: spec-driven\n");
        Files.writeString(deltaFile, """
                # Authentication Session Delta

                ## REMOVED Requirements

                ### Requirement: Legacy session warning
                """);

        var result = new OpenSpecRequirementDeltaReader().read(workspace, new StableTestIdentityResolver());

        assertEquals(1, result.requirementDeltas().size());
        var removed = result.requirementDeltas().getFirst();
        assertEquals(RequirementDeltaKind.REMOVED, removed.kind());
        assertEquals("auth-session/legacy-session-warning", removed.key().orElseThrow());
        assertTrue(removed.statement().isEmpty());
        assertTrue(removed.scenarios().isEmpty());
        assertEquals(1, result.evidence().size());
    }

    private Path fixture(String name) {
        Path current = Path.of("").toAbsolutePath().normalize();
        Path fromRoot = current.resolve("experiments/m0/fixtures").resolve(name);
        if (Files.isDirectory(fromRoot)) {
            return fromRoot;
        }

        Path fromModule = current.resolve("../experiments/m0/fixtures").normalize().resolve(name);
        if (Files.isDirectory(fromModule)) {
            return fromModule;
        }

        throw new IllegalStateException("M0 fixture not found: " + name + " from " + current);
    }

    private static final class StableTestIdentityResolver implements EntityIdentityResolver {
        private final Map<String, DomainIdentity> identities = new HashMap<>();

        @Override
        public DomainIdentity resolve(ProviderId providerId, String entityType, String externalId) {
            String key = providerId.value() + "|" + entityType + "|" + externalId;
            return identities.computeIfAbsent(key, ignored -> DomainIdentity.generate());
        }
    }
}
