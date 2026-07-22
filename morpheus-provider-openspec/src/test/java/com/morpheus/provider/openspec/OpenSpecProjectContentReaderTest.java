package com.morpheus.provider.openspec;

import com.morpheus.application.identity.EntityIdentityResolver;
import com.morpheus.domain.identity.DomainIdentity;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.provider.ProviderId;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenSpecProjectContentReaderTest {

    @Test
    void aggregatesCurrentSpecificationsAndChangeMetadataIntoOneNormalizedGraph() {
        var content = new OpenSpecProjectContentReader().read(
                fixture("openspec-basic"),
                ProjectSpecificationId.generate(),
                new StableTestIdentityResolver());

        assertEquals(1, content.specifications().size());
        assertEquals(2, content.requirements().size());
        assertEquals(2, content.scenarios().size());
        assertEquals(1, content.changes().size());
        assertEquals(2, content.constraints().size());
        assertEquals(2, content.designDecisions().size());
        assertEquals(8, content.tasks().size());
        assertEquals(18, content.evidence().size());
        assertTrue(content.diagnostics().isEmpty());

        var evidenceIds = new HashSet<>();
        content.evidence().forEach(item -> evidenceIds.add(item.id()));
        assertEquals(18, evidenceIds.size());

        var changeId = content.changes().getFirst().id();
        assertTrue(content.constraints().stream().allMatch(item -> item.changeId().equals(changeId)));
        assertTrue(content.designDecisions().stream().allMatch(item -> item.changeId().equals(changeId)));
        assertTrue(content.tasks().stream().allMatch(item -> item.changeId().equals(changeId)));
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
