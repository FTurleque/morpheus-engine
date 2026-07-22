package com.morpheus.architecture;

import com.morpheus.application.identity.PersistentEntityIdentityResolver;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.provider.openspec.OpenSpecCurrentSpecificationReader;
import com.morpheus.store.sqlite.SqliteEntityIdentityStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PersistentIdentityOpenSpecIntegrationTest {

    @TempDir
    Path tempDir;

    @Test
    void normalizedOpenSpecIdentitiesSurviveSqliteResolverReopen() {
        Path database = tempDir.resolve("identity.db");
        ProjectSpecificationId projectId = ProjectSpecificationId.generate();
        OpenSpecCurrentSpecificationReader reader = new OpenSpecCurrentSpecificationReader();

        var first = read(reader, database, projectId);
        var second = read(reader, database, projectId);

        assertEquals(
                first.specifications().stream().map(item -> item.id()).toList(),
                second.specifications().stream().map(item -> item.id()).toList());
        assertEquals(
                first.requirements().stream().map(item -> item.id()).toList(),
                second.requirements().stream().map(item -> item.id()).toList());
        assertEquals(
                first.scenarios().stream().map(item -> item.id()).toList(),
                second.scenarios().stream().map(item -> item.id()).toList());
        assertEquals(
                first.evidence().stream().map(item -> item.id()).toList(),
                second.evidence().stream().map(item -> item.id()).toList());
    }

    private com.morpheus.application.ingestion.NormalizedProjectContent read(
            OpenSpecCurrentSpecificationReader reader,
            Path database,
            ProjectSpecificationId projectId) {
        try (var store = new SqliteEntityIdentityStore(database)) {
            return reader.read(
                    fixture("openspec-basic"),
                    projectId,
                    new PersistentEntityIdentityResolver(store));
        }
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
}
