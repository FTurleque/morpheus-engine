package com.morpheus.application.reasoning;

import com.morpheus.application.reasoning.ReasoningContracts.AdapterResult;
import com.morpheus.application.reasoning.ReasoningContracts.Claim;
import com.morpheus.application.reasoning.ReasoningContracts.ClaimKind;
import com.morpheus.application.reasoning.ReasoningContracts.Confidence;
import com.morpheus.application.reasoning.ReasoningContracts.Evidence;
import com.morpheus.application.reasoning.ReasoningContracts.EvidenceKind;
import com.morpheus.application.reasoning.ReasoningContracts.Request;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReasoningServiceTest {

    @Test
    void noSelectedAdapterReturnsFactsOnlyWithoutFailureOrMutation() {
        Evidence fact = fact("fact-1", "Authentication requires TLS");
        var result = new ReasoningService(ReasoningAdapterRegistry.empty())
                .execute(Request.factsOnly("Is remote access protected?", List.of(fact)));

        assertEquals(List.of(fact), result.facts());
        assertTrue(result.inferences().isEmpty());
        assertTrue(result.heuristics().isEmpty());
        assertTrue(result.suggestions().isEmpty());
        assertTrue(result.executions().isEmpty());
        assertFalse(result.assisted());
        assertFalse(result.mutated());
    }

    @Test
    void deterministicAdapterKeepsClaimsSeparatedAndCitesEvidence() {
        ReasoningService service = new ReasoningService(new ReasoningAdapterRegistry(
                List.of(new EvidenceSynthesisReasoningAdapter())));
        var request = new Request(
                "Can remote mode be enabled safely?",
                List.of(
                        fact("fact-1", "Remote mode requires TLS"),
                        fact("fact-2", "Remote mode requires authentication"),
                        new Evidence("obs-1", EvidenceKind.OBSERVATION, "deployment",
                                "One environment has not provisioned a keystore", Map.of("source", "operator"))),
                List.of(EvidenceSynthesisReasoningAdapter.ID),
                Map.of(),
                10);

        var result = service.execute(request);

        assertEquals(2, result.facts().size());
        assertEquals(1, result.inferences().size());
        assertEquals(1, result.heuristics().size());
        assertEquals(1, result.suggestions().size());
        assertTrue(result.assisted());
        assertFalse(result.mutated());
        result.inferences().forEach(claim -> {
            assertFalse(claim.evidenceIds().isEmpty());
            assertTrue(claim.confidence().score() >= 0.0d && claim.confidence().score() <= 1.0d);
        });
    }

    @Test
    void adapterFailureDoesNotRemovePublishedFacts() {
        ReasoningAdapter failing = new ReasoningAdapter() {
            @Override
            public String id() {
                return "failing-adapter";
            }

            @Override
            public AdapterResult reason(ReasoningContracts.AdapterRequest request) {
                throw new IllegalStateException("simulated adapter outage");
            }
        };
        Evidence fact = fact("fact-1", "Published history remains authoritative");
        var result = new ReasoningService(new ReasoningAdapterRegistry(List.of(failing)))
                .execute(new Request("What remains authoritative?", List.of(fact),
                        List.of("failing-adapter"), Map.of(), 10));

        assertEquals(List.of(fact), result.facts());
        assertTrue(result.inferences().isEmpty());
        assertEquals(ReasoningContracts.AdapterStatus.FAILED, result.executions().getFirst().status());
        assertFalse(result.mutated());
    }

    @Test
    void claimThatCitesUnknownEvidenceIsRejectedAsAdapterFailure() {
        ReasoningAdapter invalid = new ReasoningAdapter() {
            @Override
            public String id() {
                return "invalid-adapter";
            }

            @Override
            public AdapterResult reason(ReasoningContracts.AdapterRequest request) {
                return new AdapterResult(List.of(new Claim(
                        "claim-1",
                        ClaimKind.INFERENCE,
                        "Unsupported inference",
                        Confidence.of(0.80d),
                        List.of("missing-evidence"),
                        id(),
                        Map.of())), Map.of());
            }
        };
        var result = new ReasoningService(new ReasoningAdapterRegistry(List.of(invalid)))
                .execute(new Request("Question", List.of(fact("fact-1", "Fact")),
                        List.of("invalid-adapter"), Map.of(), 10));

        assertTrue(result.inferences().isEmpty());
        assertEquals(ReasoningContracts.AdapterStatus.FAILED, result.executions().getFirst().status());
    }

    @Test
    void duplicateEvidenceIdentityIsRejectedBeforeAdapterExecution() {
        ReasoningService service = new ReasoningService(ReasoningAdapterRegistry.empty());
        assertThrows(IllegalArgumentException.class, () -> service.execute(new Request(
                "Question",
                List.of(fact("same", "Fact one"), fact("same", "Fact two")),
                List.of(),
                Map.of(),
                10)));
    }

    private static Evidence fact(String id, String statement) {
        return new Evidence(id, EvidenceKind.PUBLISHED_FACT, "specification", statement,
                Map.of("source", "published-snapshot"));
    }
}
