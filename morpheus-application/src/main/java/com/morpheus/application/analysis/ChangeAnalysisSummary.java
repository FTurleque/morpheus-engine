package com.morpheus.application.analysis;

/** Stable aggregate counts for one change analysis. */
public record ChangeAnalysisSummary(
        int addedRequirements,
        int modifiedRequirements,
        int removedRequirements,
        int changedDocumentaryFields,
        int currentScenarios,
        int proposedScenarios,
        int constraints,
        int designDecisions,
        int implementationTasks,
        int dependencies,
        int dependents,
        int warnings) {

    public ChangeAnalysisSummary {
        requireNonNegative(addedRequirements, "addedRequirements");
        requireNonNegative(modifiedRequirements, "modifiedRequirements");
        requireNonNegative(removedRequirements, "removedRequirements");
        requireNonNegative(changedDocumentaryFields, "changedDocumentaryFields");
        requireNonNegative(currentScenarios, "currentScenarios");
        requireNonNegative(proposedScenarios, "proposedScenarios");
        requireNonNegative(constraints, "constraints");
        requireNonNegative(designDecisions, "designDecisions");
        requireNonNegative(implementationTasks, "implementationTasks");
        requireNonNegative(dependencies, "dependencies");
        requireNonNegative(dependents, "dependents");
        requireNonNegative(warnings, "warnings");
    }

    public int affectedRequirements() {
        return addedRequirements + modifiedRequirements + removedRequirements;
    }

    private static void requireNonNegative(int value, String name) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must be non-negative");
        }
    }
}
