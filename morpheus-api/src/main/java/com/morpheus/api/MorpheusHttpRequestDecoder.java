package com.morpheus.api;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ExecutorService;

/** Strict, bounded JSON request decoding for the local HTTP adapter. */
final class MorpheusHttpRequestDecoder {
    private final int maxRequestBodyBytes;
    private final Duration readTimeout;
    private final ExecutorService executor;
    private final JsonMapper mapper;

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

    private byte[] readBody(HttpExchange exchange) {
        try {
            return TimedBoundedInputReader.read(
                    exchange.getRequestBody(), maxRequestBodyBytes, readTimeout, executor);
        } catch (TimedBoundedInputReader.LimitExceededException tooLarge) {
            throw ApiFailure.badRequest("request body exceeds " + maxRequestBodyBytes + " bytes");
        } catch (TimedBoundedInputReader.ReadTimeoutException timeout) {
            throw ApiFailure.badRequest("request body exceeded its read deadline");
        } catch (IOException failure) {
            throw ApiFailure.badRequest("cannot read request body");
        }
    }

    private <T> T decode(byte[] body, Class<T> type) {
        try {
            return mapper.readValue(body, type);
        } catch (Exception failure) {
            throw ApiFailure.badRequest("invalid JSON request body: " + safeMessage(failure));
        }
    }

    private static void requireJsonContentType(Headers headers) {
        String value = headers.getFirst("Content-Type");
        if (!JsonMediaType.isJson(value)) {
            throw ApiFailure.unsupportedMediaType("Content-Type application/json is required");
        }
    }

    private static String safeMessage(Throwable failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
    }
}
