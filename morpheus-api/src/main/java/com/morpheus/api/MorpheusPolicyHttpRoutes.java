package com.morpheus.api;

import com.morpheus.application.policy.PolicyConflictException;
import com.morpheus.application.query.compact.CanonicalJsonSerializer;
import com.morpheus.application.store.KnowledgeStoreException;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** Isolated M25 HTTP contexts for policy registry and read-only governance evaluation. */
final class MorpheusPolicyHttpRoutes {
    private static final String PACK_CONTEXT = MorpheusHttpServer.API_PREFIX + "/policy-packs";
    private static final String POLICY_CONTEXT = MorpheusHttpServer.API_PREFIX + "/policies";
    private static final String OVERRIDE_CONTEXT = MorpheusHttpServer.API_PREFIX + "/policy-overrides";

    private final MorpheusPolicyApiService service;
    private final CanonicalJsonSerializer serializer = new CanonicalJsonSerializer();
    private final JsonMapper mapper = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .build();

    private MorpheusPolicyHttpRoutes(Path databasePath) {
        service = new MorpheusPolicyApiService(databasePath);
    }

    static void register(HttpServer server, Path databasePath) {
        Objects.requireNonNull(server, "server");
        MorpheusPolicyHttpRoutes routes = new MorpheusPolicyHttpRoutes(databasePath);
        server.createContext(PACK_CONTEXT, routes::handlePacks);
        server.createContext(POLICY_CONTEXT, routes::handlePolicies);
        server.createContext(OVERRIDE_CONTEXT, routes::handleOverrides);
        MorpheusPolicyManagementHttpRoutes.register(server, databasePath);
    }

    private void handlePacks(HttpExchange exchange) throws IOException {
        handle(exchange, () -> routePacks(exchange));
    }

    private Response routePacks(HttpExchange exchange) {
        String method = exchange.getRequestMethod().toUpperCase(Locale.ROOT);
        List<String> segments = suffixSegments(exchange.getRequestURI().getPath(), PACK_CONTEXT);
        if (segments.isEmpty()) {
            rejectQueryParameters(exchange);
            if (method.equals("GET")) {
                requireEmptyBody(exchange);
                return new Response(200, service.list());
            }
            if (method.equals("POST")) {
                return new Response(201, service.create(readJson(exchange, MorpheusPolicyApiService.CreateRequest.class)));
            }
            throw new HttpFailure(405, "METHOD_NOT_ALLOWED", "policy-packs supports GET and POST");
        }

        rejectQueryParameters(exchange);
        String id = segments.getFirst();
        if (segments.size() == 1) {
            if (method.equals("GET")) {
                requireEmptyBody(exchange);
                return new Response(200, service.get(id));
            }
            if (method.equals("PUT")) {
                return new Response(200, service.update(id, readJson(exchange, MorpheusPolicyApiService.UpdateRequest.class)));
            }
            throw new HttpFailure(405, "METHOD_NOT_ALLOWED", "policy pack supports GET and PUT");
        }
        if (segments.size() == 2) {
            String action = segments.get(1);
            return switch (action) {
                case "versions" -> {
                    requireMethod(exchange, "GET");
                    requireEmptyBody(exchange);
                    yield new Response(200, service.versions(id));
                }
                case "activate" -> {
                    requireMethod(exchange, "POST");
                    yield new Response(200, service.activate(id, readJson(exchange, MorpheusPolicyApiService.ActivationRequest.class)));
                }
                case "deactivate" -> {
                    requireMethod(exchange, "POST");
                    yield new Response(200, service.deactivate(id, readJson(exchange, MorpheusPolicyApiService.DeactivationRequest.class)));
                }
                case "audit" -> {
                    requireMethod(exchange, "GET");
                    requireEmptyBody(exchange);
                    yield new Response(200, service.audit(id));
                }
                default -> throw new HttpFailure(404, "NOT_FOUND", "unknown policy-pack action: " + action);
            };
        }
        if (segments.size() == 3 && segments.get(1).equals("overrides")) {
            requireMethod(exchange, "PUT");
            return new Response(200, service.putOverride(
                    id, segments.get(2), readJson(exchange, MorpheusPolicyApiService.OverrideRequest.class)));
        }
        throw new HttpFailure(404, "NOT_FOUND", "unknown policy-pack route");
    }

