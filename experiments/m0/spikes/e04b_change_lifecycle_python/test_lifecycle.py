from __future__ import annotations

import unittest

from lifecycle import ChangeFacts, LifecyclePolicy


class ChangeLifecycleTest(unittest.TestCase):
    def setUp(self) -> None:
        self.policy = LifecyclePolicy()

    def test_proposed_to_specified_is_blocked_when_specification_is_incomplete(self) -> None:
        status, blockers = self.policy.validate(ChangeFacts("PROPOSED"), "SPECIFIED")
        self.assertEqual("BLOCKED", status)
        self.assertEqual(
            {
                "MISSING_REQUIREMENTS",
                "UNKNOWN_CRITICAL_CONSTRAINTS",
                "MISSING_ACCEPTANCE_CRITERIA",
            },
            set(blockers),
        )

    def test_proposed_to_specified_is_allowed_with_minimum_facts(self) -> None:
        facts = ChangeFacts(
            "PROPOSED",
            requirements_defined=True,
            critical_constraints_known=True,
            acceptance_criteria_defined=True,
        )
        self.assertEqual(("ALLOWED", ()), self.policy.validate(facts, "SPECIFIED"))

    def test_required_design_blocks_designed_transition_until_decisions_resolved(self) -> None:
        facts = ChangeFacts("SPECIFIED", design_required=True)
        self.assertIn(
            "UNRESOLVED_DESIGN_DECISIONS",
            self.policy.validate(facts, "DESIGNED")[1],
        )

    def test_trivial_change_can_skip_separate_design_when_policy_allows_it(self) -> None:
        facts = ChangeFacts("SPECIFIED", design_required=False, tasks_planned=True)
        self.assertEqual(("ALLOWED", ()), self.policy.validate(facts, "PLANNED"))

    def test_implementation_is_blocked_by_known_blocker(self) -> None:
        facts = ChangeFacts("PLANNED", blockers=("DEPENDENCY_UNRESOLVED",))
        status, blockers = self.policy.validate(facts, "IMPLEMENTING")
        self.assertEqual("BLOCKED", status)
        self.assertEqual(("DEPENDENCY_UNRESOLVED",), blockers)

    def test_completed_is_blocked_when_acceptance_is_unverified(self) -> None:
        facts = ChangeFacts("VERIFYING", blocking_criteria_unverified=True)
        self.assertIn(
            "BLOCKING_ACCEPTANCE_UNVERIFIED",
            self.policy.validate(facts, "COMPLETED")[1],
        )

    def test_completed_does_not_promote_temporal_state(self) -> None:
        facts = ChangeFacts("VERIFYING", temporal_state="PROPOSED")
        self.assertEqual(("ALLOWED", ()), self.policy.validate(facts, "COMPLETED"))
        self.assertEqual("PROPOSED", facts.temporal_state)

    def test_backward_transition_is_legitimate(self) -> None:
        self.assertEqual(
            ("ALLOWED", ()),
            self.policy.validate(ChangeFacts("VERIFYING"), "IMPLEMENTING"),
        )

    def test_abandon_requires_reason(self) -> None:
        self.assertEqual(
            "BLOCKED",
            self.policy.validate(ChangeFacts("PLANNED"), "ABANDONED")[0],
        )
        facts = ChangeFacts("PLANNED", abandonment_reason="OBSOLETE")
        self.assertEqual(("ALLOWED", ()), self.policy.validate(facts, "ABANDONED"))

    def test_abandoned_change_can_be_reopened_to_proposed(self) -> None:
        self.assertEqual(
            ("ALLOWED", ()),
            self.policy.validate(ChangeFacts("ABANDONED"), "PROPOSED"),
        )

    def test_archived_change_is_not_directly_reopened(self) -> None:
        self.assertEqual(
            "BLOCKED",
            self.policy.validate(ChangeFacts("ARCHIVED"), "PROPOSED")[0],
        )


if __name__ == "__main__":
    unittest.main()
