package com.morpheus.store.sqlite;

import com.morpheus.application.policy.PolicyConfiguration;
import com.morpheus.application.policy.PolicyConflictException;
import com.morpheus.application.policy.PolicyIds;
import com.morpheus.application.policy.PolicyPack;
import com.morpheus.application.policy.PolicyPackCodec;
import com.morpheus.application.policy.PolicyScope;
import com.morpheus.application.store.KnowledgeStoreException;
import com.morpheus.application.store.PolicyPackStore;
import com.morpheus.domain.identity.DomainIdentity;
import com.morpheus.domain.portfolio.PortfolioId;
import com.morpheus.domain.project.ProjectSpecificationId;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** SQLite V015 adapter for versioned policy packs, activations, overrides and immutable audit. */
public final class SqlitePolicyPackStore implements PolicyPackStore, AutoCloseable {
    private final Connection connection;
    private final PolicyPackCodec codec = new PolicyPackCodec();
    private boolean closed;

    public SqlitePolicyPackStore(Path databasePath) {
        Objects.requireNonNull(databasePath, "databasePath");
        Connection opened = null;
        try {
            opened = SqliteDatabaseSecurity.open(databasePath);
            new SqliteSchemaManager().migrate(opened);
            this.connection = opened;
        } catch (SQLException | RuntimeException failure) {
            closeQuietly(opened);
            if (failure instanceof KnowledgeStoreException knowledgeStoreException) {
                throw knowledgeStoreException;
            }
            throw new KnowledgeStoreException("Cannot initialize SQLite policy-pack store", failure);
        }
    }

