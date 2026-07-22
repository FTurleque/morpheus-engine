package com.morpheus.provider.openspec;

import com.morpheus.application.identity.EntityIdentityResolver;
import com.morpheus.domain.identity.DomainIdentity;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.provider.ProviderId;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenSpecChangeMetadataReaderTest {

    @Test
    void normalizesProposalConstraintsDecisionsTasksAndEvidenceFromM0Fixture() {
        var content = new OpenSpecChangeMetadataReader().read(
                fixture("openspec-basic"),
                ProjectSpecificationId.generate(),
                new StableTestIdentityResolver());

        assertEquals(1, content.changes().size());
        assertEquals(2, content.constraints().size());
        assertEquals(2, content.designDecisions().size());
        assertEquals(8, content.tasks().size());
        assertEquals(13, content.evidence().size());
        assertTrue(content.diagnostics().isEmpty());

        var change = content.changes().getFirst();
        assertEquals("add-remember-me", change.key().orElseThrow());
        assertEquals("Add remember-me sessions", change.title());
        assertTrue(change.intent().startsWith("Permettre à un utilisateur"));
        assertEquals(4, change.scope().size());
        assertEquals(4, change.outOfScope().size());
        assertEquals(3, change.risks().size());
        assertEquals("file:openspec/changes/add-remember-me/proposal.md", change.provenance().source().toString());

        assertEquals(
                "une session standard SHALL conserver le comportement courant par défaut ;",
                content.constraints().getFirst().statement());
        assertEquals("Separate persistent credential", content.designDecisions().getFirst().title());
        assertEquals("Explicit opt-in", content.designDecisions().get(1).title());
        assertTrue(content.tasks().stream().allMatch(task -> !task.completed()));
        assertEquals("Define the persistent-session data model", content.tasks().getFirst().title());

        content.evidence().forEach(item -> {
            assertTrue(item.range().isPresent());
            assertEquals(64, item.excerptHash().orElseThrow().length());
        });
    }

    @Test
    void anonymousConstraintAndTaskKeysAreStructuralNotTextDerived() {
        var content = new OpenSpecChangeMetadataReader().read(
                fixture("openspec-basic"),
                ProjectSpecificationId.generate(),
                new StableTestIdentityResolver());

        assertEquals("constraint:add-remember-me:1", content.constraints().getFirst().provenance().externalId().orElseThrow());
        assertEquals("constraint:add-remember-me:2", content.constraints().get(1).provenance().externalId().orElseThrow());
        assertEquals("task:add-remember-me:1", content.tasks().getFirst().provenance().externalId().orElseThrow());
        assertEquals("task:add-remember-me:8", content.tasks().get(7).provenance().externalId().orElseThrow());
        assertFalse(content.tasks().getFirst().provenance().externalId().orElseThrow().contains("persistent-session-data-model"));
    }

    @Test
    void repeatedReadWithSameIdentityResolverPreservesChangeFamilyIdentities() {
        ProjectSpecificationId projectId = ProjectSpecificationId.generate();
        StableTestIdentityResolver identities = new StableTestIdentityResolver();
        OpenSpecChangeMetadataReader reader = new OpenSpecChangeMetadataReader();

        var first = reader.read(fixture("openspec-basic"), projectId, identities);
        var second = reader.read(fixture("openspec-basic"), projectId, identities);

        assertEquals(first.changes().getFirst().id(), second.changes().getFirst().id());
        assertEquals(first.constraints().getFirst().id(), second.constraints().getFirst().id());
        assertEquals(first.designDecisions().getFirst().id(), second.designDecisions().getFirst().id());
        assertEquals(first.tasks().getFirst().id(), second.tasks().getFirst().id());
        assertEquals(first.evidence().getFirst().id(), second.evidence().getFirst().id());
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
