package com.morpheus.store.sqlite;

import com.morpheus.application.store.KnowledgeStoreException;

import java.nio.file.Path;

/**
 * Child-process fixture that opens the knowledge store on the database it is given, closes it and exits: {@code 0}
 * when the store opened, {@link #REFUSED} when the store refused with its own exception. Two of these started on one
 * outdated database are two MORPHEUS processes migrating it at once, which two threads of one JVM are not.
 */
public final class FixtureOpenStoreProcess {
    static final int REFUSED = 3;

    private FixtureOpenStoreProcess() {
    }

    public static void main(String[] arguments) {
        System.exit(openThenClose(Path.of(arguments[0])));
    }

    private static int openThenClose(Path database) {
        try (SqliteSpecificationKnowledgeStore ignored = new SqliteSpecificationKnowledgeStore(database)) {
            return 0;
        } catch (KnowledgeStoreException refused) {
            System.err.println(refused.getMessage());
            return REFUSED;
        }
    }
}
