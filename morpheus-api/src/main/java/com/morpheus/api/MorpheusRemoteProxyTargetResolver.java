package com.morpheus.api;

import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Resolves the private loopback target used by the remote facade.
 *
 * <p>The resolver owns only URI construction and server-controlled provider-plugin query parameters. It does
 * not perform authentication, authorization, request-body validation or HTTP transport.</p>
 */
final class MorpheusRemoteProxyTargetResolver {
    private static final String PROVIDER_PLUGIN_PREFIX = MorpheusHttpServer.API_PREFIX + "/provider-plugins/";

    private final int localPort;
    private final Path providerPluginDirectory;
    private final AllowedWorkspaceRoots allowedWorkspaceRoots;

    MorpheusRemoteProxyTargetResolver(
            int localPort,
            Path providerPluginDirectory,
            AllowedWorkspaceRoots allowedWorkspaceRoots) {
        this.localPort = localPort;
        this.providerPluginDirectory = Objects.requireNonNull(providerPluginDirectory, "providerPluginDirectory")
                .toAbsolutePath()
                .normalize();
        this.allowedWorkspaceRoots = Objects.requireNonNull(allowedWorkspaceRoots, "allowedWorkspaceRoots");
    }

    URI resolve(URI requestUri) {
        Objects.requireNonNull(requestUri, "requestUri");
        String suffix = requestUri.getRawPath();
        if (isProviderPluginPath(requestUri.getPath())) {
            Map<String, String> query = parseQuery(requestUri.getRawQuery());
            if (query.containsKey("directory")) {
                throw failure(
                        "SERVER_CONFIGURED_PLUGIN_DIRECTORY",
                        "provider-plugin directory is configured by the remote server and must not be supplied by the client");
            }
            Map<String, String> upstream = new LinkedHashMap<>(query);
            upstream.put("directory", providerPluginDirectory.toString());
            if (requestUri.getPath().endsWith("/probe")) {
                String sha256 = query.get("sha256");
                if (sha256 == null || sha256.isBlank()) {
                    throw failure(
                            "PLUGIN_SHA256_REQUIRED",
                            "remote provider-plugin probe requires a trusted SHA-256 pin");
                }
                if (!sha256.matches("[0-9a-fA-F]{64}")) {
                    throw failure(
                            "PLUGIN_SHA256_INVALID",
                            "remote provider-plugin SHA-256 pin must contain exactly 64 hexadecimal characters");
                }
                upstream.put("sha256", sha256.toLowerCase(Locale.ROOT));
                upstream.put("workspace", allowedWorkspaceRoots
                        .requireAllowedDirectory(query.get("workspace"))
                        .toString());
            }
            suffix += "?" + encodeQuery(upstream);
        } else if (requestUri.getRawQuery() != null) {
            suffix += "?" + requestUri.getRawQuery();
        }
        return URI.create("http://127.0.0.1:" + localPort + suffix);
    }

    private static boolean isProviderPluginPath(String path) {
        return path.equals(PROVIDER_PLUGIN_PREFIX + "discover") || path.equals(PROVIDER_PLUGIN_PREFIX + "probe");
    }

    private static Map<String, String> parseQuery(String rawQuery) {
        if (rawQuery == null || rawQuery.isBlank()) return Map.of();
        Map<String, String> result = new LinkedHashMap<>();
        for (String part : rawQuery.split("&")) {
            if (part.isBlank()) continue;
            int separator = part.indexOf('=');
            String key = URLDecoder.decode(separator < 0 ? part : part.substring(0, separator), StandardCharsets.UTF_8);
            String value = URLDecoder.decode(separator < 0 ? "" : part.substring(separator + 1), StandardCharsets.UTF_8);
            if (key.isBlank()) throw failure("BAD_REQUEST", "query parameter name must not be blank");
            if (result.putIfAbsent(key, value) != null) {
                throw failure("BAD_REQUEST", "duplicate query parameter: " + key);
            }
        }
        return Map.copyOf(result);
    }

    private static String encodeQuery(Map<String, String> query) {
        StringBuilder result = new StringBuilder();
        for (Map.Entry<String, String> entry : query.entrySet()) {
            if (!result.isEmpty()) result.append('&');
            result.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8));
            result.append('=');
            result.append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
        }
        return result.toString();
    }

    private static ResolutionException failure(String code, String message) {
        return new ResolutionException(400, code, message);
    }

    static final class ResolutionException extends RuntimeException {
        private final int status;
        private final String code;

        private ResolutionException(int status, String code, String message) {
            super(message);
            this.status = status;
            this.code = code;
        }

        int status() {
            return status;
        }

        String code() {
            return code;
        }
    }
}
