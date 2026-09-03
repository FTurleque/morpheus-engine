package com.morpheus.application.policy;

import com.morpheus.application.store.ExternalReferenceStore;
import com.morpheus.application.store.PolicyPackStore;
import com.morpheus.application.store.PortfolioStore;
import com.morpheus.application.store.SnapshotBusinessContentStore;
import com.morpheus.application.store.SpecificationKnowledgeStore;
import com.morpheus.application.store.TraceabilityStore;
import com.morpheus.application.store.VersionedRequirementStore;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The policy service graph is a wiring decision, and wiring reads nothing.
 *
 * <p>Three adapters used to rebuild this graph by hand, and a wiring decision kept in three places drifts in
 * the one nobody reads. Assembling it must also stay free of I/O: an adapter builds this while it is still
 * opening resources, so a factory that queried a store here would do it before the runtime exists.</p>
 *
 * <p>Every port is a proxy that fails on any call, so the second property is asserted rather than assumed --
 * and application tests keep depending on no adapter module.</p>
 */
class PolicyRuntimeServicesTest {

    @Test
    void wiresBothServicesWithoutReadingFromAnyStore() {
        PolicyRuntimeServices services = PolicyRuntimeServices.from(
                unusedPort(SpecificationKnowledgeStore.class),
                unusedPort(VersionedRequirementStore.class),
                unusedPort(SnapshotBusinessContentStore.class),
                unusedPort(TraceabilityStore.class),
                unusedPort(ExternalReferenceStore.class),
                unusedPort(PortfolioStore.class),
                unusedPort(PolicyPackStore.class));

        assertNotNull(services.registry(), "the pack registry must be wired");
        assertNotNull(services.evaluation(), "the evaluation service must be wired");
    }

    @Test
    void aMissingPortIsRejectedRatherThanDeferredToTheFirstQuery() {
        assertThrows(NullPointerException.class, () -> PolicyRuntimeServices.from(
                unusedPort(SpecificationKnowledgeStore.class),
                unusedPort(VersionedRequirementStore.class),
                unusedPort(SnapshotBusinessContentStore.class),
                unusedPort(TraceabilityStore.class),
                unusedPort(ExternalReferenceStore.class),
                unusedPort(PortfolioStore.class),
                null));
    }

    @Test
    void theRecordItselfRefusesAnIncompleteGraph() {
        PolicyRuntimeServices services = PolicyRuntimeServices.from(
                unusedPort(SpecificationKnowledgeStore.class),
                unusedPort(VersionedRequirementStore.class),
                unusedPort(SnapshotBusinessContentStore.class),
                unusedPort(TraceabilityStore.class),
                unusedPort(ExternalReferenceStore.class),
                unusedPort(PortfolioStore.class),
                unusedPort(PolicyPackStore.class));

        assertThrows(NullPointerException.class,
                () -> new PolicyRuntimeServices(null, services.evaluation()));
        assertThrows(NullPointerException.class,
                () -> new PolicyRuntimeServices(services.registry(), null));
    }

    /** A port that fails on any call: wiring is allowed to hold it, never to use it. */
    private static <T> T unusedPort(Class<T> type) {
        return type.cast(Proxy.newProxyInstance(
                PolicyRuntimeServicesTest.class.getClassLoader(),
                new Class<?>[]{type},
                (proxy, method, args) -> {
                    throw new AssertionError(
                            "assembling the policy graph must not call "
                                    + type.getSimpleName() + "." + method.getName());
                }));
    }
}
