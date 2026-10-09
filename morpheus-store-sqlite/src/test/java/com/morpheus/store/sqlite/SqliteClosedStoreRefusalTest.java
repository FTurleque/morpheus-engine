package com.morpheus.store.sqlite;

import com.morpheus.application.store.KnowledgeStoreException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every SQLite store refuses an operation after {@code close()} the same way: a {@link KnowledgeStoreException} that
 * names the store, with no cause, before touching its arguments or the database.
 *
 * <p>The operations are called with {@code null} arguments on purpose: the guard must come first, so a store that
 * validated its arguments before its open state would fail here with a {@code NullPointerException}.</p>
 */
class SqliteClosedStoreRefusalTest {
    private static final Pattern CLOSED_MESSAGE = Pattern.compile("\"(SQLite [a-z -]+ is closed)\"");
    private static final Pattern STORE_CLASS = Pattern.compile("public final class (Sqlite\\w+Store)\\b");

    @TempDir
    Path tempDir;

    @Test
    void theSavedViewStoreRefusesEveryOperationAfterClose() {
        SqliteSavedViewStore store = new SqliteSavedViewStore(tempDir.resolve("saved-views.db"));
        store.close();

        assertEveryOperationRefused("SQLite saved-view store is closed", List.of(
                () -> store.create(null, null),
                () -> store.find(null),
                () -> store.list(null),
                () -> store.listVersions(null),
                () -> store.count(null),
                () -> store.archive(null, 1L, null),
                () -> store.compareAndSet(null, 1L, null, null)));
    }

    @Test
    void theCompositionStateStoreRefusesEveryOperationAfterClose() {
        SqliteCompositionStateStore store = new SqliteCompositionStateStore(tempDir.resolve("composition.db"));
        store.close();

        assertEveryOperationRefused("SQLite composition state store is closed", List.of(
                () -> store.save(null),
                () -> store.find(null)));
    }

    /** A store added later must be listed here, or the census below refuses it. */
    @Test
    void everySqliteStoreRefusesAnOperationAfterCloseTheSameWay() throws IOException {
        Map<String, Function<Path, Executable>> stores = new TreeMap<>();
        stores.put("SqliteChangeLifecycleMutationStore",
                path -> closed(new SqliteChangeLifecycleMutationStore(path), store -> store.apply(null)));
        stores.put("SqliteCompositionStateStore",
                path -> closed(new SqliteCompositionStateStore(path), store -> store.find(null)));
        stores.put("SqliteEntityIdentityStore",
                path -> closed(new SqliteEntityIdentityStore(path), store -> store.find(null)));
        stores.put("SqliteExternalReferenceStore",
                path -> closed(new SqliteExternalReferenceStore(path), store -> store.putReference(null, null)));
        stores.put("SqlitePolicyPackStore",
                path -> closed(new SqlitePolicyPackStore(path), store -> store.listActivations(null)));
        stores.put("SqlitePortfolioStore",
                path -> closed(new SqlitePortfolioStore(path), store -> store.findPortfolio(null)));
        stores.put("SqliteSavedViewStore", path -> closed(new SqliteSavedViewStore(path), store -> store.find(null)));
        stores.put("SqliteSnapshotBusinessContentStore",
                path -> closed(new SqliteSnapshotBusinessContentStore(path), store -> store.findSnapshotContent(null)));
        stores.put("SqliteSpecificationKnowledgeStore",
                path -> closed(new SqliteSpecificationKnowledgeStore(path), store -> store.findProject(null)));
        stores.put("SqliteSyncStateStore",
                path -> closed(new SqliteSyncStateStore(path), store -> store.findSyncState(null)));
        stores.put("SqliteTraceabilityStore",
                path -> closed(new SqliteTraceabilityStore(path), store -> store.putLink(null, null)));
        stores.put("SqliteVersionedRequirementStore",
                path -> closed(new SqliteVersionedRequirementStore(path), store -> store.findSpecificationVersion(null)));

        Map<String, String> declared = closedStoreMessages();
        assertEquals(declared.keySet(), new TreeSet<>(stores.keySet()),
                "every SQLite store with a closed guard must be exercised here, and only those");
        stores.forEach((name, operation) -> {
            Executable call = operation.apply(tempDir.resolve(name + ".db"));
            KnowledgeStoreException refusal = assertThrows(KnowledgeStoreException.class, call, name);
            assertEquals(declared.get(name), refusal.getMessage(), name);
            assertNull(refusal.getCause(), name + " must not report a cause: nothing failed below it");
        });
    }

    private static void assertEveryOperationRefused(String message, List<Executable> operations) {
        for (Executable operation : operations) {
            KnowledgeStoreException refusal = assertThrows(KnowledgeStoreException.class, operation);
            assertEquals(message, refusal.getMessage());
            assertNull(refusal.getCause());
        }
    }

    private static <S extends AutoCloseable> Executable closed(S store, StoreCall<S> call) {
        assertDoesNotThrow(store::close, "closing a fresh store must succeed");
        assertDoesNotThrow(store::close, "closing a closed store again must succeed");
        return () -> call.apply(store);
    }

    /** Each store class of the module that declares a closed-store message, with that message. */
    private static Map<String, String> closedStoreMessages() throws IOException {
        Map<String, String> messages = new TreeMap<>();
        try (Stream<Path> sources = Files.list(Path.of("src/main/java/com/morpheus/store/sqlite"))) {
            for (Path source : sources.filter(path -> path.getFileName().toString().endsWith("Store.java")).toList()) {
                String text = Files.readString(source);
                Matcher className = STORE_CLASS.matcher(text);
                Matcher message = CLOSED_MESSAGE.matcher(text);
                if (className.find() && message.find()) {
                    messages.put(className.group(1), message.group(1));
                }
            }
        }
        assertTrue(messages.size() >= 12, () -> "the census found too few stores: " + messages.keySet());
        return messages;
    }

    @FunctionalInterface
    private interface StoreCall<S> {
        void apply(S store) throws Throwable;
    }
}
