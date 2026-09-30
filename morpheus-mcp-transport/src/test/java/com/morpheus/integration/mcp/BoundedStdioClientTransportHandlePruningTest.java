package com.morpheus.integration.mcp;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pins the retention rule for observed MCP peer processes on the pure function that carries it: a handle is
 * released once its process is dead, never while it is alive, and the root is never released.
 */
class BoundedStdioClientTransportHandlePruningTest {
    private static final long ROOT = 100L;

    @Test
    void aDeadDescendantIsReleasedAndALiveOneIsRetained() {
        Map<Long, String> observed = observed(Map.of(ROOT, "root", 101L, "dead-child", 102L, "live-child"));

        List<String> live = BoundedStdioClientTransport.pruneDeadHandles(
                observed, ROOT, Set.of("root", "live-child")::contains);

        assertEquals(Set.of(ROOT, 102L), observed.keySet());
        assertEquals(Set.of("root", "live-child"), Set.copyOf(live));
    }

    @Test
    void theRootIsRetainedEvenWhenItsProcessIsDeadButIsNotReturnedAsLive() {
        Map<Long, String> observed = observed(Map.of(ROOT, "root", 101L, "live-orphan"));

        List<String> live = BoundedStdioClientTransport.pruneDeadHandles(
                observed, ROOT, "live-orphan"::equals);

        assertEquals(Set.of(ROOT, 101L), observed.keySet());
        assertEquals(List.of("live-orphan"), live);
    }

    @Test
    void aHandleObservedUnderAReusedPidIsNotReleasedInPlaceOfTheDeadOne() {
        Map<Long, String> observed = observed(Map.of(ROOT, "root", 101L, "dead-child"));

        List<String> live = BoundedStdioClientTransport.pruneDeadHandles(observed, ROOT, handle -> {
            if (handle.equals("dead-child")) {
                observed.put(101L, "new-process-same-pid");
                return false;
            }
            return true;
        });

        assertEquals("new-process-same-pid", observed.get(101L));
        assertEquals(List.of("root"), live);
    }

    private static Map<Long, String> observed(Map<Long, String> initial) {
        return new ConcurrentHashMap<>(initial);
    }
}
