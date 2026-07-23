package com.morpheus.application.quality;

import com.morpheus.application.lifecycle.ChangeLifecycleFacts;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Tri-state projection of the nine facts consumed by the M3 change lifecycle state machine. */
public record ChangeLifecycleFactAssessment(
        QualityFactValue requirementsIdentified,
        QualityFactValue criticalConstraintsKnown,
        QualityFactValue acceptanceCriteriaDefined,
        QualityFactValue designRequired,
        QualityFactValue designDecisionsAvailable,
        QualityFactValue planPresent,
        QualityFactValue knownBlocker,
        QualityFactValue blockingAcceptanceCriterionFailed,
        QualityFactValue blockingAcceptanceCriterionUnverified) {

    public ChangeLifecycleFactAssessment {
        Objects.requireNonNull(requirementsIdentified, "requirementsIdentified");
        Objects.requireNonNull(criticalConstraintsKnown, "criticalConstraintsKnown");
        Objects.requireNonNull(acceptanceCriteriaDefined, "acceptanceCriteriaDefined");
        Objects.requireNonNull(designRequired, "designRequired");
        Objects.requireNonNull(designDecisionsAvailable, "designDecisionsAvailable");
        Objects.requireNonNull(planPresent, "planPresent");
        Objects.requireNonNull(knownBlocker, "knownBlocker");
        Objects.requireNonNull(blockingAcceptanceCriterionFailed, "blockingAcceptanceCriterionFailed");
        Objects.requireNonNull(blockingAcceptanceCriterionUnverified, "blockingAcceptanceCriterionUnverified");
    }

    public QualityFactValue value(String factName) {
        Objects.requireNonNull(factName, "factName");
        return switch (factName) {
            case "requirementsIdentified" -> requirementsIdentified;
            case "criticalConstraintsKnown" -> criticalConstraintsKnown;
            case "acceptanceCriteriaDefined" -> acceptanceCriteriaDefined;
            case "designRequired" -> designRequired;
            case "designDecisionsAvailable" -> designDecisionsAvailable;
            case "planPresent" -> planPresent;
            case "knownBlocker" -> knownBlocker;
            case "blockingAcceptanceCriterionFailed" -> blockingAcceptanceCriterionFailed;
            case "blockingAcceptanceCriterionUnverified" -> blockingAcceptanceCriterionUnverified;
            default -> throw new IllegalArgumentException("unknown lifecycle fact: " + factName);
        };
    }

    public List<String> unavailableFacts() {
        return orderedValues().entrySet().stream()
                .filter(entry -> entry.getValue() == QualityFactValue.UNAVAILABLE)
                .map(Map.Entry::getKey)
                .toList();
    }

    public static ChangeLifecycleFactAssessment explicit(ChangeLifecycleFacts facts) {
        Objects.requireNonNull(facts, "facts");
        return new ChangeLifecycleFactAssessment(
                QualityFactValue.of(facts.requirementsIdentified()),
                QualityFactValue.of(facts.criticalConstraintsKnown()),
                QualityFactValue.of(facts.acceptanceCriteriaDefined()),
                QualityFactValue.of(facts.designRequired()),
                QualityFactValue.of(facts.designDecisionsAvailable()),
                QualityFactValue.of(facts.planPresent()),
                QualityFactValue.of(facts.knownBlocker()),
                QualityFactValue.of(facts.blockingAcceptanceCriterionFailed()),
                QualityFactValue.of(facts.blockingAcceptanceCriterionUnverified()));
    }

    public ChangeLifecycleFacts materializeUnavailableAsFalse() {
        return new ChangeLifecycleFacts(
                booleanValue(requirementsIdentified),
                booleanValue(criticalConstraintsKnown),
                booleanValue(acceptanceCriteriaDefined),
                booleanValue(designRequired),
                booleanValue(designDecisionsAvailable),
                booleanValue(planPresent),
                booleanValue(knownBlocker),
                booleanValue(blockingAcceptanceCriterionFailed),
                booleanValue(blockingAcceptanceCriterionUnverified));
    }

    private Map<String, QualityFactValue> orderedValues() {
        Map<String, QualityFactValue> values = new LinkedHashMap<>();
        values.put("requirementsIdentified", requirementsIdentified);
        values.put("criticalConstraintsKnown", criticalConstraintsKnown);
        values.put("acceptanceCriteriaDefined", acceptanceCriteriaDefined);
        values.put("designRequired", designRequired);
        values.put("designDecisionsAvailable", designDecisionsAvailable);
        values.put("planPresent", planPresent);
        values.put("knownBlocker", knownBlocker);
        values.put("blockingAcceptanceCriterionFailed", blockingAcceptanceCriterionFailed);
        values.put("blockingAcceptanceCriterionUnverified", blockingAcceptanceCriterionUnverified);
        return values;
    }

    private static boolean booleanValue(QualityFactValue value) {
        return value == QualityFactValue.TRUE;
    }
}
