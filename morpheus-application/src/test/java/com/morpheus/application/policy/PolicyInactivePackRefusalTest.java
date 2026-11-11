package com.morpheus.application.policy;

import com.morpheus.application.store.EntityStateException;
import com.morpheus.domain.project.ProjectSpecificationId;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A pack that is not active in the scope is a state refusal, named for the pack, and nothing is evaluated or written.
 *
 * <p>The store answers only that no activation exists; any other call fails the test.</p>
 */
class PolicyInactivePackRefusalTest {
    private final PolicyScope scope = new PolicyScope.Project(ProjectSpecificationId.generate());
    private final PolicyIds.PackId packId = PolicyIds.PackId.generate();
    private final PolicyPackStore store = noActivation();

    @Test
    void evaluatingAPackThatIsNotActiveIsAStateRefusal() {
        PolicyEvaluationService evaluation = new PolicyEvaluationService(store, (ignoredScope, rule) -> {
            throw new AssertionError("no rule is evaluated for an inactive pack");
        });

        EntityStateException refused = assertThrows(EntityStateException.class,
                () -> evaluation.evaluatePack(scope, packId));

        assertEquals("policy pack is not active in scope: " + packId, refused.getMessage());
    }

    @Test
    void deactivatingOrOverridingAPackThatIsNotActiveIsAStateRefusal() {
        PolicyPackService registry = new PolicyPackService(store, Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));

        EntityStateException deactivate = assertThrows(EntityStateException.class,
                () -> registry.deactivate(scope, packId, 1L, "maintainer", "retire"));
        assertEquals("policy pack is not active in scope: " + packId, deactivate.getMessage());

        EntityStateException override = assertThrows(EntityStateException.class,
                () -> registry.putOverride(scope, packId, PolicyIds.RuleId.generate(),
                        PolicyConfiguration.OverrideMode.DISABLE, 0L, "maintainer", "mute"));
        assertEquals("policy pack must be active before adding an override: " + packId, override.getMessage());
    }

    private PolicyPackStore noActivation() {
        return (PolicyPackStore) Proxy.newProxyInstance(
                PolicyInactivePackRefusalTest.class.getClassLoader(),
                new Class<?>[]{PolicyPackStore.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("findActivation")) {
                        return Optional.empty();
                    }
                    throw new AssertionError("an inactive pack must not reach PolicyPackStore." + method.getName());
                });
    }
}
