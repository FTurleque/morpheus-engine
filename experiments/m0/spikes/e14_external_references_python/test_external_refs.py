from __future__ import annotations

import unittest

from external_refs import ExternalReference, FakeMinosResolver, ResolverRegistry


class ExternalReferenceTest(unittest.TestCase):
    def setUp(self) -> None:
        self.registry = ResolverRegistry()
        self.minos = FakeMinosResolver()
        self.reference = ExternalReference(
            system="MINOS",
            project="morpheus-engine",
            resource_type="SYMBOL",
            external_id="symbol:RequirementService",
            provenance="mapping.yaml:12",
        )

    def test_reference_exists_without_target_system(self) -> None:
        self.assertEqual("UNVALIDATED", self.reference.state)
        self.assertEqual("mapping.yaml:12", self.reference.provenance)
        self.assertEqual([], self.reference.history)

    def test_missing_resolver_becomes_unresolved(self) -> None:
        resolved = self.registry.resolve(self.reference)
        self.assertEqual("UNRESOLVED", resolved.state)
        self.assertEqual("NO_RESOLVER", resolved.history[-1]["reason"])

    def test_fake_minos_resolver_resolves_reference(self) -> None:
        self.minos.put(
            project="morpheus-engine",
            resource_type="SYMBOL",
            external_id="symbol:RequirementService",
            payload={"name": "RequirementService", "kind": "CLASS"},
        )
        self.registry.register(self.minos)
        resolved = self.registry.resolve(self.reference)
        self.assertEqual("RESOLVED", resolved.state)
        self.assertEqual("RequirementService", resolved.resolved_payload["name"])

    def test_previously_resolved_target_becomes_stale_when_removed(self) -> None:
        self.minos.put(
            project="morpheus-engine",
            resource_type="SYMBOL",
            external_id="symbol:RequirementService",
            payload={"name": "RequirementService"},
        )
        self.registry.register(self.minos)
        self.registry.resolve(self.reference)
        self.assertEqual("RESOLVED", self.reference.state)

        self.minos.remove(
            project="morpheus-engine",
            resource_type="SYMBOL",
            external_id="symbol:RequirementService",
        )
        self.registry.resolve(self.reference)
        self.assertEqual("STALE", self.reference.state)
        self.assertEqual("TARGET_NOT_FOUND", self.reference.history[-1]["reason"])

    def test_unresolved_reference_can_later_be_resolved(self) -> None:
        self.registry.register(self.minos)
        self.registry.resolve(self.reference)
        self.assertEqual("UNRESOLVED", self.reference.state)

        self.minos.put(
            project="morpheus-engine",
            resource_type="SYMBOL",
            external_id="symbol:RequirementService",
            payload={"name": "RequirementService"},
        )
        self.registry.resolve(self.reference)
        self.assertEqual("RESOLVED", self.reference.state)
        self.assertEqual(2, len(self.reference.history))

    def test_history_and_provenance_survive_resolution_changes(self) -> None:
        self.registry.register(self.minos)
        self.registry.resolve(self.reference)
        self.minos.put(
            project="morpheus-engine",
            resource_type="SYMBOL",
            external_id="symbol:RequirementService",
            payload={"name": "RequirementService"},
        )
        self.registry.resolve(self.reference)
        self.assertEqual("mapping.yaml:12", self.reference.provenance)
        self.assertEqual("UNVALIDATED", self.reference.history[0]["from"])
        self.assertEqual("UNRESOLVED", self.reference.history[0]["to"])
        self.assertEqual("RESOLVED", self.reference.history[1]["to"])


if __name__ == "__main__":
    unittest.main()
