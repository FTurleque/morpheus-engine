package com.morpheus.application.operability;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The logs directory holds the operational log when the operator asks for a file.
 *
 * <p>The directory was resolved, published by the {@code paths} command and named in the user pages, and the product
 * wrote nothing in it. The file is bounded: an operator who turns the log on does not find a full disk later.</p>
 */
class RotatingOperationalLogFileTest {
    @TempDir
    Path temporaryDirectory;

    private final OperationalMetrics metrics = new OperationalMetrics();

    @Test
    void anEventIsOneRedactedJsonLineOfAFileCreatedWithItsDirectory() throws IOException {
        Path logs = temporaryDirectory.resolve("product").resolve("logs");
        RotatingOperationalLogFile log = RotatingOperationalLogFile.open(logs, metrics);

        log.emit(event("first", Map.of("token", "s3cr3t-value")));
        log.emit(event("second", Map.of()));

        assertEquals(logs.resolve("morpheus-operational.jsonl"), log.file());
        List<String> lines = Files.readAllLines(log.file());
        assertEquals(2, lines.size());
        assertTrue(lines.get(0).startsWith("{\"timestamp\":\"2026-10-13T10:00:00Z\",\"level\":\"WARNING\","
                + "\"code\":\"THIRD_PARTY_LOG\""), lines.get(0));
        assertTrue(lines.get(0).contains("\"message\":\"first\""), lines.get(0));
        assertFalse(lines.get(0).contains("s3cr3t-value"), lines.get(0));
        assertTrue(lines.get(1).contains("\"message\":\"second\""), lines.get(1));
        assertEquals(Map.of(), metrics.snapshot().counters());
    }

    @Test
    void aLogOpenedAgainGoesOnWhereItStopped() throws IOException {
        RotatingOperationalLogFile.open(temporaryDirectory, metrics).emit(event("before", Map.of()));

        RotatingOperationalLogFile.open(temporaryDirectory, metrics).emit(event("after", Map.of()));

        List<String> lines = Files.readAllLines(temporaryDirectory.resolve(RotatingOperationalLogFile.FILE_NAME));
        assertEquals(2, lines.size());
        assertTrue(lines.get(0).contains("before") && lines.get(1).contains("after"), lines::toString);
    }

    @Test
    void theEventThatWouldCrossTheBoundStartsANewFileAndOnlyTheKeptGenerationsRemain() throws IOException {
        int lineBytes = (new StructuredOperationalEventSink(System.err).line(event("event-0", Map.of())) + "\n")
                .length();
        RotatingOperationalLogFile log =
                RotatingOperationalLogFile.open(temporaryDirectory, 2L * lineBytes, 2, metrics);

        for (int number = 0; number < 9; number++) {
            log.emit(event("event-" + number, Map.of()));
        }

        assertEquals(List.of("morpheus-operational.jsonl", "morpheus-operational.jsonl.1",
                "morpheus-operational.jsonl.2"), names(temporaryDirectory));
        assertEquals(List.of("event-8"), messages(log.file()));
        assertEquals(List.of("event-6", "event-7"), messages(generation(1)));
        assertEquals(List.of("event-4", "event-5"), messages(generation(2)));
        for (Path kept : List.of(log.file(), generation(1), generation(2))) {
            assertTrue(Files.size(kept) <= 2L * lineBytes, kept::toString);
        }
    }

    @Test
    void anEventLargerThanTheBoundIsWrittenAloneRatherThanLost() throws IOException {
        RotatingOperationalLogFile log = RotatingOperationalLogFile.open(temporaryDirectory, 16, 1, metrics);

        log.emit(event("first", Map.of()));
        log.emit(event("second", Map.of()));

        assertEquals(List.of("second"), messages(log.file()));
        assertEquals(List.of("first"), messages(generation(1)));
    }

    @Test
    @EnabledOnOs({OS.LINUX, OS.MAC})
    void theDirectoryAndTheFilesArePrivateToTheAccount() throws IOException {
        Path logs = temporaryDirectory.resolve("logs");
        RotatingOperationalLogFile log = RotatingOperationalLogFile.open(logs, 16, 1, metrics);

        log.emit(event("first", Map.of()));
        log.emit(event("second", Map.of()));

        assertEquals("rwx------", PosixFilePermissions.toString(Files.getPosixFilePermissions(logs)));
        assertEquals("rw-------", PosixFilePermissions.toString(Files.getPosixFilePermissions(log.file())));
        assertEquals("rw-------", PosixFilePermissions.toString(Files.getPosixFilePermissions(
                logs.resolve(RotatingOperationalLogFile.FILE_NAME + ".1"))));
    }

    @Test
    void aDirectoryThatCannotBeOneIsRefusedByNameWithWhatToDo() throws IOException {
        Path occupied = Files.writeString(temporaryDirectory.resolve("logs"), "a file where the directory should be");

        OperationalLogFileException refused = assertThrows(OperationalLogFileException.class,
                () -> RotatingOperationalLogFile.open(occupied, metrics));

        assertTrue(refused.getMessage().startsWith(RotatingOperationalLogFile.REFUSAL + ": "), refused::getMessage);
        assertTrue(refused.getMessage().contains("MORPHEUS_LOGS_DIR"), refused::getMessage);
        assertTrue(refused.getMessage().contains(OperationalLogMode.ENVIRONMENT_VARIABLE), refused::getMessage);
    }

    @Test
    void anEventThatCannotBeWrittenIsCountedAndTheNextOneIsStillTried() throws IOException {
        RotatingOperationalLogFile log = RotatingOperationalLogFile.open(temporaryDirectory, metrics);
        Files.delete(log.file());
        Files.createDirectory(log.file());

        log.emit(event("lost", Map.of()));
        log.emit(event("lost too", Map.of()));

        assertEquals(Map.of(RotatingOperationalLogFile.WRITE_FAILED, 2L), metrics.snapshot().counters());
    }

    @Test
    void aBoundThatBoundsNothingIsNotAccepted() {
        assertThrows(IllegalArgumentException.class,
                () -> RotatingOperationalLogFile.open(temporaryDirectory, 0, 1, metrics));
        assertThrows(IllegalArgumentException.class,
                () -> RotatingOperationalLogFile.open(temporaryDirectory, 16, 0, metrics));
    }

    private Path generation(int number) {
        return temporaryDirectory.resolve(RotatingOperationalLogFile.FILE_NAME + "." + number);
    }

    private static OperationalEvent event(String message, Map<String, String> more) {
        Map<String, String> attributes = new java.util.TreeMap<>(more);
        attributes.put("message", message);
        return new OperationalEvent(Instant.parse("2026-10-13T10:00:00Z"), OperationalEventLevel.WARNING,
                OperationalEventCode.THIRD_PARTY_LOG, attributes);
    }

    private static List<String> messages(Path file) throws IOException {
        return Files.readAllLines(file).stream()
                .map(line -> line.replaceAll(".*\"message\":\"([^\"]*)\".*", "$1"))
                .toList();
    }

    private static List<String> names(Path directory) throws IOException {
        try (Stream<Path> entries = Files.list(directory)) {
            return entries.map(entry -> entry.getFileName().toString()).sorted().toList();
        }
    }
}
