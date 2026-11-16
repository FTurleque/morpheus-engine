package com.morpheus.architecture;

import com.morpheus.application.read.ProviderReadRequest;
import com.morpheus.application.read.ProviderReadResult;
import com.morpheus.application.read.ReadCategory;
import com.morpheus.application.read.ReadCategoryReport;
import com.morpheus.application.read.ReadCategoryStatus;
import com.morpheus.domain.diagnostic.Diagnostic;
import com.morpheus.domain.diagnostic.DiagnosticCode;
import com.morpheus.domain.identity.DomainIdentity;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.provider.ProviderProbeResult;
import com.morpheus.provider.openspec.OpenSpecSpecificationContentReader;
import com.morpheus.provider.openspec.OpenSpecSpecificationProvider;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The specification corpus of this repository is part of the qualification set of the default evidence budget: the
 * provider probes {@code openspec/}, whose schema declares that it derives from {@code spec-driven}, and reads it with
 * the default budget without any category failing on a budget refusal.
 */
class RepositorySpecificationCorpusFitsTheDefaultBudgetTest {

    @Test
    void theOpenSpecCorpusOfThisRepositoryIsProbedAndReadWithinTheDefaultBudget() {
        Path root = RepositoryRoot.locate();

        ProviderProbeResult probe = new OpenSpecSpecificationProvider().probe(root);
        assertTrue(probe.supported(), () -> "the repository schema is read: " + probe);

        Map<String, DomainIdentity> identities = new TreeMap<>();
        ProviderReadResult result = new OpenSpecSpecificationContentReader().read(
                new ProviderReadRequest(root, ProjectSpecificationId.generate(), EnumSet.allOf(ReadCategory.class)),
                (provider, entityType, externalId) -> identities.computeIfAbsent(
                        provider.value() + "|" + entityType + "|" + externalId, ignored -> DomainIdentity.generate()));

        List<Diagnostic> refusals = result.diagnostics().stream()
                .filter(RepositorySpecificationCorpusFitsTheDefaultBudgetTest::isBudgetRefusal)
                .toList();
        assertEquals(List.of(), refusals, "no budget refusal on the repository corpus");
        for (ReadCategory category : EnumSet.allOf(ReadCategory.class)) {
            ReadCategoryReport report = result.report(category).orElseThrow();
            assertTrue(report.status() != ReadCategoryStatus.FAILED, () -> report + ": " + result.diagnostics());
        }
    }

    private static boolean isBudgetRefusal(Diagnostic diagnostic) {
        return diagnostic.code() == DiagnosticCode.INVALID_SOURCE
                && "ingestion-budget".equals(diagnostic.details().get("group"));
    }
}
