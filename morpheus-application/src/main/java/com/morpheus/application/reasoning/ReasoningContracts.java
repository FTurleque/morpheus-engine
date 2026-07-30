package com.morpheus.application.reasoning;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Provider-neutral contracts that keep published evidence and assisted claims structurally distinct. */
public final class ReasoningContracts {
    public static final int MAX_EVIDENCE = 256;
    public static final int MAX_ADAPTERS = 8;
    public static final int MAX_CLAIMS = 256;
    public static final int MAX_EVIDENCE_REFERENCES = 32;
    public static final int MAX_PROVENANCE_ENTRIES = 32;
    public static final int MAX_QUESTION_CHARS = 8_192;
    public static final int MAX_STATEMENT_CHARS = 16_384;
    public static final int MAX_PARAMETER_ENTRIES = 32;

    private ReasoningContracts() {
    }

    public enum EvidenceKind {
        PUBLISHED_FACT,
        SOURCE_EXCERPT,
        POLICY_RESULT,
        EXTERNAL_CONTEXT,
        OBSERVATION
    }

    public enum ClaimKind {
        INFERENCE,
        HEURISTIC,
        SUGGESTION
    }

    public enum ConfidenceBand {
        VERY_LOW,
        LOW,
        MEDIUM,
        HIGH,
        VERY_HIGH
    }

    public enum AdapterStatus {
        SUCCEEDED,
        FAILED
    }

    public record Evidence(
            String id,
            EvidenceKind kind,
            String subject,
            String statement,
            Map<String, String> provenance) {
        public Evidence {
            id = required(id, "evidence id", 128);
            kind = Objects.requireNonNull(kind, "kind");
            subject = required(subject, "evidence subject", 512);
            statement = required(statement, "evidence statement", MAX_STATEMENT_CHARS);
            provenance = immutableMap(provenance, "evidence provenance", MAX_PROVENANCE_ENTRIES);
        }
    }

    public record Confidence(double score, ConfidenceBand band) {
        public Confidence {
            if (!Double.isFinite(score) || score < 0.0d || score > 1.0d) {
                throw new IllegalArgumentException("confidence score must be finite and between 0.0 and 1.0");
            }
            band = Objects.requireNonNull(band, "band");
            if (band != bandFor(score)) {
                throw new IllegalArgumentException("confidence band does not match score");
            }
        }

        public static Confidence of(double score) {
            return new Confidence(score, bandFor(score));
        }

        private static ConfidenceBand bandFor(double score) {
            if (score < 0.20d) {
                return ConfidenceBand.VERY_LOW;
            }
            if (score < 0.40d) {
                return ConfidenceBand.LOW;
            }
            if (score < 0.65d) {
                return ConfidenceBand.MEDIUM;
            }
            if (score < 0.85d) {
                return ConfidenceBand.HIGH;
            }
            return ConfidenceBand.VERY_HIGH;
        }
    }

    public record Claim(
            String id,
            ClaimKind kind,
            String statement,
            Confidence confidence,
            List<String> evidenceIds,
            String adapterId,
            Map<String, String> provenance) {
        public Claim {
            id = required(id, "claim id", 128);
            kind = Objects.requireNonNull(kind, "kind");
            statement = required(statement, "claim statement", MAX_STATEMENT_CHARS);
            confidence = Objects.requireNonNull(confidence, "confidence");
            evidenceIds = immutableStrings(
                    evidenceIds, "claim evidence ids", 1, MAX_EVIDENCE_REFERENCES, 128);
            adapterId = required(adapterId, "adapter id", 128);
            provenance = immutableMap(provenance, "claim provenance", MAX_PROVENANCE_ENTRIES);
        }
    }

