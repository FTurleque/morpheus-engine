package com.morpheus.application.query.dsl;

import com.morpheus.domain.portfolio.PortfolioId;
import com.morpheus.domain.project.ProjectSpecificationId;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

/** Versioned deterministic binary codec used only to persist provider-neutral query definitions. */
public final class QueryDefinitionCodec {
    private static final int VERSION = 1;

    public String encode(QueryDefinition query) {
        new QueryValidator().requireValid(query);
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream out = new DataOutputStream(bytes)) {
                out.writeInt(VERSION);
                writeScope(out, query.scope());
                out.writeUTF(query.entityType().name());
                out.writeBoolean(query.filter().isPresent());
                if (query.filter().isPresent()) {
                    writeFilter(out, query.filter().orElseThrow());
                }
                out.writeInt(query.sort().size());
                for (QuerySort sort : query.sort()) {
                    out.writeUTF(sort.field());
                    out.writeUTF(sort.direction().name());
                }
                out.writeInt(query.projection().fields().size());
                for (String field : query.projection().fields()) {
                    out.writeUTF(field);
                }
                out.writeInt(query.page().offset());
                out.writeInt(query.page().limit());
            }
            String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes.toByteArray());
            requireEncodedSize(encoded);
            return encoded;
        } catch (IOException exception) {
            throw new IllegalStateException("cannot encode query definition", exception);
        }
    }

    public QueryDefinition decode(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            throw new IllegalArgumentException("encoded query definition must not be blank");
        }
        requireEncodedSize(encoded);
        try {
            byte[] bytes = Base64.getUrlDecoder().decode(encoded);
            DecodeBudget budget = new DecodeBudget();
            try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes))) {
                int version = in.readInt();
                if (version != VERSION) {
                    throw new IllegalArgumentException("unsupported query definition codec version: " + version);
                }
                QueryScope scope = readScope(in);
                QueryEntityType entityType = QueryEntityType.valueOf(in.readUTF());
                Optional<QueryFilter> filter = in.readBoolean()
                        ? Optional.of(readFilter(in, budget, 1))
                        : Optional.empty();
                int sortCount = boundedCount(in.readInt(), QueryBudgets.MAX_SORT_FIELDS, "sort");
                List<QuerySort> sort = new ArrayList<>(sortCount);
                for (int index = 0; index < sortCount; index++) {
                    sort.add(new QuerySort(in.readUTF(), QuerySortDirection.valueOf(in.readUTF())));
                }
                int projectionCount = boundedCount(
                        in.readInt(), QueryBudgets.MAX_PROJECTION_FIELDS, "projection");
                List<String> projection = new ArrayList<>(projectionCount);
                for (int index = 0; index < projectionCount; index++) {
                    projection.add(in.readUTF());
                }
                QueryPage page = new QueryPage(in.readInt(), in.readInt());
                if (in.available() != 0) {
                    throw new IllegalArgumentException("encoded query definition contains trailing data");
                }
                QueryDefinition query = new QueryDefinition(
                        scope, entityType, filter, sort, new QueryProjection(projection), page);
                new QueryValidator().requireValid(query, encoded.length());
                return query;
            }
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (EOFException exception) {
            throw new IllegalArgumentException("encoded query definition is truncated", exception);
        } catch (IOException exception) {
            throw new IllegalArgumentException("cannot decode query definition", exception);
        }
    }

    private void requireEncodedSize(String encoded) {
        if (encoded.length() > QueryBudgets.MAX_ENCODED_EXPRESSION_BYTES) {
            throw new IllegalArgumentException(
                    "encoded query definition exceeds " + QueryBudgets.MAX_ENCODED_EXPRESSION_BYTES + " bytes");
        }
    }

    private void writeScope(DataOutputStream out, QueryScope scope) throws IOException {
        if (scope instanceof ProjectQueryScope project) {
            out.writeByte(1);
            out.writeUTF(project.projectId().toString());
            return;
        }
        if (scope instanceof PortfolioQueryScope portfolio) {
            out.writeByte(2);
            out.writeUTF(portfolio.portfolioId().toString());
            return;
        }
        throw new IllegalArgumentException("unsupported query scope: " + scope.getClass().getName());
    }

    private QueryScope readScope(DataInputStream in) throws IOException {
        return switch (in.readUnsignedByte()) {
            case 1 -> new ProjectQueryScope(ProjectSpecificationId.parse(in.readUTF()));
            case 2 -> new PortfolioQueryScope(PortfolioId.parse(in.readUTF()));
            default -> throw new IllegalArgumentException("unsupported query scope tag");
        };
    }

    private void writeFilter(DataOutputStream out, QueryFilter filter) throws IOException {
        if (filter instanceof QueryPredicate predicate) {
            out.writeByte(1);
            out.writeUTF(predicate.field());
            out.writeUTF(predicate.operator().name());
            out.writeInt(predicate.values().size());
            for (String value : predicate.values()) {
                out.writeUTF(value);
            }
            return;
        }
        if (filter instanceof QueryAnd and) {
            out.writeByte(2);
            writeChildren(out, and.children());
            return;
        }
        if (filter instanceof QueryOr or) {
            out.writeByte(3);
            writeChildren(out, or.children());
            return;
        }
        if (filter instanceof QueryNot not) {
            out.writeByte(4);
            writeFilter(out, not.child());
            return;
        }
        throw new IllegalArgumentException("unsupported query filter: " + filter.getClass().getName());
    }

    private void writeChildren(DataOutputStream out, List<QueryFilter> children) throws IOException {
        out.writeInt(children.size());
        for (QueryFilter child : children) {
            writeFilter(out, child);
        }
    }

    private QueryFilter readFilter(DataInputStream in, DecodeBudget budget, int depth) throws IOException {
        budget.enterNode(depth);
        return switch (in.readUnsignedByte()) {
            case 1 -> readPredicate(in, budget);
            case 2 -> new QueryAnd(readChildren(in, budget, depth));
            case 3 -> new QueryOr(readChildren(in, budget, depth));
            case 4 -> new QueryNot(readFilter(in, budget, depth + 1));
            default -> throw new IllegalArgumentException("unsupported query filter tag");
        };
    }

    private QueryPredicate readPredicate(DataInputStream in, DecodeBudget budget) throws IOException {
        budget.enterPredicate();
        String field = in.readUTF();
        QueryOperator operator = QueryOperator.valueOf(in.readUTF());
        int count = boundedCount(in.readInt(), QueryBudgets.MAX_PREDICATES, "predicate values");
        List<String> values = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            values.add(in.readUTF());
        }
        return new QueryPredicate(field, operator, values);
    }

    private List<QueryFilter> readChildren(DataInputStream in, DecodeBudget budget, int parentDepth) throws IOException {
        int count = boundedCount(in.readInt(), QueryBudgets.MAX_AST_NODES, "filter children");
        List<QueryFilter> children = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            children.add(readFilter(in, budget, parentDepth + 1));
        }
        return children;
    }

    private int boundedCount(int count, int max, String name) {
        if (count < 0 || count > max) {
            throw new IllegalArgumentException(name + " count is outside supported bounds: " + count);
        }
        return count;
    }

    private static final class DecodeBudget {
        private int nodes;
        private int predicates;

        private void enterNode(int depth) {
            if (depth > QueryBudgets.MAX_BOOLEAN_DEPTH) {
                throw new IllegalArgumentException(
                        "query boolean depth exceeds " + QueryBudgets.MAX_BOOLEAN_DEPTH);
            }
            nodes++;
            if (nodes > QueryBudgets.MAX_AST_NODES) {
                throw new IllegalArgumentException(
                        "query AST nodes exceed " + QueryBudgets.MAX_AST_NODES);
            }
        }

        private void enterPredicate() {
            predicates++;
            if (predicates > QueryBudgets.MAX_PREDICATES) {
                throw new IllegalArgumentException(
                        "query predicates exceed " + QueryBudgets.MAX_PREDICATES);
            }
        }
    }
}
