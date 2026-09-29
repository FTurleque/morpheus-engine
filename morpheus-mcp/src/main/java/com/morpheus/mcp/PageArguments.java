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
 * advice impossible to follow, so every tool that returns a collection which only ever grows takes these
 * arguments (ADR-0107, amendment of 29 September 2026 on MCP-2). The defaults and the maximum are the catalog's,
 * which {@code PageRequest} also enforces.</p>
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
     * The arguments are read, and refused if out of range, <em>before</em> the collection is: a caller who asks for
     * {@code limit = 0} learns that without the store having been opened for a page nobody can return.
     */
    static <T> Map<String, Object> slice(
            Map<String, Object> arguments, Supplier<? extends List<T>> source, Function<? super T, ?> projection) {
        int offset = McpArguments.optionalInt(arguments, "offset", 0, 0, Integer.MAX_VALUE);
        int limit = McpArguments.optionalInt(arguments, "limit", DEFAULT_LIMIT, 1, MAX_LIMIT);
        return PagedEnvelope.slice(offset, limit, source.get(), projection);
    }
}