    public record Request(
            String question,
            List<Evidence> evidence,
            List<String> adapterIds,
            Map<String, String> parameters,
            int maxClaims) {
        public Request {
            question = required(question, "reasoning question", MAX_QUESTION_CHARS);
            evidence = immutableObjects(evidence, "evidence", 0, MAX_EVIDENCE);
            adapterIds = immutableStrings(adapterIds, "adapter ids", 0, MAX_ADAPTERS, 128);
            parameters = immutableMap(parameters, "reasoning parameters", MAX_PARAMETER_ENTRIES);
            if (maxClaims < 1 || maxClaims > MAX_CLAIMS) {
                throw new IllegalArgumentException("maxClaims must be between 1 and " + MAX_CLAIMS);
            }
        }

        public static Request factsOnly(String question, List<Evidence> evidence) {
            return new Request(question, evidence, List.of(), Map.of(), MAX_CLAIMS);
        }
    }

    public record AdapterRequest(
            String question,
            List<Evidence> evidence,
            Map<String, String> parameters,
            int maxClaims) {
        public AdapterRequest {
            question = required(question, "reasoning question", MAX_QUESTION_CHARS);
            evidence = immutableObjects(evidence, "evidence", 0, MAX_EVIDENCE);
            parameters = immutableMap(parameters, "reasoning parameters", MAX_PARAMETER_ENTRIES);
            if (maxClaims < 1 || maxClaims > MAX_CLAIMS) {
                throw new IllegalArgumentException("adapter maxClaims must be between 1 and " + MAX_CLAIMS);
            }
        }
    }

    public record AdapterResult(List<Claim> claims, Map<String, String> metadata) {
        public AdapterResult {
            claims = immutableObjects(claims, "adapter claims", 0, MAX_CLAIMS);
            metadata = immutableMap(metadata, "adapter metadata", MAX_PROVENANCE_ENTRIES);
        }

        public static AdapterResult empty() {
            return new AdapterResult(List.of(), Map.of());
        }
    }

    public record AdapterExecution(
            String adapterId,
            AdapterStatus status,
            int acceptedClaims,
            String message,
            Map<String, String> metadata) {
        public AdapterExecution {
            adapterId = required(adapterId, "adapter id", 128);
            status = Objects.requireNonNull(status, "status");
            if (acceptedClaims < 0 || acceptedClaims > MAX_CLAIMS) {
                throw new IllegalArgumentException("acceptedClaims out of range");
            }
            message = optional(message, MAX_STATEMENT_CHARS);
            metadata = immutableMap(metadata, "execution metadata", MAX_PROVENANCE_ENTRIES);
            if (status == AdapterStatus.FAILED && acceptedClaims != 0) {
                throw new IllegalArgumentException("failed adapter execution cannot accept claims");
            }
        }
    }

    public record Result(
            String question,
            List<Evidence> evidence,
            List<Evidence> facts,
            List<Claim> inferences,
            List<Claim> heuristics,
            List<Claim> suggestions,
            List<AdapterExecution> executions,
            boolean assisted,
            boolean mutated) {
        public Result {
            question = required(question, "reasoning question", MAX_QUESTION_CHARS);
            evidence = immutableObjects(evidence, "evidence", 0, MAX_EVIDENCE);
            facts = immutableObjects(facts, "facts", 0, MAX_EVIDENCE);
            inferences = immutableObjects(inferences, "inferences", 0, MAX_CLAIMS);
            heuristics = immutableObjects(heuristics, "heuristics", 0, MAX_CLAIMS);
            suggestions = immutableObjects(suggestions, "suggestions", 0, MAX_CLAIMS);
            executions = immutableObjects(executions, "executions", 0, MAX_ADAPTERS);
            List<Evidence> expectedFacts = evidence.stream()
                    .filter(item -> item.kind() == EvidenceKind.PUBLISHED_FACT)
                    .toList();
            if (!facts.equals(expectedFacts)) {
                throw new IllegalArgumentException("facts must exactly match PUBLISHED_FACT evidence");
            }
            requireClaimKind(inferences, ClaimKind.INFERENCE, "inferences");
            requireClaimKind(heuristics, ClaimKind.HEURISTIC, "heuristics");
            requireClaimKind(suggestions, ClaimKind.SUGGESTION, "suggestions");
            Set<String> evidenceIds = new LinkedHashSet<>();
            for (Evidence item : evidence) {
                if (!evidenceIds.add(item.id())) {
                    throw new IllegalArgumentException("duplicate evidence id in result: " + item.id());
                }
            }
            requireKnownEvidence(inferences, evidenceIds);
            requireKnownEvidence(heuristics, evidenceIds);
            requireKnownEvidence(suggestions, evidenceIds);
            boolean hasClaims = !inferences.isEmpty() || !heuristics.isEmpty() || !suggestions.isEmpty();
            if (assisted != hasClaims) {
                throw new IllegalArgumentException("assisted must reflect the presence of accepted assisted claims");
            }
            if (mutated) {
                throw new IllegalArgumentException("reasoning results are read-only and cannot report mutation");
            }
        }
    }

