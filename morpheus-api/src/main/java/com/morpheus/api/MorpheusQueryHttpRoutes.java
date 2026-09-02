package com.morpheus.api;

import com.morpheus.application.query.compact.CanonicalJsonSerializer;
import com.morpheus.application.query.export.QueryExport;
import com.morpheus.application.query.export.QueryExportBudgetException;
import com.morpheus.application.query.saved.SavedViewConflictException;
import com.morpheus.application.query.dsl.QueryValidationException;
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
import java.util.Optional;

/** Isolated M24/M25 extension routing contexts so legacy /api/v1 routes remain untouched. */
final class MorpheusQueryHttpRoutes {
    private static final String QUERY_CONTEXT = MorpheusHttpServer.API_PREFIX + "/queries";
    private static final String VIEW_CONTEXT = MorpheusHttpServer.API_PREFIX + "/saved-views";
    private static final String EXPORT_CONTEXT = MorpheusHttpServer.API_PREFIX + "/exports";

    private final MorpheusQueryApiService service;
    private final CanonicalJsonSerializer serializer = new CanonicalJsonSerializer();
    private final JsonMapper mapper = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .build();

    private MorpheusQueryHttpRoutes(Path databasePath) {
        service = new MorpheusQueryApiService(databasePath);
    }

    static void register(HttpServer server, Path databasePath) {
        Objects.requireNonNull(server, "server");
        MorpheusQueryHttpRoutes routes = new MorpheusQueryHttpRoutes(databasePath);
        server.createContext(QUERY_CONTEXT, routes::handleQueries);
        server.createContext(VIEW_CONTEXT, routes::handleSavedViews);
        server.createContext(EXPORT_CONTEXT, routes::handleExports);
        MorpheusPolicyHttpRoutes.register(server, databasePath);
        MorpheusReasoningHttpRoutes.register(server);
    }

    private void handleQueries(HttpExchange exchange) throws IOException {
        handle(exchange, () -> {
            requireMethod(exchange, "POST");
            requireExactPath(exchange, QUERY_CONTEXT + "/execute");
            rejectQueryParameters(exchange);
            return json(200, service.execute(readJson(exchange, MorpheusQueryApiService.ScopedQueryRequest.class)));
        });
    }

    private void handleExports(HttpExchange exchange) throws IOException {
        handle(exchange, () -> {
            requireMethod(exchange, "POST");
            requireExactPath(exchange, EXPORT_CONTEXT);
            rejectQueryParameters(exchange);
            return raw(200, service.export(readJson(exchange, MorpheusQueryApiService.ExportRequest.class)));
        });
    }

    private void handleSavedViews(HttpExchange exchange) throws IOException {
        handle(exchange, () -> routeSavedViews(exchange));
    }

