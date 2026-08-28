package com.morpheus.api;

import com.morpheus.application.portfolio.PortfolioQueryService;
import com.sun.net.httpserver.HttpExchange;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Local portfolio route group. HTTP dispatch only; portfolio behavior stays in the API application service. */
final class MorpheusPortfolioHttpRoutes {
    private final MorpheusPortfolioApiService service;
    private final MorpheusHttpRequestDecoder requestDecoder;

    MorpheusPortfolioHttpRoutes(MorpheusPortfolioApiService service, MorpheusHttpRequestDecoder requestDecoder) {
        this.service = Objects.requireNonNull(service, "service");
        this.requestDecoder = Objects.requireNonNull(requestDecoder, "requestDecoder");
    }

    MorpheusHttpRouteResponse route(
            HttpExchange exchange,
            String method,
            List<String> segments,
            MorpheusHttpQuery query) {
        Objects.requireNonNull(exchange, "exchange");
        Objects.requireNonNull(method, "method");
        Objects.requireNonNull(segments, "segments");
        Objects.requireNonNull(query, "query");

        if (segments.size() == 1) {
            if (method.equals("GET")) {
                query.rejectUnknown(Set.of("offset", "limit"));
                return ok(service.list(
                        query.intValue("offset", 0, 0, Integer.MAX_VALUE),
                        query.intValue("limit", 100, 1, PortfolioQueryService.MAX_PAGE_SIZE)));
            }
            if (method.equals("POST")) {
                query.rejectUnknown(Set.of());
                Object created = service.create(
                        requestDecoder.readRequiredJson(exchange, MorpheusPortfolioApiService.CreatePortfolioRequest.class));
                return new MorpheusHttpRouteResponse(201, created);
            }
            throw ApiFailure.methodNotAllowed("portfolios supports GET and POST");
        }

        String portfolioId = segments.get(1);
        if (segments.size() == 2) {
            MorpheusHttpRouteGuards.requireMethod(method, "GET");
            query.rejectUnknown(Set.of());
            return ok(service.overview(portfolioId));
        }

        String resource = segments.get(2);
        if (resource.equals("members")) {
            MorpheusHttpRouteGuards.requireExactSegments(segments, 3);
            MorpheusHttpRouteGuards.requireMethod(method, "GET");
            query.rejectUnknown(Set.of("offset", "limit"));
            return ok(service.members(
                    portfolioId,
                    query.intValue("offset", 0, 0, Integer.MAX_VALUE),
                    query.intValue("limit", 100, 1, PortfolioQueryService.MAX_PAGE_SIZE)));
        }
        if (resource.equals("projects")) {
            if (segments.size() == 3) {
                MorpheusHttpRouteGuards.requireMethod(method, "POST");
                query.rejectUnknown(Set.of());
                return new MorpheusHttpRouteResponse(201, service.registerProject(
                        portfolioId,
                        requestDecoder.readRequiredJson(exchange, MorpheusPortfolioApiService.RegisterProjectRequest.class)));
            }
            if (segments.size() == 5 && segments.get(4).equals("missing")) {
                MorpheusHttpRouteGuards.requireMethod(method, "POST");
                query.rejectUnknown(Set.of());
                return ok(service.markMissing(portfolioId, segments.get(3)));
            }
            if (segments.size() == 5 && segments.get(4).equals("freshness")) {
                MorpheusHttpRouteGuards.requireMethod(method, "POST");
                query.rejectUnknown(Set.of());
                return ok(service.observeFreshness(
                        portfolioId,
                        segments.get(3),
                        requestDecoder.readRequiredJson(exchange, MorpheusPortfolioApiService.FreshnessRequest.class)));
            }
            throw ApiFailure.notFound("unknown portfolio projects route");
        }
        if (resource.equals("references")) {
            MorpheusHttpRouteGuards.requireExactSegments(segments, 3);
            if (method.equals("GET")) {
                query.rejectUnknown(Set.of("projectId", "offset", "limit"));
                return ok(service.references(
                        portfolioId,
                        query.string("projectId").map(String::trim).filter(value -> !value.isEmpty()),
                        query.intValue("offset", 0, 0, Integer.MAX_VALUE),
                        query.intValue("limit", 100, 1, PortfolioQueryService.MAX_PAGE_SIZE)));
            }
            if (method.equals("POST")) {
                query.rejectUnknown(Set.of());
                return new MorpheusHttpRouteResponse(201, service.addReference(
                        portfolioId,
                        requestDecoder.readRequiredJson(
                                exchange, MorpheusPortfolioApiService.CrossProjectReferenceRequest.class)));
            }
            throw ApiFailure.methodNotAllowed("portfolio references supports GET and POST");
        }
        if (resource.equals("conflicts")) {
            MorpheusHttpRouteGuards.requireExactSegments(segments, 3);
            MorpheusHttpRouteGuards.requireMethod(method, "GET");
            query.rejectUnknown(Set.of());
            return ok(service.conflicts(portfolioId));
        }
        if (resource.equals("traverse")) {
            MorpheusHttpRouteGuards.requireExactSegments(segments, 3);
            MorpheusHttpRouteGuards.requireMethod(method, "POST");
            query.rejectUnknown(Set.of());
            return ok(service.traverse(
                    portfolioId,
                    requestDecoder.readRequiredJson(exchange, MorpheusPortfolioApiService.TraversalRequest.class)));
        }
        throw ApiFailure.notFound("unknown portfolio API resource: " + resource);
    }

    private MorpheusHttpRouteResponse ok(Object data) {
        return new MorpheusHttpRouteResponse(200, data);
    }
}
