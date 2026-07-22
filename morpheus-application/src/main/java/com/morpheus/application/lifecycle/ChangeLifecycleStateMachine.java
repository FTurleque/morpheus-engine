package com.morpheus.application.lifecycle;

import com.morpheus.domain.change.lifecycle.ChangeAbandonmentReason;
import com.morpheus.domain.change.lifecycle.ChangeLifecycle;
import com.morpheus.domain.change.lifecycle.ChangeLifecycleState;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Deterministic business lifecycle validator. */
public final class ChangeLifecycleStateMachine {
    private static final Set<ChangeLifecycleState> ABANDONABLE_STATES = EnumSet.of(
            ChangeLifecycleState.DRAFT,
            ChangeLifecycleState.PROPOSED,
            ChangeLifecycleState.SPECIFIED,
            ChangeLifecycleState.DESIGNED,
            ChangeLifecycleState.PLANNED,
            ChangeLifecycleState.IMPLEMENTING,
            ChangeLifecycleState.VERIFYING);

    public ChangeLifecycleTransitionDecision evaluate(ChangeLifecycleTransitionRequest request) {
        ChangeLifecycle source = request.source();
        ChangeLifecycleState from = source.state();
        ChangeLifecycleState to = request.targetState();

        if (from == to) return deny(ChangeLifecycleBlocker.INVALID_TRANSITION);
        if (to == ChangeLifecycleState.ABANDONED) return abandon(source, request.abandonmentReason());
        if (from == ChangeLifecycleState.ABANDONED) {
            return to == ChangeLifecycleState.PROPOSED
                    ? allow(source, to)
                    : deny(ChangeLifecycleBlocker.INVALID_TRANSITION);
        }
        if (from == ChangeLifecycleState.ARCHIVED) {
            return deny(ChangeLifecycleBlocker.ARCHIVED_REOPEN_NOT_ALLOWED);
        }

        ChangeLifecycleTransitionDecision nominal = nominal(request);
        if (nominal != null) return nominal;

        ChangeLifecycleTransitionDecision backward = backward(request);
        if (backward != null) return backward;

        return deny(ChangeLifecycleBlocker.INVALID_TRANSITION);
    }

    private ChangeLifecycleTransitionDecision nominal(ChangeLifecycleTransitionRequest request) {
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
            return decide(source, to, blockers);
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
            return decide(source, to, blockers);
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
            return decide(source, to, blockers);
        }

        if (from == ChangeLifecycleState.COMPLETED && to == ChangeLifecycleState.ARCHIVED) return allow(source, to);
        return null;
    }

    private ChangeLifecycleTransitionDecision backward(ChangeLifecycleTransitionRequest request) {
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

    private ChangeLifecycleTransitionDecision abandon(
            ChangeLifecycle source,
            Optional<ChangeAbandonmentReason> reason) {
        if (!ABANDONABLE_STATES.contains(source.state())) return deny(ChangeLifecycleBlocker.INVALID_TRANSITION);
        if (reason.isEmpty()) return deny(ChangeLifecycleBlocker.ABANDONMENT_REASON_REQUIRED);
        return ChangeLifecycleTransitionDecision.allowed(
                ChangeLifecycle.abandoned(source.changeId(), reason.orElseThrow()));
    }

    private ChangeLifecycleTransitionDecision decide(
            ChangeLifecycle source,
            ChangeLifecycleState target,
            List<ChangeLifecycleBlocker> blockers) {
        return blockers.isEmpty()
                ? allow(source, target)
                : ChangeLifecycleTransitionDecision.blocked(blockers);
    }

    private ChangeLifecycleTransitionDecision allow(ChangeLifecycle source, ChangeLifecycleState target) {
        return ChangeLifecycleTransitionDecision.allowed(ChangeLifecycle.of(source.changeId(), target));
    }

    private ChangeLifecycleTransitionDecision deny(ChangeLifecycleBlocker blocker) {
        return ChangeLifecycleTransitionDecision.blocked(List.of(blocker));
    }
}
