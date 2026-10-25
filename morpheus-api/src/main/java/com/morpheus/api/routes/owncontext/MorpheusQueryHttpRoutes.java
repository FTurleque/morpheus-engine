package com.morpheus.api.routes.owncontext;

import com.morpheus.api.MorpheusQueryApiService;
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
import com.morpheus.application.query.dsl.QueryValidationException;
import com.morpheus.application.query.export.QueryExport;
import com.morpheus.application.query.export.QueryExportBudgetException;
import com.morpheus.application.query.saved.SavedViewConflictException;
import com.morpheus.application.store.KnowledgeStoreException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Isolated M24/M25 extension routing contexts so legacy /api/v1 routes remain untouched. */
public final class MorpheusQueryHttpRoutes {
    private static final String QUERIES = "queries";
    private static final String VIEWS = "saved-views";
    private static final String EXPORTS = "exports";
    private static final String QUERY_CONTEXT = MorpheusHttpRouteTable.API_PREFIX + "/" + QUERIES;
    private static final String VIEW_CONTEXT = MorpheusHttpRouteTable.API_PREFIX + "/" + VIEWS;
    private static final String EXPORT_CONTEXT = MorpheusHttpRouteTable.API_PREFIX + "/" + EXPORTS;

    private final MorpheusQueryApiService service;
    private final OperationalRecorder recorder;
    private final MorpheusHttpRequestDecoder requestDecoder;
    private final MorpheusHttpAllowedMethods allowedMethods;
    private final MorpheusHttpResponseWriter responseWriter;

    private MorpheusQueryHttpRoutes(Path databasePath, OperationalRecorder recorder,
            MorpheusHttpRequestDecoder requestDecoder, MorpheusHttpAllowedMethods allowedMethods,
            MorpheusHttpResponseWriter responseWriter) {
        service = new MorpheusQueryApiService(databasePath, recorder);
        this.recorder = Objects.requireNonNull(recorder, "recorder");
        this.requestDecoder = Objects.requireNonNull(requestDecoder, "requestDecoder");
        this.allowedMethods = Objects.requireNonNull(allowedMethods, "allowedMethods");
        this.responseWriter = Objects.requireNonNull(responseWriter, "responseWriter");
    }

    public static void register(HttpServer server, Path databasePath, OperationalRecorder recorder,
            MorpheusHttpRequestDecoder requestDecoder, MorpheusHttpAllowedMethods allowedMethods,
            MorpheusHttpResponseWriter responseWriter) {
        Objects.requireNonNull(server, "server");
        MorpheusQueryHttpRoutes routes = new MorpheusQueryHttpRoutes(databasePath, recorder, requestDecoder,
                allowedMethods, responseWriter);
        server.createContext(QUERY_CONTEXT, routes::handleQueries);
        server.createContext(VIEW_CONTEXT, routes::handleSavedViews);
        server.createContext(EXPORT_CONTEXT, routes::handleExports);
        MorpheusPolicyHttpRoutes.register(server, databasePath, recorder, requestDecoder, allowedMethods,
                responseWriter);
        MorpheusReasoningHttpRoutes.register(server, recorder, requestDecoder, allowedMethods, responseWriter);
    }

    private void handleQueries(HttpExchange exchange) throws IOException {
        handle(exchange, () -> {
            requireMethod(exchange, "POST");
            requireExactPath(exchange, QUERIES, "execute");
            rejectQueryParameters(exchange);
            return json(200, service.execute(
                    requestDecoder.readRequiredJson(exchange, MorpheusQueryApiService.ScopedQueryRequest.class)));
        });
    }

    private void handleExports(HttpExchange exchange) throws IOException {
        handle(exchange, () -> {
            requireMethod(exchange, "POST");
            requireExactPath(exchange, EXPORTS);
            rejectQueryParameters(exchange);
            return raw(200, service.export(
                    requestDecoder.readRequiredJson(exchange, MorpheusQueryApiService.ExportRequest.class)));
        });
    }

    private void handleSavedViews(HttpExchange exchange) throws IOException {
        handle(exchange, () -> routeSavedViews(exchange));
    }

