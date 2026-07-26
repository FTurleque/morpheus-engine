package com.morpheus.application.traceability;

import com.morpheus.application.ingestion.NormalizedProjectContent;
import com.morpheus.domain.acceptance.AcceptanceCriterion;
import com.morpheus.domain.evidence.EvidenceId;
import com.morpheus.domain.identity.DomainIdentity;
import com.morpheus.domain.scenario.Scenario;
import com.morpheus.domain.traceability.TraceabilityEntityKind;
import com.morpheus.domain.traceability.TraceabilityEntityRef;
import com.morpheus.domain.traceability.TraceabilityLink;
import com.morpheus.domain.traceability.TraceabilityLinkId;
import com.morpheus.domain.traceability.TraceabilityLinkOrigin;
import com.morpheus.domain.traceability.TraceabilityRelationType;
import com.morpheus.domain.traceability.TraceabilityResolutionState;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/** Deterministically derives only traceability relations already encoded by normalized structural references. */
public final class DeterministicTraceabilityDerivationService {

    public List<TraceabilityLink> derive(
            NormalizedProjectContent content,
            TraceabilityLinkIdentityResolver identityResolver,
            Instant observedAt) {
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(identityResolver, "identityResolver");
        Objects.requireNonNull(observedAt, "observedAt");

        Map<TraceabilityDerivationKey, Set<EvidenceId>> evidenceByKey = new TreeMap<>();

        content.requirements().forEach(requirement -> addFact(
                evidenceByKey,
                new TraceabilityDerivationKey(
                        ref(TraceabilityEntityKind.REQUIREMENT, requirement.id().value()),
                        ref(TraceabilityEntityKind.REQUIREMENT, requirement.id().value()),
                        TraceabilityRelationType.DERIVES_FROM,
                        ref(TraceabilityEntityKind.SPECIFICATION, requirement.specificationId().value())),
                requirement.provenance().evidenceId()));

        content.scenarios().forEach(scenario -> deriveScenario(evidenceByKey, scenario));

        content.constraints().forEach(constraint -> addFact(
                evidenceByKey,
                new TraceabilityDerivationKey(
                        ref(TraceabilityEntityKind.CONSTRAINT, constraint.id().value()),
                        ref(TraceabilityEntityKind.CONSTRAINT, constraint.id().value()),
                        TraceabilityRelationType.CONSTRAINS,
                        ref(TraceabilityEntityKind.CHANGE, constraint.changeId().value())),
                constraint.provenance().evidenceId()));

        content.designDecisions().forEach(decision -> addFact(
                evidenceByKey,
                new TraceabilityDerivationKey(
                        ref(TraceabilityEntityKind.DESIGN_DECISION, decision.id().value()),
                        ref(TraceabilityEntityKind.CHANGE, decision.changeId().value()),
                        TraceabilityRelationType.DECIDED_BY,
                        ref(TraceabilityEntityKind.DESIGN_DECISION, decision.id().value())),
                decision.provenance().evidenceId()));

        content.requirementDeltas().forEach(delta -> {
            addFact(
                    evidenceByKey,
                    new TraceabilityDerivationKey(
                            ref(TraceabilityEntityKind.REQUIREMENT_DELTA, delta.id().value()),
                            ref(TraceabilityEntityKind.CHANGE, delta.changeId().value()),
                            TraceabilityRelationType.AFFECTS,
                            ref(TraceabilityEntityKind.REQUIREMENT, delta.requirementId().value())),
                    delta.provenance().evidenceId());
            delta.scenarios().forEach(scenario -> deriveScenario(evidenceByKey, scenario));
        });

        content.acceptanceCriteria().forEach(criterion -> deriveAcceptanceCriterion(evidenceByKey, criterion));

        List<TraceabilityLink> links = new ArrayList<>(evidenceByKey.size());
        Map<TraceabilityLinkId, TraceabilityDerivationKey> keyByLinkId = new HashMap<>();

        evidenceByKey.forEach((key, evidenceIds) -> {
            Optional<TraceabilityLinkId> resolved = Objects.requireNonNull(
                    identityResolver.resolve(key),
                    "identityResolver returned null Optional");
            TraceabilityLinkId linkId = resolved.orElseThrow(() ->
                    new IllegalArgumentException("missing traceability link identity for derivation key: " + key));
            TraceabilityDerivationKey existingKey = keyByLinkId.putIfAbsent(linkId, key);
            if (existingKey != null && !existingKey.equals(key)) {
                throw new IllegalArgumentException(
                        "traceability link identity is assigned to multiple derivation keys: " + linkId);
            }

            links.add(new TraceabilityLink(
                    linkId,
                    key.source(),
                    key.relationType(),
                    key.target(),
                    TraceabilityLinkOrigin.DERIVED,
                    TraceabilityResolutionState.RESOLVED,
                    Optional.empty(),
                    evidenceIds,
                    observedAt));
        });
        return List.copyOf(links);
    }

    private void deriveScenario(
            Map<TraceabilityDerivationKey, Set<EvidenceId>> evidenceByKey,
            Scenario scenario) {
        scenario.requirementId().ifPresent(requirementId -> addFact(
                evidenceByKey,
                new TraceabilityDerivationKey(
                        ref(TraceabilityEntityKind.SCENARIO, scenario.id().value()),
                        ref(TraceabilityEntityKind.SCENARIO, scenario.id().value()),
                        TraceabilityRelationType.REFINES,
                        ref(TraceabilityEntityKind.REQUIREMENT, requirementId.value())),
                scenario.provenance().evidenceId()));
    }

    private void deriveAcceptanceCriterion(
            Map<TraceabilityDerivationKey, Set<EvidenceId>> evidenceByKey,
            AcceptanceCriterion criterion) {
        TraceabilityEntityRef criterionRef = ref(
                TraceabilityEntityKind.ACCEPTANCE_CRITERION,
                criterion.id().value());

        criterion.requirementId().ifPresent(requirementId -> addFact(
                evidenceByKey,
                new TraceabilityDerivationKey(
                        criterionRef,
                        ref(TraceabilityEntityKind.REQUIREMENT, requirementId.value()),
                        TraceabilityRelationType.VERIFIED_BY,
                        criterionRef),
                criterion.provenance().evidenceId()));

        criterion.changeId().ifPresent(changeId -> addFact(
                evidenceByKey,
                new TraceabilityDerivationKey(
                        criterionRef,
                        ref(TraceabilityEntityKind.CHANGE, changeId.value()),
                        TraceabilityRelationType.VERIFIED_BY,
                        criterionRef),
                criterion.provenance().evidenceId()));

        criterion.verificationEvidenceIds().forEach(evidenceId -> addFact(
                evidenceByKey,
                new TraceabilityDerivationKey(
                        ref(TraceabilityEntityKind.EVIDENCE, evidenceId.value()),
                        criterionRef,
                        TraceabilityRelationType.VERIFIED_BY,
                        ref(TraceabilityEntityKind.EVIDENCE, evidenceId.value())),
                evidenceId));
    }

    private void addFact(
            Map<TraceabilityDerivationKey, Set<EvidenceId>> evidenceByKey,
            TraceabilityDerivationKey key,
            EvidenceId evidenceId) {
        evidenceByKey.computeIfAbsent(key, ignored -> new TreeSet<>()).add(evidenceId);
    }

    private TraceabilityEntityRef ref(TraceabilityEntityKind kind, DomainIdentity identity) {
        return new TraceabilityEntityRef(kind, identity);
    }
}
