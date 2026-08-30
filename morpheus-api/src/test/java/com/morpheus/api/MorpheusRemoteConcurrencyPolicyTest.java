package com.morpheus.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    @Test
    void authenticationHasIndependentBoundedCapacity() {
        assertEquals(4, MorpheusRemoteHttpServer.authenticationConcurrencyLimit(1));
        assertEquals(8, MorpheusRemoteHttpServer.authenticationConcurrencyLimit(8));
        assertEquals(64, MorpheusRemoteHttpServer.authenticationConcurrencyLimit(64));
        assertEquals(64, MorpheusRemoteHttpServer.authenticationConcurrencyLimit(512));
    }
}
