package com.morpheus.api;

import com.morpheus.api.transport.ApiError;
import com.morpheus.api.transport.ApiErrorEnvelope;
import com.morpheus.api.transport.ApiFailure;
import com.morpheus.api.transport.ApiSuccess;
import com.morpheus.api.transport.BoundaryFailureMessage;
import com.morpheus.api.transport.MorpheusHttpAllowedMethods;
import com.morpheus.api.transport.MorpheusHttpQuery;
import com.morpheus.api.transport.MorpheusHttpRequestDecoder;
import com.morpheus.api.transport.MorpheusHttpResponseWriter;
import com.morpheus.api.transport.MorpheusHttpRouteTable;
import com.morpheus.api.transport.UnforeseenFailureTrace;
import com.morpheus.application.operability.OperationalRecorder;
import com.morpheus.application.policy.PolicyConflictException;
import com.morpheus.application.query.PageRequest;
import com.morpheus.application.store.KnowledgeStoreException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Isolated M25 HTTP contexts for policy registry and read-only governance evaluation. */
final class MorpheusPolicyHttpRoutes {
    private static final String PACKS = "policy-packs";
    private static final String POLICIES = "policies";
    private static final String OVERRIDES = "policy-overrides";
    private static final String PACK_CONTEXT = MorpheusHttpRouteTable.API_PREFIX + "/" + PACKS;
    private static final String POLICY_CONTEXT = MorpheusHttpRouteTable.API_PREFIX + "/" + POLICIES;
    private static final String OVERRIDE_CONTEXT = MorpheusHttpRouteTable.API_PREFIX + "/" + OVERRIDES;

    private final MorpheusPolicyApiService service;
    private final OperationalRecorder recorder;
    private final MorpheusHttpRequestDecoder requestDecoder;
    private final MorpheusHttpAllowedMethods allowedMethods;
    private final MorpheusHttpResponseWriter responseWriter;

    private MorpheusPolicyHttpRoutes(Path databasePath, OperationalRecorder recorder,
            MorpheusHttpRequestDecoder requestDecoder, MorpheusHttpAllowedMethods allowedMethods,
            MorpheusHttpResponseWriter responseWriter) {
        service = new MorpheusPolicyApiService(databasePath, recorder);
        this.recorder = Objects.requireNonNull(recorder, "recorder");
        this.requestDecoder = Objects.requireNonNull(requestDecoder, "requestDecoder");
        this.allowedMethods = Objects.requireNonNull(allowedMethods, "allowedMethods");
        this.responseWriter = Objects.requireNonNull(responseWriter, "responseWriter");
    }

    static void register(HttpServer server, Path databasePath, OperationalRecorder recorder,
            MorpheusHttpRequestDecoder requestDecoder, MorpheusHttpAllowedMethods allowedMethods,
            MorpheusHttpResponseWriter responseWriter) {
        Objects.requireNonNull(server, "server");
        MorpheusPolicyHttpRoutes routes = new MorpheusPolicyHttpRoutes(databasePath, recorder, requestDecoder,
                allowedMethods, responseWriter);
        server.createContext(PACK_CONTEXT, routes::handlePacks);
        server.createContext(POLICY_CONTEXT, routes::handlePolicies);
        server.createContext(OVERRIDE_CONTEXT, routes::handleOverrides);
        MorpheusPolicyManagementHttpRoutes.register(server, databasePath, recorder, requestDecoder, allowedMethods,
                responseWriter);
    }

    private void handlePacks(HttpExchange exchange) throws IOException {
        handle(exchange, () -> routePacks(exchange));
    }

    private Response routePacks(HttpExchange exchange) {
        String method = exchange.getRequestMethod().toUpperCase(Locale.ROOT);
        List<String> segments = requestDecoder.pathSegmentsBelow(exchange, PACKS);
        if (segments.isEmpty()) {
            rejectQueryParameters(exchange);
            if (method.equals("GET")) {
                requestDecoder.requireEmptyBody(exchange);
                return new Response(200, service.list());
            }
            if (method.equals("POST")) {
                return new Response(201, service.create(
                        requestDecoder.readRequiredJson(exchange, MorpheusPolicyApiService.CreateRequest.class)));
            }
            throw ApiFailure.methodNotAllowed("policy-packs supports GET and POST");
        }

        String id = segments.getFirst();
        if (segments.size() == 2 && segments.get(1).equals("audit")) {
            requireMethod(exchange, "GET");
            requestDecoder.requireEmptyBody(exchange);
            return new Response(200, service.audit(id, auditPage(exchange)));
        }
        rejectQueryParameters(exchange);
        if (segments.size() == 1) {
            if (method.equals("GET")) {
                requestDecoder.requireEmptyBody(exchange);
                return new Response(200, service.get(id));
            }
            if (method.equals("PUT")) {
                return new Response(200, service.update(
                        id, requestDecoder.readRequiredJson(exchange, MorpheusPolicyApiService.UpdateRequest.class)));
            }
            throw ApiFailure.methodNotAllowed("policy pack supports GET and PUT");
        }
        if (segments.size() == 2) {
            String action = segments.get(1);
            return switch (action) {
                case "versions" -> {
                    requireMethod(exchange, "GET");
                    requestDecoder.requireEmptyBody(exchange);
                    yield new Response(200, service.versions(id));
                }
                case "activate" -> {
                    requireMethod(exchange, "POST");
                    yield new Response(200, service.activate(
                            id, requestDecoder.readRequiredJson(exchange, MorpheusPolicyApiService.ActivationRequest.class)));
                }
                case "deactivate" -> {
                    requireMethod(exchange, "POST");
                    yield new Response(200, service.deactivate(
                            id, requestDecoder.readRequiredJson(exchange, MorpheusPolicyApiService.DeactivationRequest.class)));
                }
                default -> throw ApiFailure.notFound("unknown policy-pack action: " + action);
            };
        }
        if (segments.size() == 3 && segments.get(1).equals("overrides")) {
            requireMethod(exchange, "PUT");
            return new Response(200, service.putOverride(id, segments.get(2),
                    requestDecoder.readRequiredJson(exchange, MorpheusPolicyApiService.OverrideRequest.class)));
        }
        throw ApiFailure.notFound("unknown policy-pack route");
    }

