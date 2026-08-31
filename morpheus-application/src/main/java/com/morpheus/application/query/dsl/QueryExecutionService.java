package com.morpheus.application.query.dsl;

import com.morpheus.application.store.KnowledgeStoreException;
import com.morpheus.application.store.PortfolioStore;
import com.morpheus.application.store.SnapshotBusinessContent;
import com.morpheus.application.store.SnapshotBusinessContentStore;
import com.morpheus.application.store.SpecificationKnowledgeStore;
import com.morpheus.application.store.VersionedRequirementStore;
import com.morpheus.domain.acceptance.AcceptanceCriterion;
import com.morpheus.domain.change.ChangeProposal;
import com.morpheus.domain.constraint.Constraint;
import com.morpheus.domain.decision.DesignDecision;
import com.morpheus.domain.evidence.Evidence;
import com.morpheus.domain.portfolio.CrossProjectReference;
import com.morpheus.domain.portfolio.PortfolioMembership;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.requirement.Requirement;
import com.morpheus.domain.scenario.Scenario;
import com.morpheus.domain.specification.Specification;
import com.morpheus.domain.task.ImplementationTask;
import com.morpheus.domain.temporal.TemporalState;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/** Provider-neutral deterministic query engine over published MORPHEUS facts. */
public final class QueryExecutionService {
    private final SpecificationKnowledgeStore snapshotStore;
    private final VersionedRequirementStore requirementStore;
    private final SnapshotBusinessContentStore contentStore;
    private final PortfolioStore portfolioStore;
    private final QueryValidator validator;

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
    }

    public QueryResult execute(QueryDefinition query) {
        Objects.requireNonNull(query, "query");
        validator.requireValid(query);

        List<QueryRow> source = sourceRows(query);
        List<QueryRow> matches = source.stream()
                .filter(row -> query.filter().map(filter -> matches(row, filter)).orElse(true))
                .sorted(comparator(query))
                .toList();

        int totalMatches = matches.size();
        int from = Math.min(query.page().offset(), totalMatches);
        int to = (int) Math.min((long) from + query.page().limit(), totalMatches);
        List<String> columns = columns(query);
        List<QueryRow> page = matches.subList(from, to).stream().map(row -> row.project(columns)).toList();
        return new QueryResult(query, columns, page, totalMatches, to < totalMatches);
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
                    .map(this::membershipRow)
                    .toList(), "$.source.portfolioMemberships");
        }
        if (query.entityType() == QueryEntityType.PORTFOLIO_REFERENCE) {
            List<CrossProjectReference> references = portfolioStore.listReferences(portfolio.portfolioId());
            requireSourceRowBudget(references.size(), "$.source.portfolioReferences");
            return references.stream()
                    .sorted()
                    .map(this::referenceRow)
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
                    .map(record -> requirementRow(projectId, record.entityVersion().content()))
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
                    item -> specificationRow(projectId, item), "$.source.specifications");
            case SCENARIO -> mapBounded(content.scenarios(),
                    item -> scenarioRow(projectId, item), "$.source.scenarios");
            case CHANGE -> mapBounded(content.changes(),
                    item -> changeRow(projectId, item), "$.source.changes");
            case CONSTRAINT -> mapBounded(content.constraints(),
                    item -> constraintRow(projectId, item), "$.source.constraints");
            case DESIGN_DECISION -> mapBounded(content.designDecisions(),
                    item -> decisionRow(projectId, item), "$.source.designDecisions");
            case TASK -> mapBounded(content.tasks(),
                    item -> taskRow(projectId, item), "$.source.tasks");
            case ACCEPTANCE_CRITERION -> mapBounded(content.acceptanceCriteria(),
                    item -> acceptanceRow(projectId, item), "$.source.acceptanceCriteria");
            case EVIDENCE -> mapBounded(content.evidence(),
                    item -> evidenceRow(projectId, item), "$.source.evidence");
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

    private boolean matches(QueryRow row, QueryFilter filter) {
        if (filter instanceof QueryPredicate predicate) {
            return matchesPredicate(row, predicate);
        }
        if (filter instanceof QueryAnd and) {
            return and.children().stream().allMatch(child -> matches(row, child));
        }
        if (filter instanceof QueryOr or) {
            return or.children().stream().anyMatch(child -> matches(row, child));
        }
        return !matches(row, ((QueryNot) filter).child());
    }

    private boolean matchesPredicate(QueryRow row, QueryPredicate predicate) {
        QueryCell cell = row.cell(predicate.field()).orElseGet(() -> new QueryCell(predicate.field(), List.of()));
        if (predicate.operator() == QueryOperator.EXISTS) {
            return !cell.values().isEmpty();
        }
        QueryFieldType fieldType = QuerySchemaRegistry.fields(row.entityType()).get(predicate.field()).type();
        return switch (predicate.operator()) {
            case EQ -> anyEqual(cell.values(), predicate.values().getFirst(), fieldType);
            case NEQ -> !anyEqual(cell.values(), predicate.values().getFirst(), fieldType);
            case CONTAINS -> textAny(cell.values(), predicate.values().getFirst(), TextMatch.CONTAINS);
            case STARTS_WITH -> textAny(cell.values(), predicate.values().getFirst(), TextMatch.STARTS_WITH);
            case ENDS_WITH -> textAny(cell.values(), predicate.values().getFirst(), TextMatch.ENDS_WITH);
            case IN -> predicate.values().stream().anyMatch(value -> anyEqual(cell.values(), value, fieldType));
            case EXISTS -> throw new IllegalStateException("handled above");
        };
    }

    private boolean anyEqual(List<String> values, String expected, QueryFieldType type) {
        return values.stream().anyMatch(value -> equal(value, expected, type));
    }

    private boolean equal(String actual, String expected, QueryFieldType type) {
        return QueryValueSemantics.equal(actual, expected, type);
    }

    private boolean textAny(List<String> values, String expected, TextMatch mode) {
        String needle = expected.toLowerCase(Locale.ROOT);
        return values.stream().map(value -> value.toLowerCase(Locale.ROOT)).anyMatch(value -> switch (mode) {
            case CONTAINS -> value.contains(needle);
            case STARTS_WITH -> value.startsWith(needle);
            case ENDS_WITH -> value.endsWith(needle);
        });
    }

    private Comparator<QueryRow> comparator(QueryDefinition query) {
        Comparator<QueryRow> comparator = (left, right) -> 0;
        Map<String, QueryFieldDefinition> schema = QuerySchemaRegistry.fields(query.entityType());
        for (QuerySort sort : query.sort()) {
            QueryFieldDefinition definition = schema.get(sort.field());
            if (definition == null) {
                throw new IllegalStateException("validated sort field missing from query schema: " + sort.field());
            }
            QueryFieldType fieldType = definition.type();
            Comparator<QueryRow> key = (left, right) -> QueryValueSemantics.compare(
                    left.cell(sort.field()), right.cell(sort.field()), fieldType);
            if (sort.direction() == QuerySortDirection.DESC) {
                key = key.reversed();
            }
            comparator = comparator.thenComparing(key);
        }
        return comparator
                .thenComparing(QueryRow::projectId)
                .thenComparing(QueryRow::entityId);
    }

    private List<String> columns(QueryDefinition query) {
        LinkedHashSet<String> columns = new LinkedHashSet<>();
        switch (query.entityType()) {
            case PORTFOLIO_MEMBERSHIP -> {
                columns.add("portfolioId");
                columns.add("projectId");
            }
            case PORTFOLIO_REFERENCE -> {
                columns.add("id");
                columns.add("portfolioId");
                columns.add("projectId");
            }
            default -> {
                columns.add("id");
                columns.add("projectId");
            }
        }
        if (query.projection().fields().isEmpty()) {
            columns.addAll(QuerySchemaRegistry.defaultProjection(query.entityType()));
        } else {
            columns.addAll(query.projection().fields());
        }
        return List.copyOf(columns);
    }

    private QueryRow requirementRow(ProjectSpecificationId projectId, Requirement item) {
        return row(QueryEntityType.REQUIREMENT, projectId, item.id(), List.of(
                QueryCell.scalar("id", item.id()), QueryCell.scalar("projectId", projectId),
                QueryCell.scalar("specificationId", item.specificationId()), QueryCell.optional("key", item.key()),
                QueryCell.scalar("title", item.title()), QueryCell.scalar("statement", item.statement()),
                QueryCell.scalar("providerId", item.provenance().providerId().value())));
    }

    private QueryRow specificationRow(ProjectSpecificationId projectId, Specification item) {
        return row(QueryEntityType.SPECIFICATION, projectId, item.id(), List.of(
                QueryCell.scalar("id", item.id()), QueryCell.scalar("projectId", projectId), QueryCell.scalar("key", item.key()),
                QueryCell.scalar("title", item.title()), QueryCell.optional("description", item.description()),
                QueryCell.scalar("providerId", item.provenance().providerId().value())));
    }

    private QueryRow scenarioRow(ProjectSpecificationId projectId, Scenario item) {
        return row(QueryEntityType.SCENARIO, projectId, item.id(), List.of(
                QueryCell.scalar("id", item.id()), QueryCell.scalar("projectId", projectId), QueryCell.optional("requirementId", item.requirementId()),
                QueryCell.scalar("title", item.title()), new QueryCell("preconditions", item.preconditions()), QueryCell.scalar("action", item.action()),
                QueryCell.scalar("expectedOutcome", item.expectedOutcome()), QueryCell.scalar("providerId", item.provenance().providerId().value())));
    }

    private QueryRow changeRow(ProjectSpecificationId projectId, ChangeProposal item) {
        return row(QueryEntityType.CHANGE, projectId, item.id(), List.of(
                QueryCell.scalar("id", item.id()), QueryCell.scalar("projectId", projectId), QueryCell.optional("key", item.key()),
                QueryCell.scalar("title", item.title()), QueryCell.scalar("intent", item.intent()), new QueryCell("scope", item.scope()),
                new QueryCell("outOfScope", item.outOfScope()), new QueryCell("risks", item.risks()),
                QueryCell.scalar("providerId", item.provenance().providerId().value())));
    }

    private QueryRow constraintRow(ProjectSpecificationId projectId, Constraint item) {
        return row(QueryEntityType.CONSTRAINT, projectId, item.id(), List.of(
                QueryCell.scalar("id", item.id()), QueryCell.scalar("projectId", projectId), QueryCell.scalar("changeId", item.changeId()),
                QueryCell.scalar("statement", item.statement()), QueryCell.scalar("applicability", item.applicability().name()),
                QueryCell.scalar("severity", item.severity().name()), QueryCell.scalar("satisfaction", item.satisfaction().name()),
                QueryCell.scalar("blockingMode", item.blockingPolicy().mode().name()),
                QueryCell.scalar("providerId", item.provenance().providerId().value())));
    }

    private QueryRow decisionRow(ProjectSpecificationId projectId, DesignDecision item) {
        return row(QueryEntityType.DESIGN_DECISION, projectId, item.id(), List.of(
                QueryCell.scalar("id", item.id()), QueryCell.scalar("projectId", projectId), QueryCell.scalar("changeId", item.changeId()),
                QueryCell.scalar("title", item.title()), QueryCell.scalar("decision", item.decision()),
                QueryCell.scalar("providerId", item.provenance().providerId().value())));
    }

    private QueryRow taskRow(ProjectSpecificationId projectId, ImplementationTask item) {
        return row(QueryEntityType.TASK, projectId, item.id(), List.of(
                QueryCell.scalar("id", item.id()), QueryCell.scalar("projectId", projectId), QueryCell.scalar("changeId", item.changeId()),
                QueryCell.optional("key", item.key()), QueryCell.scalar("title", item.title()), QueryCell.scalar("completed", item.completed()),
                QueryCell.scalar("providerId", item.provenance().providerId().value())));
    }

    private QueryRow acceptanceRow(ProjectSpecificationId projectId, AcceptanceCriterion item) {
        return row(QueryEntityType.ACCEPTANCE_CRITERION, projectId, item.id(), List.of(
                QueryCell.scalar("id", item.id()), QueryCell.scalar("projectId", projectId), QueryCell.optional("requirementId", item.requirementId()),
                QueryCell.optional("changeId", item.changeId()), QueryCell.scalar("title", item.title()), QueryCell.scalar("condition", item.condition()),
                QueryCell.scalar("verificationStatus", item.verificationStatus().name()),
                QueryCell.scalar("providerId", item.provenance().providerId().value())));
    }

    private QueryRow evidenceRow(ProjectSpecificationId projectId, Evidence item) {
        return row(QueryEntityType.EVIDENCE, projectId, item.id(), List.of(
                QueryCell.scalar("id", item.id()), QueryCell.scalar("projectId", projectId), QueryCell.scalar("source", item.source()),
                QueryCell.optional("range", item.range()), QueryCell.optional("excerptHash", item.excerptHash())));
    }

    private QueryRow membershipRow(PortfolioMembership item) {
        return new QueryRow(QueryEntityType.PORTFOLIO_MEMBERSHIP, item.projectId().toString(), item.projectId().toString(), List.of(
                QueryCell.scalar("portfolioId", item.portfolioId()), QueryCell.scalar("projectId", item.projectId()),
                QueryCell.scalar("displayName", item.displayName()), QueryCell.optional("workspace", item.workspace()),
                QueryCell.optional("repository", item.repository()), new QueryCell("providers", item.providers().stream().map(provider -> provider.value()).sorted().toList()),
                QueryCell.scalar("status", item.status().name())));
    }

    private QueryRow referenceRow(CrossProjectReference item) {
        return new QueryRow(QueryEntityType.PORTFOLIO_REFERENCE, item.source().projectId().toString(), item.id().toString(), List.of(
                QueryCell.scalar("id", item.id()), QueryCell.scalar("portfolioId", item.portfolioId()),
                QueryCell.scalar("projectId", item.source().projectId()), QueryCell.scalar("sourceProjectId", item.source().projectId()),
                QueryCell.scalar("sourceType", item.source().entityType()), QueryCell.scalar("sourceId", item.source().entityId()),
                QueryCell.scalar("targetProjectId", item.target().projectId()), QueryCell.scalar("targetType", item.target().entityType()),
                QueryCell.scalar("targetId", item.target().entityId()), QueryCell.scalar("relation", item.relation()),
                QueryCell.scalar("providerId", item.providerId().value()), QueryCell.optional("sourceLocator", item.sourceLocator()),
                QueryCell.optional("evidenceId", item.evidenceId())));
    }

    private QueryRow row(QueryEntityType type, ProjectSpecificationId projectId, Object entityId, List<QueryCell> cells) {
        return new QueryRow(type, projectId.toString(), entityId.toString(), cells);
    }

    private enum TextMatch {
        CONTAINS,
        STARTS_WITH,
        ENDS_WITH
    }
}
