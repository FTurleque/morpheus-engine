package com.morpheus.store.sqlite;

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class SqliteDriverSmokeTest {

    @Test
    void sqliteJdbcCanOpenInMemoryDatabase() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:");
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("select sqlite_version()")) {

            assertNotNull(connection);
            assertFalse(connection.isClosed());
            resultSet.next();
            assertNotNull(resultSet.getString(1));
        }
    }
}
