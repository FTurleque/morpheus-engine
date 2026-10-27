package com.morpheus.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpecificationQueryApiServiceArchitectureTest {

    @Test
    void activeSpecificationQueriesStayExtractedFromApiFacade() throws IOException {
        String facade = Files.readString(ProductionSources.mainSource("morpheus-api", "MorpheusApiService"));
        String specifications = Files.readString(ProductionSources.mainSource("morpheus-api", "MorpheusSpecificationQueryApiService"));
        String routes = Files.readString(ProductionSources.mainSource("morpheus-api", "MorpheusSpecificationsHttpRoutes"));

        assertFacadeDelegatesToSpecificationQueryService(facade);
        assertSpecificationQueryServiceImplementsQueries(specifications);
        assertSpecificationsHttpRoutesUseQueryService(routes);
    }

    private void assertFacadeDelegatesToSpecificationQueryService(String facade) {
        assertTrue(facade.contains("private final MorpheusSpecificationQueryApiService specificationQueryService;"));
        assertTrue(facade.contains("return specificationQueryService.listSpecifications(projectIdValue, pageRequest);"));
        assertTrue(facade.contains("return specificationQueryService.specification(projectIdValue, specificationIdValue);"));
        assertTrue(facade.contains("return specificationQueryService.specificationContext(projectIdValue, specificationIdValue, pageRequest);"));
        assertFalse(facade.contains("new SpecificationContextQueryService("));
        assertFalse(facade.contains("content.specifications().stream()"));
        assertFalse(facade.contains("activeSpecification(projectId, specificationId)"));
        assertFalse(facade.contains("private Object specification(Specification item)"));
        assertFalse(facade.contains("private Object scenario(Scenario item)"));
    }

    private void assertSpecificationQueryServiceImplementsQueries(String specifications) {
        assertTrue(specifications.contains("final class MorpheusSpecificationQueryApiService"));
        assertTrue(specifications.contains("content.specifications().stream()"));
        assertTrue(specifications.contains("new SpecificationContextQueryService("));
        assertTrue(specifications.contains("activeSpecification(projectId, specificationId)"));
        assertTrue(specifications.contains("PublicPayloads::specification"));
        assertTrue(specifications.contains("PublicPayloads::scenario"));
        assertFalse(specifications.contains("private Object specification(Specification item)"));
        assertFalse(specifications.contains("private Object scenario(Scenario item)"));
        assertFalse(specifications.contains("RequirementQueryService"));
        assertFalse(specifications.contains("TraceRequirementQueryService"));
        assertFalse(specifications.contains("ChangeContextQueryService"));
        assertFalse(specifications.contains("QualityReportService"));
        assertFalse(specifications.contains("PublishedSnapshotHistoryService"));
        assertFalse(specifications.contains("LocalSourceInventoryScanner"));
        assertFalse(specifications.contains("MorpheusHttpResponseWriter"));
        assertFalse(specifications.contains("MorpheusRemote"));
    }

    private void assertSpecificationsHttpRoutesUseQueryService(String routes) {
        assertTrue(routes.contains("private final MorpheusSpecificationQueryApiService service;"));
        assertTrue(routes.contains("specificationQueryService()"));
        assertTrue(routes.contains("service.listSpecifications(projectId, page(query))"));
        assertTrue(routes.contains("service.specification(projectId, segments.get(3))"));
        assertTrue(routes.contains("service.specificationContext(projectId, segments.get(3), page(query))"));
        assertFalse(routes.contains("private final MorpheusApiService service;"));
    }
}
