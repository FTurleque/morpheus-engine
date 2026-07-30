package com.morpheus.api;

import com.morpheus.application.query.compact.CanonicalJsonSerializer;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** Isolated M27 HTTP context. Reasoning execution never mutates the knowledge store. */
final class MorpheusReasoningHttpRoutes {
    static final String CONTEXT = MorpheusHttpServer.API_PREFIX + "/reasoning";

    private final MorpheusReasoningApiService service = new MorpheusReasoningApiService();
    private final CanonicalJsonSerializer serializer = new CanonicalJsonSerializer();
    private final JsonMapper mapper = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .build();

    private MorpheusReasoningHttpRoutes() {
    }

    static void register(HttpServer server) {
        Objects.requireNonNull(server, "server");
        MorpheusReasoningHttpRoutes routes = new MorpheusReasoningHttpRoutes();
        server.createContext(CONTEXT, routes::handle);
    }

    private void handle(HttpExchange exchange) throws IOException {
        try {
            rejectQueryParameters(exchange);
            String path = exchange.getRequestURI().getPath();
            String method = exchange.getRequestMethod().toUpperCase(Locale.ROOT);
            Object response;
            if (path.equals(CONTEXT + "/adapters")) {
                requireMethod(method, "GET");
                requireEmptyBody(exchange);
                response = service.adapters();
            } else if (path.equals(CONTEXT + "/analyze")) {
                requireMethod(method, "POST");
                response = service.analyze(readJson(exchange));
            } else {
                throw new HttpFailure(404, "NOT_FOUND", "unknown reasoning route");
            }
            sendJson(exchange, 200, new ApiSuccess("v1", response));
        } catch (HttpFailure failure) {
            if (failure.status == 405) {
                exchange.getResponseHeaders().set("Allow", failure.allowedMethod);
            }
            sendJson(exchange, failure.status, new ApiErrorEnvelope(
                    "v1", new ApiError(failure.code, failure.getMessage(), Map.of())));
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

    private MorpheusReasoningApiService.ReasoningRequest readJson(HttpExchange exchange) {
        byte[] body = readBody(exchange);
        if (body.length == 0) {
            throw new HttpFailure(400, "BAD_REQUEST", "JSON request body is required");
        }
        String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
        if (contentType == null || !contentType.toLowerCase(Locale.ROOT).startsWith("application/json")) {
            throw new HttpFailure(415, "UNSUPPORTED_MEDIA_TYPE", "Content-Type application/json is required");
        }
        try {
            return mapper.readValue(body, MorpheusReasoningApiService.ReasoningRequest.class);
        } catch (Exception failure) {
            throw new HttpFailure(400, "BAD_REQUEST", "invalid JSON request body: " + safeMessage(failure));
        }
    }

    private byte[] readBody(HttpExchange exchange) {
        try {
            byte[] body = exchange.getRequestBody().readNBytes(MorpheusHttpServer.MAX_REQUEST_BODY_BYTES + 1);
            if (body.length > MorpheusHttpServer.MAX_REQUEST_BODY_BYTES) {
                throw new HttpFailure(400, "BAD_REQUEST",
                        "request body exceeds " + MorpheusHttpServer.MAX_REQUEST_BODY_BYTES + " bytes");
            }
            return body;
        } catch (IOException failure) {
            throw new HttpFailure(400, "BAD_REQUEST", "cannot read request body");
        }
    }

    private void requireEmptyBody(HttpExchange exchange) {
        if (readBody(exchange).length != 0) {
            throw new HttpFailure(400, "BAD_REQUEST", "request body must be empty");
        }
    }

    private static void rejectQueryParameters(HttpExchange exchange) {
        if (exchange.getRequestURI().getRawQuery() != null && !exchange.getRequestURI().getRawQuery().isBlank()) {
            throw new HttpFailure(400, "BAD_REQUEST", "query parameters are not supported on reasoning routes");
        }
    }

    private static void requireMethod(String actual, String expected) {
        if (!actual.equals(expected)) {
            throw new HttpFailure(405, "METHOD_NOT_ALLOWED",
                    "expected HTTP " + expected + " but received " + actual, expected);
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

    private static final class HttpFailure extends RuntimeException {
        private final int status;
        private final String code;
        private final String allowedMethod;

        private HttpFailure(int status, String code, String message) {
            this(status, code, message, "");
        }

        private HttpFailure(int status, String code, String message, String allowedMethod) {
            super(message);
            this.status = status;
            this.code = code;
            this.allowedMethod = allowedMethod;
        }
    }
}
