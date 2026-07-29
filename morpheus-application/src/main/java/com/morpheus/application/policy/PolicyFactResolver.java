package com.morpheus.application.policy;

/** Read-only bridge from declarative policy rules to already-owned MORPHEUS facts. */
@FunctionalInterface
public interface PolicyFactResolver {
    PolicyEvaluation.Fact resolve(PolicyScope scope, PolicyRule rule);
}