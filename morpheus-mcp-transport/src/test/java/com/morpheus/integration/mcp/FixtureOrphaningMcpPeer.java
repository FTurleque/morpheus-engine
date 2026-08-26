package com.morpheus.integration.mcp;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/** Test peer that spawns a long-lived child and exits successfully. */
final class FixtureOrphaningMcpPeer {
    private FixtureOrphaningMcpPeer() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length >= 1 && args[0].equals("--child")) {
            runChild(Path.of(args[1]));
            return;
        }
        if (args.length != 2) {
            throw new IllegalArgumentException("expected child PID file and parent-exit marker");
        }
        Path pidFile = Path.of(args[0]);
        Path exitMarker = Path.of(args[1]);
        Process child = new ProcessBuilder(
                javaExecutable(),
                "-cp",
                testClasspath(),
                FixtureOrphaningMcpPeer.class.getName(),
                "--child",
                pidFile.toString())
                .redirectInput(ProcessBuilder.Redirect.PIPE)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start();
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (!Files.isRegularFile(pidFile)) {
            if (!child.isAlive()) {
                throw new IllegalStateException("fixture child exited before publishing its PID");
            }
            if (System.nanoTime() >= deadline) {
                throw new IllegalStateException("fixture child did not publish its PID");
            }
            TimeUnit.MILLISECONDS.sleep(10);
        }
        TimeUnit.MILLISECONDS.sleep(250);
        Files.writeString(exitMarker, "parent-exiting");
    }

    private static void runChild(Path pidFile) throws Exception {
        Files.writeString(pidFile, Long.toString(ProcessHandle.current().pid()));
        TimeUnit.SECONDS.sleep(30);
    }

    private static String javaExecutable() {
        boolean windows = System.getProperty("os.name", "")
                .toLowerCase(Locale.ROOT)
                .contains("win");
        return Path.of(System.getProperty("java.home"), "bin", windows ? "java.exe" : "java").toString();
    }

    private static String testClasspath() {
        return System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
    }
}
