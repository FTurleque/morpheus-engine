package com.morpheus.store.sqlite;

import com.morpheus.application.store.KnowledgeStoreException;
import com.morpheus.application.store.PortfolioStore;
import com.morpheus.domain.evidence.EvidenceId;
import com.morpheus.domain.identity.DomainIdentity;
import com.morpheus.domain.portfolio.CrossProjectReference;
import com.morpheus.domain.portfolio.CrossProjectReferenceId;
import com.morpheus.domain.portfolio.PortfolioDefinition;
import com.morpheus.domain.portfolio.PortfolioEntityRef;
import com.morpheus.domain.portfolio.PortfolioFreshness;
import com.morpheus.domain.portfolio.PortfolioFreshnessState;
import com.morpheus.domain.portfolio.PortfolioId;
import com.morpheus.domain.portfolio.PortfolioMembership;
import com.morpheus.domain.portfolio.PortfolioMembershipStatus;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.provider.ProviderId;
import com.morpheus.domain.source.SourceLocator;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** SQLite adapter for M23 portfolio intelligence. */
public final class SqlitePortfolioStore implements PortfolioStore, AutoCloseable {
    private final Connection connection;
    private boolean closed;

    public SqlitePortfolioStore(Path databasePath) {
        Objects.requireNonNull(databasePath, "databasePath");
        this.connection = SqliteStoreConnection.openAndMigrate(databasePath, "Cannot initialize SQLite portfolio store");
    }

