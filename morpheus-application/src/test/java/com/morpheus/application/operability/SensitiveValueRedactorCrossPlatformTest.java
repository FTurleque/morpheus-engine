package com.morpheus.application.operability;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SensitiveValueRedactorCrossPlatformTest {

    @Test
    void redactsWindowsAndUnixAbsolutePathsIndependentlyOfHostOperatingSystem() {
        SensitiveValueRedactor redactor = new SensitiveValueRedactor();

        assertEquals(SensitiveValueRedactor.PATH_REDACTED,
                redactor.redact("databasePath", "C:\\Users\\alice\\.morpheus\\morpheus.db"));
        assertEquals(SensitiveValueRedactor.PATH_REDACTED,
                redactor.redact("workspaceRoot", "\\\\server\\share\\specs"));
        assertEquals(SensitiveValueRedactor.PATH_REDACTED,
                redactor.redact("workspacePath", "/home/alice/project"));
    }
}
