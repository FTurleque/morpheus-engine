package com.morpheus.mcp;

import com.morpheus.application.operability.OperationalRecorder;
import com.morpheus.application.reference.ExternalReferenceResolverRegistry;
import com.morpheus.mcp.server.MorpheusMcpServer;
import io.modelcontextprotocol.server.McpServerFeatures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every tool the server publishes is served by exactly one class, and the class is read from the handler the server
 * holds.
 *
 * <p>A tool's call handler is a lambda, and the JDK names the class it generates for one after the class that
 * declares it. That class is the tool's owner: the capability class whose {@code specifications()} built the
 * handler, or {@code MorpheusMcpServer} for the tools it wraps around {@code MorpheusMcpToolService}. A tool name
 * published twice fails, and so does a tool the server class owns unless it is one it still serves itself.</p>
 *
 * <p>{@link #SERVED_BY_THE_SERVER_CLASS} only shrinks: a tool extracted into a capability class leaves it.</p>
 */
class ServedToolsAreClaimedOnceTest {

    private static final String LAMBDA_MARKER = "$$Lambda";

    /** The tools {@code MorpheusMcpServer} wraps around {@code MorpheusMcpToolService}. */
    static final Set<String> SERVED_BY_THE_SERVER_CLASS = Set.of(
            "get_current_specification",
            "get_acceptance_criteria",
            "get_specification_context",
            "get_sync_status");

    @TempDir
    Path temporaryDirectory;

    @Test
    void everyPublishedToolIsServedByExactlyOneClass() {
        List<McpServerFeatures.SyncToolSpecification> served = MorpheusMcpServer.toolSpecifications(
                temporaryDirectory.resolve("claims.db").toAbsolutePath().normalize(),
                new ExternalReferenceResolverRegistry(List.of()),
                MorpheusMcpServer.unconfiguredTechnicalContext(),
                MorpheusMcpServer.deniedWriteCapability(),
                OperationalRecorder.noop());
        assertTrue(served.size() > SERVED_BY_THE_SERVER_CLASS.size(), "the server publishes too few tools to be read");

        Map<String, List<String>> owners = new TreeMap<>();
        for (McpServerFeatures.SyncToolSpecification specification : served) {
            owners.computeIfAbsent(specification.tool().name(), name -> new ArrayList<>())
                    .add(owner(specification));
        }

        Map<String, List<String>> publishedTwice = new TreeMap<>();
        Set<String> ownedByTheServer = new TreeSet<>();
        owners.forEach((tool, owning) -> {
            if (owning.size() > 1) {
                publishedTwice.put(tool, owning);
            } else if (owning.getFirst().equals(MorpheusMcpServer.class.getSimpleName())) {
                ownedByTheServer.add(tool);
            }
        });
        assertEquals(Map.of(), publishedTwice, "tool names that two specifications publish, with their owners");
        assertEquals(new TreeSet<>(SERVED_BY_THE_SERVER_CLASS), ownedByTheServer,
                "the tools whose handler the server class declares must be exactly those it still serves itself: a "
                        + "tool extracted into a capability class leaves this list");
    }

    /** The top-level class that declares the lambda of the tool's call handler. */
    private static String owner(McpServerFeatures.SyncToolSpecification specification) {
        String name = specification.callHandler().getClass().getName();
        int lambda = name.indexOf(LAMBDA_MARKER);
        assertTrue(lambda > 0, "the call handler of " + specification.tool().name()
                + " is not a lambda, so its owner cannot be read: " + name);
        String declaring = name.substring(0, lambda);
        int nested = declaring.indexOf('$');
        String topLevel = nested < 0 ? declaring : declaring.substring(0, nested);
        return topLevel.substring(topLevel.lastIndexOf('.') + 1);
    }
}
