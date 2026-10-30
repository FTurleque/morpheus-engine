package com.morpheus.domain.change.lifecycle;


import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** A requested lifecycle transition, with the facts and the policy it is decided on. */
public record ChangeLifecycleTransitionRequest(
        ChangeLifecycle source,
        ChangeLifecycleState targetState,
        ChangeLifecycleFacts facts,
        ChangeLifecyclePolicy policy,
        Optional<ChangeAbandonmentReason> abandonmentReason) {

    public ChangeLifecycleTransitionRequest {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(targetState, "targetState");
        Objects.requireNonNull(facts, "facts");
        Objects.requireNonNull(policy, "policy");
        abandonmentReason = Objects.requireNonNull(abandonmentReason, "abandonmentReason");
    }

    public static ChangeLifecycleTransitionRequest to(
            ChangeLifecycle source,
            ChangeLifecycleState targetState,
            ChangeLifecycleFacts facts,
            ChangeLifecyclePolicy policy) {
        return new ChangeLifecycleTransitionRequest(source, targetState, facts, policy, Optional.empty());
    }

    private static final Set<ChangeLifecycleState> ABANDONABLE_STATES = EnumSet.of(
            ChangeLifecycleState.DRAFT,
            ChangeLifecycleState.PROPOSED,
            ChangeLifecycleState.SPECIFIED,
            ChangeLifecycleState.DESIGNED,
            ChangeLifecycleState.PLANNED,
            ChangeLifecycleState.IMPLEMENTING,
            ChangeLifecycleState.VERIFYING);

    /** Decides the transition by MORPHEUS lifecycle rules: the nominal path, then the canonical backward steps. */
    public ChangeLifecycleTransitionDecision decide() {
        ChangeLifecycleState from = source.state();
        ChangeLifecycleState to = targetState;

        if (from == to) return deny(ChangeLifecycleBlocker.INVALID_TRANSITION);
        if (to == ChangeLifecycleState.ABANDONED) return abandon(source, abandonmentReason);
        if (from == ChangeLifecycleState.ABANDONED) {
            return to == ChangeLifecycleState.PROPOSED
                    ? allow(source, to)
                    : deny(ChangeLifecycleBlocker.INVALID_TRANSITION);
        }
        if (from == ChangeLifecycleState.ARCHIVED) {
            return deny(ChangeLifecycleBlocker.ARCHIVED_REOPEN_NOT_ALLOWED);
        }

        ChangeLifecycleTransitionDecision nominal = nominal(this);
        if (nominal != null) return nominal;

        ChangeLifecycleTransitionDecision backward = backward(this);
        if (backward != null) return backward;

        return deny(ChangeLifecycleBlocker.INVALID_TRANSITION);
    }

    private static ChangeLifecycleTransitionDecision nominal(ChangeLifecycleTransitionRequest request) {
        ChangeLifecycle source = request.source();
        ChangeLifecycleFacts facts = request.facts();
        ChangeLifecycleState from = source.state();
        ChangeLifecycleState to = request.targetState();

        if (from == ChangeLifecycleState.DRAFT && to == ChangeLifecycleState.PROPOSED) return allow(source, to);

        if (from == ChangeLifecycleState.PROPOSED && to == ChangeLifecycleState.SPECIFIED) {
            List<ChangeLifecycleBlocker> blockers = new ArrayList<>();
            if (!facts.requirementsIdentified()) blockers.add(ChangeLifecycleBlocker.MISSING_REQUIREMENTS);
            if (!facts.criticalConstraintsKnown()) blockers.add(ChangeLifecycleBlocker.UNKNOWN_CRITICAL_CONSTRAINTS);
            if (!facts.acceptanceCriteriaDefined()) blockers.add(ChangeLifecycleBlocker.MISSING_ACCEPTANCE_CRITERIA);
            return allowedUnlessBlocked(source, to, blockers);
        }

        if (from == ChangeLifecycleState.SPECIFIED && to == ChangeLifecycleState.DESIGNED) {
            return facts.designRequired() && !facts.designDecisionsAvailable()
                    ? deny(ChangeLifecycleBlocker.MISSING_DESIGN)
                    : allow(source, to);
        }

        if (from == ChangeLifecycleState.SPECIFIED && to == ChangeLifecycleState.PLANNED) {
            List<ChangeLifecycleBlocker> blockers = new ArrayList<>();
            if (facts.designRequired()) blockers.add(ChangeLifecycleBlocker.DESIGN_REQUIRED);
            if (!facts.planPresent()) blockers.add(ChangeLifecycleBlocker.MISSING_PLAN);
            return allowedUnlessBlocked(source, to, blockers);
        }

        if (from == ChangeLifecycleState.DESIGNED && to == ChangeLifecycleState.PLANNED) {
            return facts.planPresent() ? allow(source, to) : deny(ChangeLifecycleBlocker.MISSING_PLAN);
        }

        if (from == ChangeLifecycleState.PLANNED && to == ChangeLifecycleState.IMPLEMENTING) {
            return facts.knownBlocker() ? deny(ChangeLifecycleBlocker.KNOWN_BLOCKER) : allow(source, to);
        }

        if (from == ChangeLifecycleState.IMPLEMENTING && to == ChangeLifecycleState.VERIFYING) return allow(source, to);

        if (from == ChangeLifecycleState.VERIFYING && to == ChangeLifecycleState.COMPLETED) {
            List<ChangeLifecycleBlocker> blockers = new ArrayList<>();
            if (facts.blockingAcceptanceCriterionFailed()) {
                blockers.add(ChangeLifecycleBlocker.BLOCKING_ACCEPTANCE_CRITERION_FAILED);
            }
            if (facts.blockingAcceptanceCriterionUnverified()) {
                blockers.add(ChangeLifecycleBlocker.BLOCKING_ACCEPTANCE_CRITERION_UNVERIFIED);
            }
            return allowedUnlessBlocked(source, to, blockers);
        }

        if (from == ChangeLifecycleState.COMPLETED && to == ChangeLifecycleState.ARCHIVED) return allow(source, to);
        return null;
    }

    private static ChangeLifecycleTransitionDecision backward(ChangeLifecycleTransitionRequest request) {
        ChangeLifecycleState from = request.source().state();
        ChangeLifecycleState to = request.targetState();
        boolean canonical =
                (from == ChangeLifecycleState.SPECIFIED && to == ChangeLifecycleState.PROPOSED)
                || (from == ChangeLifecycleState.DESIGNED && to == ChangeLifecycleState.SPECIFIED)
                || (from == ChangeLifecycleState.PLANNED && to == ChangeLifecycleState.DESIGNED)
                || (from == ChangeLifecycleState.IMPLEMENTING && to == ChangeLifecycleState.PLANNED)
                || (from == ChangeLifecycleState.VERIFYING && to == ChangeLifecycleState.IMPLEMENTING)
                || (from == ChangeLifecycleState.COMPLETED && to == ChangeLifecycleState.VERIFYING);

        if (!canonical) return null;
        if (!request.policy().allowBackwardTransitions()) {
            return deny(ChangeLifecycleBlocker.BACKWARD_TRANSITION_DISABLED);
        }
        if (from == ChangeLifecycleState.COMPLETED
                && !request.policy().allowCompletedReopen()) {
            return deny(ChangeLifecycleBlocker.COMPLETED_REOPEN_DISABLED);
        }
        return allow(request.source(), to);
    }

    private static ChangeLifecycleTransitionDecision abandon(
            ChangeLifecycle source,
            Optional<ChangeAbandonmentReason> reason) {
        if (!ABANDONABLE_STATES.contains(source.state())) return deny(ChangeLifecycleBlocker.INVALID_TRANSITION);
        if (reason.isEmpty()) return deny(ChangeLifecycleBlocker.ABANDONMENT_REASON_REQUIRED);
        return new ChangeLifecycleTransitionDecision.Allowed(
                ChangeLifecycle.abandoned(source.changeId(), reason.orElseThrow()));
    }

    private static ChangeLifecycleTransitionDecision allowedUnlessBlocked(
            ChangeLifecycle source,
            ChangeLifecycleState target,
            List<ChangeLifecycleBlocker> blockers) {
        return blockers.isEmpty()
                ? allow(source, target)
                : new ChangeLifecycleTransitionDecision.Blocked(blockers);
    }

    private static ChangeLifecycleTransitionDecision allow(ChangeLifecycle source, ChangeLifecycleState target) {
        return new ChangeLifecycleTransitionDecision.Allowed(ChangeLifecycle.of(source.changeId(), target));
    }

    private static ChangeLifecycleTransitionDecision deny(ChangeLifecycleBlocker blocker) {
        return new ChangeLifecycleTransitionDecision.Blocked(List.of(blocker));
    }
}
