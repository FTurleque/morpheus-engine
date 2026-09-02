package com.morpheus.cli;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Real-STDIO MCP server process driven by the CLI integration tests.
 *
 * <p>Standard error is drained into memory rather than through {@code redirectError(File)}: the JDK hands the
 * child a duplicated handle on the redirect target and releases the parent-side handle only when the
 * unreferenced stream is collected, so {@code @TempDir} cleanup raced that handle on Windows and failed the
 * build with {@code DirectoryNotEmptyException}. Keeping the diagnostics in memory removes the file, and with
 * it the race, without weakening the failure messages.
 */
final class McpStdioSession implements AutoCloseable {
    private static final Duration TERMINATION_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration DRAIN_TIMEOUT = Duration.ofSeconds(5);

    private final Process process;
    private final Thread stderrDrain;
    private final StringBuffer stderr = new StringBuffer();
    private final BufferedWriter writer;
    private final BufferedReader reader;

    private McpStdioSession(Process process) {
        this.process = process;
        this.writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
        this.reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
        this.stderrDrain = Thread.ofPlatform().daemon().name("mcp-stdio-stderr").start(this::drainStderr);
    }

    static McpStdioSession start(Path database) throws IOException {
        String testClasspath = System.getProperty(
                "surefire.test.class.path",
                System.getProperty("java.class.path"));
        return new McpStdioSession(new ProcessBuilder(
                javaExecutable().toString(),
                "-cp", testClasspath,
                MorpheusMain.class.getName(),
                "--db", database.toString(),
                "mcp", "--stdio")
                .start());
    }

    Process process() {
        return process;
    }

    String stderr() {
        return stderr.toString();
    }

    void send(String message) throws IOException {
        writer.write(message.strip());
        writer.newLine();
        writer.flush();
    }

    void closeStdin() throws IOException {
        writer.close();
    }

    String readLine(Duration timeout) throws Exception {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (reader.ready()) {
                String line = reader.readLine();
                if (line != null) {
                    return line;
                }
            }
            if (!process.isAlive()) {
                fail("MCP process exited with " + process.exitValue() + "; stderr=" + stderr());
            }
            Thread.sleep(10L);
        }
        fail("Timed out waiting for MCP response; stderr=" + stderr());
        return "";
    }

    @Override
    public void close() throws Exception {
        if (process.isAlive()) {
            process.destroy();
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly();
            }
        }
        assertTrue(process.waitFor(TERMINATION_TIMEOUT.toSeconds(), TimeUnit.SECONDS),
                "MCP process did not terminate; stderr=" + stderr());
        stderrDrain.join(DRAIN_TIMEOUT.toMillis());
        writer.close();
        reader.close();
    }

    private void drainStderr() {
        try (BufferedReader errors = new BufferedReader(
                new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
            String line = errors.readLine();
            while (line != null) {
                stderr.append(line).append(System.lineSeparator());
                line = errors.readLine();
            }
        } catch (IOException streamClosedWithProcess) {
            stderr.append("stderr drain stopped: ").append(streamClosedWithProcess);
        }
    }

    private static Path javaExecutable() {
        String executable = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")
                ? "java.exe" : "java";
        return Path.of(System.getProperty("java.home"), "bin", executable);
    }
}
