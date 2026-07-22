package com.morpheus.application.ingestion;

import com.morpheus.domain.diagnostic.Diagnostic;
import com.morpheus.domain.evidence.Evidence;
import com.morpheus.domain.evidence.EvidenceId;
import com.morpheus.domain.project.ProjectSpecification;
import com.morpheus.domain.requirement.Requirement;
import com.morpheus.domain.requirement.RequirementId;
import com.morpheus.domain.scenario.Scenario;
import com.morpheus.domain.specification.Specification;
import com.morpheus.domain.specification.SpecificationId;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Coherent provider-neutral content produced by one M2 normalization pass. */
public record NormalizedProjectContent(
        ProjectSpecification project,
        List<Specification> specifications,
        List<Requirement> requirements,
        List<Scenario> scenarios,
        List<Evidence> evidence,
        List<Diagnostic> diagnostics) {

    public NormalizedProjectContent {
        Objects.requireNonNull(project, "project");
        specifications = List.copyOf(Objects.requireNonNull(specifications, "specifications"));
        requirements = List.copyOf(Objects.requireNonNull(requirements, "requirements"));
        scenarios = List.copyOf(Objects.requireNonNull(scenarios, "scenarios"));
        evidence = List.copyOf(Objects.requireNonNull(evidence, "evidence"));
        diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));

        Set<SpecificationId> specificationIds = new HashSet<>();
        for (Specification specification : specifications) {
            if (!specification.projectId().equals(project.id())) {
                throw new IllegalArgumentException("specification belongs to another project: " + specification.id());
            }
            if (!specificationIds.add(specification.id())) {
                throw new IllegalArgumentException("duplicate specification identity: " + specification.id());
            }
        }

        Set<RequirementId> requirementIds = new HashSet<>();
        for (Requirement requirement : requirements) {
            if (!specificationIds.contains(requirement.specificationId())) {
                throw new IllegalArgumentException("requirement references unknown specification: " + requirement.id());
            }
            if (!requirementIds.add(requirement.id())) {
                throw new IllegalArgumentException("duplicate requirement identity: " + requirement.id());
            }
        }

        scenarios.forEach(scenario -> scenario.requirementId().ifPresent(requirementId -> {
            if (!requirementIds.contains(requirementId)) {
                throw new IllegalArgumentException("scenario references unknown requirement: " + scenario.id());
            }
        }));

        Set<EvidenceId> evidenceIds = new HashSet<>();
        evidence.forEach(item -> {
            if (!evidenceIds.add(item.id())) {
                throw new IllegalArgumentException("duplicate evidence identity: " + item.id());
            }
        });

        specifications.forEach(item -> requireEvidence(item.provenance().evidenceId(), evidenceIds));
        requirements.forEach(item -> requireEvidence(item.provenance().evidenceId(), evidenceIds));
        scenarios.forEach(item -> requireEvidence(item.provenance().evidenceId(), evidenceIds));
    }

    private static void requireEvidence(EvidenceId evidenceId, Set<EvidenceId> evidenceIds) {
        if (!evidenceIds.contains(evidenceId)) {
            throw new IllegalArgumentException("provenance references unknown evidence: " + evidenceId);
        }
    }
}
