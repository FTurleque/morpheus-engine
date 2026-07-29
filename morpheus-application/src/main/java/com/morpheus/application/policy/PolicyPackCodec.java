package com.morpheus.application.policy;

import com.morpheus.application.query.dsl.QueryDefinitionCodec;
import com.morpheus.domain.change.ChangeId;
import com.morpheus.domain.change.lifecycle.ChangeLifecycleState;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/** Versioned deterministic binary codec for immutable M25 policy pack versions. */
public final class PolicyPackCodec {
    private static final int VERSION = 1;
    private final QueryDefinitionCodec queryCodec = new QueryDefinitionCodec();

    public String encode(PolicyPack.Version version) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream out = new DataOutputStream(bytes)) {
                out.writeInt(VERSION);
                out.writeUTF(version.packId().toString());
                out.writeUTF(version.versionId().toString());
                out.writeLong(version.versionNumber());
                out.writeUTF(version.name());
                out.writeUTF(version.createdAt().toString());
                out.writeInt(version.rules().size());
                for (PolicyRule rule : version.rules()) {
                    writeRule(out, rule);
                }
            }
            return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes.toByteArray());
        } catch (IOException exception) {
            throw new IllegalStateException("cannot encode policy pack version", exception);
        }
    }

    public PolicyPack.Version decode(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            throw new IllegalArgumentException("encoded policy pack version must not be blank");
        }
        try {
            byte[] bytes = Base64.getUrlDecoder().decode(encoded);
            try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes))) {
                int version = in.readInt();
                if (version != VERSION) {
                    throw new IllegalArgumentException("unsupported policy pack codec version: " + version);
                }
                PolicyIds.PackId packId = PolicyIds.PackId.parse(in.readUTF());
                PolicyIds.VersionId versionId = PolicyIds.VersionId.parse(in.readUTF());
                long versionNumber = in.readLong();
                String name = in.readUTF();
                Instant createdAt = Instant.parse(in.readUTF());
                int count = in.readInt();
                if (count <= 0 || count > PolicyBudgets.MAX_RULES_PER_PACK) {
                    throw new IllegalArgumentException("invalid encoded policy rule count: " + count);
                }
                List<PolicyRule> rules = new ArrayList<>(count);
                for (int index = 0; index < count; index++) {
                    rules.add(readRule(in));
                }
                if (in.available() != 0) {
                    throw new IllegalArgumentException("encoded policy pack contains trailing data");
                }
                return new PolicyPack.Version(packId, versionId, versionNumber, name, rules, createdAt);
            }
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("invalid encoded policy pack version", exception);
        }
    }

    private void writeRule(DataOutputStream out, PolicyRule rule) throws IOException {
        out.writeUTF(rule.id().toString());
        out.writeUTF(rule.description());
        out.writeUTF(rule.kind().name());
        out.writeUTF(rule.severity().name());
        switch (rule.config()) {
            case PolicyRule.ConstraintGuard config -> {
                out.writeUTF(config.changeId().toString());
                out.writeUTF(config.targetState().name());
            }
            case PolicyRule.LifecycleGuard config -> {
                out.writeUTF(config.changeId().toString());
                out.writeUTF(config.sourceState().name());
                out.writeUTF(config.targetState().name());
            }
            case PolicyRule.QualityThreshold config -> {
                out.writeUTF(config.metric().name());
                out.writeUTF(config.comparison().name());
                out.writeDouble(config.threshold());
            }
            case PolicyRule.QueryAssertion config -> {
                out.writeUTF(queryCodec.encode(config.query()));
                out.writeUTF(config.comparison().name());
                out.writeLong(config.expectedCount());
            }
        }
    }

    private PolicyRule readRule(DataInputStream in) throws IOException {
        PolicyIds.RuleId id = PolicyIds.RuleId.parse(in.readUTF());
        String description = in.readUTF();
        PolicyRule.Kind kind = PolicyRule.Kind.valueOf(in.readUTF());
        PolicyRule.Severity severity = PolicyRule.Severity.valueOf(in.readUTF());
        PolicyRule.Config config = switch (kind) {
            case CONSTRAINT_GUARD -> new PolicyRule.ConstraintGuard(
                    ChangeId.parse(in.readUTF()), ChangeLifecycleState.valueOf(in.readUTF()));
            case LIFECYCLE_GUARD -> new PolicyRule.LifecycleGuard(
                    ChangeId.parse(in.readUTF()),
                    ChangeLifecycleState.valueOf(in.readUTF()),
                    ChangeLifecycleState.valueOf(in.readUTF()));
            case QUALITY_THRESHOLD -> new PolicyRule.QualityThreshold(
                    PolicyRule.QualityMetric.valueOf(in.readUTF()),
                    PolicyRule.Comparison.valueOf(in.readUTF()),
                    in.readDouble());
            case QUERY_ASSERTION -> new PolicyRule.QueryAssertion(
                    queryCodec.decode(in.readUTF()),
                    PolicyRule.Comparison.valueOf(in.readUTF()),
                    in.readLong());
        };
        return new PolicyRule(id, description, kind, severity, config);
    }
}