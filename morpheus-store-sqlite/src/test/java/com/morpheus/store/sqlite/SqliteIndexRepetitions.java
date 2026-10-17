package com.morpheus.store.sqlite;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads the indexes of a database and reports every index, created by a {@code CREATE INDEX} statement, that is
 * neither unique nor partial and whose columns - names, collations and directions, in order - are the leading part of
 * another non-partial index of the same table, whether that other index comes from a statement or from a
 * {@code PRIMARY KEY} or {@code UNIQUE} constraint. The comparison is derived from the database; no index is named.
 */
final class SqliteIndexRepetitions {
    private SqliteIndexRepetitions() {
    }

    static List<String> find(Connection connection) throws SQLException {
        List<String> repetitions = new ArrayList<>();
        for (String table : tables(connection)) {
            List<Index> indexes = indexes(connection, table);
            for (Index candidate : indexes) {
                if (!candidate.createdByStatement() || candidate.unique() || candidate.partial()) {
                    continue;
                }
                for (Index other : indexes) {
                    if (other == candidate || other.partial() || !other.columns().startsWith(candidate.columns())) {
                        continue;
                    }
                    repetitions.add("index " + candidate.name() + " of table " + table + " repeats "
                            + (other.columns().length() == candidate.columns().length() ? "exactly" : "the leading part of")
                            + " index " + other.name() + " (" + other.columns() + ")");
                }
            }
        }
        return List.copyOf(repetitions);
    }

    private static List<String> tables(Connection connection) throws SQLException {
        List<String> tables = new ArrayList<>();
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(
                     "SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%' ORDER BY name")) {
            while (rows.next()) {
                tables.add(rows.getString(1));
            }
        }
        return tables;
    }

    private static List<Index> indexes(Connection connection, String table) throws SQLException {
        List<Index> indexes = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM pragma_index_list(?)")) {
            statement.setString(1, table);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    String name = rows.getString("name");
                    indexes.add(new Index(
                            name,
                            "c".equals(rows.getString("origin")),
                            rows.getInt("unique") == 1,
                            rows.getInt("partial") == 1,
                            columns(connection, name)));
                }
            }
        }
        return indexes;
    }

    private static String columns(Connection connection, String index) throws SQLException {
        StringBuilder columns = new StringBuilder();
        try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM pragma_index_xinfo(?)")) {
            statement.setString(1, index);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    if (rows.getInt("key") == 1) {
                        columns.append(rows.getString("name"))
                                .append(' ').append(rows.getInt("desc") == 1 ? "DESC" : "ASC")
                                .append(' ').append(rows.getString("coll"))
                                .append(',');
                    }
                }
            }
        }
        return columns.toString();
    }

    private record Index(String name, boolean createdByStatement, boolean unique, boolean partial, String columns) {
    }
}
