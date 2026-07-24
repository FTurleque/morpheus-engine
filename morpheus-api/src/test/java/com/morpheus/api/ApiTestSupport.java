package com.morpheus.api;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;

final class ApiTestSupport {
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    Response get(MorpheusHttpServer server, String pathAndQuery) {
        return send(HttpRequest.newBuilder(uri(server, pathAndQuery)).GET().build());
    }

    Response postJson(MorpheusHttpServer server, String path, String body) {
        return send(HttpRequest.newBuilder(uri(server, path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build());
    }

    Response postWithoutContentType(MorpheusHttpServer server, String path, String body) {
        return send(HttpRequest.newBuilder(uri(server, path))
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build());
    }

    Response post(MorpheusHttpServer server, String path) {
        return send(HttpRequest.newBuilder(uri(server, path))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build());
    }

    Path fixture(String name) {
        Path current = Path.of("").toAbsolutePath().normalize();
        Path fromRoot = current.resolve("experiments/m0/fixtures").resolve(name);
        if (Files.isDirectory(fromRoot)) {
            return fromRoot;
        }
        Path fromModule = current.resolve("../experiments/m0/fixtures").normalize().resolve(name);
        if (Files.isDirectory(fromModule)) {
            return fromModule;
        }
        throw new IllegalStateException("M0 fixture not found: " + name + " from " + current);
    }

    Path copyFixture(String name, Path destination) {
        Path source = fixture(name);
        try (var paths = Files.walk(source)) {
            paths.forEach(path -> {
                Path relative = source.relativize(path);
                Path target = destination.resolve(relative);
                try {
                    if (Files.isDirectory(path)) {
                        Files.createDirectories(target);
                    } else {
                        Files.createDirectories(target.getParent());
                        Files.copy(path, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
                    }
                } catch (IOException failure) {
                    throw new IllegalStateException("cannot copy test fixture entry: " + path, failure);
                }
            });
        } catch (IOException failure) {
            throw new IllegalStateException("cannot copy test fixture: " + source, failure);
        }
        return destination;
    }

    String jsonString(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    String field(String json, String field) {
        String marker = "\"" + field + "\":\"";
        int start = json.indexOf(marker);
        if (start < 0) {
            throw new AssertionError("missing field " + field + " in " + json);
        }
        start += marker.length();
        int end = json.indexOf('"', start);
        if (end < 0) {
            throw new AssertionError("unterminated field " + field + " in " + json);
        }
        return json.substring(start, end);
    }

    String firstUuidField(String json, String field) {
        return field(json, field);
    }

    private URI uri(MorpheusHttpServer server, String pathAndQuery) {
        String suffix = pathAndQuery.startsWith("/") ? pathAndQuery : "/" + pathAndQuery;
        return URI.create("http://" + server.host() + ":" + server.port() + MorpheusHttpServer.API_PREFIX + suffix);
    }

    private Response send(HttpRequest request) {
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            return new Response(
                    response.statusCode(),
                    response.headers().firstValue("Content-Type").orElse(""),
                    response.body());
        } catch (IOException failure) {
            throw new IllegalStateException("HTTP test request failed", failure);
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("HTTP test request interrupted", failure);
        }
    }

    record Response(int status, String contentType, String body) {
    }
}
