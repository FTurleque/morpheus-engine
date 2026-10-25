package com.morpheus.api;

import com.morpheus.api.transport.ApiError;
import com.morpheus.api.transport.ApiErrorEnvelope;
import com.morpheus.api.transport.ApiFailure;
import com.morpheus.api.transport.ApiSuccess;
import com.morpheus.api.transport.BoundaryFailureMessage;
import com.morpheus.api.transport.MorpheusHttpAllowedMethods;
import com.morpheus.api.transport.MorpheusHttpRequestDecoder;
import com.morpheus.api.transport.MorpheusHttpResponseWriter;
import com.morpheus.api.transport.MorpheusHttpRouteTable;
import com.morpheus.api.transport.UnforeseenFailureTrace;
import com.morpheus.application.operability.OperationalRecorder;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** Isolated M27 HTTP context. Reasoning execution never mutates the knowledge store. */
final class MorpheusReasoningHttpRoutes {
    private static final String REASONING = "reasoning";
    static final String CONTEXT = MorpheusHttpRouteTable.API_PREFIX + "/" + REASONING;
    private static final List<String> ADAPTERS = List.of(REASONING, "adapters");
    private static final List<String> ANALYZE = List.of(REASONING, "analyze");

    private final MorpheusReasoningApiService service;
    private final OperationalRecorder recorder;
    private final MorpheusHttpRequestDecoder requestDecoder;
    private final MorpheusHttpAllowedMethods allowedMethods;
    private final MorpheusHttpResponseWriter responseWriter;

    private MorpheusReasoningHttpRoutes(OperationalRecorder recorder, MorpheusHttpRequestDecoder requestDecoder,
            MorpheusHttpAllowedMethods allowedMethods, MorpheusHttpResponseWriter responseWriter) {
        this.service = new MorpheusReasoningApiService(recorder);
        this.recorder = recorder;
        this.requestDecoder = Objects.requireNonNull(requestDecoder, "requestDecoder");
        this.allowedMethods = Objects.requireNonNull(allowedMethods, "allowedMethods");
        this.responseWriter = Objects.requireNonNull(responseWriter, "responseWriter");
    }

    static void register(HttpServer server, OperationalRecorder recorder, MorpheusHttpRequestDecoder requestDecoder,
            MorpheusHttpAllowedMethods allowedMethods, MorpheusHttpResponseWriter responseWriter) {
        Objects.requireNonNull(server, "server");
        MorpheusReasoningHttpRoutes routes =
                new MorpheusReasoningHttpRoutes(recorder, requestDecoder, allowedMethods, responseWriter);
        server.createContext(CONTEXT, routes::handle);
    }

    private void handle(HttpExchange exchange) throws IOException {
        try {
            List<String> path = requestDecoder.pathSegments(exchange);
            rejectQueryParameters(exchange);
            String method = exchange.getRequestMethod().toUpperCase(Locale.ROOT);
            Object response;
            if (path.equals(ADAPTERS)) {
                requireMethod(method, "GET");
                requestDecoder.requireEmptyBody(exchange);
                response = service.adapters();
            } else if (path.equals(ANALYZE)) {
                requireMethod(method, "POST");
                response = service.analyze(
                        requestDecoder.readRequiredJson(exchange, MorpheusReasoningApiService.ReasoningRequest.class));
            } else {
                throw ApiFailure.notFound("unknown reasoning route");
            }
            responseWriter.send(exchange, 200, new ApiSuccess("v1", response));
        } catch (ApiFailure failure) {
            if (failure.status() == 405) {
                exchange.getResponseHeaders().set("Allow", allowedMethods.forPath(exchange.getRequestURI().getRawPath()));
            }
            responseWriter.send(exchange, failure.status(), new ApiErrorEnvelope(
                    "v1", new ApiError(failure.code(), failure.getMessage(), failure.details())));
        } catch (IllegalArgumentException failure) {
            responseWriter.send(exchange, 400, new ApiErrorEnvelope(
                    "v1", new ApiError("REASONING_VALIDATION", BoundaryFailureMessage.safe(failure), Map.of())));
        } catch (RuntimeException failure) {
            UnforeseenFailureTrace.record(recorder, failure);
            responseWriter.send(exchange, 500, new ApiErrorEnvelope(
                    "v1", new ApiError("INTERNAL_ERROR", "internal MORPHEUS reasoning error", Map.of())));
        } finally {
            exchange.close();
        }
    }

    private static void rejectQueryParameters(HttpExchange exchange) {
        if (exchange.getRequestURI().getRawQuery() != null && !exchange.getRequestURI().getRawQuery().isBlank()) {
            throw ApiFailure.badRequest("query parameters are not supported on reasoning routes");
        }
    }

    private static void requireMethod(String actual, String expected) {
        if (!actual.equals(expected)) {
            throw ApiFailure.methodNotAllowed("expected HTTP " + expected + " but received " + actual);
        }
    }
}
