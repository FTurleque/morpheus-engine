package com.morpheus.cli;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MorpheusMainTest {
    @Test
    void officialLauncherForcesCliSyncToMatchFullSnapshotExecution() {
        String[] normalized = MorpheusMain.normalizeForExecution(new String[]{
                "--data-dir", "data", "sync", "--project", "01900000-0000-7000-8000-000000000001"
        });
        assertTrue(Arrays.asList(normalized).contains("--force"));
    }

    @Test
    void explicitForceIsNotDuplicatedAndOtherCommandsAreUntouched() {
        String[] forced = MorpheusMain.normalizeForExecution(new String[]{"sync", "--project", "x", "--force"});
        assertArrayEquals(new String[]{"sync", "--project", "x", "--force"}, forced);

        String[] query = MorpheusMain.normalizeForExecution(new String[]{"--json", "projects", "list"});
        assertArrayEquals(new String[]{"--json", "projects", "list"}, query);
    }
}