package com.morpheus.provider.openspec;

import com.morpheus.application.identity.EntityIdentityResolver;
import com.morpheus.domain.identity.DomainIdentity;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.provider.ProviderId;
import com.morpheus.domain.requirement.RequirementDeltaKind;
import org.junit.jupiter.api.Test;

import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenSpecProjectContentReaderTest {

    @Test
    void aggregatesCurrentSpecificationsChangeMetadataAndRequirementDeltasIntoOneNormalizedGraph() {
        var content = new OpenSpecProjectContentReader().read(
                fixture("openspec-basic"),
                ProjectSpecificationId.generate(),
                new StableTestIdentityResolver());

        assertEquals(1, content.specifications().size());
        assertEquals(2, content.requirements().size());
        assertEquals(2, content.scenarios().size());
        assertEquals(1, content.changes().size());
        assertEquals(3, content.requirementDeltas().size());
        assertEquals(2, content.constraints().size());
        assertEquals(2, content.designDecisions().size());
        assertEquals(8, content.tasks().size());
        assertEquals(26, content.evidence().size());
        assertTrue(content.diagnostics().isEmpty());

        var evidenceIds = new HashSet<>();
        content.evidence().forEach(item -> evidenceIds.add(item.id()));
        assertEquals(26, evidenceIds.size());

        var changeId = content.changes().getFirst().id();
        assertTrue(content.requirementDeltas().stream().allMatch(item -> item.changeId().equals(changeId)));
        assertTrue(content.constraints().stream().allMatch(item -> item.changeId().equals(changeId)));
        assertTrue(content.designDecisions().stream().allMatch(item -> item.changeId().equals(changeId)));
        assertTrue(content.tasks().stream().allMatch(item -> item.changeId().equals(changeId)));

        var currentExpiration = content.requirements().stream()
                .filter(item -> item.key().orElseThrow().equals("auth-session/session-expiration"))
                .findFirst()
                .orElseThrow();
        var modifiedExpiration = content.requirementDeltas().stream()
                .filter(item -> item.kind() == RequirementDeltaKind.MODIFIED)
                .findFirst()
                .orElseThrow();
        assertEquals(currentExpiration.id(), modifiedExpiration.requirementId());
        assertTrue(currentExpiration.statement().contains("30 minutes of inactivity"));
        assertTrue(modifiedExpiration.statement().orElseThrow().contains("remember-me session"));
    }

    @Test
    void anAttributedFailureKeepsTheCategoryEverySurfaceMapsToAStatus() {
        Path workspace = fixture("openspec-basic");
        String attributed = "openspec/specs/auth-session/spec.md";

        IllegalArgumentException invalid = assertThrows(IllegalArgumentException.class, () -> read(
                workspace, failingWith(new IllegalArgumentException("rejected content"))));
        assertEquals(attributed + ": rejected content", invalid.getMessage());

        IllegalStateException unreadable = assertThrows(IllegalStateException.class, () -> read(
                workspace, failingWith(new IllegalStateException("unreadable content"))));
        assertEquals(attributed + ": unreadable content", unreadable.getMessage());

        NoSuchFileException platform = new NoSuchFileException(
                workspace.resolve("openspec/specs/auth-session/spec.md").toAbsolutePath().toString());
        UncheckedIOException unchecked = assertThrows(UncheckedIOException.class, () -> read(
                workspace, failingWith(new UncheckedIOException(platform))));
        assertSame(platform, unchecked.getCause());
        assertEquals(attributed + ": UncheckedIOException", unchecked.getMessage());
    }

    @Test
    void aFailureThatIsNotTheFilesPassesThroughUnchangedAndUnattributed() {
        RuntimeException defect = new ArithmeticException("integer overflow");
        RuntimeException collaborator = new RuntimeException("identity store unavailable");

        assertSame(defect, assertThrows(ArithmeticException.class, () -> read(
                fixture("openspec-basic"), failingWith(defect))));
        assertSame(collaborator, assertThrows(RuntimeException.class, () -> read(
                fixture("openspec-basic"), failingWith(collaborator))));
    }

    private void read(Path workspace, EntityIdentityResolver resolver) {
        new OpenSpecProjectContentReader().read(workspace, ProjectSpecificationId.generate(), resolver);
    }

    private static EntityIdentityResolver failingWith(RuntimeException failure) {
        return (providerId, entityType, externalId) -> {
            throw failure;
        };
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
