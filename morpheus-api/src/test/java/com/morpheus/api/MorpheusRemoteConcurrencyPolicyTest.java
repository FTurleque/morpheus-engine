package com.morpheus.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MorpheusRemoteConcurrencyPolicyTest {

    @Test
    void privilegedBudgetTargetsMutationsWithoutPenalizingAdminReads() {
        assertTrue(MorpheusRemoteHttpServer.usesPrivilegedConcurrency("POST", "/api/v1/projects"));
        assertTrue(MorpheusRemoteHttpServer.usesPrivilegedConcurrency("POST", "/api/v1/projects/p1/sync"));
        assertTrue(MorpheusRemoteHttpServer.usesPrivilegedConcurrency("POST", "/api/v1/server/backups"));
        assertTrue(MorpheusRemoteHttpServer.usesPrivilegedConcurrency("POST", "/api/v1/provider-plugins/probe"));

        assertFalse(MorpheusRemoteHttpServer.usesPrivilegedConcurrency("GET", "/api/v1/health"));
        assertFalse(MorpheusRemoteHttpServer.usesPrivilegedConcurrency("GET", "/api/v1/metrics"));
        assertFalse(MorpheusRemoteHttpServer.usesPrivilegedConcurrency("POST", "/api/v1/reasoning/analyze"));
    }

    @Test
    void privilegedCapacityPreservesReadHeadroomAsConcurrencyGrows() {
        assertEquals(1, MorpheusRemoteHttpServer.privilegedConcurrencyLimit(1));
        assertEquals(1, MorpheusRemoteHttpServer.privilegedConcurrencyLimit(4));
        assertEquals(2, MorpheusRemoteHttpServer.privilegedConcurrencyLimit(8));
        assertEquals(16, MorpheusRemoteHttpServer.privilegedConcurrencyLimit(64));
        assertEquals(64, MorpheusRemoteHttpServer.privilegedConcurrencyLimit(256));
    }

    /**
     * Privileged capacity never grows past a quarter of the request budget, so reads keep three quarters of it
     * even when every privileged slot is held by a mutation that will never finish.
     */
    @Test
    void blockedMutationsCanNeverClaimMoreThanAQuarterOfTheRequestBudget() {
        for (int maxConcurrent : new int[]{4, 8, 16, 64, 256, MorpheusRemoteHttpServer.MAX_CONCURRENT_REQUESTS}) {
            int privileged = MorpheusRemoteHttpServer.privilegedConcurrencyLimit(maxConcurrent);
            assertTrue(privileged * 4 <= maxConcurrent + 3,
                    () -> "privileged capacity must stay within a quarter of " + maxConcurrent);
            assertTrue(privileged < maxConcurrent,
                    () -> "a saturated privileged lane must leave read headroom at " + maxConcurrent);
        }
    }

    /**
     * The status lane is deliberately flat. It is not request work, and an operator polling a saturated server
     * needs a handful of concurrent reads rather than a share of a budget that is by then fully committed.
     */
    @Test
    void theStatusLaneIsBoundedIndependentlyOfRequestCapacity() {
        assertEquals(8, MorpheusRemoteHttpServer.observabilityConcurrencyLimit(1));
        assertEquals(8, MorpheusRemoteHttpServer.observabilityConcurrencyLimit(64));
        assertEquals(8, MorpheusRemoteHttpServer.observabilityConcurrencyLimit(
                MorpheusRemoteHttpServer.MAX_CONCURRENT_REQUESTS));
        assertThrows(IllegalArgumentException.class,
                () -> MorpheusRemoteHttpServer.observabilityConcurrencyLimit(0));
    }

    @Test
    void authenticationHasIndependentBoundedCapacity() {
        assertEquals(4, MorpheusRemoteHttpServer.authenticationConcurrencyLimit(1));
        assertEquals(8, MorpheusRemoteHttpServer.authenticationConcurrencyLimit(8));
        assertEquals(64, MorpheusRemoteHttpServer.authenticationConcurrencyLimit(64));
        assertEquals(64, MorpheusRemoteHttpServer.authenticationConcurrencyLimit(512));
    }
}
