package com.morpheus.store.sqlite.runtime;

import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.util.Optional;

/**
 * Fails the test that leaves a connection scope attached to its thread, and detaches it so that the next test the pool
 * runs on that thread is not refused for a scope it never opened (AUD-QUA-18). Registered for every test of the module
 * through {@code META-INF/services}, so a new test class is covered without being edited.
 */
public final class NoSqliteScopeLeftBehindExtension implements AfterEachCallback {
    @Override
    public void afterEach(ExtensionContext context) {
        Optional<SqliteConnectionScope> leaked = SqliteConnectionScope.attachedToThisThread();
        if (leaked.isEmpty()) {
            return;
        }
        String detachment = "detached";
        try {
            leaked.get().close();
        } catch (RuntimeException failure) {
            detachment = "could not be detached: " + failure.getMessage();
        }
        throw new AssertionError(context.getDisplayName() + " of " + context.getRequiredTestClass().getSimpleName()
                + " left a SQLite connection scope open on its thread (" + detachment + ")");
    }
}
