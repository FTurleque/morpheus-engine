package com.morpheus.architecture;

import com.morpheus.application.identity.EntityIdentityResolver;
import com.morpheus.application.read.ProviderReadRequest;
import com.morpheus.application.read.ReadCategory;
import com.morpheus.application.read.SpecificationContentReader;
import com.morpheus.domain.identity.DomainIdentity;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.provider.openspec.OpenSpecSpecificationContentReader;
import com.morpheus.provider.synthetic.SyntheticSpecificationContentReader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProviderAntiLockInTest {
    @Test
    void openSpecAndSyntheticImplementTheSameApplicationReadPort() {
        assertInstanceOf(SpecificationContentReader.class, new OpenSpecSpecificationContentReader());
        assertInstanceOf(SpecificationContentReader.class, new SyntheticSpecificationContentReader());
    }

    @Test
    void oneProviderNeutralConsumerReadsBothFormats() {
        Summary openSpec = summarize(new OpenSpecSpecificationContentReader(), fixture("openspec-basic"));
        Summary synthetic = summarize(new SyntheticSpecificationContentReader(), fixture("synthetic-basic"));

        assertEquals(Set.of(ReadCategory.values()), openSpec.reportedCategories());
        assertEquals(Set.of(ReadCategory.values()), synthetic.reportedCategories());
        assertEquals(1, openSpec.specificationCount());
        assertEquals(1, synthetic.specificationCount());
        assertTrue(openSpec.requirementCount() > 0);
        assertTrue(synthetic.requirementCount() > 0);
        assertTrue(openSpec.scenarioCount() > 0);
        assertTrue(synthetic.scenarioCount() > 0);
        assertTrue(openSpec.changeCount() > 0);
        assertTrue(synthetic.changeCount() > 0);
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
                ProviderReadRequest.all(fixture("openspec-basic"), ProjectSpecificationId.generate()),
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

    private Path fixture(String name) {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        while (current != null) {
            Path candidate = current.resolve("experiments/m0/fixtures").resolve(name);
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Cannot locate fixture " + name);
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
