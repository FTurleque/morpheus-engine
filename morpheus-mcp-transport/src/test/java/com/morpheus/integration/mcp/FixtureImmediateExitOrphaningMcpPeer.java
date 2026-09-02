package com.morpheus.integration.mcp;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Test peer that spawns a long-lived child and exits in the same instant, without ever giving MORPHEUS a window in
 * which the child is observable as its descendant.
 *
 * <p>Unlike {@link FixtureOrphaningMcpPeer}, this parent neither waits for the child to publish its PID nor sleeps
 * before exiting. The child publishes its own PID, so the test can identify it without the parent staying alive to
 * help. This is the shape MORPHEUS cannot cover by sampling {@code descendants()}.
 */
final class FixtureImmediateExitOrphaningMcpPeer {
    private FixtureImmediateExitOrphaningMcpPeer() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length >= 1 && args[0].equals("--child")) {
            runChild(Path.of(args[1]));
            return;
        }
        if (args.length != 2) {
            throw new IllegalArgumentException("expected child PID file and peer PID file");
        }
        Files.writeString(Path.of(args[1]), Long.toString(ProcessHandle.current().pid()));
        new ProcessBuilder(
                javaExecutable(),
                "-cp",
                testClasspath(),
                FixtureImmediateExitOrphaningMcpPeer.class.getName(),
                "--child",
                args[0])
                .redirectInput(ProcessBuilder.Redirect.PIPE)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start();
        // halt() rather than a normal return: a JVM shutdown takes tens of milliseconds, which would hand MORPHEUS
        // several observation polls. Real peers can be native binaries that exit in microseconds, and this fixture
        // must reproduce that, not the comfortable JVM case.
        Runtime.getRuntime().halt(0);
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
