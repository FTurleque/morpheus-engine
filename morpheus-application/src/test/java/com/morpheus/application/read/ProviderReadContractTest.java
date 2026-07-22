package com.morpheus.application.read;

import com.morpheus.domain.provider.ProviderId;
import com.morpheus.domain.project.ProjectSpecificationId;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProviderReadContractTest {

    @Test
    void requestRejectsEmptyCategorySet() {
        assertThrows(IllegalArgumentException.class, () -> new ProviderReadRequest(
                Path.of("."), ProjectSpecificationId.generate(), Set.of()));
    }

    @Test
    void resultRejectsDuplicateCategoryReports() {
        var report = ReadCategoryReport.of(ReadCategory.REQUIREMENTS, ReadCategoryStatus.READ, 2);

        assertThrows(IllegalArgumentException.class, () -> new ProviderReadResult(
                new ProviderId("test"), Optional.empty(), List.of(report, report), List.of()));
    }

    @Test
    void reportLookupIsProviderNeutral() {
        var report = ReadCategoryReport.of(ReadCategory.SCENARIOS, ReadCategoryStatus.PARTIAL, 1);
        var result = new ProviderReadResult(
                new ProviderId("synthetic"), Optional.empty(), List.of(report), List.of());

        assertEquals(ReadCategoryStatus.PARTIAL, result.report(ReadCategory.SCENARIOS).orElseThrow().status());
    }
}