    private void handlePolicies(HttpExchange exchange) throws IOException {
        handle(exchange, () -> {
            requireMethod(exchange, "POST");
            rejectQueryParameters(exchange);
            List<String> segments = requestDecoder.pathSegmentsBelow(exchange, POLICIES);
            if (segments.size() != 1) throw ApiFailure.notFound("unknown policies route");
            return switch (segments.getFirst()) {
                case "evaluate" -> new Response(200, service.evaluate(
                        requestDecoder.readRequiredJson(exchange, MorpheusPolicyApiService.EvaluateRequest.class)));
                case "dry-run" -> new Response(200, service.dryRun(
                        requestDecoder.readRequiredJson(exchange, MorpheusPolicyApiService.DryRunRequest.class)));
                default -> throw ApiFailure.notFound("unknown policies action");
            };
        });
    }

    private void handleOverrides(HttpExchange exchange) throws IOException {
        handle(exchange, () -> {
            requireMethod(exchange, "GET");
            // The context also receives every deeper path; the list is served at its own path only.
            if (!requestDecoder.pathSegmentsBelow(exchange, OVERRIDES).isEmpty()) {
                throw ApiFailure.notFound("unknown policy-overrides route");
            }
            requestDecoder.requireEmptyBody(exchange);
            MorpheusHttpQuery query = MorpheusHttpQuery.parse(exchange.getRequestURI().getRawQuery());
            query.rejectUnknown(Set.of("scopeKind", "scopeId"));
            return new Response(200, service.listOverrides(query.required("scopeKind"), query.required("scopeId")));
        });
    }

    private void handle(HttpExchange exchange, Handler handler) throws IOException {
        try {
            Response response = handler.route();
            responseWriter.send(exchange, response.status(), new ApiSuccess("v1", response.data()));
        } catch (PolicyConflictException failure) {
            responseWriter.send(exchange, 409, new ApiErrorEnvelope("v1", new ApiError("REVISION_CONFLICT", BoundaryFailureMessage.safe(failure), Map.of())));
        } catch (ApiFailure failure) {
            if (failure.status() == 405) {
                exchange.getResponseHeaders().set("Allow", allowedMethods.forPath(exchange.getRequestURI().getRawPath()));
            }
            responseWriter.send(exchange, failure.status(), new ApiErrorEnvelope("v1", new ApiError(failure.code(), failure.getMessage(), failure.details())));
        } catch (IllegalArgumentException failure) {
            responseWriter.send(exchange, 400, new ApiErrorEnvelope("v1", new ApiError("BAD_REQUEST", BoundaryFailureMessage.safe(failure), Map.of())));
        } catch (KnowledgeStoreException | IllegalStateException failure) {
            responseWriter.send(exchange, 409, new ApiErrorEnvelope("v1", new ApiError("STATE_CONFLICT", BoundaryFailureMessage.safe(failure), Map.of())));
        } catch (RuntimeException failure) {
            UnforeseenFailureTrace.record(recorder, failure);
            responseWriter.send(exchange, 500, new ApiErrorEnvelope("v1", new ApiError("INTERNAL_ERROR", "internal MORPHEUS API error", Map.of())));
        } finally {
            exchange.close();
        }
    }

    private void requireMethod(HttpExchange exchange, String expected) {
        String actual = exchange.getRequestMethod().toUpperCase(Locale.ROOT);
        if (!actual.equals(expected)) throw ApiFailure.methodNotAllowed("expected HTTP " + expected + " but received " + actual);
    }

    /** The audit is the one route of the pack tree that takes a query: the page of a journal that only grows. */
    private PageRequest auditPage(HttpExchange exchange) {
        MorpheusHttpQuery query = MorpheusHttpQuery.parse(exchange.getRequestURI().getRawQuery());
        query.rejectUnknown(Set.of("offset", "limit"));
        return new PageRequest(
                query.intValue("offset", 0, 0, Integer.MAX_VALUE),
                query.intValue("limit", MorpheusApiService.DEFAULT_LIMIT, 1, MorpheusApiService.MAX_LIMIT));
    }

    private void rejectQueryParameters(HttpExchange exchange) {
        if (exchange.getRequestURI().getRawQuery() != null && !exchange.getRequestURI().getRawQuery().isBlank()) {
            throw ApiFailure.badRequest("query parameters are not supported on this route");
        }
    }

    private record Response(int status, Object data) {}

    @FunctionalInterface
    private interface Handler { Response route(); }
}
