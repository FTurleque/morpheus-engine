package com.morpheus.cli;

import com.morpheus.application.operability.OperationalEvent;
import com.morpheus.application.operability.OperationalEventCode;
import com.morpheus.application.operability.OperationalEventLevel;
import com.morpheus.application.operability.OperationalLogMode;
import com.morpheus.application.operability.StructuredOperationalEventSink;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.spi.SLF4JServiceProvider;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the embedded libraries log at warning level and above is heard when the operator asked for logs.
 *
 * <p>The product shipped a binding that discarded every record. The MCP SDK reports a handshake that failed, a stream
 * that broke, a frame it could not parse through SLF4J and nowhere else: with the operational log on, the operator got
 * the product's own events and none of those.</p>
 */
class OperationalSlf4jServiceProviderTest {
    private final List<OperationalEvent> emitted = new ArrayList<>();

    @Test
    void aWarningAndAnErrorBecomeOneEventEachWithTheLoggerTheLevelAndTheFormattedMessage() {
        Logger logger = new OperationalSlf4jServiceProvider.SinkLogger("io.example.Client", emitted::add, true);

        logger.warn("handshake with {} failed after {} ms", "peer", 30_000);
        logger.error("stream closed", new IllegalStateException("broken pipe"));

        assertEquals(2, emitted.size());
        OperationalEvent warning = emitted.get(0);
        assertEquals(OperationalEventCode.THIRD_PARTY_LOG, warning.code());
        assertEquals(OperationalEventLevel.WARNING, warning.level());
        assertEquals(Map.of("logger", "io.example.Client", "message", "handshake with peer failed after 30000 ms"),
                warning.attributes());
        OperationalEvent error = emitted.get(1);
        assertEquals(OperationalEventLevel.ERROR, error.level());
        assertEquals(Map.of("logger", "io.example.Client", "message", "stream closed",
                "failureType", "IllegalStateException"), error.attributes());
    }

    @Test
    void whatIsBelowAWarningIsNotAccepted() {
        Logger logger = new OperationalSlf4jServiceProvider.SinkLogger("io.example.Client", emitted::add, true);

        logger.info("connected");
        logger.debug("frame {}", 1);
        logger.trace("byte {}", 2);

        assertFalse(logger.isInfoEnabled());
        assertTrue(logger.isWarnEnabled());
        assertTrue(logger.isErrorEnabled());
        assertEquals(List.of(), emitted);
    }

    @Test
    void nothingIsAcceptedWhenNoOperationalLogIsWritten() {
        Logger logger = new OperationalSlf4jServiceProvider.SinkLogger("io.example.Client", emitted::add, false);

        logger.warn("handshake failed");
        logger.error("stream closed", new IllegalStateException("broken pipe"));

        assertFalse(logger.isWarnEnabled());
        assertFalse(logger.isErrorEnabled());
        assertEquals(List.of(), emitted);
    }

    @Test
    void aMessageIsBoundedAndAMissingOneIsEmpty() {
        Logger logger = new OperationalSlf4jServiceProvider.SinkLogger("io.example.Client", emitted::add, true);

        logger.warn("x".repeat(OperationalSlf4jServiceProvider.MAX_MESSAGE_CHARACTERS + 1));
        logger.warn("x".repeat(OperationalSlf4jServiceProvider.MAX_MESSAGE_CHARACTERS));
        logger.warn(null);

        assertEquals("x".repeat(OperationalSlf4jServiceProvider.MAX_MESSAGE_CHARACTERS) + "...",
                emitted.get(0).attributes().get("message"));
        assertEquals("x".repeat(OperationalSlf4jServiceProvider.MAX_MESSAGE_CHARACTERS),
                emitted.get(1).attributes().get("message"));
        assertEquals("", emitted.get(2).attributes().get("message"));
    }

    @Test
    void aSecretALibraryPutInItsMessageIsRedactedByTheSinkLikeAnyOtherEvent() {
        ByteArrayOutputStream written = new ByteArrayOutputStream();
        Logger logger = new OperationalSlf4jServiceProvider.SinkLogger("io.example.Client",
                new StructuredOperationalEventSink(new PrintStream(written, true, StandardCharsets.UTF_8)), true);

        logger.warn("request refused, token={}", "s3cr3t-value");

        String line = written.toString(StandardCharsets.UTF_8);
        assertTrue(line.contains("THIRD_PARTY_LOG"), line);
        assertFalse(line.contains("s3cr3t-value"), line);
    }

    @Test
    void theClassPathOfTheProductCarriesOneBindingAndItIsThisOne() {
        List<String> providers = ServiceLoader.load(SLF4JServiceProvider.class).stream()
                .map(provider -> provider.type().getName())
                .toList();

        assertEquals(List.of(OperationalSlf4jServiceProvider.class.getName()), providers);
        assertEquals(OperationalSlf4jServiceProvider.SinkLogger.class,
                LoggerFactory.getLogger("io.example.Client").getClass());
    }

    @Test
    void aProcessWithLogsOnWritesOneJsonLineOnStandardErrorAndNothingOnStandardOutput() throws Exception {
        Outcome outcome = launch("json");

        assertEquals(0, outcome.exitCode(), outcome::toString);
        assertEquals("", outcome.standardOutput(), "standard output is the protocol stream of the MCP server");
        List<String> lines = outcome.standardError().lines().filter(line -> !line.isBlank()).toList();
        assertEquals(1, lines.size(), outcome::toString);
        assertTrue(lines.get(0).startsWith("{") && lines.get(0).contains("\"THIRD_PARTY_LOG\"")
                && lines.get(0).contains("io.example.Fixture")
                && lines.get(0).contains("handshake with peer failed"), outcome::toString);
    }

    @Test
    void aProcessWithLogsOffWritesNothingAtAllNotEvenTheWarningOfAMissingBinding() throws Exception {
        Outcome outcome = launch(null);

        assertEquals(0, outcome.exitCode(), outcome::toString);
        assertEquals("", outcome.standardOutput());
        assertEquals("", outcome.standardError());
    }

    private static Outcome launch(String mode) throws Exception {
        Path launcher = Path.of(System.getProperty("java.home"), "bin", "java");
        String classpath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        ProcessBuilder builder = new ProcessBuilder(
                launcher.toString(), "-cp", classpath, FixtureThirdPartyLogProcess.class.getName());
        if (mode == null) {
            builder.environment().remove(OperationalLogMode.ENVIRONMENT_VARIABLE);
        } else {
            builder.environment().put(OperationalLogMode.ENVIRONMENT_VARIABLE, mode);
        }
        Process process = builder.start();
        process.getOutputStream().close();
        String standardOutput = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        String standardError = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(process.waitFor(60, TimeUnit.SECONDS), "the launched process did not end");
        return new Outcome(process.exitValue(), standardOutput, standardError);
    }

    private record Outcome(int exitCode, String standardOutput, String standardError) {
    }
}
