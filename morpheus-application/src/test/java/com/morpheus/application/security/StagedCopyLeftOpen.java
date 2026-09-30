package com.morpheus.application.security;

import java.nio.file.Path;

/**
 * Child-JVM entry point that stages a verified copy and exits without deleting it, as a caller would when the JVM
 * ends before its {@code close()} runs.
 */
public final class StagedCopyLeftOpen {
    private StagedCopyLeftOpen() {
    }

    public static void main(String[] args) {
        Path staged = ExternalJarIntegrity.stageVerifiedCopy(Path.of(args[0]), args[1]);
        System.out.println(staged);
        System.out.flush();
        System.exit(0);
    }
}
