package com.morpheus.integration.minos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.morpheus.application.reference.ExternalReferenceResolutionService;
import com.morpheus.application.reference.ExternalReferenceResolverRegistry;
import com.morpheus.domain.identity.DomainIdentity;
import com.morpheus.domain.reference.ExternalReference;
import com.morpheus.domain.reference.ExternalReferenceId;
import com.morpheus.domain.reference.ExternalReferenceResolutionReason;
import com.morpheus.domain.reference.ExternalReferenceResolutionState;
import com.morpheus.domain.reference.ExternalReferenceTarget;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * A search bounded by a page size cannot conclude that a symbol was removed.
 *
 * <p>The resolver filtered a page of at most 1000 symbols on an exact key and answered {@code NOT_FOUND} when the
 * key was not on it; the resolution service turned that into {@code TARGET_REMOVED} for a reference already
 * resolved once. Exercised from the resolver through the service, which is what an agent reading
 * {@code resolutionState} and {@code resolutionReason} sees.</p>
 */
class MinosBoundedSearchResolutionTest {
    private static final String KEY = "symbol:Wanted";
    private static final int LIMIT = 1000;
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-25T10:00:00Z"), ZoneOffset.UTC);

    @Test
    void aTruncatedSearchNeverConcludesRemovalOfAPreviouslyResolvedSymbol() {
        boolean[] present = {true};
        ExternalReferenceResolutionService service = service(limit -> present[0]
                ? new MinosCodeGateway.SymbolSearch(List.of(symbol(KEY)), false)
                : new MinosCodeGateway.SymbolSearch(fullPageWithoutTheKey(limit), true));

        ExternalReference resolved = service.resolve(reference());
        present[0] = false;
        ExternalReference afterTruncation = service.resolve(resolved);

        assertEquals(ExternalReferenceResolutionState.RESOLVED, resolved.resolutionState());
        assertEquals(ExternalReferenceResolutionState.STALE, afterTruncation.resolutionState());
        assertEquals(ExternalReferenceResolutionReason.TARGET_UNAVAILABLE, afterTruncation.resolutionReason());
        assertNotEquals(ExternalReferenceResolutionReason.TARGET_REMOVED, afterTruncation.resolutionReason());
    }

    @Test
    void aTruncatedSearchOnAFreshReferenceIsUnresolvedAsUnavailable() {
        ExternalReferenceResolutionService service = service(
                limit -> new MinosCodeGateway.SymbolSearch(fullPageWithoutTheKey(limit), true));

        ExternalReference result = service.resolve(reference());

        assertEquals(ExternalReferenceResolutionState.UNRESOLVED, result.resolutionState());
        assertEquals(ExternalReferenceResolutionReason.TARGET_UNAVAILABLE, result.resolutionReason());
    }

    @Test
    void anExhaustiveSearchStillConcludesRemovalOfAPreviouslyResolvedSymbol() {
        boolean[] present = {true};
        ExternalReferenceResolutionService service = service(limit -> present[0]
                ? new MinosCodeGateway.SymbolSearch(List.of(symbol(KEY)), false)
                : new MinosCodeGateway.SymbolSearch(List.of(symbol(KEY + "Other")), false));

        ExternalReference resolved = service.resolve(reference());
        present[0] = false;
        ExternalReference removed = service.resolve(resolved);

        assertEquals(ExternalReferenceResolutionState.STALE, removed.resolutionState());
        assertEquals(ExternalReferenceResolutionReason.TARGET_REMOVED, removed.resolutionReason());
    }

    private interface Search {
        MinosCodeGateway.SymbolSearch answer(int limit);
    }

    private static ExternalReferenceResolutionService service(Search search) {
        MinosCodeGateway gateway = new MinosCodeGateway() {
            @Override
            public IndexStatus indexStatus(String project) {
                return new IndexStatus("project-1", "morpheus-engine", "READY", "snapshot-1", "scip-java", "1");
            }

            @Override
            public SymbolSearch findSymbols(String project, String query, int limit) {
                return search.answer(limit);
            }

            @Override
            public void close() {
            }
        };
        return new ExternalReferenceResolutionService(new ExternalReferenceResolverRegistry(
                List.of(new MinosMcpExternalReferenceResolver(() -> gateway))), CLOCK);
    }

    private static List<MinosCodeGateway.Symbol> fullPageWithoutTheKey(int limit) {
        assertEquals(LIMIT, limit, "the resolver must ask for the largest page MINOS allows");
        List<MinosCodeGateway.Symbol> page = new ArrayList<>();
        for (int index = 0; index < limit; index++) {
            page.add(symbol("symbol:Filler" + index));
        }
        return page;
    }

    private static MinosCodeGateway.Symbol symbol(String key) {
        return new MinosCodeGateway.Symbol(
                "id-" + key, key, "project-1", "module", "file", "CLASS", "Name", "pkg.Name", "class Name", "java",
                "RESOLVED", new MinosCodeGateway.Origin("scip-java", "1", "run-1"));
    }

    private static ExternalReference reference() {
        return ExternalReference.unvalidated(
                ExternalReferenceId.generate(),
                DomainIdentity.generate(),
                new ExternalReferenceTarget("MINOS", Optional.of("morpheus-engine"), "SYMBOL", KEY, Optional.empty()),
                Optional.empty());
    }
}