    private Response routeSavedViews(HttpExchange exchange) {
        String method = exchange.getRequestMethod().toUpperCase(Locale.ROOT);
        List<String> segments = suffixSegments(exchange.getRequestURI().getPath(), VIEW_CONTEXT);
        if (segments.isEmpty()) {
            if (method.equals("GET")) {
                Query query = Query.parse(exchange.getRequestURI().getRawQuery());
                query.rejectUnknown(List.of("scopeKind", "scopeId"));
                return json(200, service.listSavedViews(query.required("scopeKind"), query.required("scopeId")));
            }
            if (method.equals("POST")) {
                rejectQueryParameters(exchange);
                return json(201, service.createSavedView(
                        readJson(exchange, MorpheusQueryApiService.CreateSavedViewRequest.class)));
            }
            throw new HttpFailure(405, "METHOD_NOT_ALLOWED", "saved-views supports GET and POST");
        }

        rejectQueryParameters(exchange);
        String id = segments.getFirst();
        if (segments.size() == 1) {
            if (method.equals("GET")) {
                return json(200, service.getSavedView(id));
            }
            if (method.equals("PUT")) {
                return json(200, service.updateSavedView(
                        id, readJson(exchange, MorpheusQueryApiService.UpdateSavedViewRequest.class)));
            }
            throw new HttpFailure(405, "METHOD_NOT_ALLOWED", "saved view supports GET and PUT");
        }
        if (segments.size() != 2) {
            throw new HttpFailure(404, "NOT_FOUND", "unknown saved-view route");
        }
        String action = segments.get(1);
        return switch (action) {
            case "versions" -> {
                requireMethod(exchange, "GET");
                yield json(200, service.savedViewVersions(id));
            }
            case "execute" -> {
                requireMethod(exchange, "POST");
                requireEmptyBody(exchange);
                yield json(200, service.executeSavedView(id));
            }
            case "archive" -> {
                requireMethod(exchange, "POST");
                yield json(200, service.archiveSavedView(
                        id, readJson(exchange, MorpheusQueryApiService.RevisionRequest.class)));
            }
            case "export" -> {
                requireMethod(exchange, "POST");
                yield raw(200, service.exportSavedView(
                        id, readJson(exchange, MorpheusQueryApiService.ExportSavedViewRequest.class)));
            }
            default -> throw new HttpFailure(404, "NOT_FOUND", "unknown saved-view action: " + action);
        };
    }

    private void handle(HttpExchange exchange, Handler handler) throws IOException {
        try {
            Response response = handler.route();
            if (response.export().isPresent()) {
                QueryExport export = response.export().orElseThrow();
                sendRaw(exchange, response.status(), export.mediaType(), export.utf8());
            } else {
                sendJson(exchange, response.status(), new ApiSuccess("v1", response.data()));
            }
        } catch (QueryValidationException failure) {
            sendJson(exchange, 400, new ApiErrorEnvelope(
                    "v1", new ApiError("QUERY_VALIDATION", safeMessage(failure), Map.of())));
        } catch (QueryExportBudgetException failure) {
            sendJson(exchange, 400, new ApiErrorEnvelope(
                    "v1", new ApiError("QUERY_BUDGET_EXCEEDED", safeMessage(failure), Map.of())));
        } catch (SavedViewConflictException failure) {
            sendJson(exchange, 409, new ApiErrorEnvelope(
                    "v1", new ApiError("REVISION_CONFLICT", safeMessage(failure), Map.of())));
        } catch (HttpFailure failure) {
            if (failure.status == 405) {
                exchange.getResponseHeaders().set("Allow", allowed(exchange.getRequestURI().getPath()));
            }
            sendJson(exchange, failure.status, new ApiErrorEnvelope(
                    "v1", new ApiError(failure.code, failure.getMessage(), Map.of())));
        } catch (IllegalArgumentException failure) {
            sendJson(exchange, 400, new ApiErrorEnvelope(
                    "v1", new ApiError("BAD_REQUEST", safeMessage(failure), Map.of())));
        } catch (KnowledgeStoreException | IllegalStateException failure) {
            sendJson(exchange, 409, new ApiErrorEnvelope(
                    "v1", new ApiError("STATE_CONFLICT", safeMessage(failure), Map.of())));
        } catch (RuntimeException failure) {
            sendJson(exchange, 500, new ApiErrorEnvelope(
                    "v1", new ApiError("INTERNAL_ERROR", "internal MORPHEUS API error", Map.of())));
        } finally {
            exchange.close();
        }
    }

    private <T> T readJson(HttpExchange exchange, Class<T> type) {
        byte[] body = readBody(exchange);
        if (body.length == 0) {
            throw new HttpFailure(400, "BAD_REQUEST", "JSON request body is required");
        }
        String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
        if (!JsonMediaType.isJson(contentType)) {
            throw new HttpFailure(415, "UNSUPPORTED_MEDIA_TYPE", "Content-Type application/json is required");
        }
        try {
            return mapper.readValue(body, type);
        } catch (Exception failure) {
            throw new HttpFailure(400, "BAD_REQUEST", "invalid JSON request body: " + safeMessage(failure));
        }
    }