    private void handlePolicies(HttpExchange exchange) throws IOException {
        handle(exchange, () -> {
            requireMethod(exchange, "POST");
            rejectQueryParameters(exchange);
            List<String> segments = suffixSegments(exchange.getRequestURI().getPath(), POLICY_CONTEXT);
            if (segments.size() != 1) throw new HttpFailure(404, "NOT_FOUND", "unknown policies route");
            return switch (segments.getFirst()) {
                case "evaluate" -> new Response(200, service.evaluate(readJson(exchange, MorpheusPolicyApiService.EvaluateRequest.class)));
                case "dry-run" -> new Response(200, service.dryRun(readJson(exchange, MorpheusPolicyApiService.DryRunRequest.class)));
                default -> throw new HttpFailure(404, "NOT_FOUND", "unknown policies action");
            };
        });
    }

    private void handleOverrides(HttpExchange exchange) throws IOException {
        handle(exchange, () -> {
            requireMethod(exchange, "GET");
            requireEmptyBody(exchange);
            Query query = Query.parse(exchange.getRequestURI().getRawQuery());
            query.rejectUnknown(List.of("scopeKind", "scopeId"));
            return new Response(200, service.listOverrides(query.required("scopeKind"), query.required("scopeId")));
        });
    }

    private void handle(HttpExchange exchange, Handler handler) throws IOException {
        try {
            Response response = handler.route();
            send(exchange, response.status(), new ApiSuccess("v1", response.data()));
        } catch (PolicyConflictException failure) {
            send(exchange, 409, new ApiErrorEnvelope("v1", new ApiError("REVISION_CONFLICT", safeMessage(failure), Map.of())));
        } catch (HttpFailure failure) {
            if (failure.status == 405) exchange.getResponseHeaders().set("Allow", allowed(exchange.getRequestURI().getPath()));
            send(exchange, failure.status, new ApiErrorEnvelope("v1", new ApiError(failure.code, failure.getMessage(), Map.of())));
        } catch (IllegalArgumentException failure) {
            send(exchange, 400, new ApiErrorEnvelope("v1", new ApiError("BAD_REQUEST", safeMessage(failure), Map.of())));
        } catch (KnowledgeStoreException | IllegalStateException failure) {
            send(exchange, 409, new ApiErrorEnvelope("v1", new ApiError("STATE_CONFLICT", safeMessage(failure), Map.of())));
        } catch (RuntimeException failure) {
            send(exchange, 500, new ApiErrorEnvelope("v1", new ApiError("INTERNAL_ERROR", "internal MORPHEUS API error", Map.of())));
        } finally {
            exchange.close();
        }
    }

    private <T> T readJson(HttpExchange exchange, Class<T> type) {
        byte[] body = readBody(exchange);
        if (body.length == 0) throw new HttpFailure(400, "BAD_REQUEST", "JSON request body is required");
        String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
        if (contentType == null || !contentType.toLowerCase(Locale.ROOT).startsWith("application/json")) {
            throw new HttpFailure(415, "UNSUPPORTED_MEDIA_TYPE", "Content-Type application/json is required");
        }
        try {
            return mapper.readValue(body, type);
        } catch (Exception failure) {
            throw new HttpFailure(400, "BAD_REQUEST", "invalid JSON request body: " + safeMessage(failure));
        }
    }

    private byte[] readBody(HttpExchange exchange) {
        try {
            byte[] body = exchange.getRequestBody().readNBytes(MorpheusHttpServer.MAX_REQUEST_BODY_BYTES + 1);
            if (body.length > MorpheusHttpServer.MAX_REQUEST_BODY_BYTES) {
                throw new HttpFailure(400, "BAD_REQUEST", "request body exceeds " + MorpheusHttpServer.MAX_REQUEST_BODY_BYTES + " bytes");
            }
            return body;
        } catch (IOException failure) {
            throw new HttpFailure(400, "BAD_REQUEST", "cannot read request body");
        }
    }

