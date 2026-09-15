package com.morpheus.api;

import com.morpheus.application.policy.PolicyConflictException;
import com.morpheus.application.policy.PolicyIds;
import com.morpheus.application.policy.PolicyPackService;
import com.morpheus.application.policy.PolicyPublicViews;
import com.morpheus.application.policy.PolicyScope;
import com.morpheus.application.query.compact.CanonicalJsonSerializer;
import com.morpheus.application.store.KnowledgeStoreException;
import com.morpheus.domain.portfolio.PortfolioId;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.store.sqlite.SqlitePolicyPackStore;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** M25 management routes needed to discover CAS state and remove an override explicitly. */
final class MorpheusPolicyManagementHttpRoutes {
    private static final String ACTIVATION_CONTEXT = MorpheusHttpServer.API_PREFIX + "/policy-activations";
    private static final String REMOVE_OVERRIDE_CONTEXT = MorpheusHttpServer.API_PREFIX + "/policy-overrides/remove";

    private final Path databasePath;
    private final CanonicalJsonSerializer serializer = new CanonicalJsonSerializer();
    private final MorpheusHttpRequestDecoder requestDecoder;

    private MorpheusPolicyManagementHttpRoutes(Path databasePath, MorpheusHttpRequestDecoder requestDecoder) {
        this.databasePath = Objects.requireNonNull(databasePath, "databasePath").toAbsolutePath().normalize();
        this.requestDecoder = Objects.requireNonNull(requestDecoder, "requestDecoder");
    }

    static void register(HttpServer server, Path databasePath, MorpheusHttpRequestDecoder requestDecoder) {
        MorpheusPolicyManagementHttpRoutes routes = new MorpheusPolicyManagementHttpRoutes(databasePath, requestDecoder);
        server.createContext(ACTIVATION_CONTEXT, routes::handleActivations);
        server.createContext(REMOVE_OVERRIDE_CONTEXT, routes::handleRemoveOverride);
    }

    private void handleActivations(HttpExchange exchange) throws IOException {
        handle(exchange, () -> {
            requireMethod(exchange, "GET");
            requireExactPath(exchange, ACTIVATION_CONTEXT);
            requestDecoder.requireEmptyBody(exchange);
            Query query = Query.parse(exchange.getRequestURI().getRawQuery());
            query.rejectUnknown(List.of("scopeKind", "scopeId"));
            PolicyScope scope = scope(query.required("scopeKind"), query.required("scopeId"));
            try (SqlitePolicyPackStore store = new SqlitePolicyPackStore(databasePath)) {
                return PolicyPublicViews.activations(new PolicyPackService(store).activations(scope));
            }
        });
    }

    private void handleRemoveOverride(HttpExchange exchange) throws IOException {
        handle(exchange, () -> {
            requireMethod(exchange, "POST");
            requireExactPath(exchange, REMOVE_OVERRIDE_CONTEXT);
            rejectQueryParameters(exchange);
            RemoveOverrideRequest request = requestDecoder.readRequiredJson(exchange, RemoveOverrideRequest.class);
            try (SqlitePolicyPackStore store = new SqlitePolicyPackStore(databasePath)) {
                new PolicyPackService(store).removeOverride(
                        scope(request.scopeKind(), request.scopeId()),
                        PolicyIds.PackId.parse(requiredText(request.id(), "id")),
                        PolicyIds.RuleId.parse(requiredText(request.ruleId(), "ruleId")),
                        positive(request.expectedRevision(), "expectedRevision"),
                        requiredText(request.actor(), "actor"),
                        requiredText(request.reason(), "reason"));
            }
            return Map.of("removed", true);
        });
    }

    private PolicyScope scope(String rawKind, String rawId) {
        String kind = requiredText(rawKind, "scopeKind").toUpperCase(Locale.ROOT);
        String id = requiredText(rawId, "scopeId");
        return switch (kind) {
            case "PROJECT" -> new PolicyScope.Project(ProjectSpecificationId.parse(id));
            case "PORTFOLIO" -> new PolicyScope.Portfolio(PortfolioId.parse(id));
            default -> throw new IllegalArgumentException("scopeKind must be PROJECT or PORTFOLIO");
        };
    }

    private void handle(HttpExchange exchange, Handler handler) throws IOException {
        try {
            send(exchange, 200, new ApiSuccess("v1", handler.execute()));
        } catch (PolicyConflictException failure) {
            send(exchange, 409, new ApiErrorEnvelope("v1", new ApiError("REVISION_CONFLICT", safeMessage(failure), Map.of())));
        } catch (ApiFailure failure) {
            if (failure.status() == 405) {
                exchange.getResponseHeaders().set("Allow", exchange.getRequestURI().getPath().equals(ACTIVATION_CONTEXT) ? "GET" : "POST");
            }
            send(exchange, failure.status(), new ApiErrorEnvelope("v1", new ApiError(failure.code(), failure.getMessage(), failure.details())));
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

    private void requireMethod(HttpExchange exchange, String expected) {
        String actual = exchange.getRequestMethod().toUpperCase(Locale.ROOT);
        if (!actual.equals(expected)) {
            throw ApiFailure.methodNotAllowed("expected HTTP " + expected + " but received " + actual);
        }
    }

    private void requireExactPath(HttpExchange exchange, String expected) {
        if (!exchange.getRequestURI().getPath().equals(expected)) {
            throw ApiFailure.notFound("unknown API route");
        }
    }

    private void rejectQueryParameters(HttpExchange exchange) {
        if (exchange.getRequestURI().getRawQuery() != null && !exchange.getRequestURI().getRawQuery().isBlank()) {
            throw ApiFailure.badRequest("query parameters are not supported on this route");
        }
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

    private static String requiredText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }

    private static long positive(Long value, String name) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(name + " must be a positive integer");
        }
        return value;
    }

    private static String safeMessage(Throwable failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
    }

    record RemoveOverrideRequest(
            String id,
            String ruleId,
            String scopeKind,
            String scopeId,
            Long expectedRevision,
            String actor,
            String reason) {}

    private record ApiSuccess(String apiVersion, Object data) {}
    private record ApiError(String code, String message, Map<String, Object> details) {}
    private record ApiErrorEnvelope(String apiVersion, ApiError error) {}

    @FunctionalInterface
    private interface Handler {
        Object execute();
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
                    throw ApiFailure.badRequest("invalid or duplicate query parameter: " + key);
                }
            }
            return new Query(values);
        }

        String required(String name) {
            String value = values.get(name);
            if (value == null || value.isBlank()) {
                throw ApiFailure.badRequest("query parameter is required: " + name);
            }
            return value;
        }

        void rejectUnknown(List<String> allowed) {
            values.keySet().stream().filter(key -> !allowed.contains(key)).findFirst()
                    .ifPresent(key -> {
                        throw ApiFailure.badRequest("unknown query parameter: " + key);
                    });
        }
    }
}