    @Override
    public synchronized void putPortfolio(PortfolioDefinition portfolio) {
        ensureOpen();
        Objects.requireNonNull(portfolio, "portfolio");
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO portfolios(id, name, created_at, updated_at)
                VALUES (?, ?, ?, ?)
                ON CONFLICT(id) DO UPDATE SET
                    name = excluded.name,
                    updated_at = excluded.updated_at
                """)) {
            statement.setString(1, portfolio.id().toString());
            statement.setString(2, portfolio.name());
            statement.setString(3, portfolio.createdAt().toString());
            statement.setString(4, portfolio.updatedAt().toString());
            statement.executeUpdate();
        } catch (SQLException failure) {
            throw new KnowledgeStoreException("Cannot persist portfolio " + portfolio.id(), failure);
        }
    }

    @Override
    public synchronized Optional<PortfolioDefinition> findPortfolio(PortfolioId portfolioId) {
        ensureOpen();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT id, name, created_at, updated_at FROM portfolios WHERE id = ?")) {
            statement.setString(1, portfolioId.toString());
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(readPortfolio(result)) : Optional.empty();
            }
        } catch (SQLException failure) {
            throw new KnowledgeStoreException("Cannot read portfolio " + portfolioId, failure);
        }
    }

    @Override
    public synchronized List<PortfolioDefinition> listPortfolios() {
        ensureOpen();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT id, name, created_at, updated_at FROM portfolios ORDER BY id");
             ResultSet result = statement.executeQuery()) {
            List<PortfolioDefinition> values = new ArrayList<>();
            while (result.next()) {
                values.add(readPortfolio(result));
            }
            return List.copyOf(values);
        } catch (SQLException failure) {
            throw new KnowledgeStoreException("Cannot list portfolios", failure);
        }
    }

    @Override
    public synchronized void putMembership(PortfolioMembership membership) {
        ensureOpen();
        requirePortfolio(membership.portfolioId());
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO portfolio_memberships(
                    portfolio_id, project_id, display_name,
                    workspace_scheme, workspace_value, repository_scheme, repository_value,
                    provider_ids, status, first_registered_at, last_observed_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(portfolio_id, project_id) DO UPDATE SET
                    display_name = excluded.display_name,
                    workspace_scheme = excluded.workspace_scheme,
                    workspace_value = excluded.workspace_value,
                    repository_scheme = excluded.repository_scheme,
                    repository_value = excluded.repository_value,
                    provider_ids = excluded.provider_ids,
                    status = excluded.status,
                    last_observed_at = excluded.last_observed_at
                """)) {
            statement.setString(1, membership.portfolioId().toString());
            statement.setString(2, membership.projectId().toString());
            statement.setString(3, membership.displayName());
            locator(statement, 4, 5, membership.workspace());
            locator(statement, 6, 7, membership.repository());
            statement.setString(8, encodeProviders(membership.providers()));
            statement.setString(9, membership.status().name());
            statement.setString(10, membership.firstRegisteredAt().toString());
            statement.setString(11, membership.lastObservedAt().toString());
            statement.executeUpdate();
        } catch (SQLException failure) {
            throw new KnowledgeStoreException("Cannot persist portfolio membership", failure);
        }
    }

    @Override
    public synchronized Optional<PortfolioMembership> findMembership(
            PortfolioId portfolioId,
            ProjectSpecificationId projectId) {
        ensureOpen();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT portfolio_id, project_id, display_name,
                       workspace_scheme, workspace_value, repository_scheme, repository_value,
                       provider_ids, status, first_registered_at, last_observed_at
                FROM portfolio_memberships
                WHERE portfolio_id = ? AND project_id = ?
                """)) {
            statement.setString(1, portfolioId.toString());
            statement.setString(2, projectId.toString());
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(readMembership(result)) : Optional.empty();
            }
        } catch (SQLException failure) {
            throw new KnowledgeStoreException("Cannot read portfolio membership", failure);
        }
    }

    @Override
    public synchronized List<PortfolioMembership> listMemberships(PortfolioId portfolioId) {
        ensureOpen();
        requirePortfolio(portfolioId);
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT portfolio_id, project_id, display_name,
                       workspace_scheme, workspace_value, repository_scheme, repository_value,
                       provider_ids, status, first_registered_at, last_observed_at
                FROM portfolio_memberships
                WHERE portfolio_id = ?
                ORDER BY project_id
                """)) {
            statement.setString(1, portfolioId.toString());
            try (ResultSet result = statement.executeQuery()) {
                List<PortfolioMembership> values = new ArrayList<>();
                while (result.next()) {
                    values.add(readMembership(result));
                }
                return List.copyOf(values);
            }
        } catch (SQLException failure) {
            throw new KnowledgeStoreException("Cannot list portfolio memberships", failure);
        }
    }

    @Override
    public synchronized void putReference(CrossProjectReference reference) {
        ensureOpen();
        requireMembership(reference.portfolioId(), reference.source().projectId());
        requireMembership(reference.portfolioId(), reference.target().projectId());
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO portfolio_cross_project_references(
                    id, portfolio_id,
                    source_project_id, source_entity_type, source_entity_id,
                    target_project_id, target_entity_type, target_entity_id,
                    relation, provider_id,
                    source_locator_scheme, source_locator_value, evidence_id, observed_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(id) DO NOTHING
                """)) {
            statement.setString(1, reference.id().toString());
            statement.setString(2, reference.portfolioId().toString());
            statement.setString(3, reference.source().projectId().toString());
            statement.setString(4, reference.source().entityType());
            statement.setString(5, reference.source().entityId().toString());
            statement.setString(6, reference.target().projectId().toString());
            statement.setString(7, reference.target().entityType());
            statement.setString(8, reference.target().entityId().toString());
            statement.setString(9, reference.relation());
            statement.setString(10, reference.providerId().value());
            locator(statement, 11, 12, reference.sourceLocator());
            nullable(statement, 13, reference.evidenceId().map(EvidenceId::toString));
            statement.setString(14, reference.observedAt().toString());
            statement.executeUpdate();
        } catch (SQLException failure) {
            throw new KnowledgeStoreException("Cannot persist cross-project reference", failure);
        }
    }

    @Override
    public synchronized Optional<CrossProjectReference> findReference(CrossProjectReferenceId referenceId) {
        ensureOpen();
        try (PreparedStatement statement = connection.prepareStatement(referenceSelect() + " WHERE id = ?")) {
            statement.setString(1, referenceId.toString());
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(readReference(result)) : Optional.empty();
            }
        } catch (SQLException failure) {
            throw new KnowledgeStoreException("Cannot read cross-project reference", failure);
        }
    }

    @Override
    public synchronized List<CrossProjectReference> listReferences(PortfolioId portfolioId) {
        ensureOpen();
        requirePortfolio(portfolioId);
        return queryReferences(referenceSelect() + " WHERE portfolio_id = ? ORDER BY id", statement -> {
            statement.setString(1, portfolioId.toString());
        });
    }

    @Override
    public synchronized List<CrossProjectReference> outgoing(
            PortfolioId portfolioId,
            PortfolioEntityRef source) {
        ensureOpen();
        return queryReferences(referenceSelect() + """
                 WHERE portfolio_id = ?
                   AND source_project_id = ?
                   AND source_entity_type = ?
                   AND source_entity_id = ?
                 ORDER BY id
                """, statement -> bindEntity(statement, portfolioId, source));
    }

    @Override
    public synchronized List<CrossProjectReference> incoming(
            PortfolioId portfolioId,
            PortfolioEntityRef target) {
        ensureOpen();
        return queryReferences(referenceSelect() + """
                 WHERE portfolio_id = ?
                   AND target_project_id = ?
                   AND target_entity_type = ?
                   AND target_entity_id = ?
                 ORDER BY id
                """, statement -> bindEntity(statement, portfolioId, target));
    }

    @Override
    public synchronized void putFreshness(PortfolioFreshness freshness) {
        ensureOpen();
        requireMembership(freshness.portfolioId(), freshness.projectId());
        Optional<PortfolioFreshness> previous = findFreshness(freshness.portfolioId(), freshness.projectId());
        if (previous.isPresent() && freshness.observedAt().isBefore(previous.orElseThrow().observedAt())) {
            throw new IllegalArgumentException("freshness observation must not move backwards");
        }
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO portfolio_freshness(portfolio_id, project_id, state, observed_at, revision, explanation)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT(portfolio_id, project_id) DO UPDATE SET
                    state = excluded.state,
                    observed_at = excluded.observed_at,
                    revision = excluded.revision,
                    explanation = excluded.explanation
                """)) {
            statement.setString(1, freshness.portfolioId().toString());
            statement.setString(2, freshness.projectId().toString());
            statement.setString(3, freshness.state().name());
            statement.setString(4, freshness.observedAt().toString());
            nullable(statement, 5, freshness.revision());
            nullable(statement, 6, freshness.explanation());
            statement.executeUpdate();
        } catch (SQLException failure) {
            throw new KnowledgeStoreException("Cannot persist portfolio freshness", failure);
        }
    }

    @Override
    public synchronized Optional<PortfolioFreshness> findFreshness(
            PortfolioId portfolioId,
            ProjectSpecificationId projectId) {
        ensureOpen();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT portfolio_id, project_id, state, observed_at, revision, explanation
                FROM portfolio_freshness
                WHERE portfolio_id = ? AND project_id = ?
                """)) {
            statement.setString(1, portfolioId.toString());
            statement.setString(2, projectId.toString());
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(readFreshness(result)) : Optional.empty();
            }
        } catch (SQLException failure) {
            throw new KnowledgeStoreException("Cannot read portfolio freshness", failure);
        }
    }

    @Override
    public synchronized List<PortfolioFreshness> listFreshness(PortfolioId portfolioId) {
        ensureOpen();
        requirePortfolio(portfolioId);
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT portfolio_id, project_id, state, observed_at, revision, explanation
                FROM portfolio_freshness
                WHERE portfolio_id = ?
                ORDER BY project_id
                """)) {
            statement.setString(1, portfolioId.toString());
            try (ResultSet result = statement.executeQuery()) {
                List<PortfolioFreshness> values = new ArrayList<>();
                while (result.next()) {
                    values.add(readFreshness(result));
                }
                return List.copyOf(values);
            }
        } catch (SQLException failure) {
            throw new KnowledgeStoreException("Cannot list portfolio freshness", failure);
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
            throw new KnowledgeStoreException("Cannot close SQLite portfolio store", failure);
        }
    }

    private PortfolioDefinition readPortfolio(ResultSet result) throws SQLException {
        return new PortfolioDefinition(
                PortfolioId.parse(result.getString("id")),
                result.getString("name"),
                Instant.parse(result.getString("created_at")),
                Instant.parse(result.getString("updated_at")));
    }

    private PortfolioMembership readMembership(ResultSet result) throws SQLException {
        return new PortfolioMembership(
                PortfolioId.parse(result.getString("portfolio_id")),
                ProjectSpecificationId.parse(result.getString("project_id")),
                result.getString("display_name"),
                locator(result, "workspace_scheme", "workspace_value"),
                locator(result, "repository_scheme", "repository_value"),
                decodeProviders(result.getString("provider_ids")),
                PortfolioMembershipStatus.valueOf(result.getString("status")),
                Instant.parse(result.getString("first_registered_at")),
                Instant.parse(result.getString("last_observed_at")));
    }

    private CrossProjectReference readReference(ResultSet result) throws SQLException {
        return new CrossProjectReference(
                CrossProjectReferenceId.parse(result.getString("id")),
                PortfolioId.parse(result.getString("portfolio_id")),
                new PortfolioEntityRef(
                        ProjectSpecificationId.parse(result.getString("source_project_id")),
                        result.getString("source_entity_type"),
                        DomainIdentity.parse(result.getString("source_entity_id"))),
                new PortfolioEntityRef(
                        ProjectSpecificationId.parse(result.getString("target_project_id")),
                        result.getString("target_entity_type"),
                        DomainIdentity.parse(result.getString("target_entity_id"))),
                result.getString("relation"),
                new ProviderId(result.getString("provider_id")),
                locator(result, "source_locator_scheme", "source_locator_value"),
                optional(result.getString("evidence_id")).map(EvidenceId::parse),
                Instant.parse(result.getString("observed_at")));
    }

    private PortfolioFreshness readFreshness(ResultSet result) throws SQLException {
        return new PortfolioFreshness(
                PortfolioId.parse(result.getString("portfolio_id")),
                ProjectSpecificationId.parse(result.getString("project_id")),
                PortfolioFreshnessState.valueOf(result.getString("state")),
                Instant.parse(result.getString("observed_at")),
                optional(result.getString("revision")),
                optional(result.getString("explanation")));
    }

    private String referenceSelect() {
        return """
                SELECT id, portfolio_id,
                       source_project_id, source_entity_type, source_entity_id,
                       target_project_id, target_entity_type, target_entity_id,
                       relation, provider_id,
                       source_locator_scheme, source_locator_value, evidence_id, observed_at
                FROM portfolio_cross_project_references
                """;
    }

    private List<CrossProjectReference> queryReferences(String sql, StatementBinder binder) {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            binder.bind(statement);
            try (ResultSet result = statement.executeQuery()) {
                List<CrossProjectReference> values = new ArrayList<>();
                while (result.next()) {
                    values.add(readReference(result));
                }
                return values.stream().sorted().toList();
            }
        } catch (SQLException failure) {
            throw new KnowledgeStoreException("Cannot query cross-project references", failure);
        }
    }

    private void bindEntity(PreparedStatement statement, PortfolioId portfolioId, PortfolioEntityRef entity)
            throws SQLException {
        statement.setString(1, portfolioId.toString());
        statement.setString(2, entity.projectId().toString());
        statement.setString(3, entity.entityType());
        statement.setString(4, entity.entityId().toString());
    }

    private void requirePortfolio(PortfolioId portfolioId) {
        if (findPortfolio(portfolioId).isEmpty()) {
            throw new IllegalArgumentException("unknown portfolio: " + portfolioId);
        }
    }

    private void requireMembership(PortfolioId portfolioId, ProjectSpecificationId projectId) {
        if (findMembership(portfolioId, projectId).isEmpty()) {
            throw new IllegalArgumentException("project is not a portfolio member: " + projectId);
        }
    }

    private void ensureOpen() {
        if (closed) {
            throw new KnowledgeStoreException("SQLite portfolio store is closed");
        }
    }

    private static void locator(
            PreparedStatement statement,
            int schemeIndex,
            int valueIndex,
            Optional<SourceLocator> locator) throws SQLException {
        nullable(statement, schemeIndex, locator.map(SourceLocator::scheme));
        nullable(statement, valueIndex, locator.map(SourceLocator::value));
    }

    private static Optional<SourceLocator> locator(ResultSet result, String scheme, String value) throws SQLException {
        String schemeValue = result.getString(scheme);
        String locatorValue = result.getString(value);
        if (schemeValue == null || locatorValue == null) {
            return Optional.empty();
        }
        return Optional.of(new SourceLocator(schemeValue, locatorValue));
    }

    private static String encodeProviders(Set<ProviderId> providers) {
        return providers.stream().sorted().map(ProviderId::value).collect(Collectors.joining("\n"));
    }

    private static Set<ProviderId> decodeProviders(String value) {
        if (value == null || value.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(value.split("\\n"))
                .filter(item -> !item.isBlank())
                .map(ProviderId::new)
                .collect(Collectors.toUnmodifiableSet());
    }

    private static Optional<String> optional(String value) {
        return value == null ? Optional.empty() : Optional.of(value);
    }

    private static void nullable(PreparedStatement statement, int index, Optional<String> value) throws SQLException {
        if (value.isPresent()) {
            statement.setString(index, value.orElseThrow());
        } else {
            statement.setNull(index, java.sql.Types.VARCHAR);
        }
    }


    @FunctionalInterface
    private interface StatementBinder {
        void bind(PreparedStatement statement) throws SQLException;
    }
}
