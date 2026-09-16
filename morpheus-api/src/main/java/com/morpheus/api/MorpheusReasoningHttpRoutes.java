package com.morpheus.api;

import com.morpheus.api.MorpheusHttpServer.ApiError;
import com.morpheus.api.MorpheusHttpServer.ApiErrorEnvelope;
import com.morpheus.api.MorpheusHttpServer.ApiSuccess;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** Isolated M27 HTTP context. Reasoning execution never mutates the knowledge store. */
final class MorpheusReasoningHttpRoutes {
    static final String CONTEXT = MorpheusHttpServer.API_PREFIX + "/reasoning";
    private static final String ADAPTERS = CONTEXT + "/adapters";
    private static final String ANALYZE = CONTEXT + "/analyze";

    private final MorpheusReasoningApiService service = new MorpheusReasoningApiService();
    private final MorpheusHttpRequestDecoder requestDecoder;
    private final MorpheusHttpResponseWriter responseWriter;

    private MorpheusReasoningHttpRoutes(MorpheusHttpRequestDecoder requestDecoder,
            MorpheusHttpResponseWriter responseWriter) {
        this.requestDecoder = Objects.requireNonNull(requestDecoder, "requestDecoder");
        this.responseWriter = Objects.requireNonNull(responseWriter, "responseWriter");
    }

    static void register(HttpServer server, MorpheusHttpRequestDecoder requestDecoder,
            MorpheusHttpResponseWriter responseWriter) {
        Objects.requireNonNull(server, "server");
        MorpheusReasoningHttpRoutes routes = new MorpheusReasoningHttpRoutes(requestDecoder, responseWriter);
        server.createContext(CONTEXT, routes::handle);
    }

    private void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        try {
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
                exchange.getResponseHeaders().set("Allow", path.equals(ADAPTERS) ? "GET" : "POST");
            }
            responseWriter.send(exchange, failure.status(), new ApiErrorEnvelope(
                    "v1", new ApiError(failure.code(), failure.getMessage(), failure.details())));
        } catch (IllegalArgumentException failure) {
            responseWriter.send(exchange, 400, new ApiErrorEnvelope(
                    "v1", new ApiError("REASONING_VALIDATION", safeMessage(failure), Map.of())));
        } catch (RuntimeException failure) {
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

    private static String safeMessage(Throwable failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
    }
}
