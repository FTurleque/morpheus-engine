from __future__ import annotations

from dataclasses import dataclass

STATES = (
    "DRAFT",
    "PROPOSED",
    "SPECIFIED",
    "DESIGNED",
    "PLANNED",
    "IMPLEMENTING",
    "VERIFYING",
    "COMPLETED",
    "ARCHIVED",
    "ABANDONED",
)


@dataclass(frozen=True)
class ChangeFacts:
    state: str
    requirements_defined: bool = False
    critical_constraints_known: bool = False
    acceptance_criteria_defined: bool = False
    design_required: bool = True
    design_decisions_resolved: bool = False
    tasks_planned: bool = False
    blockers: tuple[str, ...] = ()
    blocking_criteria_failed: bool = False
    blocking_criteria_unverified: bool = False
    temporal_state: str = "PROPOSED"
    abandonment_reason: str | None = None


class LifecyclePolicy:
    nominal = {
        "DRAFT": "PROPOSED",
        "PROPOSED": "SPECIFIED",
        "SPECIFIED": "DESIGNED",
        "DESIGNED": "PLANNED",
        "PLANNED": "IMPLEMENTING",
        "IMPLEMENTING": "VERIFYING",
        "VERIFYING": "COMPLETED",
        "COMPLETED": "ARCHIVED",
    }

    backward = {
        ("SPECIFIED", "PROPOSED"),
        ("DESIGNED", "SPECIFIED"),
        ("PLANNED", "DESIGNED"),
        ("IMPLEMENTING", "PLANNED"),
        ("VERIFYING", "IMPLEMENTING"),
        ("COMPLETED", "VERIFYING"),
    }

    def allowed_targets(self, facts: ChangeFacts) -> set[str]:
        targets: set[str] = set()
        if facts.state in self.nominal:
            targets.add(self.nominal[facts.state])
        if not facts.design_required and facts.state == "SPECIFIED":
            targets.add("PLANNED")
        for source, target in self.backward:
            if source == facts.state:
                targets.add(target)
        if facts.state not in {"ARCHIVED", "ABANDONED"}:
            targets.add("ABANDONED")
        if facts.state == "ABANDONED":
            targets.add("PROPOSED")
        return targets

    def validate(self, facts: ChangeFacts, target: str) -> tuple[str, tuple[str, ...]]:
        if target not in self.allowed_targets(facts):
            return "BLOCKED", ("INVALID_TRANSITION",)

        blockers: list[str] = []

        if (facts.state, target) == ("PROPOSED", "SPECIFIED"):
            if not facts.requirements_defined:
                blockers.append("MISSING_REQUIREMENTS")
            if not facts.critical_constraints_known:
                blockers.append("UNKNOWN_CRITICAL_CONSTRAINTS")
            if not facts.acceptance_criteria_defined:
                blockers.append("MISSING_ACCEPTANCE_CRITERIA")

        elif facts.state == "SPECIFIED" and target == "DESIGNED":
            if facts.design_required and not facts.design_decisions_resolved:
                blockers.append("UNRESOLVED_DESIGN_DECISIONS")

        elif target == "PLANNED" and facts.state in {"SPECIFIED", "DESIGNED"}:
            if facts.design_required and facts.state == "SPECIFIED":
                blockers.append("DESIGN_REQUIRED")
            if not facts.tasks_planned:
                blockers.append("MISSING_PLAN")

        elif (facts.state, target) == ("PLANNED", "IMPLEMENTING"):
            blockers.extend(facts.blockers)

        elif (facts.state, target) == ("VERIFYING", "COMPLETED"):
            if facts.blocking_criteria_failed:
                blockers.append("BLOCKING_ACCEPTANCE_FAILED")
            if facts.blocking_criteria_unverified:
                blockers.append("BLOCKING_ACCEPTANCE_UNVERIFIED")

        elif target == "ABANDONED":
            if not facts.abandonment_reason:
                blockers.append("ABANDONMENT_REASON_REQUIRED")

        if blockers:
            return "BLOCKED", tuple(blockers)
        return "ALLOWED", ()
