package com.morpheus.integration.minos;

import java.util.List;
import java.util.Objects;

/** Minimal MINOS-facing contract required by the MORPHEUS external-reference resolver. */
public interface MinosCodeGateway extends AutoCloseable {
    IndexStatus indexStatus(String project);

    /**
     * A search bounded by {@code limit}. MINOS reports a count but no total or truncation flag, so a page that
     * fills the limit is declared {@link SymbolSearch#possiblyTruncated() possibly truncated}: a caller must
     * not read the absence of a symbol from a page that may have stopped before reaching it.
     */
    SymbolSearch findSymbols(String project, String query, int limit);

    @Override
    void close();

    record SymbolSearch(List<Symbol> symbols, boolean possiblyTruncated) {
        public SymbolSearch {
            symbols = List.copyOf(Objects.requireNonNull(symbols, "symbols"));
        }
    }

    record IndexStatus(
            String projectId,
            String projectName,
            String state,
            String activeSnapshotId,
            String providerId,
            String providerVersion) {
    }

    record Symbol(
            String id,
            String symbolKey,
            String projectId,
            String moduleId,
            String fileId,
            String kind,
            String name,
            String qualifiedName,
            String signature,
            String language,
            String resolutionStatus,
            Origin origin) {
        public Symbol {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(symbolKey, "symbolKey");
            Objects.requireNonNull(projectId, "projectId");
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(origin, "origin");
        }
    }

    record Origin(String providerId, String providerVersion, String indexRunId) {
    }
}
