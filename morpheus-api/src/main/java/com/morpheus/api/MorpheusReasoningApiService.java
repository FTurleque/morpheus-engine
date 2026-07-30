package com.morpheus.api;

import com.morpheus.application.reasoning.ReasoningContracts;
import com.morpheus.application.reasoning.ReasoningContracts.Evidence;
import com.morpheus.application.reasoning.ReasoningContracts.EvidenceKind;
import com.morpheus.application.reasoning.ReasoningContracts.Request;
import com.morpheus.application.reasoning.ReasoningService;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** HTTP-safe translation for the read-only M27 reasoning application service. */
final class MorpheusReasoningApiService {
    private final ReasoningService service;

    MorpheusReasoningApiService() {
        this(ReasoningService.standard());
    }

    MorpheusReasoningApiService(ReasoningService service) {
        this.service = Objects.requireNonNull(service, "service");
    }

    Object adapters() {
        return service.adapters();
    }

    Object analyze(ReasoningRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("reasoning request is required");
        }
        try {
            List<Evidence> evidence = new ArrayList<>();
            for (EvidenceRequest item : request.evidenceOrEmpty()) {
                if (item == null) {
                    throw new IllegalArgumentException("evidence entries must not be null");
                }
                evidence.add(new Evidence(
                        item.id(),
                        evidenceKind(item.kind()),
                        item.subject(),
                        item.statement(),
                        item.provenanceOrEmpty()));
            }
            return service.execute(new Request(
                    request.question(),
                    List.copyOf(evidence),
                    request.adapterIdsOrEmpty(),
                    request.parametersOrEmpty(),
                    request.maxClaimsOrDefault()));
        } catch (NullPointerException failure) {
            throw new IllegalArgumentException("reasoning request contains a null value", failure);
        }
    }

    private static EvidenceKind evidenceKind(String raw) {
        try {
            return EvidenceKind.valueOf(Objects.requireNonNull(raw, "evidence kind").trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException failure) {
            throw new IllegalArgumentException("invalid evidence kind: " + raw, failure);
        }
    }

    record ReasoningRequest(
            String question,
            List<EvidenceRequest> evidence,
            List<String> adapterIds,
            Map<String, String> parameters,
            Integer maxClaims) {
        List<EvidenceRequest> evidenceOrEmpty() {
            return evidence == null ? List.of() : List.copyOf(evidence);
        }

        List<String> adapterIdsOrEmpty() {
            return adapterIds == null ? List.of() : List.copyOf(adapterIds);
        }

        Map<String, String> parametersOrEmpty() {
            return parameters == null ? Map.of() : Map.copyOf(parameters);
        }

        int maxClaimsOrDefault() {
            return maxClaims == null ? ReasoningContracts.MAX_CLAIMS : maxClaims;
        }
    }

    record EvidenceRequest(
            String id,
            String kind,
            String subject,
            String statement,
            Map<String, String> provenance) {
        Map<String, String> provenanceOrEmpty() {
            return provenance == null ? Map.of() : Map.copyOf(provenance);
        }
    }
}