    @Override
    public synchronized void create(
            PolicyPack.Definition definition,
            PolicyPack.Version initialVersion,
            PolicyConfiguration.AuditRecord audit) {
        ensureOpen();
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(initialVersion, "initialVersion");
        Objects.requireNonNull(audit, "audit");
        if (!definition.id().equals(initialVersion.packId()) || !definition.id().equals(audit.packId())) {
            throw new IllegalArgumentException("policy create identity mismatch");
        }
        if (findDefinition(definition.id()).isPresent()) {
            throw new PolicyConflictException("policy pack already exists: " + definition.id());
        }
        transaction(() -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO policy_packs(id, name, revision, latest_version_number, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """)) {
                bindDefinition(statement, definition);
                statement.executeUpdate();
            }
            insertVersion(initialVersion);
            insertAudit(audit);
            return null;
        }, "Cannot persist policy pack " + definition.id());
    }

    @Override
    public synchronized Optional<PolicyPack.Definition> findDefinition(PolicyIds.PackId id) {
        ensureOpen();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT id, name, revision, latest_version_number, created_at, updated_at
                FROM policy_packs WHERE id = ?
                """)) {
            statement.setString(1, id.toString());
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(readDefinition(result)) : Optional.empty();
            }
        } catch (SQLException failure) {
            throw new KnowledgeStoreException("Cannot read policy pack " + id, failure);
        }
    }

    @Override
    public synchronized List<PolicyPack.Definition> listDefinitions() {
        ensureOpen();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT id, name, revision, latest_version_number, created_at, updated_at
                FROM policy_packs ORDER BY id
                """)) {
            try (ResultSet result = statement.executeQuery()) {
                List<PolicyPack.Definition> values = new ArrayList<>();
                while (result.next()) {
                    values.add(readDefinition(result));
                }
                return List.copyOf(values);
            }
        } catch (SQLException failure) {
            throw new KnowledgeStoreException("Cannot list policy packs", failure);
        }
    }

    @Override
    public synchronized Optional<PolicyPack.Version> findVersion(
            PolicyIds.PackId packId,
            PolicyIds.VersionId versionId) {
        ensureOpen();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT encoded_version FROM policy_pack_versions
                WHERE pack_id = ? AND version_id = ?
                """)) {
            statement.setString(1, packId.toString());
            statement.setString(2, versionId.toString());
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return Optional.empty();
                }
                PolicyPack.Version version = codec.decode(result.getString("encoded_version"));
                requireVersionIdentity(version, packId, versionId);
                return Optional.of(version);
            }
        } catch (SQLException failure) {
            throw new KnowledgeStoreException("Cannot read policy version " + versionId, failure);
        }
    }

    @Override
    public synchronized List<PolicyPack.Version> listVersions(PolicyIds.PackId packId) {
        ensureOpen();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT version_id, encoded_version FROM policy_pack_versions
                WHERE pack_id = ? ORDER BY version_number
                """)) {
            statement.setString(1, packId.toString());
            try (ResultSet result = statement.executeQuery()) {
                List<PolicyPack.Version> values = new ArrayList<>();
                while (result.next()) {
                    PolicyIds.VersionId versionId = PolicyIds.VersionId.parse(result.getString("version_id"));
                    PolicyPack.Version version = codec.decode(result.getString("encoded_version"));
                    requireVersionIdentity(version, packId, versionId);
                    values.add(version);
                }
                return List.copyOf(values);
            }
        } catch (SQLException failure) {
            throw new KnowledgeStoreException("Cannot list policy versions " + packId, failure);
        }
    }

    @Override
    public synchronized PolicyPack.Definition compareAndSetDefinition(
            PolicyIds.PackId packId,
            long expectedRevision,
            PolicyPack.Definition replacement,
            PolicyPack.Version newVersion,
            PolicyConfiguration.AuditRecord audit) {
        ensureOpen();
        if (!replacement.id().equals(packId) || !newVersion.packId().equals(packId) || !audit.packId().equals(packId)) {
            throw new IllegalArgumentException("policy update identity mismatch");
        }
        Integer changed = transaction(() -> {
            int updated;
            try (PreparedStatement statement = connection.prepareStatement("""
                    UPDATE policy_packs
                    SET name = ?, revision = ?, latest_version_number = ?, updated_at = ?
                    WHERE id = ? AND revision = ?
                    """)) {
                statement.setString(1, replacement.name());
                statement.setLong(2, replacement.revision());
                statement.setLong(3, replacement.latestVersionNumber());
                statement.setString(4, replacement.updatedAt().toString());
                statement.setString(5, packId.toString());
                statement.setLong(6, expectedRevision);
                updated = statement.executeUpdate();
            }
            if (updated == 1) {
                insertVersion(newVersion);
                insertAudit(audit);
            }
            return updated;
        }, "Cannot update policy pack " + packId);
        if (changed != 1) {
            PolicyPack.Definition current = findDefinition(packId)
                    .orElseThrow(() -> new IllegalArgumentException("unknown policy pack: " + packId));
            throw stale("policy pack", expectedRevision, current.revision());
        }
        return replacement;
    }

    @Override
    public synchronized Optional<PolicyConfiguration.Activation> findActivation(
            PolicyScope scope,
            PolicyIds.PackId packId) {
        ensureOpen();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT scope_kind, scope_id, pack_id, version_id, revision, actor, updated_at
                FROM policy_pack_activations
                WHERE scope_kind = ? AND scope_id = ? AND pack_id = ?
                """)) {
            bindScope(statement, 1, scope);
            statement.setString(3, packId.toString());
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(readActivation(result)) : Optional.empty();
            }
        } catch (SQLException failure) {
            throw new KnowledgeStoreException("Cannot read policy activation", failure);
        }
    }

    @Override
    public synchronized List<PolicyConfiguration.Activation> listActivations(PolicyScope scope) {
        ensureOpen();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT scope_kind, scope_id, pack_id, version_id, revision, actor, updated_at
                FROM policy_pack_activations
                WHERE scope_kind = ? AND scope_id = ?
                ORDER BY pack_id
                """)) {
            bindScope(statement, 1, scope);
            try (ResultSet result = statement.executeQuery()) {
                List<PolicyConfiguration.Activation> values = new ArrayList<>();
                while (result.next()) {
                    values.add(readActivation(result));
                }
                return List.copyOf(values);
            }
        } catch (SQLException failure) {
            throw new KnowledgeStoreException("Cannot list policy activations", failure);
        }
    }

    @Override
    public synchronized PolicyConfiguration.Activation compareAndSetActivation(
            PolicyScope scope,
            PolicyIds.PackId packId,
            long expectedRevision,
            PolicyConfiguration.Activation replacement,
            PolicyConfiguration.AuditRecord audit) {
        ensureOpen();
        if (!replacement.scope().equals(scope) || !replacement.packId().equals(packId)
                || replacement.revision() != expectedRevision + 1) {
            throw new IllegalArgumentException("policy activation replacement mismatch");
        }
        Integer changed = transaction(() -> {
            int updated;
            if (expectedRevision == 0) {
                if (findActivation(scope, packId).isPresent()) {
                    return 0;
                }
                try (PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO policy_pack_activations(
                            scope_kind, scope_id, pack_id, version_id, revision, actor, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?)
                        """)) {
                    bindScope(statement, 1, scope);
                    statement.setString(3, packId.toString());
                    statement.setString(4, replacement.versionId().toString());
                    statement.setLong(5, replacement.revision());
                    statement.setString(6, replacement.actor());
                    statement.setString(7, replacement.updatedAt().toString());
                    updated = statement.executeUpdate();
                }
            } else {
                try (PreparedStatement statement = connection.prepareStatement("""
                        UPDATE policy_pack_activations
                        SET version_id = ?, revision = ?, actor = ?, updated_at = ?
                        WHERE scope_kind = ? AND scope_id = ? AND pack_id = ? AND revision = ?
                        """)) {
                    statement.setString(1, replacement.versionId().toString());
                    statement.setLong(2, replacement.revision());
                    statement.setString(3, replacement.actor());
                    statement.setString(4, replacement.updatedAt().toString());
                    statement.setString(5, scopeKind(scope));
                    statement.setString(6, scopeId(scope));
                    statement.setString(7, packId.toString());
                    statement.setLong(8, expectedRevision);
                    updated = statement.executeUpdate();
                }
            }
            if (updated == 1) {
                insertAudit(audit);
            }
            return updated;
        }, "Cannot update policy activation");
        if (changed != 1) {
            long actual = findActivation(scope, packId).map(PolicyConfiguration.Activation::revision).orElse(0L);
            throw stale("policy activation", expectedRevision, actual);
        }
        return replacement;
    }

    @Override
    public synchronized void removeActivation(
            PolicyScope scope,
            PolicyIds.PackId packId,
            long expectedRevision,
            PolicyConfiguration.AuditRecord audit) {
        ensureOpen();
        Integer changed = transaction(() -> {
            int deleted;
            try (PreparedStatement statement = connection.prepareStatement("""
                    DELETE FROM policy_pack_activations
                    WHERE scope_kind = ? AND scope_id = ? AND pack_id = ? AND revision = ?
                    """)) {
                bindScope(statement, 1, scope);
                statement.setString(3, packId.toString());
                statement.setLong(4, expectedRevision);
                deleted = statement.executeUpdate();
            }
            if (deleted == 1) {
                insertAudit(audit);
            }
            return deleted;
        }, "Cannot remove policy activation");
        if (changed != 1) {
            long actual = findActivation(scope, packId).map(PolicyConfiguration.Activation::revision).orElse(0L);
            throw stale("policy activation", expectedRevision, actual);
        }
    }

    @Override
    public synchronized Optional<PolicyConfiguration.Override> findOverride(
            PolicyScope scope,
            PolicyIds.PackId packId,
            PolicyIds.RuleId ruleId) {
        ensureOpen();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT scope_kind, scope_id, pack_id, rule_id, mode, reason, actor, revision, updated_at
                FROM policy_overrides
                WHERE scope_kind = ? AND scope_id = ? AND pack_id = ? AND rule_id = ?
                """)) {
            bindScope(statement, 1, scope);
            statement.setString(3, packId.toString());
            statement.setString(4, ruleId.toString());
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(readOverride(result)) : Optional.empty();
            }
        } catch (SQLException failure) {
            throw new KnowledgeStoreException("Cannot read policy override", failure);
        }
    }

    @Override
    public synchronized List<PolicyConfiguration.Override> listOverrides(PolicyScope scope) {
        ensureOpen();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT scope_kind, scope_id, pack_id, rule_id, mode, reason, actor, revision, updated_at
                FROM policy_overrides
                WHERE scope_kind = ? AND scope_id = ?
                ORDER BY pack_id, rule_id
                """)) {
            bindScope(statement, 1, scope);
            try (ResultSet result = statement.executeQuery()) {
                List<PolicyConfiguration.Override> values = new ArrayList<>();
                while (result.next()) {
                    values.add(readOverride(result));
                }
                return List.copyOf(values);
            }
        } catch (SQLException failure) {
            throw new KnowledgeStoreException("Cannot list policy overrides", failure);
        }
    }

    @Override
    public synchronized PolicyConfiguration.Override compareAndSetOverride(
            PolicyScope scope,
            PolicyIds.PackId packId,
            PolicyIds.RuleId ruleId,
            long expectedRevision,
            PolicyConfiguration.Override replacement,
            PolicyConfiguration.AuditRecord audit) {
        ensureOpen();
        if (!replacement.scope().equals(scope) || !replacement.packId().equals(packId)
                || !replacement.ruleId().equals(ruleId) || replacement.revision() != expectedRevision + 1) {
            throw new IllegalArgumentException("policy override replacement mismatch");
        }
        Integer changed = transaction(() -> {
            int updated;
            if (expectedRevision == 0) {
                if (findOverride(scope, packId, ruleId).isPresent()) {
                    return 0;
                }
                try (PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO policy_overrides(
                            scope_kind, scope_id, pack_id, rule_id, mode, reason, actor, revision, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """)) {
                    bindScope(statement, 1, scope);
                    statement.setString(3, packId.toString());
                    statement.setString(4, ruleId.toString());
                    statement.setString(5, replacement.mode().name());
                    statement.setString(6, replacement.reason());
                    statement.setString(7, replacement.actor());
                    statement.setLong(8, replacement.revision());
                    statement.setString(9, replacement.updatedAt().toString());
                    updated = statement.executeUpdate();
                }
            } else {
                try (PreparedStatement statement = connection.prepareStatement("""
                        UPDATE policy_overrides
                        SET mode = ?, reason = ?, actor = ?, revision = ?, updated_at = ?
                        WHERE scope_kind = ? AND scope_id = ? AND pack_id = ? AND rule_id = ? AND revision = ?
                        """)) {
                    statement.setString(1, replacement.mode().name());
                    statement.setString(2, replacement.reason());
                    statement.setString(3, replacement.actor());
                    statement.setLong(4, replacement.revision());
                    statement.setString(5, replacement.updatedAt().toString());
                    statement.setString(6, scopeKind(scope));
                    statement.setString(7, scopeId(scope));
                    statement.setString(8, packId.toString());
                    statement.setString(9, ruleId.toString());
                    statement.setLong(10, expectedRevision);
                    updated = statement.executeUpdate();
                }
            }
            if (updated == 1) {
                insertAudit(audit);
            }
            return updated;
        }, "Cannot update policy override");
        if (changed != 1) {
            long actual = findOverride(scope, packId, ruleId).map(PolicyConfiguration.Override::revision).orElse(0L);
            throw stale("policy override", expectedRevision, actual);
        }
        return replacement;
    }

    @Override
    public synchronized void removeOverride(
            PolicyScope scope,
            PolicyIds.PackId packId,
            PolicyIds.RuleId ruleId,
            long expectedRevision,
            PolicyConfiguration.AuditRecord audit) {
        ensureOpen();
        Integer changed = transaction(() -> {
            int deleted;
            try (PreparedStatement statement = connection.prepareStatement("""
                    DELETE FROM policy_overrides
                    WHERE scope_kind = ? AND scope_id = ? AND pack_id = ? AND rule_id = ? AND revision = ?
                    """)) {
                bindScope(statement, 1, scope);
                statement.setString(3, packId.toString());
                statement.setString(4, ruleId.toString());
                statement.setLong(5, expectedRevision);
                deleted = statement.executeUpdate();
            }
            if (deleted == 1) {
                insertAudit(audit);
            }
            return deleted;
        }, "Cannot remove policy override");
        if (changed != 1) {
            long actual = findOverride(scope, packId, ruleId).map(PolicyConfiguration.Override::revision).orElse(0L);
            throw stale("policy override", expectedRevision, actual);
        }
    }

    @Override
    public synchronized List<PolicyConfiguration.AuditRecord> listAudit(PolicyIds.PackId packId) {
        ensureOpen();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT id, action, pack_id, version_id, rule_id, scope_kind, scope_id, actor, reason, at
                FROM policy_audit WHERE pack_id = ? ORDER BY at, id
                """)) {
            statement.setString(1, packId.toString());
            try (ResultSet result = statement.executeQuery()) {
                List<PolicyConfiguration.AuditRecord> values = new ArrayList<>();
                while (result.next()) {
                    values.add(readAudit(result));
                }
                return List.copyOf(values);
            }
        } catch (SQLException failure) {
            throw new KnowledgeStoreException("Cannot list policy audit", failure);
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
        } catch (SQLException failure) {
            throw new KnowledgeStoreException("Cannot close SQLite policy-pack store", failure);
        }
    }

    private void bindDefinition(PreparedStatement statement, PolicyPack.Definition definition) throws SQLException {
        statement.setString(1, definition.id().toString());
        statement.setString(2, definition.name());
        statement.setLong(3, definition.revision());
        statement.setLong(4, definition.latestVersionNumber());
        statement.setString(5, definition.createdAt().toString());
        statement.setString(6, definition.updatedAt().toString());
    }

    private PolicyPack.Definition readDefinition(ResultSet result) throws SQLException {
        return new PolicyPack.Definition(
                PolicyIds.PackId.parse(result.getString("id")),
                result.getString("name"),
                result.getLong("revision"),
                result.getLong("latest_version_number"),
                Instant.parse(result.getString("created_at")),
                Instant.parse(result.getString("updated_at")));
    }

    private void insertVersion(PolicyPack.Version version) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO policy_pack_versions(pack_id, version_id, version_number, encoded_version, created_at)
                VALUES (?, ?, ?, ?, ?)
                """)) {
            statement.setString(1, version.packId().toString());
            statement.setString(2, version.versionId().toString());
            statement.setLong(3, version.versionNumber());
            statement.setString(4, codec.encode(version));
            statement.setString(5, version.createdAt().toString());
            statement.executeUpdate();
        }
    }

    private PolicyConfiguration.Activation readActivation(ResultSet result) throws SQLException {
        return new PolicyConfiguration.Activation(
                readScope(result.getString("scope_kind"), result.getString("scope_id")),
                PolicyIds.PackId.parse(result.getString("pack_id")),
                PolicyIds.VersionId.parse(result.getString("version_id")),
                result.getLong("revision"),
                result.getString("actor"),
                Instant.parse(result.getString("updated_at")));
    }

    private PolicyConfiguration.Override readOverride(ResultSet result) throws SQLException {
        return new PolicyConfiguration.Override(
                readScope(result.getString("scope_kind"), result.getString("scope_id")),
                PolicyIds.PackId.parse(result.getString("pack_id")),
                PolicyIds.RuleId.parse(result.getString("rule_id")),
                PolicyConfiguration.OverrideMode.valueOf(result.getString("mode")),
                result.getString("reason"),
                result.getString("actor"),
                result.getLong("revision"),
                Instant.parse(result.getString("updated_at")));
    }

    private void insertAudit(PolicyConfiguration.AuditRecord audit) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO policy_audit(
                    id, action, pack_id, version_id, rule_id, scope_kind, scope_id, actor, reason, at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            statement.setString(1, audit.id().toString());
            statement.setString(2, audit.action().name());
            statement.setString(3, audit.packId().toString());
            statement.setString(4, audit.versionId().map(Object::toString).orElse(null));
            statement.setString(5, audit.ruleId().map(Object::toString).orElse(null));
            statement.setString(6, audit.scope().map(this::scopeKind).orElse(null));
            statement.setString(7, audit.scope().map(this::scopeId).orElse(null));
            statement.setString(8, audit.actor());
            statement.setString(9, audit.reason());
            statement.setString(10, audit.at().toString());
            statement.executeUpdate();
        }
    }

    private PolicyConfiguration.AuditRecord readAudit(ResultSet result) throws SQLException {
        String version = result.getString("version_id");
        String rule = result.getString("rule_id");
        String scopeKind = result.getString("scope_kind");
        String scopeId = result.getString("scope_id");
        return new PolicyConfiguration.AuditRecord(
                DomainIdentity.parse(result.getString("id")),
                PolicyConfiguration.AuditAction.valueOf(result.getString("action")),
                PolicyIds.PackId.parse(result.getString("pack_id")),
                Optional.ofNullable(version).map(PolicyIds.VersionId::parse),
                Optional.ofNullable(rule).map(PolicyIds.RuleId::parse),
                scopeKind == null ? Optional.empty() : Optional.of(readScope(scopeKind, scopeId)),
                result.getString("actor"),
                result.getString("reason"),
                Instant.parse(result.getString("at")));
    }

    private void requireVersionIdentity(
            PolicyPack.Version version,
            PolicyIds.PackId packId,
            PolicyIds.VersionId versionId) {
        if (!version.packId().equals(packId) || !version.versionId().equals(versionId)) {
            throw new KnowledgeStoreException("policy version columns disagree with encoded payload");
        }
    }

    private void bindScope(PreparedStatement statement, int start, PolicyScope scope) throws SQLException {
        statement.setString(start, scopeKind(scope));
        statement.setString(start + 1, scopeId(scope));
    }

    private String scopeKind(PolicyScope scope) {
        return scope.type();
    }

    private String scopeId(PolicyScope scope) {
        return scope.identity();
    }

    private PolicyScope readScope(String kind, String id) {
        return switch (kind) {
            case "PROJECT" -> new PolicyScope.Project(ProjectSpecificationId.parse(id));
            case "PORTFOLIO" -> new PolicyScope.Portfolio(PortfolioId.parse(id));
            default -> throw new KnowledgeStoreException("unsupported policy scope kind: " + kind);
        };
    }

    private PolicyConflictException stale(String target, long expected, long actual) {
        return new PolicyConflictException("stale " + target + " revision: expected " + expected + " but current is " + actual);
    }

    private <T> T transaction(SqlWork<T> work, String message) {
        final boolean previousAutoCommit;
        try {
            previousAutoCommit = connection.getAutoCommit();
        } catch (SQLException failure) {
            throw new KnowledgeStoreException("Cannot inspect SQLite auto-commit mode", failure);
        }
        try {
            connection.setAutoCommit(false);
            T value = work.run();
            connection.commit();
            return value;
        } catch (SQLException | RuntimeException failure) {
            rollbackQuietly();
            if (failure instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new KnowledgeStoreException(message, failure);
        } finally {
            try {
                if (!connection.isClosed()) {
                    connection.setAutoCommit(previousAutoCommit);
                }
            } catch (SQLException failure) {
                throw new KnowledgeStoreException("Cannot restore SQLite auto-commit mode", failure);
            }
        }
    }

    private void rollbackQuietly() {
        try {
            connection.rollback();
        } catch (SQLException ignored) {
            // Preserve the original failure.
        }
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("SQLite policy-pack store is closed");
        }
    }

    private static void closeQuietly(Connection connection) {
        if (connection == null) {
            return;
        }
        try {
            connection.close();
        } catch (SQLException ignored) {
            // Preserve initialization failure.
        }
    }

    @FunctionalInterface
    private interface SqlWork<T> {
        T run() throws SQLException;
    }
}