    private Response routeSavedViews(HttpExchange exchange) {
        String method = exchange.getRequestMethod().toUpperCase(Locale.ROOT);
        List<String> segments = requestDecoder.pathSegmentsBelow(exchange, VIEWS);
        if (segments.isEmpty()) {
            if (method.equals("GET")) {
                MorpheusHttpQuery query = MorpheusHttpQuery.parse(exchange.getRequestURI().getRawQuery());
                query.rejectUnknown(Set.of("scopeKind", "scopeId"));
                return json(200, service.listSavedViews(query.required("scopeKind"), query.required("scopeId")));
            }
            if (method.equals("POST")) {
                rejectQueryParameters(exchange);
                return json(201, service.createSavedView(
                        requestDecoder.readRequiredJson(exchange, MorpheusQueryApiService.CreateSavedViewRequest.class)));
            }
            throw ApiFailure.methodNotAllowed("saved-views supports GET and POST");
        }

        rejectQueryParameters(exchange);
        String id = segments.getFirst();
        if (segments.size() == 1) {
            if (method.equals("GET")) {
                return json(200, service.getSavedView(id));
            }
            if (method.equals("PUT")) {
                return json(200, service.updateSavedView(
                        id, requestDecoder.readRequiredJson(exchange, MorpheusQueryApiService.UpdateSavedViewRequest.class)));
            }
            throw ApiFailure.methodNotAllowed("saved view supports GET and PUT");
        }
        if (segments.size() != 2) {
            throw ApiFailure.notFound("unknown saved-view route");
        }
        String action = segments.get(1);
        return switch (action) {
            case "versions" -> {
                requireMethod(exchange, "GET");
                yield json(200, service.savedViewVersions(id));
            }
            case "execute" -> {
                requireMethod(exchange, "POST");
                requestDecoder.requireEmptyBody(exchange);
                yield json(200, service.executeSavedView(id));
            }
            case "archive" -> {
                requireMethod(exchange, "POST");
                yield json(200, service.archiveSavedView(
                        id, requestDecoder.readRequiredJson(exchange, MorpheusQueryApiService.RevisionRequest.class)));
            }
            case "export" -> {
                requireMethod(exchange, "POST");
                yield raw(200, service.exportSavedView(
                        id, requestDecoder.readRequiredJson(exchange, MorpheusQueryApiService.ExportSavedViewRequest.class)));
            }
            default -> throw ApiFailure.notFound("unknown saved-view action: " + action);
        };
    }

    private void handle(HttpExchange exchange, Handler handler) throws IOException {
        try {
            Response response = handler.route();
            if (response.export().isPresent()) {
                QueryExport export = response.export().orElseThrow();
                responseWriter.sendRaw(exchange, response.status(), export.mediaType(), export.utf8());
            } else {
                responseWriter.send(exchange, response.status(), new ApiSuccess("v1", response.data()));
            }
        } catch (QueryValidationException failure) {
            responseWriter.send(exchange, 400, new ApiErrorEnvelope(
                    "v1", new ApiError("QUERY_VALIDATION", BoundaryFailureMessage.safe(failure), Map.of())));
        } catch (QueryExportBudgetException failure) {
            responseWriter.send(exchange, 400, new ApiErrorEnvelope(
                    "v1", new ApiError("QUERY_BUDGET_EXCEEDED", BoundaryFailureMessage.safe(failure), Map.of())));
        } catch (SavedViewConflictException failure) {
            responseWriter.send(exchange, 409, new ApiErrorEnvelope(
                    "v1", new ApiError("REVISION_CONFLICT", BoundaryFailureMessage.safe(failure), Map.of())));
        } catch (ApiFailure failure) {
            if (failure.status() == 405) {
                exchange.getResponseHeaders().set("Allow", allowedMethods.forPath(exchange.getRequestURI().getRawPath()));
            }
            responseWriter.send(exchange, failure.status(), new ApiErrorEnvelope(
                    "v1", new ApiError(failure.code(), failure.getMessage(), failure.details())));
        } catch (IllegalArgumentException failure) {
            responseWriter.send(exchange, 400, new ApiErrorEnvelope(
                    "v1", new ApiError("BAD_REQUEST", BoundaryFailureMessage.safe(failure), Map.of())));
        } catch (KnowledgeStoreException | IllegalStateException failure) {
            responseWriter.send(exchange, 409, new ApiErrorEnvelope(
                    "v1", new ApiError("STATE_CONFLICT", BoundaryFailureMessage.safe(failure), Map.of())));
        } catch (RuntimeException failure) {
            UnforeseenFailureTrace.record(recorder, failure);
            responseWriter.send(exchange, 500, new ApiErrorEnvelope(
                    "v1", new ApiError("INTERNAL_ERROR", "internal MORPHEUS API error", Map.of())));
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

    private void requireExactPath(HttpExchange exchange, String... expected) {
        if (!requestDecoder.pathSegments(exchange).equals(List.of(expected))) {
            throw ApiFailure.notFound("unknown API route");
        }
    }

    private void rejectQueryParameters(HttpExchange exchange) {
        if (exchange.getRequestURI().getRawQuery() != null && !exchange.getRequestURI().getRawQuery().isBlank()) {
            throw ApiFailure.badRequest("query parameters are not supported on this route");
        }
    }

    private Response json(int status, Object data) {
        return new Response(status, Objects.requireNonNull(data, "data"), Optional.empty());
    }

    private Response raw(int status, QueryExport export) {
        return new Response(status, export, Optional.of(export));
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

    @FunctionalInterface
    private interface Handler {
        Response route();
    }
}
