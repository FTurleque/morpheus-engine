package com.morpheus.application.reasoning;

import com.morpheus.application.reasoning.ReasoningContracts.AdapterExecution;
import com.morpheus.application.reasoning.ReasoningContracts.AdapterRequest;
import com.morpheus.application.reasoning.ReasoningContracts.AdapterResult;
import com.morpheus.application.reasoning.ReasoningContracts.AdapterStatus;
import com.morpheus.application.reasoning.ReasoningContracts.Claim;
import com.morpheus.application.reasoning.ReasoningContracts.ClaimKind;
import com.morpheus.application.reasoning.ReasoningContracts.Evidence;
import com.morpheus.application.reasoning.ReasoningContracts.EvidenceKind;
import com.morpheus.application.reasoning.ReasoningContracts.Request;
import com.morpheus.application.reasoning.ReasoningContracts.Result;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Read-only orchestration of optional reasoning adapters with fail-isolated assisted output. */
public final class ReasoningService {
    private final ReasoningAdapterRegistry registry;

    public ReasoningService(ReasoningAdapterRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    public static ReasoningService standard() {
        return new ReasoningService(ReasoningAdapterRegistry.standard());
    }

    public List<ReasoningAdapterRegistry.Descriptor> adapters() {
        return registry.descriptors();
    }

    public Result execute(Request request) {
        Objects.requireNonNull(request, "request");
        Map<String, Evidence> evidenceById = indexEvidence(request.evidence());
        List<Evidence> facts = request.evidence().stream()
                .filter(item -> item.kind() == EvidenceKind.PUBLISHED_FACT)
                .toList();
        List<Claim> accepted = new ArrayList<>();
        Set<String> claimIds = new LinkedHashSet<>();
        List<AdapterExecution> executions = new ArrayList<>();

        for (ReasoningAdapter adapter : registry.select(request.adapterIds())) {
            int remaining = request.maxClaims() - accepted.size();
            if (remaining == 0) {
                executions.add(new AdapterExecution(
                        adapter.id(),
                        AdapterStatus.FAILED,
                        0,
                        "global reasoning claim budget exhausted before adapter execution",
                        Map.of("budget", Integer.toString(request.maxClaims()))));
                continue;
            }
            try {
                AdapterResult result = Objects.requireNonNull(
                        adapter.reason(new AdapterRequest(
                                request.question(), request.evidence(), request.parameters(), remaining)),
                        "adapter result");
                List<Claim> validated = validateClaims(adapter, result.claims(), evidenceById, claimIds, remaining);
                accepted.addAll(validated);
                executions.add(new AdapterExecution(
                        adapter.id(),
                        AdapterStatus.SUCCEEDED,
                        validated.size(),
                        "",
                        result.metadata()));
            } catch (RuntimeException failure) {
                executions.add(new AdapterExecution(
                        adapter.id(),
                        AdapterStatus.FAILED,
                        0,
                        safeMessage(failure),
                        Map.of("failureType", failure.getClass().getSimpleName())));
            }
        }

        List<Claim> inferences = accepted.stream().filter(claim -> claim.kind() == ClaimKind.INFERENCE).toList();
        List<Claim> heuristics = accepted.stream().filter(claim -> claim.kind() == ClaimKind.HEURISTIC).toList();
        List<Claim> suggestions = accepted.stream().filter(claim -> claim.kind() == ClaimKind.SUGGESTION).toList();
        return new Result(
                request.question(),
                request.evidence(),
                facts,
                inferences,
                heuristics,
                suggestions,
                executions,
                !accepted.isEmpty(),
                false);
    }

    private static Map<String, Evidence> indexEvidence(List<Evidence> evidence) {
        Map<String, Evidence> indexed = new LinkedHashMap<>();
        int statementChars = 0;
        for (Evidence item : evidence) {
            if (indexed.putIfAbsent(item.id(), item) != null) {
                throw new IllegalArgumentException("duplicate evidence id: " + item.id());
            }
            statementChars += item.statement().length();
            if (statementChars > ReasoningContracts.MAX_EVIDENCE * ReasoningContracts.MAX_STATEMENT_CHARS) {
                throw new IllegalArgumentException("aggregate evidence statement budget exceeded");
            }
        }
        return Map.copyOf(indexed);
    }

    private static List<Claim> validateClaims(
            ReasoningAdapter adapter,
            List<Claim> rawClaims,
            Map<String, Evidence> evidenceById,
            Set<String> existingClaimIds,
            int remaining) {
        if (rawClaims.size() > remaining) {
            throw new IllegalArgumentException(
                    "adapter " + adapter.id() + " exceeded remaining claim budget " + remaining);
        }
        List<Claim> accepted = new ArrayList<>(rawClaims.size());
        Set<String> localIds = new LinkedHashSet<>();
        for (Claim claim : rawClaims) {
            if (!claim.adapterId().equals(adapter.id())) {
                throw new IllegalArgumentException(
                        "claim " + claim.id() + " does not identify its producing adapter");
            }
            if (!localIds.add(claim.id()) || existingClaimIds.contains(claim.id())) {
                throw new IllegalArgumentException("duplicate reasoning claim id: " + claim.id());
            }
            for (String evidenceId : claim.evidenceIds()) {
                if (!evidenceById.containsKey(evidenceId)) {
                    throw new IllegalArgumentException(
                            "claim " + claim.id() + " cites unknown evidence: " + evidenceId);
                }
            }
            accepted.add(claim);
        }
        existingClaimIds.addAll(localIds);
        return List.copyOf(accepted);
    }

    private static String safeMessage(RuntimeException failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
    }
}
