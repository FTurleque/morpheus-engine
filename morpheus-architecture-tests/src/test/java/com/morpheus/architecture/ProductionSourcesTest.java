package com.morpheus.architecture;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductionSourcesTest {

    @Test
    void aClassIsFoundWhateverPackageOfItsModuleItResidesIn(@TempDir Path sources) throws IOException {
        Path nested = Files.createDirectories(sources.resolve("com/example/deep"));
        Path expected = Files.writeString(nested.resolve("Target.java"), "final class Target {}");
        Files.writeString(Files.createDirectories(sources.resolve("com/example")).resolve("Other.java"), "");

        assertEquals(expected, ProductionSources.find(sources, "Target"));
    }

    @Test
    void anAbsentClassIsRefusedWithTheTreeItSearched(@TempDir Path sources) throws IOException {
        Files.writeString(Files.createDirectories(sources.resolve("com/example")).resolve("Other.java"), "");

        IllegalStateException refusal =
                assertThrows(IllegalStateException.class, () -> ProductionSources.find(sources, "Target"));
        assertEquals("no source of Target under " + sources, refusal.getMessage());
    }

    @Test
    void aClassDeclaredTwiceIsRefusedWithBothFiles(@TempDir Path sources) throws IOException {
        Files.writeString(Files.createDirectories(sources.resolve("com/example/a")).resolve("Target.java"), "");
        Files.writeString(Files.createDirectories(sources.resolve("com/example/b")).resolve("Target.java"), "");

        IllegalStateException refusal =
                assertThrows(IllegalStateException.class, () -> ProductionSources.find(sources, "Target"));
        assertTrue(refusal.getMessage().startsWith("Target is ambiguous under " + sources), refusal.getMessage());
        assertTrue(refusal.getMessage().contains(sources.resolve("com/example/a/Target.java").toString())
                && refusal.getMessage().contains(sources.resolve("com/example/b/Target.java").toString()),
                refusal.getMessage());
    }

    @Test
    void aNameIsNotMatchedByASuffix(@TempDir Path sources) throws IOException {
        Files.writeString(Files.createDirectories(sources.resolve("com/example")).resolve("BigTarget.java"), "");

        IllegalStateException refusal =
                assertThrows(IllegalStateException.class, () -> ProductionSources.find(sources, "Target"));
        assertEquals("no source of Target under " + sources, refusal.getMessage());
    }

    @Test
    void theRealTreeResolvesAClassInASubPackageOfItsModule() {
        Path identity = ProductionSources.mainSource("morpheus-domain", "DomainIdentity");

        assertTrue(Files.isRegularFile(identity), identity.toString());
        assertEquals(Path.of("morpheus-domain/src/main/java/com/morpheus/domain/identity/DomainIdentity.java"),
                ProductionSources.relativeMainSource("morpheus-domain", "DomainIdentity"));
    }
}
