package com.morpheus.application.reasoning;

import com.morpheus.application.reasoning.ReasoningContracts.AdapterRequest;
import com.morpheus.application.reasoning.ReasoningContracts.AdapterResult;
import com.morpheus.application.reasoning.ReasoningContracts.Claim;
import com.morpheus.application.reasoning.ReasoningContracts.ClaimKind;
import com.morpheus.application.reasoning.ReasoningContracts.Confidence;
import com.morpheus.application.reasoning.ReasoningContracts.Evidence;
import com.morpheus.application.reasoning.ReasoningContracts.EvidenceKind;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Built-in deterministic adapter. It performs no network access and is never executed unless selected. */
public final class EvidenceSynthesisReasoningAdapter implements ReasoningAdapter {
    public static final String ID = "builtin-evidence-synthesis-v1";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String description() {
        return "Deterministic evidence coverage synthesis without network or LLM dependency";
    }

    @Override
    public AdapterResult reason(AdapterRequest request) {
        if (request.evidence().isEmpty()) {
            return new AdapterResult(List.of(), Map.of("mode", "facts-only", "evidenceCount", "0"));
        }

        List<Evidence> facts = request.evidence().stream()
                .filter(item -> item.kind() == EvidenceKind.PUBLISHED_FACT)
                .toList();
        List<String> allReferences = request.evidence().stream()
                .limit(ReasoningContracts.MAX_EVIDENCE_REFERENCES)
                .map(Evidence::id)
                .toList();
        List<String> factReferences = facts.stream()
                .limit(ReasoningContracts.MAX_EVIDENCE_REFERENCES)
                .map(Evidence::id)
                .toList();
        List<Claim> claims = new ArrayList<>();

        double coverage = (double) facts.size() / (double) request.evidence().size();
        claims.add(new Claim(
                "evidence-coverage",
                ClaimKind.HEURISTIC,
                "The evidence set contains " + facts.size() + " published fact(s) across "
                        + request.evidence().size() + " total evidence item(s); assisted conclusions remain provisional.",
                Confidence.of(Math.min(0.90d, 0.35d + (coverage * 0.55d))),
                allReferences,
                ID,
                Map.of("method", "deterministic-coverage", "publishedFactRatio", Double.toString(coverage))));

        if (claims.size() < request.maxClaims() && facts.size() >= 2) {
            claims.add(new Claim(
                    "multi-fact-basis",
                    ClaimKind.INFERENCE,
                    "The supplied published facts provide a multi-fact basis for addressing the question: "
                            + request.question(),
                    Confidence.of(Math.min(0.84d, 0.55d + (facts.size() * 0.04d))),
                    factReferences,
                    ID,
                    Map.of("method", "deterministic-fact-count", "factCount", Integer.toString(facts.size()))));
        }

        if (claims.size() < request.maxClaims() && facts.size() < request.evidence().size()) {
            claims.add(new Claim(
                    "review-non-published-evidence",
                    ClaimKind.SUGGESTION,
                    "Review non-published observations and external context before promoting any assisted conclusion into a published specification fact.",
                    Confidence.of(0.78d),
                    allReferences,
                    ID,
                    Map.of("method", "governance-safeguard")));
        }

        return new AdapterResult(
                List.copyOf(claims),
                Map.of(
                        "mode", "deterministic",
                        "networkAccess", "false",
                        "evidenceCount", Integer.toString(request.evidence().size()),
                        "publishedFactCount", Integer.toString(facts.size())));
    }
}
