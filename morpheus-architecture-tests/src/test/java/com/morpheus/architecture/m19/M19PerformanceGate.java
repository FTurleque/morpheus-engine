package com.morpheus.architecture.m19;

import com.morpheus.application.store.ProjectStoreEntry;
import com.morpheus.application.sync.IncrementalSyncService;
import com.morpheus.application.sync.LocalSourceInventoryScanner;
import com.morpheus.application.sync.SyncPlan;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.source.SourceLocator;
import com.morpheus.store.memory.MemorySpecificationKnowledgeStore;
import com.morpheus.store.memory.MemorySyncStateStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Explicit M19 benchmark gate. The class name intentionally does not match the default Surefire *Test patterns;
 * run it only through the M19 validator with -Dtest=M19PerformanceGate.
 */
class M19PerformanceGate {
    private static final long INVENTORY_SCAN_BUDGET_NANOS = 20_000_000_000L;
    private static final long INCREMENTAL_PLAN_BUDGET_NANOS = 2_000_000_000L;
    private static final long HEAP_BUDGET_BYTES = 768L * 1024L * 1024L;
    private static final int WARMUP_ITERATIONS = 1;
    private static final int MEASURED_ITERATIONS = 5;
    private static final Instant T0 = Instant.parse("2026-07-26T18:00:00Z");

    @TempDir
    Path tempDir;

    @Test
    void largeInventoryAndIncrementalPlanStayWithinFrozenBudgets() throws Exception {
        Path workspace = tempDir.resolve("workspace");
        var fixture = M19LargeFixtureSupport.generateSourceFixture(
                workspace,
                M19LargeFixtureSupport.GATE_SOURCE_FILES,
                M19LargeFixtureSupport.SEED);
        assertEquals(M19LargeFixtureSupport.GATE_SOURCE_FILES, fixture.fileCount());
        assertTrue(fixture.totalBytes() >= 10L * 1024L * 1024L, "gate fixture must contain at least 10 MiB");

        ProjectSpecificationId projectId = new ProjectSpecificationId(
                M19LargeFixtureSupport.deterministicIdentity(19, 1));
        var projectStore = new MemorySpecificationKnowledgeStore();
        projectStore.putProject(new ProjectStoreEntry(projectId, SourceLocator.file(workspace.toString())));
        var syncStore = new MemorySyncStateStore(projectStore);
        var syncService = new IncrementalSyncService(syncStore);
        var scanner = new LocalSourceInventoryScanner();

        var baselineScan = scanner.scan(workspace, projectId, Optional.of("m19-baseline"), T0, List.of());
        assertTrue(baselineScan.complete(), () -> "baseline scan failures: " + baselineScan.failures());
        SyncPlan baselinePlan = syncService.prepare(baselineScan, SyncPlan.Trigger.manual(), T0);
        assertEquals(SyncPlan.SyncMode.FULL_REBUILD, baselinePlan.mode());
        syncService.complete(baselinePlan, T0.plusSeconds(1));

        runWarmup(() -> scanner.scan(workspace, projectId, Optional.of("m19-baseline"), T0.plusSeconds(2), List.of()));
        List<Long> scanSamples = measure(() -> {
            var scan = scanner.scan(workspace, projectId, Optional.of("m19-baseline"), T0.plusSeconds(3), List.of());
            assertTrue(scan.complete(), () -> "large scan failures: " + scan.failures());
        });
        long scanP95 = M19LargeFixtureSupport.percentile95Nanos(scanSamples);
        metric("inventory_scan_p95_ms", scanP95 / 1_000_000L);
        assertTrue(scanP95 <= INVENTORY_SCAN_BUDGET_NANOS,
                () -> "inventory scan p95 exceeded frozen 20s budget: " + scanP95 / 1_000_000L + " ms");

        M19LargeFixtureSupport.mutateDeterministically(
                workspace,
                M19LargeFixtureSupport.INCREMENTAL_CHANGED_FILES,
                M19LargeFixtureSupport.SEED);
        var changedScan = scanner.scan(workspace, projectId, Optional.of("m19-changed"), T0.plusSeconds(4), List.of());
        assertTrue(changedScan.complete(), () -> "changed scan failures: " + changedScan.failures());

        runWarmup(() -> syncService.prepare(changedScan, SyncPlan.Trigger.manual(), T0.plusSeconds(5)));
        List<Long> planSamples = measure(() -> {
            SyncPlan plan = syncService.prepare(changedScan, SyncPlan.Trigger.manual(), T0.plusSeconds(6));
            assertEquals(SyncPlan.SyncMode.INCREMENTAL, plan.mode());
            assertEquals(M19LargeFixtureSupport.INCREMENTAL_CHANGED_FILES,
                    plan.diff().orElseThrow().effectiveChanges().size());
        });
        long planP95 = M19LargeFixtureSupport.percentile95Nanos(planSamples);
        metric("incremental_plan_p95_ms", planP95 / 1_000_000L);
        assertTrue(planP95 <= INCREMENTAL_PLAN_BUDGET_NANOS,
                () -> "incremental diff/plan p95 exceeded frozen 2s budget: " + planP95 / 1_000_000L + " ms");

        long maxHeap = Runtime.getRuntime().maxMemory();
        metric("max_heap_mib", maxHeap / (1024L * 1024L));
        assertTrue(maxHeap <= HEAP_BUDGET_BYTES,
                () -> "benchmark JVM max heap exceeds frozen 768 MiB budget: " + maxHeap);
    }

    private void runWarmup(ThrowingRunnable operation) throws Exception {
        for (int index = 0; index < WARMUP_ITERATIONS; index++) {
            operation.run();
        }
    }

    private List<Long> measure(ThrowingRunnable operation) throws Exception {
        List<Long> samples = new ArrayList<>(MEASURED_ITERATIONS);
        for (int index = 0; index < MEASURED_ITERATIONS; index++) {
            long start = System.nanoTime();
            operation.run();
            samples.add(System.nanoTime() - start);
        }
        return List.copyOf(samples);
    }

    private void metric(String name, long value) {
        System.out.println("M19_METRIC " + name + "=" + value);
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
