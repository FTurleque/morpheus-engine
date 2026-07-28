package com.morpheus.application.query.dsl;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** One deterministic projected business row; project identity is always explicit. */
public record QueryRow(
        QueryEntityType entityType,
        String projectId,
        String entityId,
        List<QueryCell> cells) {

    public QueryRow {
        Objects.requireNonNull(entityType, "entityType");
        projectId = require(projectId, "projectId");
        entityId = require(entityId, "entityId");
        Objects.requireNonNull(cells, "cells");
        cells = cells.stream().map(cell -> Objects.requireNonNull(cell, "cell")).toList();
        if (cells.stream().map(QueryCell::field).distinct().count() != cells.size()) {
            throw new IllegalArgumentException("query row fields must be unique");
        }
    }

    public Optional<QueryCell> cell(String field) {
        return cells.stream().filter(cell -> cell.field().equals(field)).findFirst();
    }

    public QueryRow project(List<String> fields) {
        return new QueryRow(entityType, projectId, entityId, fields.stream()
                .map(field -> cell(field).orElseGet(() -> new QueryCell(field, List.of())))
                .toList());
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
