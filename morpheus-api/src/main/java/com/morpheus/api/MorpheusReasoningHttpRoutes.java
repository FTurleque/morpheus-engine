package com.morpheus.api;

import com.morpheus.application.query.compact.CanonicalJsonSerializer;
import com.sun.net.httpserver.Headers;
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
    private final CanonicalJsonSerializer serializer = new CanonicalJsonSerializer();
    private final MorpheusHttpRequestDecoder requestDecoder;

    private MorpheusReasoningHttpRoutes(MorpheusHttpRequestDecoder requestDecoder) {
        this.requestDecoder = Objects.requireNonNull(requestDecoder, "requestDecoder");
    }

    static void register(HttpServer server, MorpheusHttpRequestDecoder requestDecoder) {
        Objects.requireNonNull(server, "server");
        MorpheusReasoningHttpRoutes routes = new MorpheusReasoningHttpRoutes(requestDecoder);
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
            sendJson(exchange, 200, new ApiSuccess("v1", response));
        } catch (ApiFailure failure) {
            if (failure.status() == 405) {
                exchange.getResponseHeaders().set("Allow", path.equals(ADAPTERS) ? "GET" : "POST");
            }
            sendJson(exchange, failure.status(), new ApiErrorEnvelope(
                    "v1", new ApiError(failure.code(), failure.getMessage(), failure.details())));
        } catch (IllegalArgumentException failure) {
            sendJson(exchange, 400, new ApiErrorEnvelope(
                    "v1", new ApiError("REASONING_VALIDATION", safeMessage(failure), Map.of())));
        } catch (RuntimeException failure) {
            sendJson(exchange, 500, new ApiErrorEnvelope(
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

    private void sendJson(HttpExchange exchange, int status, Object body) throws IOException {
        byte[] bytes = serializer.toUtf8(body);
        Headers headers = exchange.getResponseHeaders();
        headers.set("Content-Type", "application/json; charset=utf-8");
        headers.set("Cache-Control", "no-store");
        headers.set("X-Content-Type-Options", "nosniff");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }

    private static String safeMessage(Throwable failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
    }

    private record ApiSuccess(String apiVersion, Object data) {
    }

    private record ApiError(String code, String message, Map<String, Object> details) {
    }

    private record ApiErrorEnvelope(String apiVersion, ApiError error) {
    }
}
