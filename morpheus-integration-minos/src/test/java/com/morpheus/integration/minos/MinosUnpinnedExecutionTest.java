package com.morpheus.integration.minos;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertFalse;

class MinosUnpinnedExecutionTest {
    @TempDir
    Path tempDirectory;

    @Test
    void existingUnpinnedJarIsNeverExecutable() throws Exception {
        Path jar = Files.createFile(tempDirectory.resolve("minos.jar"));
        MinosIntegrationSettings settings = MinosIntegrationSettings.resolve(
                Map.of(MinosIntegrationSettings.JAR_ENV, jar.toString()),
                new Properties());

        assertFalse(settings.enabled());
    }
}
