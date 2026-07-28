package com.morpheus.application.query.export;

import com.morpheus.application.query.dsl.QueryBudgets;

/** Central M24 export limits. Exceeding a limit is always explicit and never truncates the result. */
public final class QueryExportBudgetPolicy {
    public void requireRows(int rows) {
        if (rows < 0) {
            throw new IllegalArgumentException("export row count must be non-negative");
        }
        if (rows > QueryBudgets.MAX_EXPORT_ROWS) {
            throw new QueryExportBudgetException(
                    "export rows exceed " + QueryBudgets.MAX_EXPORT_ROWS + ": " + rows);
        }
    }

    public void requireBytes(int bytes) {
        if (bytes < 0) {
            throw new IllegalArgumentException("export byte count must be non-negative");
        }
        if (bytes > QueryBudgets.MAX_EXPORT_BYTES) {
            throw new QueryExportBudgetException(
                    "export bytes exceed " + QueryBudgets.MAX_EXPORT_BYTES + ": " + bytes);
        }
    }
}
