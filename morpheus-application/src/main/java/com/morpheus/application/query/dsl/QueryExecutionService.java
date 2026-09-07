package com.morpheus.application.query.dsl;

import com.morpheus.application.store.KnowledgeStoreException;
import com.morpheus.application.store.PortfolioStore;
import com.morpheus.application.store.SnapshotBusinessContent;
import com.morpheus.application.store.SnapshotBusinessContentStore;
import com.morpheus.application.store.SpecificationKnowledgeStore;
import com.morpheus.application.store.VersionedRequirementStore;
import com.morpheus.domain.portfolio.CrossProjectReference;
import com.morpheus.domain.portfolio.PortfolioMembership;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.temporal.TemporalState;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/** Provider-neutral deterministic query engine over published MORPHEUS facts. */
public final class QueryExecutionService {
    private final SpecificationKnowledgeStore snapshotStore;
    private final VersionedRequirementStore requirementStore;
    private final SnapshotBusinessContentStore contentStore;
    private final PortfolioStore portfolioStore;
    private final QueryValidator validator;
    private final QueryRowMapper rowMapper;
    private final QueryRowOperations rowOperations;

    public QueryExecutionService(
            SpecificationKnowledgeStore snapshotStore,
            VersionedRequirementStore requirementStore,
            SnapshotBusinessContentStore contentStore,
            PortfolioStore portfolioStore) {
        this(snapshotStore, requirementStore, contentStore, portfolioStore, new QueryValidator());
    }

    public QueryExecutionService(
            SpecificationKnowledgeStore snapshotStore,
            VersionedRequirementStore requirementStore,
            SnapshotBusinessContentStore contentStore,
            PortfolioStore portfolioStore,
            QueryValidator validator) {
        this.snapshotStore = Objects.requireNonNull(snapshotStore, "snapshotStore");
        this.requirementStore = Objects.requireNonNull(requirementStore, "requirementStore");
        this.contentStore = Objects.requireNonNull(contentStore, "contentStore");
        this.portfolioStore = Objects.requireNonNull(portfolioStore, "portfolioStore");
        this.validator = Objects.requireNonNull(validator, "validator");
        this.rowMapper = new QueryRowMapper();
        this.rowOperations = new QueryRowOperations();
    }

    public QueryResult execute(QueryDefinition query) {
        MaterializedRows materialized = materializeRows(query);
        int totalMatches = materialized.matches().size();
        int from = Math.min(query.page().offset(), totalMatches);
        int to = (int) Math.min((long) from + query.page().limit(), totalMatches);
        List<QueryRow> page = materialized.matches().subList(from, to).stream()
                .map(row -> row.project(materialized.columns()))
                .toList();
        return new QueryResult(query, materialized.columns(), page, totalMatches, to < totalMatches);
    }

    /**
     * Materializes one complete, bounded query view from a single source/filter/sort pass.
     *
     * <p>The caller chooses a hard row ceiling. If the filtered result exceeds that ceiling the method fails
     * before projecting the complete result, so export-style consumers can preserve their own tighter budgets
     * without re-running paged queries or allocating an over-budget projection.</p>
     */
    public QueryMaterializedView materializeComplete(QueryDefinition query, int maximumRows) {
        if (maximumRows < 1 || maximumRows > QueryBudgets.MAX_SOURCE_ROWS) {
            throw new IllegalArgumentException(
                    "maximumRows must be between 1 and " + QueryBudgets.MAX_SOURCE_ROWS);
        }
        MaterializedRows materialized = materializeRows(query);
        int totalMatches = materialized.matches().size();
        if (totalMatches > maximumRows) {
            throw new QueryMaterializationLimitException(maximumRows, totalMatches);
        }
        List<QueryRow> projected = materialized.matches().stream()
                .map(row -> row.project(materialized.columns()))
                .toList();
        return new QueryMaterializedView(query, materialized.columns(), projected);
    }

    private MaterializedRows materializeRows(QueryDefinition query) {
        Objects.requireNonNull(query, "query");
        validator.requireValid(query);

        List<QueryRow> source = sourceRows(query);
        List<QueryRow> matches = source.stream()
                .filter(row -> query.filter().map(filter -> rowOperations.matches(row, filter)).orElse(true))
                .sorted(rowOperations.comparator(query))
                .toList();
        return new MaterializedRows(matches, rowOperations.columns(query));
    }

