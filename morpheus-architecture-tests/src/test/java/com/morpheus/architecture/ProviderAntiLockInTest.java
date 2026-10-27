package com.morpheus.architecture;

import com.morpheus.application.identity.EntityIdentityResolver;
import com.morpheus.application.identity.PersistentEntityIdentityResolver;
import com.morpheus.application.ingestion.ProjectContentIngestion;
import com.morpheus.application.ingestion.ProjectSnapshotImportResult;
import com.morpheus.application.ingestion.ProjectSnapshotImportService;
import com.morpheus.application.read.ProviderReadRequest;
import com.morpheus.application.read.ReadCategory;
import com.morpheus.application.read.SpecificationContentReader;
import com.morpheus.domain.identity.DomainIdentity;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotState;
import com.morpheus.provider.markdown.StructuredMarkdownSpecificationContentReader;
import com.morpheus.provider.openspec.OpenSpecSpecificationContentReader;
import com.morpheus.provider.synthetic.SyntheticSpecificationContentReader;
import com.morpheus.store.memory.MemorySnapshotBusinessContentStore;
import com.morpheus.store.memory.MemorySpecificationKnowledgeStore;
import com.morpheus.store.memory.MemoryTraceabilityStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProviderAntiLockInTest {
    @Test
    void openSpecSyntheticAndStructuredMarkdownImplementTheSameApplicationReadPort() {
        assertInstanceOf(SpecificationContentReader.class, new OpenSpecSpecificationContentReader());
        assertInstanceOf(SpecificationContentReader.class, new SyntheticSpecificationContentReader());
        assertInstanceOf(SpecificationContentReader.class, new StructuredMarkdownSpecificationContentReader());
    }

    @Test
    void oneProviderNeutralConsumerReadsAllThreeFormats() {
        Summary openSpec = summarize(new OpenSpecSpecificationContentReader(), RepositoryRoot.fixture("openspec-basic"));
        Summary synthetic = summarize(new SyntheticSpecificationContentReader(), RepositoryRoot.fixture("synthetic-basic"));
        Summary markdown = summarize(new StructuredMarkdownSpecificationContentReader(), RepositoryRoot.fixture("openspec-basic"));

        assertEquals(Set.of(ReadCategory.values()), openSpec.reportedCategories());
        assertEquals(Set.of(ReadCategory.values()), synthetic.reportedCategories());
        assertEquals(Set.of(ReadCategory.values()), markdown.reportedCategories());
        assertEquals(1, openSpec.specificationCount());
        assertEquals(1, synthetic.specificationCount());
        assertEquals(2, markdown.specificationCount());
        assertTrue(openSpec.requirementCount() > 0);
        assertTrue(synthetic.requirementCount() > 0);
        assertTrue(markdown.requirementCount() > 0);
        assertTrue(openSpec.scenarioCount() > 0);
        assertTrue(synthetic.scenarioCount() > 0);
        assertTrue(markdown.changeCount() > 0);
        assertTrue(openSpec.changeCount() > 0);
        assertTrue(synthetic.changeCount() > 0);
    }

    @Test
    void oneProviderNeutralPublicationPublishesEachOfTheThreeFormats() {
        for (Map.Entry<SpecificationContentReader, String> provider : List.of(
                Map.entry((SpecificationContentReader) new OpenSpecSpecificationContentReader(), "openspec-basic"),
                Map.entry((SpecificationContentReader) new SyntheticSpecificationContentReader(), "synthetic-basic"),
                Map.entry((SpecificationContentReader) new StructuredMarkdownSpecificationContentReader(),
                        "openspec-basic"))) {
            var snapshots = new MemorySpecificationKnowledgeStore();
            ProjectSpecificationId projectId = ProjectSpecificationId.generate();
            var content = new ProjectContentIngestion(provider.getKey()).read(
                    RepositoryRoot.fixture(provider.getValue()), projectId, new PersistentEntityIdentityResolver(snapshots));

            ProjectSnapshotImportResult published = new ProjectSnapshotImportService(
                    snapshots,
                    snapshots,
                    new MemorySnapshotBusinessContentStore(snapshots, snapshots),
                    new MemoryTraceabilityStore(snapshots))
                    .publishFull(content, Optional.empty(), Instant.parse("2026-10-25T00:00:00Z"));

            String name = provider.getKey().providerId().value();
            assertEquals(KnowledgeSnapshotState.ACTIVE, published.snapshot().state(), name);
            assertTrue(published.requirementCount() > 0, name);
        }
    }

    @Test
    void identicalExternalKeysRemainProviderScoped(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve("morpheus-spec.json"), """
                {
                  "format_version": 1,
                  "current": {
                    "specifications": 1,
                    "specification": {"key": "auth-session", "title": "Authentication Session"},
                    "requirements": [
                      {
                        "key": "auth-session/session-expiration",
                        "title": "Session expiration",
                        "statement": "Synthetic statement for identity scoping.",
                        "scenarios": [
                          {
                            "title": "Expire session",
                            "steps": [
                              "GIVEN an authenticated session",
                              "WHEN expiration is evaluated",
                              "THEN require authentication"
                            ]
                          }
                        ]
                      }
                    ]
                  },
                  "proposed": {"changes": []},
                  "historical": {"changes": []},
                  "diagnostics": []
                }
                """);

        InMemoryResolver resolver = new InMemoryResolver();
        var openSpec = new OpenSpecSpecificationContentReader().read(
                ProviderReadRequest.all(RepositoryRoot.fixture("openspec-basic"), ProjectSpecificationId.generate()),
                resolver).content().orElseThrow();
        var synthetic = new SyntheticSpecificationContentReader().read(
                ProviderReadRequest.all(tempDir, ProjectSpecificationId.generate()),
                resolver).content().orElseThrow();

        var openSpecRequirement = openSpec.requirements().stream()
                .filter(item -> item.key().orElse("").equals("auth-session/session-expiration"))
                .findFirst()
                .orElseThrow();
        var syntheticRequirement = synthetic.requirements().getFirst();

        assertEquals(openSpecRequirement.key(), syntheticRequirement.key());
        assertNotEquals(openSpecRequirement.id(), syntheticRequirement.id());
        assertNotEquals(openSpec.specifications().getFirst().id(), synthetic.specifications().getFirst().id());
    }

    private Summary summarize(SpecificationContentReader reader, Path root) {
        var result = reader.read(
                ProviderReadRequest.all(root, ProjectSpecificationId.generate()),
                new InMemoryResolver());
        var content = result.content().orElseThrow();
        Set<ReadCategory> categories = result.categoryReports().stream()
                .map(report -> report.category())
                .collect(Collectors.toSet());
        return new Summary(
                content.specifications().size(),
                content.requirements().size(),
                content.scenarios().size(),
                content.changes().size(),
                categories);
    }

    private record Summary(
            int specificationCount,
            int requirementCount,
            int scenarioCount,
            int changeCount,
            Set<ReadCategory> reportedCategories) {}

    private static final class InMemoryResolver implements EntityIdentityResolver {
        private final Map<String, DomainIdentity> identities = new HashMap<>();

        @Override
        public DomainIdentity resolve(com.morpheus.domain.provider.ProviderId providerId, String entityType, String externalId) {
            String key = providerId + "|" + entityType + "|" + externalId;
            return identities.computeIfAbsent(key, ignored -> DomainIdentity.generate());
        }
    }
}
