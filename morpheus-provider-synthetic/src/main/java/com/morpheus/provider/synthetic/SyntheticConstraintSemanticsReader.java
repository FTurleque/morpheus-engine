package com.morpheus.provider.synthetic;

import com.morpheus.application.identity.EntityIdentityResolver;
import com.morpheus.application.files.SafeWorkspaceFileResolver;
import com.morpheus.domain.change.ChangeId;
import com.morpheus.domain.change.lifecycle.ChangeLifecycleState;
import com.morpheus.domain.constraint.Constraint;
import com.morpheus.domain.constraint.ConstraintApplicability;
import com.morpheus.domain.constraint.ConstraintBlockingMode;
import com.morpheus.domain.constraint.ConstraintBlockingPolicy;
import com.morpheus.domain.constraint.ConstraintId;
import com.morpheus.domain.constraint.ConstraintSatisfaction;
import com.morpheus.domain.constraint.ConstraintSeverity;
import com.morpheus.domain.evidence.Evidence;
import com.morpheus.domain.evidence.EvidenceId;
import com.morpheus.domain.evidence.SourceRange;
import com.morpheus.domain.provenance.Provenance;
import com.morpheus.domain.source.SourceLocator;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** Parses only explicit synthetic M16 policy fields; no text heuristic is allowed. */
final class SyntheticConstraintSemanticsReader {

    List<Constraint> read(
            List<Object> rawConstraints,
            ChangeId changeId,
            String changeExternalId,
            Path workspaceRoot,
            SourceLocator definitionSource,
            String sourceText,
            int sourceLines,
            EntityIdentityResolver identities,
            List<Evidence> evidence) throws IOException {
        List<Constraint> result = new ArrayList<>();
        for (Object raw : rawConstraints) {
            Map<String, Object> source = object(raw, "constraint");
            String key = string(source, "key");
            String externalId = "constraint:" + changeExternalId + "/" + key;
            Evidence definition = definitionEvidence(identities, externalId, definitionSource, sourceText, sourceLines);
            evidence.add(definition);

            List<EvidenceId> supportingEvidence = new ArrayList<>();
            for (Object rawEvidence : optionalArray(source, "supporting_evidence")) {
                Map<String, Object> support = object(rawEvidence, "constraint supporting evidence");
                String relativePath = string(support, "source");
                Evidence item = fileEvidence(
                        workspaceRoot,
                        relativePath,
                        identities,
                        externalId + "/support/" + relativePath);
                evidence.add(item);
                supportingEvidence.add(item.id());
            }

            Map<String, Object> rawPolicy = object(source, "blocking_policy");
            ConstraintBlockingMode mode = enumValue(
                    ConstraintBlockingMode.class, string(rawPolicy, "mode"), "blocking_policy.mode");
            List<ChangeLifecycleState> targets = optionalArray(rawPolicy, "targets").stream()
                    .map(value -> {
                        if (!(value instanceof String text)) {
                            throw new IllegalArgumentException("blocking_policy.targets must contain strings");
                        }
                        return enumValue(ChangeLifecycleState.class, text, "blocking_policy.targets");
                    })
                    .toList();

            result.add(new Constraint(
                    new ConstraintId(identities.resolve(
                            SyntheticSpecificationProvider.ID, "constraint", externalId)),
                    changeId,
                    string(source, "statement"),
                    enumValue(ConstraintApplicability.class, string(source, "applicability"), "applicability"),
                    enumValue(ConstraintSeverity.class, string(source, "severity"), "severity"),
                    enumValue(ConstraintSatisfaction.class, string(source, "satisfaction"), "satisfaction"),
                    new ConstraintBlockingPolicy(mode, targets),
                    supportingEvidence,
                    new Provenance(
                            SyntheticSpecificationProvider.ID,
                            Optional.of(SyntheticSpecificationProvider.PROVIDER_VERSION),
                            definitionSource,
                            Optional.of(externalId),
                            Optional.empty(),
                            definition.id())));
        }
        return List.copyOf(result);
    }

    private Evidence definitionEvidence(
            EntityIdentityResolver identities,
            String externalId,
            SourceLocator source,
            String sourceText,
            int sourceLines) {
        return new Evidence(
                new EvidenceId(identities.resolve(
                        SyntheticSpecificationProvider.ID, "evidence", "evidence:" + externalId)),
                source,
                Optional.of(new SourceRange(1, sourceLines)),
                Optional.of(sha256(sourceText)));
    }

    private Evidence fileEvidence(
            Path workspaceRoot,
            String relativePath,
            EntityIdentityResolver identities,
            String externalId) throws IOException {
        String text = SafeWorkspaceFileResolver.rootedAt(workspaceRoot).readUtf8(Path.of(relativePath));
        int lines = Math.max(1, text.lines().toList().size());
        return new Evidence(
                new EvidenceId(identities.resolve(
                        SyntheticSpecificationProvider.ID, "evidence", "evidence:" + externalId)),
                SourceLocator.file(relativePath),
                Optional.of(new SourceRange(1, lines)),
                Optional.of(sha256(text)));
    }

    private <E extends Enum<E>> E enumValue(Class<E> type, String raw, String field) {
        try {
            return Enum.valueOf(type, raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("unsupported " + field + ": " + raw, exception);
        }
    }

    private Map<String, Object> object(Object value, String name) {
        if (!(value instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException(name + " must be an object");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> typed = (Map<String, Object>) map;
        return typed;
    }

    private Map<String, Object> object(Map<String, Object> source, String key) {
        return object(source.get(key), key);
    }

    private List<Object> optionalArray(Map<String, Object> source, String key) {
        Object value = source.get(key);
        if (value == null) {
            return List.of();
        }
        if (!(value instanceof List<?> list)) {
            throw new IllegalArgumentException(key + " must be an array");
        }
        return List.copyOf(list);
    }

    private String string(Map<String, Object> source, String key) {
        Object value = source.get(key);
        if (!(value instanceof String text) || text.isBlank()) {
            throw new IllegalArgumentException(key + " must be a non-blank string");
        }
        return text.trim();
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
