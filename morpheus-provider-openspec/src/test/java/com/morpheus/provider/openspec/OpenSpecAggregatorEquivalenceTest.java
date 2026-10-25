package com.morpheus.provider.openspec;

import com.morpheus.application.identity.EntityIdentityResolver;
import com.morpheus.application.ingestion.NormalizedProjectContent;
import com.morpheus.application.read.ProviderReadRequest;
import com.morpheus.application.read.ProviderReadResult;
import com.morpheus.application.read.ReadCategory;
import com.morpheus.application.read.ReadCategoryStatus;
import com.morpheus.domain.identity.DomainIdentity;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.provider.ProviderId;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The strict aggregator and the aggregator behind the port, on every M0 fixture: where the strict one reads a fixture
 * completely, the port one returns the same content, field by field; where the strict one throws, the port one
 * returns no content or reports a category {@code FAILED} or {@code PARTIAL}, so neither publishes.
 */
class OpenSpecAggregatorEquivalenceTest {
    private static final Set<ReadCategory> PUBLISHED = EnumSet.of(
            ReadCategory.CURRENT_SPECIFICATIONS, ReadCategory.REQUIREMENTS, ReadCategory.SCENARIOS,
            ReadCategory.CHANGES, ReadCategory.REQUIREMENT_DELTAS, ReadCategory.CONSTRAINTS,
            ReadCategory.DESIGN_DECISIONS, ReadCategory.IMPLEMENTATION_TASKS);

    @Test
    void aFixtureBothReadCompletelyGivesTheSameContent() {
        for (String fixture : new String[]{"openspec-basic", "openspec-upstream-cli"}) {
            assertSameContent(fixture);
        }
    }

    @Test
    void aFixtureTheStrictAggregatorRefusesIsNotPublishableThroughThePort() {
        for (String fixture : new String[]{
                "openspec-partial", "openspec-state-matrix", "openspec-unsupported-schema", "synthetic-basic"}) {
            assertNotPublishable(fixture);
        }
    }

    private static void assertSameContent(String fixture) {
        Path root = fixture(fixture);
        ProjectSpecificationId projectId = ProjectSpecificationId.generate();
        EntityIdentityResolver resolver = new StableResolver();

        NormalizedProjectContent strict = new OpenSpecProjectContentReader().read(root, projectId, resolver);
        ProviderReadResult port = new OpenSpecSpecificationContentReader()
                .read(new ProviderReadRequest(root, projectId, PUBLISHED), resolver);

        assertTrue(port.categoryReports().stream().allMatch(report -> report.status() == ReadCategoryStatus.READ
                || report.status() == ReadCategoryStatus.ABSENT), fixture + ": " + port.categoryReports());
        assertEquals(strict, port.content().orElseThrow(), fixture);
    }

    private static void assertNotPublishable(String fixture) {
        Path root = fixture(fixture);
        ProjectSpecificationId projectId = ProjectSpecificationId.generate();
        EntityIdentityResolver resolver = new StableResolver();

        assertThrows(IllegalArgumentException.class,
                () -> new OpenSpecProjectContentReader().read(root, projectId, resolver), fixture);
        ProviderReadResult port = new OpenSpecSpecificationContentReader()
                .read(new ProviderReadRequest(root, projectId, PUBLISHED), resolver);

        assertTrue(port.content().isEmpty() || port.categoryReports().stream().anyMatch(report ->
                        report.status() == ReadCategoryStatus.FAILED || report.status() == ReadCategoryStatus.PARTIAL),
                fixture + ": " + port.categoryReports());
    }

    private static Path fixture(String name) {
        Path current = Path.of("").toAbsolutePath().normalize();
        for (Path candidate : new Path[]{
                current.resolve("experiments/m0/fixtures").resolve(name),
                current.resolve("../experiments/m0/fixtures").normalize().resolve(name)}) {
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("M0 fixture not found: " + name + " from " + current);
    }

    private static final class StableResolver implements EntityIdentityResolver {
        private final Map<String, DomainIdentity> identities = new HashMap<>();

        @Override
        public DomainIdentity resolve(ProviderId providerId, String entityType, String externalId) {
            return identities.computeIfAbsent(providerId.value() + "|" + entityType + "|" + externalId,
                    ignored -> DomainIdentity.generate());
        }
    }
}
