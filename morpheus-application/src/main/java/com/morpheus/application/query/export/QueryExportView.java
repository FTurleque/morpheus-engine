package com.morpheus.application.query.export;

import java.util.List;
import java.util.Objects;

/** Transport-safe canonical projection for M24 exports; contains no domain objects. */
public record QueryExportView(
        int schemaVersion,
        String scopeKind,
        String scopeId,
        String entityType,
        List<String> columns,
        int totalMatches,
        List<RowView> rows) {

    public QueryExportView {
        if (schemaVersion <= 0) {
            throw new IllegalArgumentException("schemaVersion must be positive");
        }
        scopeKind = require(scopeKind, "scopeKind");
        scopeId = require(scopeId, "scopeId");
        entityType = require(entityType, "entityType");
        columns = List.copyOf(Objects.requireNonNull(columns, "columns"));
        if (totalMatches < 0) {
            throw new IllegalArgumentException("totalMatches must be non-negative");
        }
        rows = List.copyOf(Objects.requireNonNull(rows, "rows"));
        if (rows.size() != totalMatches) {
            throw new IllegalArgumentException("canonical export must contain every matched row");
        }
    }

    public record RowView(String projectId, String entityId, List<CellView> cells) {
        public RowView {
            projectId = require(projectId, "projectId");
            entityId = require(entityId, "entityId");
            cells = List.copyOf(Objects.requireNonNull(cells, "cells"));
        }
    }

    public record CellView(String field, List<String> values) {
        public CellView {
            field = require(field, "field");
            values = List.copyOf(Objects.requireNonNull(values, "values"));
        }
    }

    private static String require(String value, String name) {
        Objects.requireNonNull(value, name);
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return normalized;
    }
}
