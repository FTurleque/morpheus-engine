from __future__ import annotations

import unittest

from identity import IdentityRegistry, Observation


def obs(
    *,
    provider: str = "openspec",
    locator: str = "specs/auth/spec.md:10",
    title: str = "Session expiration",
    statement: str = "The system SHALL expire sessions.",
    external_id: str | None = "REQ-1",
    logical_key: str | None = "auth/session-expiration",
    previous_external_id: str | None = None,
    temporal_state: str = "CURRENT",
) -> Observation:
    return Observation(
        provider_id=provider,
        kind="Requirement",
        locator=locator,
        title=title,
        statement=statement,
        external_id=external_id,
        logical_key=logical_key,
        previous_external_id=previous_external_id,
        temporal_state=temporal_state,
    )


class StableIdentityTest(unittest.TestCase):
    def setUp(self) -> None:
        self.registry = IdentityRegistry()

    def test_move_file_preserves_identity(self) -> None:
        first = self.registry.resolve(obs(locator="specs/a/spec.md:10"))
        moved = self.registry.resolve(obs(locator="specs/b/spec.md:25"))
        self.assertEqual(first.domain_id, moved.domain_id)
        self.assertEqual("EXTERNAL_ID", moved.reason)

    def test_rename_title_preserves_identity_when_key_is_stable(self) -> None:
        first = self.registry.resolve(obs(external_id=None))
        renamed = self.registry.resolve(
            obs(external_id=None, title="Authentication session expiration")
        )
        self.assertEqual(first.domain_id, renamed.domain_id)
        self.assertEqual("LOGICAL_KEY", renamed.reason)

    def test_external_key_change_requires_explicit_continuity(self) -> None:
        first = self.registry.resolve(obs(external_id="REQ-1"))
        changed = self.registry.resolve(
            obs(
                external_id="REQ-100",
                previous_external_id="REQ-1",
                logical_key="auth/session-expiration-v2",
            )
        )
        self.assertEqual(first.domain_id, changed.domain_id)
        self.assertEqual("EXPLICIT_CONTINUITY", changed.reason)

    def test_statement_change_preserves_identity_with_stable_external_id(self) -> None:
        first = self.registry.resolve(obs())
        modified = self.registry.resolve(
            obs(statement="The system SHALL expire sessions after 30 minutes.")
        )
        self.assertEqual(first.domain_id, modified.domain_id)

    def test_duplicate_content_does_not_merge_without_evidence(self) -> None:
        first = self.registry.resolve(obs(external_id=None, logical_key="auth/a"))
        duplicate = self.registry.resolve(
            obs(
                external_id=None,
                logical_key="auth/b",
                locator="specs/auth/spec.md:40",
            )
        )
        self.assertNotEqual(first.domain_id, duplicate.domain_id)
        self.assertEqual("HEURISTIC", duplicate.resolution)
        self.assertIn("HEURISTIC_CONTINUITY_CANDIDATE", duplicate.warnings)

    def test_delete_then_restore_with_external_id_reuses_identity(self) -> None:
        first = self.registry.resolve(obs())
        self.registry.delete(first.domain_id)
        self.assertFalse(self.registry.get(first.domain_id).active)

        restored = self.registry.resolve(obs(locator="specs/restored/spec.md:10"))
        self.assertEqual(first.domain_id, restored.domain_id)
        self.assertTrue(self.registry.get(first.domain_id).active)

    def test_archive_preserves_identity_but_changes_temporal_state(self) -> None:
        first = self.registry.resolve(obs())
        self.registry.archive(first.domain_id)
        record = self.registry.get(first.domain_id)
        self.assertEqual("HISTORICAL", record.temporal_state)
        self.assertFalse(record.active)

    def test_same_external_id_from_different_providers_does_not_collide(self) -> None:
        first = self.registry.resolve(obs(provider="openspec", external_id="REQ-1"))
        second = self.registry.resolve(obs(provider="synthetic", external_id="REQ-1"))
        self.assertNotEqual(first.domain_id, second.domain_id)

    def test_conflicting_logical_key_emits_collision_warning(self) -> None:
        first = self.registry.resolve(obs(external_id="REQ-1", logical_key="auth/key"))
        second = self.registry.resolve(
            obs(
                external_id="REQ-2",
                logical_key="auth/key",
                title="Different requirement",
                statement="Different statement.",
            )
        )
        self.assertNotEqual(first.domain_id, second.domain_id)
        self.assertIn("IDENTITY_COLLISION", second.warnings)

    def test_title_and_locator_are_not_identity(self) -> None:
        first = self.registry.resolve(obs(external_id=None, logical_key="auth/stable"))
        second = self.registry.resolve(
            obs(
                external_id=None,
                logical_key="auth/stable",
                title="Completely renamed",
                locator="another/file.md:999",
            )
        )
        self.assertEqual(first.domain_id, second.domain_id)


if __name__ == "__main__":
    unittest.main()
