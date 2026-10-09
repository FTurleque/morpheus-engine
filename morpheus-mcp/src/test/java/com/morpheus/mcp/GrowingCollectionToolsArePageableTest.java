package com.morpheus.mcp;

import com.morpheus.application.reference.ExternalReferenceResolverRegistry;
import io.modelcontextprotocol.server.McpServerFeatures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A tool the server serves either takes a page or says, in writing, why it does not (ADR-0107, amendment on MCP-2).
 *
 * <p>The transport answers an oversized response with advice that only works on a tool whose schema accepts
 * {@code offset} and {@code limit}. Three tools that read collections which only ever grow published a schema
 * that forbade both, so the advice could not be followed. This guard refuses the next one: it asks every tool the
 * default wiring serves whether its schema declares both properties and, if it does not, whether the tool is on
 * {@link #NOT_PAGED} with a reason. A tool that is neither fails the build and is named, so adding a tool forces
 * its author to answer the question rather than to remember it.</p>
 *
 * <p>"Paged" is read from the schema, structurally; no paged tool is named here. The exemptions are named, because
 * naming them is the point: each carries a reason of one of three kinds. A count bound (the second kind) says nothing
 * about size under the 1 MiB frame, and a reason of the third kind is a recorded residual, not a guarantee. The check runs in both directions: an exemption for a tool that is no longer
 * served, or that has since become paged, fails too, so the list cannot rot into decoration.</p>
 *
 * <p><b>What it does not cover.</b></p>
 * <ul>
 *   <li>Whether a collection <em>grows</em>. Nothing in a schema says so; the reasons below are judgements written
 *   by a person, checked against the code on the day they were written, and this test cannot re-check them. A tool
 *   that returns a growing collection and is wrongly listed passes.</li>
 *   <li>Whether a paged tool <em>honours</em> its {@code offset} and {@code limit}. Having both properties is what
 *   is read. {@code export_query} used to declare them and ignore them (MCP-3 removed them), and {@code create_saved_view}
 *   and {@code update_saved_view} declare them because they belong to the stored query, not to a page of results.
 *   {@code get_specification_context} pages its requirements and answers its scenarios and changes whole.</li>
 *   <li>Whether an answer fits the 1 MiB frame. {@code BOUNDED_IN_COUNT} is a number of elements and promises no
 *   size. The criterion "count budget times the largest element exceeds the frame" was <b>not applied in bytes to
 *   every tool</b>: it was applied, by arithmetic on the constants and on a constructed sample of the JSON, to the
 *   tools whose reason says so (the traversals {@code traverse_portfolio}, {@code trace_requirement} and
 *   {@code get_change_context} among them: 5000 links of at least about 320 bytes each are above the frame), and not
 *   applied to {@code list_policy_activations} and {@code execute_saved_view}, whose byte worst case is unevaluated.
 *   Nothing was measured on a real answer, and whether a real snapshot or portfolio can reach the count is not
 *   established either.</li>
 *   <li>Whether the page is bounded at the source. The slice is taken from a collection already read, so the bound
 *   limits the response and not the cost of reading it.</li>
 *   <li>Tools the default wiring does not serve. It judges {@code MorpheusMcpServer.toolSpecifications} as built
 *   with no plugin directory; {@code discover_provider_plugins} is declared by a class that wiring does not
 *   instantiate with a directory, and returns one discovery result.</li>
 * </ul>
 */
class GrowingCollectionToolsArePageableTest {
    private enum Reason {
        /** The tool answers about one identified thing, or applies one write and answers with what it wrote. */
        ONE_RESULT,
        /**
         * A count the code enforces bounds the answer; the constant is named so it can be checked. It says nothing about
         * the size of the answer under the 1 MiB MCP frame: an element can itself be large. A tool whose worst case
         * (count times the smallest element that arithmetic can establish) was found to exceed the frame is listed as
         * unbounded, not here; a tool listed here was either found below the frame, in which case its reason says so, or
         * was not evaluated in bytes, in which case its reason says that.
         */
        BOUNDED_IN_COUNT,
        /** No bound was found. Recorded so the gap is visible; it is not a claim that the answer stays small. */
        UNBOUNDED_ACKNOWLEDGED
    }

    private record Exemption(Reason reason, String why) {
    }

    /**
     * Every tool the default wiring serves that does not declare {@code offset} and {@code limit}, with the reason.
     * Verified against the code on 29 September 2026; the third kind of reason is the honest list of what is not
     * bounded.
     */
    private static final Map<String, Exemption> NOT_PAGED = exemptions();

    @TempDir
    Path temporaryDirectory;

    @Test
    void everyServedToolIsPagedOrExemptedWithAReason() {
        Map<String, Set<String>> served = served();

        assertEquals(List.of(), problems(served, NOT_PAGED),
                "a tool must take offset and limit, or be listed in NOT_PAGED with the reason it need not "
                        + "(ADR-0107, MCP-2): the transport's advice is to retry with a smaller limit");
    }

    @Test
    void theStructuralCriterionFindsPagedToolsAndUnpagedOnes() {
        Map<String, Set<String>> served = served();
        long paged = served.values().stream().filter(GrowingCollectionToolsArePageableTest::isPaged).count();

        assertTrue(served.size() > 10, () -> "the guard read " + served.size() + " tools: the wiring changed or the read is empty");
        assertTrue(paged > 0, "no served tool declares offset and limit: the criterion reads nothing");
        assertTrue(paged < served.size(), "every served tool is paged: the criterion has stopped discriminating");
    }

    @Test
    void anExemptionNeedsARealReason() {
        for (Map.Entry<String, Exemption> entry : NOT_PAGED.entrySet()) {
            assertFalse(entry.getValue().why().isBlank(), entry.getKey() + " is exempted without a reason");
            assertTrue(entry.getValue().why().length() >= 30,
                    entry.getKey() + " is exempted with a reason too short to be one: " + entry.getValue().why());
        }
    }

    @Test
    void theRuleFlagsWhatItIsWrittenToFlag() {
        Map<String, Set<String>> served = new TreeMap<>(Map.of(
                "pages", Set.of("id", "offset", "limit"),
                "listed", Set.of("id"),
                "forgotten", Set.of("id")));
        Map<String, Exemption> exempt = Map.of(
                "listed", new Exemption(Reason.ONE_RESULT, "one identified thing, nothing to page here at all"));

        assertEquals(List.of("forgotten: neither paged (offset and limit) nor listed in NOT_PAGED"), problems(served, exempt));

        assertEquals(List.of("listed: listed in NOT_PAGED but no longer served"),
                problems(Map.of("pages", Set.of("offset", "limit")), Map.of(
                        "listed", new Exemption(Reason.ONE_RESULT, "one identified thing, nothing to page here at all"))));

        assertEquals(List.of("pages: listed in NOT_PAGED but takes offset and limit: remove the exemption"),
                problems(Map.of("pages", Set.of("offset", "limit")), Map.of(
                        "pages", new Exemption(Reason.ONE_RESULT, "one identified thing, nothing to page here at all"))));

        assertEquals(List.of("half: neither paged (offset and limit) nor listed in NOT_PAGED"),
                problems(Map.of("half", Set.of("limit")), Map.of()));

        assertEquals(List.of("no tool was served: the guard read nothing"), problems(Map.of(), Map.of()));
    }

    @Test
    void aToolThatPagesAndIsNotListedPasses() {
        assertEquals(List.of(), problems(Map.of("pages", Set.of("offset", "limit", "id")), Map.of()));
    }

    // ---------------------------------------------------------------------------------------------

    static List<String> problems(Map<String, Set<String>> served, Map<String, Exemption> exemptions) {
        List<String> problems = new ArrayList<>();
        if (served.isEmpty()) {
            problems.add("no tool was served: the guard read nothing");
            return problems;
        }
        for (Map.Entry<String, Set<String>> tool : new TreeMap<>(served).entrySet()) {
            boolean paged = isPaged(tool.getValue());
            boolean listed = exemptions.containsKey(tool.getKey());
            if (!paged && !listed) {
                problems.add(tool.getKey() + ": neither paged (offset and limit) nor listed in NOT_PAGED");
            }
            if (paged && listed) {
                problems.add(tool.getKey() + ": listed in NOT_PAGED but takes offset and limit: remove the exemption");
            }
        }
        for (String listed : new TreeSet<>(exemptions.keySet())) {
            if (!served.containsKey(listed)) {
                problems.add(listed + ": listed in NOT_PAGED but no longer served");
            }
        }
        return problems;
    }

    private static boolean isPaged(Set<String> properties) {
        return properties.contains("offset") && properties.contains("limit");
    }

    /** Tool name to the property names its input schema declares, as the default wiring serves them. */
    private Map<String, Set<String>> served() {
        List<McpServerFeatures.SyncToolSpecification> specifications = MorpheusMcpServer.toolSpecifications(
                temporaryDirectory.resolve("guard.db").toAbsolutePath().normalize(),
                new ExternalReferenceResolverRegistry(List.of()),
                MorpheusMcpServer.unconfiguredTechnicalContext(),
                MorpheusMcpServer.deniedWriteCapability());
        Map<String, Set<String>> served = new LinkedHashMap<>();
        for (McpServerFeatures.SyncToolSpecification specification : specifications) {
            Object properties = specification.tool().inputSchema().get("properties");
            Set<String> names = new TreeSet<>();
            if (properties instanceof Map<?, ?> declared) {
                declared.keySet().forEach(name -> names.add((String) name));
            }
            served.put(specification.tool().name(), names);
        }
        return served;
    }

    private static Map<String, Exemption> exemptions() {
        Map<String, Exemption> map = new TreeMap<>();
        // One identified thing, or one write answering with what it wrote.
        one(map, "get_change", "one change of the active snapshot");
        one(map, "get_change_status", "the observable facts of one change; the lifecycle state is reported unavailable");
        one(map, "get_sync_status", "one freshness record for one project");
        one(map, "get_product_info", "fixed product metadata");
        one(map, "check_product_update", "a fixed refusal: update discovery is CLI-only and performs no I/O");
        one(map, "create_portfolio", "a write answering with the portfolio it created");
        one(map, "register_portfolio_project", "a write answering with the membership it recorded");
        one(map, "mark_portfolio_project_missing", "a write answering with the membership it changed");
        one(map, "observe_portfolio_freshness", "a write answering with the freshness observation it recorded");
        one(map, "add_cross_project_reference", "a write answering with the reference it added");
        one(map, "get_saved_view", "one saved view by identity");
        one(map, "archive_saved_view", "a write answering with the view it archived");
        one(map, "get_policy_pack", "one policy pack definition by identity");
        one(map, "create_policy_pack", "a write answering with the definition it created");
        one(map, "update_policy_pack", "a write answering with the definition it updated");
        one(map, "activate_policy_pack", "a write answering with the one activation it recorded");
        one(map, "deactivate_policy_pack", "a write answering with a fixed confirmation");
        one(map, "put_policy_override", "a write answering with the one override it recorded");
        one(map, "apply_change_lifecycle_transition", "a write answering with one mutation result");
        one(map, "resolve_external_reference", "one reference, stored and observed; its history is a count");
        one(map, "evaluate_change_transition", "one verdict on one transition of one change");
        one(map, "get_change_orchestration_state", "one orchestration state for one change");

        // A count the code enforces bounds the answer. Nothing here says the answer fits the 1 MiB frame.
        count(map, "list_policy_overrides", "overrides of one scope; PolicyPackService and the store refuse past"
                + " PolicyBudgets.MAX_OVERRIDES_PER_SCOPE (256); each carries an actor and a reason of at most 256 and 1024 characters (256 times 1280 is about 330 KB for those"
                + " two fields; the other fields were not summed, and it is not a measurement)");
        count(map, "list_policy_activations", "activations of one scope; PolicyPackService and the store refuse past"
                + " PolicyBudgets.MAX_ACTIVE_PACKS_PER_SCOPE (32); the worst case in bytes was not evaluated");
        count(map, "execute_saved_view", "one page of the stored query; QueryPage refuses a limit past"
                + " QueryBudgets.MAX_PAGE_SIZE (500) rows; the size of a row is not bounded here, so the worst case in bytes was not evaluated");

        // No bound found, or a bound above the frame. Recorded, not vouched for.
        unbounded(map, "traverse_portfolio", "maxNodes and maxLinks are bounded by PortfolioTraversalService (1000 and 5000;"
                + " the tool defaults to 250 and 1000) and truncation is declared, but a link is not small: its six identifiers"
                + " and its timestamp are about 240 bytes, and a link with one-character text fields already renders as about"
                + " 420 bytes (relation and entityType go up to 128 characters, a source locator has no bound written down), so"
                + " the declared maximum of 5000 links is above the frame. Worst case by arithmetic, not measured; whether a"
                + " portfolio holds that many references is not established");
        unbounded(map, "trace_requirement", "depth is at most MorpheusMcpToolCatalog.MAX_DEPTH (20) and"
                + " TraceabilityTraversalService stops at MAX_NODES (1000) and MAX_LINKS (5000), declaring the truncation"
                + " (ADR-0108), but the link budget is not the caller's and a link is not small: the smallest link the compact"
                + " view renders (four identifiers, one evidence id) is about 320 bytes and may carry any number of evidence"
                + " ids, so 5000 links are above the frame. Worst case by arithmetic, not measured; whether a snapshot holds"
                + " that many links is not established");
        unbounded(map, "get_change_context", "same traversal and budgets as trace_requirement (MAX_NODES 1000, MAX_LINKS"
                + " 5000, about 320 bytes for the smallest link, so above the frame at the budget), and it also returns the"
                + " requirements, constraints, decisions and tasks of one change, for which no count bound was found and whose"
                + " text fields have no size written down. Worst case by arithmetic, not measured; whether a snapshot holds"
                + " that many links is not established");
        unbounded(map, "list_saved_views", "views of one scope are refused past QueryBudgets.MAX_SAVED_VIEWS_PER_SCOPE (250)"
                + " and each carries its query definition, whose encoded expression may reach"
                + " QueryBudgets.MAX_ENCODED_EXPRESSION_BYTES (16 KiB): 250 times 16 KiB is above the 1 MiB frame."
                + " Worst case by arithmetic, not measured");
        unbounded(map, "evaluate_policies", "at most MAX_ACTIVE_PACKS_PER_SCOPE (32) packs and MAX_DRY_RUN_EVALUATIONS (4096)"
                + " rules are evaluated, but each rule result carries an evidence list of up to"
                + " PolicyBudgets.MAX_CONSTRAINT_EVALUATIONS_PER_FACT (1024) entries (ADR-0108): the count budget is above the frame");
        unbounded(map, "dry_run_policy_pack", "one version of at most 4096 rules is evaluated, but each rule result carries an"
                + " evidence list of up to 1024 entries (ADR-0108): the count budget is above the frame");
        unbounded(map, "export_saved_view", "complete by contract; refused past MAX_EXPORT_ROWS (10000) rows and"
                + " QueryBudgets.MAX_EXPORT_BYTES (10 MiB), and 10 MiB is above the 1 MiB frame: a valid export of a few MiB"
                + " cannot be answered and the transport's advice cannot be followed on it");
        unbounded(map, "reason_with_evidence", "the request is bounded (MAX_EVIDENCE 256, MAX_ADAPTERS 8, MAX_CLAIMS 256) and"
                + " by the inbound 1 MiB frame, but the answer repeats each PUBLISHED_FACT evidence twice (in evidence and in facts): a request near"
                + " the frame can overflow it on the way out");
        unbounded(map, "get_augmented_requirement_context", "the request carries a tokenBudget of at most"
                + " TechnicalContextOptions.MAX_TOKEN_BUDGET (100000): a token budget, not a byte budget, and not measured"
                + " against the frame");
        unbounded(map, "get_augmented_change_context", "the request carries a tokenBudget of at most"
                + " TechnicalContextOptions.MAX_TOKEN_BUDGET (100000): a token budget, not a byte budget, and not measured"
                + " against the frame");
        unbounded(map, "export_query", "always complete by contract (ADR-0102, MCP-3): refused past MAX_EXPORT_ROWS (10000) rows and"
                + " MAX_EXPORT_BYTES (10 MiB), both above the 1 MiB MCP frame, so a valid large export cannot be answered and"
                + " the transport's advice cannot be followed on it. A recognised residual, not a guarantee; the ways round it (execute_query"
                + " paged, the CLI, HTTP) are written in ADR-0102");
        unbounded(map, "list_policy_packs", "every pack definition of the registry: PolicyPackService.create has no ceiling"
                + " and nothing deletes a pack; each element is metadata. A recognised residual, not a guarantee");
        unbounded(map, "list_external_references", "the references one owner declares in the active snapshot: no ceiling,"
                + " but replaced at each publication rather than appended to. A recognised residual, not a guarantee");
        unbounded(map, "get_portfolio_overview", "PortfolioQueryService.overview returns the memberships and freshness of"
                + " one portfolio, and also the conflicts and the count derived from ALL its cross-project references, which"
                + " add_cross_project_reference only ever adds to. MAX_PORTFOLIO_PROJECTS bounds portfolio queries, not"
                + " registration, and nothing removes a membership. A recognised residual");
        unbounded(map, "get_composition_status", "the composition state of the active snapshot, its conflicts included,"
                + " unpaged; list_composition_conflicts pages the same conflicts. Replaced per snapshot, no ceiling");
        unbounded(map, "get_blocking_conditions", "the findings of one change, recomputed from the active snapshot: no"
                + " ceiling found, not append-only. A recognised residual, not a guarantee");
        unbounded(map, "list_reasoning_adapters", "the adapters registered when the server is wired; a fixed set, not data"
                + " that accumulates, and with no ceiling written down");
        return Map.copyOf(map);
    }

    private static void one(Map<String, Exemption> map, String tool, String why) {
        map.put(tool, new Exemption(Reason.ONE_RESULT, why + " (a single answer, nothing to page)"));
    }

    private static void count(Map<String, Exemption> map, String tool, String why) {
        map.put(tool, new Exemption(Reason.BOUNDED_IN_COUNT, why));
    }

    private static void unbounded(Map<String, Exemption> map, String tool, String why) {
        map.put(tool, new Exemption(Reason.UNBOUNDED_ACKNOWLEDGED, why));
    }
}
