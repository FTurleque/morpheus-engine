package com.morpheus.api;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;

/** Strict, bounded decoding of a request for the local HTTP adapter: its JSON body and the segments of its path. */
final class MorpheusHttpRequestDecoder {
    private final int maxRequestBodyBytes;
    private final Duration readTimeout;
    private final ExecutorService executor;
    private final JsonMapper mapper;
    private final MorpheusHttpPathParser pathParser = MorpheusHttpPathParser.api();

    MorpheusHttpRequestDecoder(int maxRequestBodyBytes, Duration readTimeout, ExecutorService executor) {
        if (maxRequestBodyBytes < 1) throw new IllegalArgumentException("maxRequestBodyBytes must be positive");
        this.maxRequestBodyBytes = maxRequestBodyBytes;
        this.readTimeout = Objects.requireNonNull(readTimeout, "readTimeout");
        if (readTimeout.isZero() || readTimeout.isNegative()) {
            throw new IllegalArgumentException("readTimeout must be positive");
        }
        this.executor = Objects.requireNonNull(executor, "executor");
        this.mapper = JsonMapper.builder()
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .build();
    }

    <T> T readRequiredJson(HttpExchange exchange, Class<T> type) {
        Objects.requireNonNull(exchange, "exchange");
        Objects.requireNonNull(type, "type");
        byte[] body = readBody(exchange);
        if (body.length == 0) throw ApiFailure.badRequest("JSON request body is required");
        requireJsonContentType(exchange.getRequestHeaders());
        return decode(body, type);
    }

    <T> T readOptionalJson(HttpExchange exchange, Class<T> type, T defaultValue) {
        Objects.requireNonNull(exchange, "exchange");
        Objects.requireNonNull(type, "type");
        byte[] body = readBody(exchange);
        if (body.length == 0) return defaultValue;
        requireJsonContentType(exchange.getRequestHeaders());
        return decode(body, type);
    }

    void requireEmptyBody(HttpExchange exchange) {
        Objects.requireNonNull(exchange, "exchange");
        if (readBody(exchange).length != 0) throw ApiFailure.badRequest("request body must be empty");
    }

    /** The decoded segments of the request path below the API prefix, read from the path as it was sent. */
    List<String> pathSegments(HttpExchange exchange) {
        Objects.requireNonNull(exchange, "exchange");
        return pathParser.segments(exchange.getRequestURI().getRawPath());
    }

    /**
     * The segments below {@code head}, for a router that registers its own context: the HTTP server hands a context
     * every path its name is a prefix of, so the first segment is checked rather than assumed.
     */
    List<String> pathSegmentsBelow(HttpExchange exchange, String head) {
        Objects.requireNonNull(head, "head");
        List<String> segments = pathSegments(exchange);
        if (segments.isEmpty() || !segments.getFirst().equals(head)) throw ApiFailure.notFound("unknown API route");
        return segments.subList(1, segments.size());
    }

    private byte[] readBody(HttpExchange exchange) {
        try {
            return HttpRequestBodyReader.read(exchange.getRequestBody(), maxRequestBodyBytes, readTimeout, executor);
        } catch (HttpRequestBodyReader.RequestBodyException failure) {
            throw ApiFailure.badRequest(BoundaryFailureMessage.safe(failure));
        }
    }

    private <T> T decode(byte[] body, Class<T> type) {
        try {
            return mapper.readValue(body, type);
        } catch (Exception failure) {
            throw ApiFailure.badRequest("invalid JSON request body: " + BoundaryFailureMessage.safe(failure));
        }
    }

    private static void requireJsonContentType(Headers headers) {
        String value = headers.getFirst("Content-Type");
        if (!JsonMediaType.isJson(value)) {
            throw ApiFailure.unsupportedMediaType("Content-Type application/json is required");
        }
    }
}