    private byte[] readBody(HttpExchange exchange) {
        return HttpRequestBodyReader.read(exchange);
    }

    private void requireEmptyBody(HttpExchange exchange) {
        if (readBody(exchange).length != 0) {
            throw new HttpFailure(400, "BAD_REQUEST", "request body must be empty");
        }
    }

    private void requireMethod(HttpExchange exchange, String expected) {
        String actual = exchange.getRequestMethod().toUpperCase(Locale.ROOT);
        if (!actual.equals(expected)) {
            throw new HttpFailure(405, "METHOD_NOT_ALLOWED", "expected HTTP " + expected + " but received " + actual);
        }
    }

    private void requireExactPath(HttpExchange exchange, String expected) {
        if (!exchange.getRequestURI().getPath().equals(expected)) {
            throw new HttpFailure(404, "NOT_FOUND", "unknown API route");
        }
    }

    private void rejectQueryParameters(HttpExchange exchange) {
        if (exchange.getRequestURI().getRawQuery() != null && !exchange.getRequestURI().getRawQuery().isBlank()) {
            throw new HttpFailure(400, "BAD_REQUEST", "query parameters are not supported on this route");
        }
    }

    private List<String> suffixSegments(String path, String context) {
        if (!path.startsWith(context)) {
            throw new HttpFailure(404, "NOT_FOUND", "unknown API route");
        }
        String suffix = path.substring(context.length());
        if (suffix.isEmpty() || suffix.equals("/")) {
            return List.of();
        }
        String normalized = suffix.startsWith("/") ? suffix.substring(1) : suffix;
        if (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        List<String> result = new ArrayList<>();
        for (String segment : normalized.split("/")) {
            if (segment.isBlank()) {
                throw new HttpFailure(404, "NOT_FOUND", "invalid API path");
            }
            result.add(URLDecoder.decode(segment, StandardCharsets.UTF_8));
        }
        return List.copyOf(result);
    }

    private Response json(int status, Object data) {
        return new Response(status, Objects.requireNonNull(data, "data"), Optional.empty());
    }

    private Response raw(int status, QueryExport export) {
        return new Response(status, export, Optional.of(export));
    }

    private void sendJson(HttpExchange exchange, int status, Object body) throws IOException {
        sendRaw(exchange, status, "application/json; charset=utf-8", serializer.toUtf8(body));
    }

    private void sendRaw(HttpExchange exchange, int status, String contentType, byte[] bytes) throws IOException {
        Headers headers = exchange.getResponseHeaders();
        headers.set("Content-Type", contentType);
        headers.set("Cache-Control", "no-store");
        headers.set("X-Content-Type-Options", "nosniff");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }

    private String allowed(String path) {
        if (path.equals(VIEW_CONTEXT)) {
            return "GET, POST";
        }
        if (path.startsWith(VIEW_CONTEXT + "/") && path.split("/").length == 5) {
            return "GET, PUT";
        }
        return "POST";
    }

    private static String safeMessage(Throwable failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
    }

    private record Response(int status, Object data, Optional<QueryExport> export) {
        private Response {
            if (status < 200 || status > 599) {
                throw new IllegalArgumentException("status out of range");
            }
            Objects.requireNonNull(data, "data");
            export = Objects.requireNonNull(export, "export");
        }
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
            if (raw == null || raw.isBlank()) {
                return new Query(Map.of());
            }
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
            if (value == null || value.isBlank()) {
                throw new HttpFailure(400, "BAD_REQUEST", "query parameter is required: " + name);
            }
            return value;
        }

        void rejectUnknown(List<String> allowed) {
            values.keySet().stream().filter(key -> !allowed.contains(key)).findFirst()
                    .ifPresent(key -> {
                        throw new HttpFailure(400, "BAD_REQUEST", "unknown query parameter: " + key);
                    });
        }
    }
}
