from __future__ import annotations

import unittest

from traceability import TraceabilityGraph, TraceabilityLink


class TraceabilityTest(unittest.TestCase):
    def setUp(self) -> None:
        self.graph = TraceabilityGraph()
        links = [
            TraceabilityLink("scenario:login", "REFINES", "req:auth", "EXPLICIT", "RESOLVED", evidence="spec.md:20"),
            TraceabilityLink("ac:auth", "VALIDATES", "req:auth", "EXPLICIT", "RESOLVED", evidence="acceptance.md:5"),
            TraceabilityLink("task:impl", "IMPLEMENTS", "req:auth", "DERIVED", "RESOLVED", evidence="tasks.md:3"),
            TraceabilityLink("change:remember", "AFFECTS", "req:auth", "EXPLICIT", "RESOLVED", evidence="delta.md:2"),
            TraceabilityLink("change:remember", "DECIDED_BY", "decision:token", "EXPLICIT", "RESOLVED", evidence="design.md:19"),
            TraceabilityLink("constraint:optin", "CONSTRAINS", "change:remember", "EXPLICIT", "RESOLVED", evidence="proposal.md:18"),
            TraceabilityLink("decision:token", "DEPENDS_ON", "constraint:optin", "DERIVED", "RESOLVED", evidence="resolver:v1"),
            TraceabilityLink("req:auth", "LINKS_TO_CODE", "minos:symbol:missing", "EXPLICIT", "UNRESOLVED", evidence="mapping.yaml:1"),
            TraceabilityLink("req:auth", "RELATED_TO", "req:session", "HEURISTIC", "HEURISTIC", confidence=0.62, evidence="heuristic:v1"),
        ]
        for link in links:
            self.graph.add(link)

    def test_direct_outgoing(self) -> None:
        links = self.graph.outgoing("change:remember")
        self.assertEqual({"AFFECTS", "DECIDED_BY"}, {link.relation_type for link in links})

    def test_inverse_query_does_not_require_duplicate_edge(self) -> None:
        incoming = self.graph.incoming("req:auth")
        self.assertEqual(
            {"REFINES", "VALIDATES", "IMPLEMENTS", "AFFECTS"},
            {link.relation_type for link in incoming},
        )

    def test_depth_three_traversal(self) -> None:
        paths = self.graph.traverse("constraint:optin", max_depth=3, bidirectional=True)
        self.assertTrue(any(item["depth"] == 3 for item in paths))
        self.assertTrue(any("req:auth" in item["path"] for item in paths))

    def test_unresolved_link_is_preserved(self) -> None:
        unresolved = self.graph.unresolved()
        self.assertEqual(1, len(unresolved))
        self.assertEqual("minos:symbol:missing", unresolved[0].target)

    def test_provenance_and_resolution_are_orthogonal_to_relation_type(self) -> None:
        link = self.graph.outgoing("req:auth", "RELATED_TO")[0]
        self.assertEqual("RELATED_TO", link.relation_type)
        self.assertEqual("HEURISTIC", link.origin)
        self.assertEqual("HEURISTIC", link.resolution)
        self.assertEqual(0.62, link.confidence)
        self.assertEqual("heuristic:v1", link.evidence)

    def test_unknown_relation_is_rejected(self) -> None:
        with self.assertRaises(ValueError):
            TraceabilityLink("a", "WHATEVER", "b", "EXPLICIT", "RESOLVED")

    def test_duplicate_edge_is_not_persisted_twice(self) -> None:
        link = TraceabilityLink("a", "DEPENDS_ON", "b", "EXPLICIT", "RESOLVED", evidence="x")
        self.graph.add(link)
        self.graph.add(link)
        self.assertEqual(1, len([item for item in self.graph.links if item == link]))

    def test_relation_filter_limits_traversal(self) -> None:
        paths = self.graph.traverse(
            "change:remember",
            max_depth=2,
            relation_types={"AFFECTS"},
        )
        self.assertTrue(paths)
        self.assertTrue(all(item["relation"] == "AFFECTS" for item in paths))


if __name__ == "__main__":
    unittest.main()