    private static void requireClaimKind(List<Claim> claims, ClaimKind expected, String name) {
        if (claims.stream().anyMatch(claim -> claim.kind() != expected)) {
            throw new IllegalArgumentException(name + " contains a claim with a different kind");
        }
    }

    private static void requireKnownEvidence(List<Claim> claims, Set<String> evidenceIds) {
        for (Claim claim : claims) {
            for (String evidenceId : claim.evidenceIds()) {
                if (!evidenceIds.contains(evidenceId)) {
                    throw new IllegalArgumentException(
                            "claim " + claim.id() + " cites unknown result evidence: " + evidenceId);
                }
            }
        }
    }

    private static String required(String raw, String name, int maxChars) {
        String value = Objects.requireNonNull(raw, name).trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        if (value.length() > maxChars) {
            throw new IllegalArgumentException(name + " exceeds " + maxChars + " characters");
        }
        return value;
    }

    private static String optional(String raw, int maxChars) {
        if (raw == null) {
            return "";
        }
        String value = raw.trim();
        if (value.length() > maxChars) {
            throw new IllegalArgumentException("text exceeds " + maxChars + " characters");
        }
        return value;
    }

    private static List<String> immutableStrings(
            List<String> raw,
            String name,
            int minimum,
            int maximum,
            int maxChars) {
        List<String> source = raw == null ? List.of() : raw;
        if (source.size() < minimum || source.size() > maximum) {
            throw new IllegalArgumentException(name + " size must be between " + minimum + " and " + maximum);
        }
        List<String> result = new ArrayList<>(source.size());
        Set<String> unique = new LinkedHashSet<>();
        for (String item : source) {
            String value = required(item, name + " item", maxChars);
            if (!unique.add(value)) {
                throw new IllegalArgumentException(name + " contains duplicate value: " + value);
            }
            result.add(value);
        }
        return List.copyOf(result);
    }

    private static <T> List<T> immutableObjects(
            List<T> raw,
            String name,
            int minimum,
            int maximum) {
        List<T> source = raw == null ? List.of() : raw;
        if (source.size() < minimum || source.size() > maximum) {
            throw new IllegalArgumentException(name + " size must be between " + minimum + " and " + maximum);
        }
        List<T> result = new ArrayList<>(source.size());
        for (T item : source) {
            result.add(Objects.requireNonNull(item, name + " item"));
        }
        return List.copyOf(result);
    }

    private static Map<String, String> immutableMap(Map<String, String> raw, String name, int maximum) {
        Map<String, String> source = raw == null ? Map.of() : raw;
        if (source.size() > maximum) {
            throw new IllegalArgumentException(name + " exceeds " + maximum + " entries");
        }
        Map<String, String> result = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            String normalizedKey = required(key, name + " key", 128);
            String normalizedValue = required(value, name + " value", 2_048);
            if (result.putIfAbsent(normalizedKey, normalizedValue) != null) {
                throw new IllegalArgumentException(name + " contains duplicate normalized key: " + normalizedKey);
            }
        });
        return Map.copyOf(result);
    }
}
