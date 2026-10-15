package com.morpheus.api;

import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

/**
 * Counts the physical SQLite connections the JDBC driver hands out while one operation runs.
 *
 * <p>The diagnostics of {@code SqliteConnectionScope} only count the connections a scope opens: a store built outside
 * any scope opens one of its own and is invisible to them. The driver sees both, so this stands in front of it for the
 * duration of the operation and gives it back afterwards. Every SQLite connection of the JVM is counted, whichever
 * thread asks for it - an HTTP request is served on another thread than the test's.</p>
 */
final class PhysicalSqliteConnections {
    private static final String SQLITE_URL = "jdbc:sqlite:";

    private PhysicalSqliteConnections() {
    }

    @FunctionalInterface
    interface Operation {
        void run() throws Exception;
    }

    static int openedDuring(Operation operation) throws Exception {
        Driver sqlite = DriverManager.getDriver(SQLITE_URL);
        CountingDriver counting = new CountingDriver(sqlite);
        // The driver manager asks its drivers in the order they registered and keeps the first connection it gets:
        // the counting driver is only asked while the real one is away.
        DriverManager.deregisterDriver(sqlite);
        DriverManager.registerDriver(counting);
        try {
            operation.run();
        } finally {
            DriverManager.deregisterDriver(counting);
            DriverManager.registerDriver(sqlite);
        }
        return counting.opened.get();
    }

    private static final class CountingDriver implements Driver {
        private final Driver sqlite;
        private final AtomicInteger opened = new AtomicInteger();

        private CountingDriver(Driver sqlite) {
            this.sqlite = sqlite;
        }

        @Override
        public Connection connect(String url, Properties info) throws SQLException {
            Connection connection = sqlite.connect(url, info);
            if (connection != null) {
                opened.incrementAndGet();
            }
            return connection;
        }

        @Override
        public boolean acceptsURL(String url) throws SQLException {
            return sqlite.acceptsURL(url);
        }

        @Override
        public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) throws SQLException {
            return sqlite.getPropertyInfo(url, info);
        }

        @Override
        public int getMajorVersion() {
            return sqlite.getMajorVersion();
        }

        @Override
        public int getMinorVersion() {
            return sqlite.getMinorVersion();
        }

        @Override
        public boolean jdbcCompliant() {
            return sqlite.jdbcCompliant();
        }

        @Override
        public Logger getParentLogger() throws SQLFeatureNotSupportedException {
            return sqlite.getParentLogger();
        }
    }
}
