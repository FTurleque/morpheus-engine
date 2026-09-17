package com.morpheus.api;

import com.morpheus.api.MorpheusHttpServer.ApiError;
import com.morpheus.api.MorpheusHttpServer.ApiErrorEnvelope;
import com.morpheus.api.MorpheusHttpServer.ApiSuccess;
import com.morpheus.application.policy.PolicyConflictException;
import com.morpheus.application.store.KnowledgeStoreException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

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
    private final MorpheusHttpRequestDecoder requestDecoder;
    private final MorpheusHttpResponseWriter responseWriter;

    private MorpheusPolicyHttpRoutes(Path databasePath, MorpheusHttpRequestDecoder requestDecoder,
            MorpheusHttpResponseWriter responseWriter) {
        service = new MorpheusPolicyApiService(databasePath);
        this.requestDecoder = Objects.requireNonNull(requestDecoder, "requestDecoder");
        this.responseWriter = Objects.requireNonNull(responseWriter, "responseWriter");
    }

    static void register(HttpServer server, Path databasePath, MorpheusHttpRequestDecoder requestDecoder,
            MorpheusHttpResponseWriter responseWriter) {
        Objects.requireNonNull(server, "server");
        MorpheusPolicyHttpRoutes routes = new MorpheusPolicyHttpRoutes(databasePath, requestDecoder, responseWriter);
        server.createContext(PACK_CONTEXT, routes::handlePacks);
        server.createContext(POLICY_CONTEXT, routes::handlePolicies);
        server.createContext(OVERRIDE_CONTEXT, routes::handleOverrides);
        MorpheusPolicyManagementHttpRoutes.register(server, databasePath, requestDecoder, responseWriter);
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
                requestDecoder.requireEmptyBody(exchange);
                return new Response(200, service.list());
            }
            if (method.equals("POST")) {
                return new Response(201, service.create(
                        requestDecoder.readRequiredJson(exchange, MorpheusPolicyApiService.CreateRequest.class)));
            }
            throw ApiFailure.methodNotAllowed("policy-packs supports GET and POST");
        }

        rejectQueryParameters(exchange);
        String id = segments.getFirst();
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
                case "audit" -> {
                    requireMethod(exchange, "GET");
                    requestDecoder.requireEmptyBody(exchange);
                    yield new Response(200, service.audit(id));
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
            List<String> segments = suffixSegments(exchange.getRequestURI().getPath(), POLICY_CONTEXT);
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
            requestDecoder.requireEmptyBody(exchange);
            Query query = Query.parse(exchange.getRequestURI().getRawQuery());
            query.rejectUnknown(List.of("scopeKind", "scopeId"));
            return new Response(200, service.listOverrides(query.required("scopeKind"), query.required("scopeId")));
        });
    }

    private void handle(HttpExchange exchange, Handler handler) throws IOException {
        try {
            Response response = handler.route();
            responseWriter.send(exchange, response.status(), new ApiSuccess("v1", response.data()));
        } catch (PolicyConflictException failure) {
            responseWriter.send(exchange, 409, new ApiErrorEnvelope("v1", new ApiError("REVISION_CONFLICT", safeMessage(failure), Map.of())));
        } catch (ApiFailure failure) {
            if (failure.status() == 405) exchange.getResponseHeaders().set("Allow", allowed(exchange.getRequestURI().getPath()));
            responseWriter.send(exchange, failure.status(), new ApiErrorEnvelope("v1", new ApiError(failure.code(), failure.getMessage(), failure.details())));
        } catch (IllegalArgumentException failure) {
            responseWriter.send(exchange, 400, new ApiErrorEnvelope("v1", new ApiError("BAD_REQUEST", safeMessage(failure), Map.of())));
        } catch (KnowledgeStoreException | IllegalStateException failure) {
            responseWriter.send(exchange, 409, new ApiErrorEnvelope("v1", new ApiError("STATE_CONFLICT", safeMessage(failure), Map.of())));
        } catch (RuntimeException failure) {
            responseWriter.send(exchange, 500, new ApiErrorEnvelope("v1", new ApiError("INTERNAL_ERROR", "internal MORPHEUS API error", Map.of())));
        } finally {
            exchange.close();
        }
    }

    private void requireMethod(HttpExchange exchange, String expected) {
        String actual = exchange.getRequestMethod().toUpperCase(Locale.ROOT);
        if (!actual.equals(expected)) throw ApiFailure.methodNotAllowed("expected HTTP " + expected + " but received " + actual);
    }

    private void rejectQueryParameters(HttpExchange exchange) {
        if (exchange.getRequestURI().getRawQuery() != null && !exchange.getRequestURI().getRawQuery().isBlank()) {
            throw ApiFailure.badRequest("query parameters are not supported on this route");
        }
    }

    private List<String> suffixSegments(String path, String context) {
        if (!path.startsWith(context)) throw ApiFailure.notFound("unknown API route");
        String suffix = path.substring(context.length());
        if (suffix.isEmpty() || suffix.equals("/")) return List.of();
        String normalized = suffix.startsWith("/") ? suffix.substring(1) : suffix;
        if (normalized.endsWith("/")) normalized = normalized.substring(0, normalized.length() - 1);
        List<String> result = new ArrayList<>();
        for (String segment : normalized.split("/")) {
            if (segment.isBlank()) throw ApiFailure.notFound("invalid API path");
            result.add(URLDecoder.decode(segment, StandardCharsets.UTF_8));
        }
        return List.copyOf(result);
    }

    private String allowed(String path) {
        if (path.equals(PACK_CONTEXT)) return "GET, POST";
        if (path.startsWith(PACK_CONTEXT + "/") && suffixSegments(path, PACK_CONTEXT).size() == 1) return "GET, PUT";
        if (path.equals(OVERRIDE_CONTEXT)) return "GET";
        return "POST";
    }

    private static String safeMessage(Throwable failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
    }

    private record Response(int status, Object data) {}

    @FunctionalInterface
    private interface Handler { Response route(); }

    private record Query(Map<String, String> values) {
        private Query { values = Map.copyOf(values); }

        static Query parse(String raw) {
            if (raw == null || raw.isBlank()) return new Query(Map.of());
            Map<String, String> values = new LinkedHashMap<>();
            for (String part : raw.split("&")) {
                int separator = part.indexOf('=');
                String key = URLDecoder.decode(separator < 0 ? part : part.substring(0, separator), StandardCharsets.UTF_8);
                String value = URLDecoder.decode(separator < 0 ? "" : part.substring(separator + 1), StandardCharsets.UTF_8);
                if (key.isBlank() || values.putIfAbsent(key, value) != null) {
                    throw ApiFailure.badRequest("invalid or duplicate query parameter");
                }
            }
            return new Query(values);
        }

        String required(String key) {
            String value = values.get(key);
            if (value == null || value.isBlank()) throw ApiFailure.badRequest("missing query parameter: " + key);
            return value;
        }

        void rejectUnknown(List<String> allowed) {
            values.keySet().stream().filter(key -> !allowed.contains(key)).findFirst()
                    .ifPresent(key -> { throw ApiFailure.badRequest("unknown query parameter: " + key); });
        }
    }
}
