package com.morpheus.mcp;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * The {@code offset} and {@code limit} arguments of a paginated read tool, read and declared from one set of
 * bounds so that the schema a tool publishes cannot promise a range its handler refuses.
 *
 * <p>The transport answers an oversized response with {@code MorpheusMcpServer.OVERSIZED_RESPONSE_GUIDANCE}:
 * paginated read tools accept {@code offset} and {@code limit}. A tool whose schema forbids them makes that
 * advice impossible to follow, so the three tools of the MCP-2 finding (the policy audit journal, the policy pack
 * versions, the saved-view versions) take these arguments (ADR-0107, amendment of 29 September 2026 on MCP-2).
 * Other growing collections are still served whole, such as {@code list_policy_packs}; each is named with its
 * reason in {@code GrowingCollectionToolsArePageableTest}. The default and the maximum are the catalog's
 * ({@code MorpheusMcpToolCatalog.DEFAULT_LIMIT} and {@code MAX_LIMIT}). The maximum equals
 * {@code PageRequest.MAX_LIMIT}, the ceiling the application's own page type enforces, and
 * {@code PageBoundsAgreementTest} fails if the two part; {@code PageRequest} has no default, so the default is
 * compared with nothing.</p>
 *
 * <p>The page itself is {@link PagedEnvelope#slice}; this class adds no second way to spell one. The slice is taken
 * from a collection the application has already read and ordered, so the bound limits the response and not the
 * cost of reading: reading the collection stays unbounded.</p>
 *
 * <p>Four tools read it: the audit journal, the policy pack versions and the saved-view versions, and
 * {@code list_composition_conflicts}, whose bounds it already applied and which now takes them from here. Other
 * tools still spell their own bounds, on purpose and unguarded: {@code MorpheusPortfolioMcpTools} (default 100,
 * maximum {@code PortfolioQueryService.MAX_PAGE_SIZE}, 500), the query tools ({@code queryProperties()}: default 100,
 * maximum {@code QueryBudgets.MAX_PAGE_SIZE}, and an {@code offset} whose schema declares no maximum although the code
 * enforces {@code Integer.MAX_VALUE}), and the catalog tools ({@code MorpheusMcpToolCatalog} and
 * {@code MorpheusMcpToolService}: 50 and 100, {@code offset} up to 1 000 000). They differ in value, so an equality guard
 * across them would be wrong, and the schema-versus-code disagreement of the query tools is the defect class of the
 * export finding (MCP-3), not of this one: no guard on it is claimed here.</p>
 */
final class PageArguments {
    static final int DEFAULT_LIMIT = MorpheusMcpToolCatalog.DEFAULT_LIMIT;
    static final int MAX_LIMIT = MorpheusMcpToolCatalog.MAX_LIMIT;

    private PageArguments() {
    }

    /** The two schema properties, with the same bounds {@link #slice} enforces. */
    static Map<String, Object> properties() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("offset", Map.of("type", "integer", "minimum", 0, "maximum", Integer.MAX_VALUE));
        properties.put("limit", Map.of("type", "integer", "minimum", 1, "maximum", MAX_LIMIT));
        return properties;
    }

    /**
     * The arguments are read, and refused if out of range, <em>before</em> the collection is read: a caller who asks
     * for {@code limit = 0} learns that without the collection having been read for a page nobody can return.
     *
     * <p>Under the server, an out-of-range page does not reach this refusal. {@code MorpheusMcpServer} arms
     * {@code validateToolInputs(true)} (pinned by
     * {@code McpFailureContractTest#theServerArmsSchemaValidationForEveryToolItServes}), and the SDK (mcp-core
     * 2.0.1, {@code McpAsyncServer.toolsCallRequestHandler}) validates the arguments against the published schema
     * before it calls the handler. {@link #properties()} publishes the bounds this method enforces
     * ({@code GrowingCollectionToolsPagingTest#theBoundsAreEnforcedByTheHandlerAndAnnouncedByTheSchemaAlike}), so a
     * served call with {@code limit = 0} is refused by the SDK before any handler runs, and no store is opened
     * for it.</p>
     *
     * <p>This refusal is reached when a handler is called directly, as the tests do. The policy and query handlers
     * open their runtime before they read any argument, so the store is already open there. That is assumed, not
     * guarded, and it is established by reading the code and the SDK source, not by measuring a served call from
     * end to end. Whether a handler refuses a semantic argument (an identifier that must parse) before or after it
     * opens its store differs from one class to the next, and no order is claimed.</p>
     */
    static <T> Map<String, Object> slice(
            Map<String, Object> arguments, Supplier<? extends List<T>> source, Function<? super T, ?> projection) {
        int offset = McpArguments.optionalInt(arguments, "offset", 0, 0, Integer.MAX_VALUE);
        int limit = McpArguments.optionalInt(arguments, "limit", DEFAULT_LIMIT, 1, MAX_LIMIT);
        return PagedEnvelope.slice(offset, limit, source.get(), projection);
    }
}