    private void requireEmptyBody(HttpExchange exchange) {
        if (readBody(exchange).length != 0) throw new HttpFailure(400, "BAD_REQUEST", "request body must be empty");
    }

    private void requireMethod(HttpExchange exchange, String expected) {
        String actual = exchange.getRequestMethod().toUpperCase(Locale.ROOT);
        if (!actual.equals(expected)) {
            throw new HttpFailure(405, "METHOD_NOT_ALLOWED", "expected HTTP " + expected + " but received " + actual);
        }
    }

    private void rejectQueryParameters(HttpExchange exchange) {
        if (exchange.getRequestURI().getRawQuery() != null && !exchange.getRequestURI().getRawQuery().isBlank()) {
            throw new HttpFailure(400, "BAD_REQUEST", "query parameters are not supported on this route");
        }
    }

    private List<String> suffixSegments(String path, String context) {
        if (!path.startsWith(context)) throw new HttpFailure(404, "NOT_FOUND", "unknown API route");
        String suffix = path.substring(context.length());
        if (suffix.isEmpty() || suffix.equals("/")) return List.of();
        String normalized = suffix.startsWith("/") ? suffix.substring(1) : suffix;
        if (normalized.endsWith("/")) normalized = normalized.substring(0, normalized.length() - 1);
        List<String> result = new ArrayList<>();
        for (String segment : normalized.split("/")) {
            if (segment.isBlank()) throw new HttpFailure(404, "NOT_FOUND", "invalid API path");
            result.add(URLDecoder.decode(segment, StandardCharsets.UTF_8));
        }
        return List.copyOf(result);
    }

    private void send(HttpExchange exchange, int status, Object body) throws IOException {
        byte[] bytes = serializer.toUtf8(body);
        Headers headers = exchange.getResponseHeaders();
        headers.set("Content-Type", "application/json; charset=utf-8");
        headers.set("Cache-Control", "no-store");
        headers.set("X-Content-Type-Options", "nosniff");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }

    private String allowed(String path) {
        if (path.equals(PACK_CONTEXT)) return "GET, POST";
        if (path.startsWith(PACK_CONTEXT + "/") && path.split("/").length == 5) return "GET, PUT";
        if (path.equals(OVERRIDE_CONTEXT)) return "GET";
        return "POST";
    }

    private static String safeMessage(Throwable failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
    }

    private record Response(int status, Object data) {}
    private record ApiSuccess(String apiVersion, Object data) {}
    private record ApiError(String code, String message, Map<String, Object> details) {}
    private record ApiErrorEnvelope(String apiVersion, ApiError error) {}

    private static final class HttpFailure extends RuntimeException {
        private final int status;
        private final String code;

        private HttpFailure(int status, String code, String message) {
            super(message);
            this.status = status;
            this.code = code;
        }
    }

    @FunctionalInterface
    private interface Handler {
        Response route();
    }

    private record Query(Map<String, String> values) {
        private Query {
            values = Map.copyOf(values);
        }

        static Query parse(String raw) {
            if (raw == null || raw.isBlank()) return new Query(Map.of());
            Map<String, String> values = new LinkedHashMap<>();
            for (String part : raw.split("&")) {
                int separator = part.indexOf('=');
                String key = URLDecoder.decode(separator < 0 ? part : part.substring(0, separator), StandardCharsets.UTF_8);
                String value = URLDecoder.decode(separator < 0 ? "" : part.substring(separator + 1), StandardCharsets.UTF_8);
                if (key.isBlank() || values.putIfAbsent(key, value) != null) {
                    throw new HttpFailure(400, "BAD_REQUEST", "invalid or duplicate query parameter: " + key);
                }
            }
            return new Query(values);
        }

        String required(String name) {
            String value = values.get(name);
            if (value == null || value.isBlank()) throw new HttpFailure(400, "BAD_REQUEST", "query parameter is required: " + name);
            return value;
        }

        void rejectUnknown(List<String> allowed) {
            values.keySet().stream().filter(key -> !allowed.contains(key)).findFirst()
                    .ifPresent(key -> { throw new HttpFailure(400, "BAD_REQUEST", "unknown query parameter: " + key); });
        }
    }
}
