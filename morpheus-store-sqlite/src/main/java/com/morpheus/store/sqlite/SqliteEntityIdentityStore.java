package com.morpheus.store.sqlite;

import com.morpheus.application.identity.EntityIdentityBinding;
import com.morpheus.application.identity.EntityIdentityKey;
import com.morpheus.application.identity.EntityIdentityStore;
import com.morpheus.application.identity.IdentityCollisionException;
import com.morpheus.application.store.KnowledgeStoreException;
import com.morpheus.domain.identity.DomainIdentity;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;
import java.util.Optional;

/** SQLite adapter for persistent provider-scoped entity identity bindings. */
public final class SqliteEntityIdentityStore implements EntityIdentityStore, AutoCloseable {
    private final Connection connection;
    private boolean closed;

    public SqliteEntityIdentityStore(Path databasePath) {
        Objects.requireNonNull(databasePath, "databasePath");
        this.connection = SqliteStoreConnection.openAndMigrate(
                databasePath, "Cannot initialize SQLite entity identity store", SqliteEntityIdentityStore::configure);
    }

    @Override
    public synchronized Optional<DomainIdentity> find(EntityIdentityKey key) {
        ensureOpen();
        Objects.requireNonNull(key, "key");
        try {
            return findInternal(key);
        } catch (SQLException exception) {
            throw new KnowledgeStoreException("Cannot read entity identity binding: " + key, exception);
        }
    }

    @Override
    public synchronized void put(EntityIdentityBinding binding) {
        ensureOpen();
        Objects.requireNonNull(binding, "binding");
        try {
            Optional<DomainIdentity> existing = findInternal(binding.key());
            if (existing.isPresent()) {
                ensureSameIdentity(binding, existing.orElseThrow());
                return;
            }

            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO entity_identity_bindings(provider_id, entity_type, external_id, domain_identity)
                    VALUES (?, ?, ?, ?)
                    """)) {
                statement.setString(1, binding.key().providerId().value());
                statement.setString(2, binding.key().entityType());
                statement.setString(3, binding.key().externalId());
                statement.setString(4, binding.identity().toString());
                statement.executeUpdate();
            } catch (SQLException insertionFailure) {
                // Another connection/process may have won the primary-key race.
                Optional<DomainIdentity> winner = findInternal(binding.key());
                if (winner.isPresent()) {
                    ensureSameIdentity(binding, winner.orElseThrow());
                    return;
                }
                throw insertionFailure;
            }
        } catch (IdentityCollisionException collision) {
            throw collision;
        } catch (SQLException exception) {
            throw new KnowledgeStoreException("Cannot store entity identity binding: " + binding.key(), exception);
        }
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        try {
            connection.close();
            closed = true;
        } catch (SQLException exception) {
            throw new KnowledgeStoreException("Cannot close SQLite entity identity store", exception);
        }
    }

    private Optional<DomainIdentity> findInternal(EntityIdentityKey key) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT domain_identity
                FROM entity_identity_bindings
                WHERE provider_id = ? AND entity_type = ? AND external_id = ?
                """)) {
            statement.setString(1, key.providerId().value());
            statement.setString(2, key.entityType());
            statement.setString(3, key.externalId());
            try (ResultSet result = statement.executeQuery()) {
                return result.next()
                        ? Optional.of(DomainIdentity.parse(result.getString("domain_identity")))
                        : Optional.empty();
            }
        }
    }

    private void ensureSameIdentity(EntityIdentityBinding binding, DomainIdentity existing) {
        if (!existing.equals(binding.identity())) {
            throw new IdentityCollisionException(
                    "external identity key already belongs to another MORPHEUS identity: " + binding.key());
        }
    }

    private static void configure(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
            statement.execute("PRAGMA busy_timeout = 5000");
        }
    }

    private void ensureOpen() {
        if (closed) {
            throw new KnowledgeStoreException("SQLite entity identity store is closed");
        }
    }

    private void closeQuietly(Connection connection) {
        if (connection == null) {
            return;
        }
        try {
            connection.close();
        } catch (SQLException ignored) {
            // Preserve the initialization failure.
        }
    }
}