    private List<QueryRow> sourceRows(QueryDefinition query) {
        if (query.scope() instanceof ProjectQueryScope project) {
            return boundedRows(projectRows(project.projectId(), query.entityType()), "$.scope.project");
        }
        PortfolioQueryScope portfolio = (PortfolioQueryScope) query.scope();
        portfolioStore.findPortfolio(portfolio.portfolioId())
                .orElseThrow(() -> new IllegalArgumentException("unknown portfolio: " + portfolio.portfolioId()));
        List<PortfolioMembership> memberships = portfolioStore.listMemberships(portfolio.portfolioId());
        requirePortfolioProjectBudget(memberships.size());
        if (query.entityType() == QueryEntityType.PORTFOLIO_MEMBERSHIP) {
            return boundedRows(memberships.stream()
                    .sorted(Comparator.comparing(PortfolioMembership::projectId))
                    .map(rowMapper::membership)
                    .toList(), "$.source.portfolioMemberships");
        }
        if (query.entityType() == QueryEntityType.PORTFOLIO_REFERENCE) {
            List<CrossProjectReference> references = portfolioStore.listReferences(portfolio.portfolioId());
            requireSourceRowBudget(references.size(), "$.source.portfolioReferences");
            return references.stream()
                    .sorted()
                    .map(rowMapper::reference)
                    .toList();
        }
        List<QueryRow> result = new ArrayList<>();
        memberships.stream()
                .map(PortfolioMembership::projectId)
                .sorted()
                .forEach(projectId -> appendBounded(
                        result,
                        projectRows(projectId, query.entityType()),
                        "$.source.portfolioProjects"));
        return List.copyOf(result);
    }

    private List<QueryRow> projectRows(ProjectSpecificationId projectId, QueryEntityType type) {
        var snapshot = snapshotStore.activeSnapshot(projectId);
        if (snapshot.isEmpty()) {
            return List.of();
        }
        if (type == QueryEntityType.REQUIREMENT) {
            var records = requirementStore.listRequirementVersions(snapshot.get().id());
            requireSourceRowBudget(records.size(), "$.source.requirements");
            return records.stream()
                    .filter(record -> record.entityVersion().temporalState() == TemporalState.CURRENT)
                    .map(record -> rowMapper.requirement(projectId, record.entityVersion().content()))
                    .toList();
        }
        if (type == QueryEntityType.PORTFOLIO_MEMBERSHIP || type == QueryEntityType.PORTFOLIO_REFERENCE) {
            throw new IllegalArgumentException(type + " requires portfolio scope");
        }
        SnapshotBusinessContent content = contentStore.findSnapshotContent(snapshot.get().id())
                .orElseThrow(() -> new KnowledgeStoreException(
                        "published snapshot has no business-content projection: " + snapshot.get().id()));
        return switch (type) {
            case SPECIFICATION -> mapBounded(content.specifications(),
                    item -> rowMapper.specification(projectId, item), "$.source.specifications");
            case SCENARIO -> mapBounded(content.scenarios(),
                    item -> rowMapper.scenario(projectId, item), "$.source.scenarios");
            case CHANGE -> mapBounded(content.changes(),
                    item -> rowMapper.change(projectId, item), "$.source.changes");
            case CONSTRAINT -> mapBounded(content.constraints(),
                    item -> rowMapper.constraint(projectId, item), "$.source.constraints");
            case DESIGN_DECISION -> mapBounded(content.designDecisions(),
                    item -> rowMapper.decision(projectId, item), "$.source.designDecisions");
            case TASK -> mapBounded(content.tasks(),
                    item -> rowMapper.task(projectId, item), "$.source.tasks");
            case ACCEPTANCE_CRITERION -> mapBounded(content.acceptanceCriteria(),
                    item -> rowMapper.acceptance(projectId, item), "$.source.acceptanceCriteria");
            case EVIDENCE -> mapBounded(content.evidence(),
                    item -> rowMapper.evidence(projectId, item), "$.source.evidence");
            case REQUIREMENT, PORTFOLIO_MEMBERSHIP, PORTFOLIO_REFERENCE -> throw new IllegalStateException("handled before switch");
        };
    }

    private <T> List<QueryRow> mapBounded(List<T> source, Function<T, QueryRow> mapper, String path) {
        requireSourceRowBudget(source.size(), path);
        return source.stream().map(mapper).toList();
    }

    private List<QueryRow> boundedRows(List<QueryRow> rows, String path) {
        requireSourceRowBudget(rows.size(), path);
        return rows;
    }

    private void appendBounded(List<QueryRow> target, List<QueryRow> rows, String path) {
        long combined = (long) target.size() + rows.size();
        requireSourceRowBudget(combined, path);
        target.addAll(rows);
    }

    private void requireSourceRowBudget(long rows, String path) {
        if (rows <= QueryBudgets.MAX_SOURCE_ROWS) {
            return;
        }
        throw budgetExceeded(
                path,
                "query source exceeds " + QueryBudgets.MAX_SOURCE_ROWS + " rows before filtering/pagination");
    }

    private void requirePortfolioProjectBudget(long projects) {
        if (projects <= QueryBudgets.MAX_PORTFOLIO_PROJECTS) {
            return;
        }
        throw budgetExceeded(
                "$.scope.portfolio",
                "portfolio query exceeds " + QueryBudgets.MAX_PORTFOLIO_PROJECTS + " projects");
    }

    private QueryValidationException budgetExceeded(String path, String message) {
        QueryDiagnostic diagnostic = new QueryDiagnostic("QUERY_SOURCE_BUDGET_EXCEEDED", path, message);
        return new QueryValidationException(List.of(diagnostic), diagnostic.code() + " at " + path + ": " + message);
    }

    private record MaterializedRows(List<QueryRow> matches, List<String> columns) {
        private MaterializedRows {
            matches = List.copyOf(Objects.requireNonNull(matches, "matches"));
            columns = List.copyOf(Objects.requireNonNull(columns, "columns"));
        }
    }
}
