package com.morpheus.api;

import com.morpheus.application.operability.OperationalMetrics;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The access journal on its own: what it records, its bound, and what a record that cannot be written costs. */
class RemoteAccessJournalTest {
    @TempDir
    Path temp;

    private final OperationalMetrics metrics = new OperationalMetrics();

    @Test
    void aReadLeavesOneRecordAndAWriteOrAnAdministrationTwo() throws IOException {
        RemoteAccessJournal journal = RemoteAccessJournal.beside(temp.resolve("remote-auth.txt"), metrics);

        assertTrue(journal.accepted(request("reader", MorpheusRemoteRole.READ, MorpheusRemoteRole.READ)));
        journal.completed(request("reader", MorpheusRemoteRole.READ, MorpheusRemoteRole.READ), 200);
        assertTrue(journal.accepted(request("admin", MorpheusRemoteRole.ADMIN, MorpheusRemoteRole.WRITE)));
        journal.completed(request("admin", MorpheusRemoteRole.ADMIN, MorpheusRemoteRole.WRITE), 201);
        assertTrue(journal.accepted(request("admin", MorpheusRemoteRole.ADMIN, MorpheusRemoteRole.ADMIN)));
        journal.completed(request("admin", MorpheusRemoteRole.ADMIN, MorpheusRemoteRole.ADMIN), -1);

        List<String> lines = Files.readAllLines(temp.resolve(RemoteAccessJournal.FILE_NAME));
        assertEquals(5, lines.size(), lines::toString);
        assertTrue(lines.get(0).contains("\"phase\":\"completed\"") && lines.get(0).contains("\"status\":\"200\""));
        assertTrue(lines.get(1).contains("\"phase\":\"accepted\"") && lines.get(1).contains("\"roleHeld\":\"ADMIN\"")
                && lines.get(1).contains("\"roleRequired\":\"WRITE\"") && lines.get(1).contains("\"method\":\"POST\""));
        assertTrue(lines.get(2).contains("\"status\":\"201\""), lines.get(2));
        assertTrue(lines.get(4).contains("\"status\":\"unsent\""), lines.get(4));
        assertEquals(Map.of(), metrics.snapshot().counters());
    }

    @Test
    void aPrincipalNamedLikeASecretIsRedactedLikeAnyOtherValue() throws IOException {
        RemoteAccessJournal journal = RemoteAccessJournal.beside(temp.resolve("remote-auth.txt"), metrics);

        journal.completed(new RemoteAccessJournal.Request(
                "request-1", "token=s3cr3t-value", MorpheusRemoteRole.READ, MorpheusRemoteRole.READ, "get",
                "health"), 200);

        String line = Files.readString(temp.resolve(RemoteAccessJournal.FILE_NAME));
        assertFalse(line.contains("s3cr3t-value"), line);
        assertTrue(line.contains("\"method\":\"GET\""), line);
    }

    @Test
    void theJournalIsRotatedAtItsBoundAndKeepsNoMoreThanItsGenerations() throws IOException {
        RemoteAccessJournal journal =
                RemoteAccessJournal.open(temp.resolve(RemoteAccessJournal.FILE_NAME), 600, 2, metrics);

        for (int number = 0; number < 40; number++) {
            journal.completed(request("reader", MorpheusRemoteRole.READ, MorpheusRemoteRole.READ), 200);
        }

        try (Stream<Path> entries = Files.list(temp)) {
            assertEquals(List.of(RemoteAccessJournal.FILE_NAME, RemoteAccessJournal.FILE_NAME + ".1",
                            RemoteAccessJournal.FILE_NAME + ".2"),
                    entries.map(entry -> entry.getFileName().toString()).sorted().toList());
        }
        assertTrue(Files.size(temp.resolve(RemoteAccessJournal.FILE_NAME)) <= 600);
    }

    @Test
    void aRecordThatCannotBeWrittenIsCountedRefusesAWriteAndLetsAReadThrough() throws IOException {
        RemoteAccessJournal journal = RemoteAccessJournal.beside(temp.resolve("remote-auth.txt"), metrics);
        Files.delete(temp.resolve(RemoteAccessJournal.FILE_NAME));
        Files.createDirectory(temp.resolve(RemoteAccessJournal.FILE_NAME));

        assertFalse(journal.accepted(request("writer", MorpheusRemoteRole.WRITE, MorpheusRemoteRole.WRITE)));
        assertTrue(journal.accepted(request("reader", MorpheusRemoteRole.READ, MorpheusRemoteRole.READ)));
        assertTrue(journal.accepted(new RemoteAccessJournal.Request(
                        "request-2", "admin", MorpheusRemoteRole.ADMIN, MorpheusRemoteRole.ADMIN, "GET", "metrics")),
                "the route that serves the counter of lost records is not refused for want of the journal");
        journal.completed(request("reader", MorpheusRemoteRole.READ, MorpheusRemoteRole.READ), 200);

        assertEquals(Map.of(RemoteAccessJournal.LOST, 2L), metrics.snapshot().counters());
    }

    @Test
    void aJournalThatCannotBeOpenedIsARefusalThatNamesNoLocation() throws IOException {
        Path occupied = Files.writeString(temp.resolve("occupied"), "a file where the directory should be");

        IllegalStateException refused = assertThrows(IllegalStateException.class,
                () -> RemoteAccessJournal.beside(occupied.resolve("remote-auth.txt"), metrics));

        assertTrue(refused.getMessage().startsWith(RemoteAccessJournal.REFUSAL + ": "), refused::getMessage);
        assertFalse(refused.getMessage().contains(temp.toString()), refused::getMessage);
    }

    private static RemoteAccessJournal.Request request(
            String principal, MorpheusRemoteRole held, MorpheusRemoteRole required) {
        return new RemoteAccessJournal.Request("request-1", principal, held, required, "POST", "projects");
    }
}
