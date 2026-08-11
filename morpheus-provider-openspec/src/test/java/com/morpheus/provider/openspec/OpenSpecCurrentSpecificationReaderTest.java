package com.morpheus.provider.openspec;

import com.morpheus.application.identity.EntityIdentityResolver;
import com.morpheus.domain.identity.DomainIdentity;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.provider.ProviderId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenSpecCurrentSpecificationReaderTest {

    @Test
    void normalizesCurrentSpecificationRequirementsScenariosAndEvidenceFromM0Fixture() {
        ProjectSpecificationId projectId = ProjectSpecificationId.generate();
        StableTestIdentityResolver identities = new StableTestIdentityResolver();

        var content = new OpenSpecCurrentSpecificationReader().read(
                fixture("openspec-basic"),
                projectId,
                identities);

        assertEquals(projectId, content.project().id());
        assertEquals(1, content.specifications().size());
        assertEquals(2, content.requirements().size());
        assertEquals(2, content.scenarios().size());
        assertEquals(5, content.evidence().size());
        assertTrue(content.diagnostics().isEmpty());

        var specification = content.specifications().getFirst();
        assertEquals("auth-session", specification.key());
        assertEquals("Authentication Session Specification", specification.title());
        assertEquals("Décrire le comportement courant de la session authentifiée.", specification.description().orElseThrow());
        assertEquals("openspec", specification.provenance().providerId().value());
        assertEquals("file:openspec/specs/auth-session/spec.md", specification.provenance().source().toString());

        var expiration = content.requirements().stream()
                .filter(requirement -> requirement.key().orElse("").equals("auth-session/session-expiration"))
                .findFirst()
                .orElseThrow();
        assertEquals("Session expiration", expiration.title());
        assertEquals(
                "The system SHALL expire an authenticated session after 30 minutes of inactivity.",
                expiration.statement());

        var expirationScenario = content.scenarios().stream()
                .filter(scenario -> scenario.title().equals("Expire an inactive session"))
                .findFirst()
                .orElseThrow();
        assertEquals(expiration.id(), expirationScenario.requirementId().orElseThrow());
        assertEquals(2, expirationScenario.preconditions().size());
        assertEquals("an authenticated user session", expirationScenario.preconditions().get(0));
        assertEquals("no activity has occurred for 30 minutes", expirationScenario.preconditions().get(1));
        assertEquals("the user attempts a protected action", expirationScenario.action());
        assertEquals("the system SHALL require authentication again", expirationScenario.expectedOutcome());

        content.evidence().forEach(item -> {
            assertTrue(item.range().isPresent());
            assertEquals(64, item.excerptHash().orElseThrow().length());
        });

        assertTrue(content.specifications().stream()
                .allMatch(item -> content.evidence().stream()
                        .anyMatch(evidence -> evidence.id().equals(item.provenance().evidenceId()))));
        assertTrue(content.requirements().stream()
                .allMatch(item -> content.evidence().stream()
                        .anyMatch(evidence -> evidence.id().equals(item.provenance().evidenceId()))));
        assertTrue(content.scenarios().stream()
                .allMatch(item -> content.evidence().stream()
                        .anyMatch(evidence -> evidence.id().equals(item.provenance().evidenceId()))));
    }

    @Test
    void repeatedReadWithSameIdentityResolverPreservesMorpheusIdentities() {
        ProjectSpecificationId projectId = ProjectSpecificationId.generate();
        StableTestIdentityResolver identities = new StableTestIdentityResolver();
        OpenSpecCurrentSpecificationReader reader = new OpenSpecCurrentSpecificationReader();

        var first = reader.read(fixture("openspec-basic"), projectId, identities);
        var second = reader.read(fixture("openspec-basic"), projectId, identities);

        assertEquals(first.specifications().getFirst().id(), second.specifications().getFirst().id());
        assertEquals(first.requirements().getFirst().id(), second.requirements().getFirst().id());
        assertEquals(first.scenarios().getFirst().id(), second.scenarios().getFirst().id());
        assertEquals(first.evidence().getFirst().id(), second.evidence().getFirst().id());
    }

    @Test
    void differentSemanticExternalKeysProduceDifferentDomainIdentities() {
        StableTestIdentityResolver identities = new StableTestIdentityResolver();
        DomainIdentity specification = identities.resolve(
                OpenSpecSpecificationProvider.ID,
                "specification",
                "specification:auth-session");
        DomainIdentity requirement = identities.resolve(
                OpenSpecSpecificationProvider.ID,
                "requirement",
                "requirement:auth-session/session-expiration");

        assertNotEquals(specification, requirement);
        assertFalse(specification.toString().contains("auth-session"));
        assertFalse(requirement.toString().contains("session-expiration"));
    }

    @Test
    void rejectsSpecificationSymlinkThatEscapesWorkspace(@TempDir Path temp) throws Exception {
        Path workspace = Files.createDirectory(temp.resolve("workspace"));
        Files.createDirectories(workspace.resolve("openspec/specs/example"));
        Files.writeString(workspace.resolve("openspec/config.yaml"), "schema: spec-driven\n");
        Path outside = Files.writeString(temp.resolve("outside-spec.md"), "# Outside specification\n");
        Path source = workspace.resolve("openspec/specs/example/spec.md");
        try {
            Files.createSymbolicLink(source, outside);
        } catch (UnsupportedOperationException | java.io.IOException | SecurityException unsupported) {
            return;
        }

        assertThrows(IllegalArgumentException.class, () -> new OpenSpecCurrentSpecificationReader().read(
                workspace,
                ProjectSpecificationId.generate(),
                new StableTestIdentityResolver()));
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
