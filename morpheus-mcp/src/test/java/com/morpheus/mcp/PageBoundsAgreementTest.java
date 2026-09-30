package com.morpheus.mcp;

import com.morpheus.application.query.PageRequest;
import io.modelcontextprotocol.server.McpServerFeatures;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * The maximum page size of the MCP catalog and the one the application enforces are two constants in two modules,
 * and the text says they are one number.
 *
 * <p>The Javadoc of {@link PageArguments} and ADR-0107 (amendment of 29 September 2026 on MCP-2, Decision, point 2)
 * write that the maximum of {@code limit} is the catalog's and is also {@code PageRequest}'s. Nothing made it so:
 * each is a literal {@code 100}. This test holds the equality of the <em>maximum</em>, and it matters in one
 * direction more than the other. {@code MorpheusMcpToolService.page} builds a {@code PageRequest} from a
 * {@code limit} bounded by {@code MorpheusMcpToolCatalog.MAX_LIMIT}, and the catalog schemas publish
 * {@code integer(1, MAX_LIMIT)}: if the catalog maximum rose above {@code PageRequest.MAX_LIMIT}, the catalog tools
 * would refuse a {@code limit} their own schema accepts (the defect class of MCP-3). Rewriting the sentence is no
 * remedy in that direction. No other test calls a catalog tool at its maximum {@code limit}
 * ({@code MorpheusMcpToolServiceTest} uses 1; {@code MorpheusMcpToolCatalogTest} pins the schema maximum to the
 * literal 100 without calling anything), so this equality is the only guard of it.</p>
 *
 * <p>It also holds the three tool descriptions that spell {@code default 50, maximum 100} by hand
 * ({@code list_policy_pack_versions}, {@code get_policy_audit}, {@code list_saved_view_versions}): each must contain
 * the text built from the constants.</p>
 *
 * <p>What it does not hold: the default ({@code PageRequest} has none; the CLI picks 20, the catalog 50, the
 * portfolio and query tools 100), the maximum of the tools that spell their own bounds
 * ({@code PortfolioQueryService.MAX_PAGE_SIZE} and {@code QueryBudgets.MAX_PAGE_SIZE}, both 500, and deliberately
 * not {@code PageRequest}'s), and the {@code offset} ceilings, which differ by design (1 000 000 in the catalog,
 * {@code Integer.MAX_VALUE} in {@link PageArguments}). It names no tool except those three descriptions and
 * discovers none: a fourth description that spelled the bounds by hand would not be seen. It does not call a
 * catalog tool at its maximum.</p>
 */
class PageBoundsAgreementTest {
    private static final String BOUNDS_TEXT =
            "default " + PageArguments.DEFAULT_LIMIT + ", maximum " + PageArguments.MAX_LIMIT;

    @Test
    void theCatalogMaximumIsTheMaximumThePageRequestEnforces() {
        assertEquals(PageRequest.MAX_LIMIT, MorpheusMcpToolCatalog.MAX_LIMIT,
                "MorpheusMcpToolCatalog.MAX_LIMIT and PageRequest.MAX_LIMIT have parted. Two things are now false."
                        + " The sentence that the maximum limit of the MCP paged tools is the catalog's and is also"
                        + " PageRequest's (Javadoc of PageArguments; ADR-0107, amendment on MCP-2, Decision, point 2)."
                        + " And, if the catalog maximum is the larger, every catalog tool served by"
                        + " MorpheusMcpToolService.page: its schema accepts a limit that its PageRequest refuses, so"
                        + " rewriting the sentence is not enough. Bring the constants back together.");
    }

    @Test
    void thePageArgumentsPublishAndSliceEnforceTheMaximumThePageRequestEnforces() {
        assertEquals(PageRequest.MAX_LIMIT, PageArguments.MAX_LIMIT,
                "PageArguments.MAX_LIMIT is the catalog's by construction; it no longer equals PageRequest.MAX_LIMIT");

        @SuppressWarnings("unchecked")
        Map<String, Object> limit = (Map<String, Object>) PageArguments.properties().get("limit");
        assertEquals(PageRequest.MAX_LIMIT, limit.get("maximum"), "the schema must publish the maximum the application enforces");

        assertDoesNotThrow(() -> new PageRequest(0, PageRequest.MAX_LIMIT));
        IllegalArgumentException refusedByRequest = assertThrows(
                IllegalArgumentException.class, () -> new PageRequest(0, PageRequest.MAX_LIMIT + 1));
        assertEquals("limit must be between 1 and " + PageRequest.MAX_LIMIT, refusedByRequest.getMessage(),
                "PageRequest must refuse the first limit above the maximum it names");

        Map<String, Object> atMaximum = PageArguments.slice(
                Map.of("limit", PageRequest.MAX_LIMIT), () -> List.<String>of(), Object::toString);
        assertEquals(PageRequest.MAX_LIMIT, atMaximum.get("limit"), "slice must accept the maximum PageRequest accepts");

        IllegalArgumentException refusedBySlice = assertThrows(IllegalArgumentException.class,
                () -> PageArguments.slice(Map.of("limit", PageRequest.MAX_LIMIT + 1),
                        () -> fail("the collection must not be read for a page that is refused"), Object::toString),
                "slice must refuse the first limit above the maximum PageRequest refuses");
        assertTrue(refusedBySlice.getMessage().startsWith("limit must be an integer between"), refusedBySlice.getMessage());
    }

    @Test
    void theToolDescriptionsThatSpellTheBoundsByHandSpellTheConstants() {
        Path unused = Path.of("bounds-agreement-never-opened.db");
        assertDescription(new MorpheusPolicyMcpTools(unused).specifications(), MorpheusPolicyMcpTools.VERSIONS);
        assertDescription(new MorpheusPolicyMcpTools(unused).specifications(), MorpheusPolicyMcpTools.AUDIT);
        assertDescription(new MorpheusQueryMcpTools(unused).specifications(), MorpheusQueryMcpTools.LIST_SAVED_VIEW_VERSIONS);
    }

    private static void assertDescription(List<McpServerFeatures.SyncToolSpecification> specifications, String tool) {
        String description = specifications.stream()
                .map(McpServerFeatures.SyncToolSpecification::tool)
                .filter(candidate -> candidate.name().equals(tool))
                .findFirst().orElseThrow(() -> new AssertionError(tool + " is not served")).description();
        assertTrue(description.contains(BOUNDS_TEXT),
                tool + " spells its bounds by hand and no longer matches the constants (expected \"" + BOUNDS_TEXT
                        + "\"): " + description);
    }
}
