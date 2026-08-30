package com.morpheus.api;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Rejects browser-originated requests whose authority is not an explicit loopback literal/localhost. */
final class LoopbackRequestPolicy {
    private static final String REJECTION_MESSAGE = "local MORPHEUS API accepts loopback hosts and origins only";

    private LoopbackRequestPolicy() {
    }

    static void requireAllowed(HttpExchange exchange) {
        Objects.requireNonNull(exchange, "exchange");
        requireAllowed(exchange.getRequestHeaders());
    }

    static void requireAllowed(Headers headers) {
        Objects.requireNonNull(headers, "headers");
        List<String> hosts = headers.get("Host");
        if (hosts == null || hosts.size() != 1 || !isAllowedAuthority(hosts.getFirst())) {
            throw new RejectedRequestException(REJECTION_MESSAGE);
        }

        List<String> origins = headers.get("Origin");
        if (origins != null) {
            if (origins.size() != 1 || !isAllowedOrigin(origins.getFirst())) {
                throw new RejectedRequestException(REJECTION_MESSAGE);
            }
        }

        List<String> fetchSites = headers.get("Sec-Fetch-Site");
        if (fetchSites != null) {
            if (fetchSites.size() != 1 || fetchSites.getFirst() == null
                    || fetchSites.getFirst().trim().equalsIgnoreCase("cross-site")) {
                throw new RejectedRequestException(REJECTION_MESSAGE);
            }
        }
    }

    private static boolean isAllowedOrigin(String rawOrigin) {
        if (rawOrigin == null || rawOrigin.isBlank() || rawOrigin.equalsIgnoreCase("null")) {
            return false;
        }
        try {
            URI origin = URI.create(rawOrigin.trim());
            String scheme = origin.getScheme();
            String path = origin.getRawPath();
            if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
                return false;
            }
            if (origin.getUserInfo() != null || origin.getHost() == null
                    || path != null && !path.isEmpty()
                    || origin.getRawQuery() != null || origin.getRawFragment() != null) {
                return false;
            }
            return isAllowedHost(origin.getHost());
        } catch (IllegalArgumentException invalid) {
            return false;
        }
    }

    private static boolean isAllowedAuthority(String rawAuthority) {
        if (rawAuthority == null || rawAuthority.isBlank()) {
            return false;
        }
        String authority = rawAuthority.trim();
        try {
            URI parsed = URI.create("http://" + authority);
            String path = parsed.getRawPath();
            if (parsed.getHost() == null || parsed.getUserInfo() != null
                    || parsed.getRawQuery() != null || parsed.getRawFragment() != null
                    || path != null && !path.isEmpty()) {
                return false;
            }
            return isAllowedHost(parsed.getHost());
        } catch (IllegalArgumentException invalid) {
            return false;
        }
    }

    private static boolean isAllowedHost(String host) {
        String normalized = Objects.requireNonNull(host, "host").trim().toLowerCase(Locale.ROOT);
        if (normalized.equals("localhost")) {
            return true;
        }
        if (isIpv4LoopbackLiteral(normalized)) {
            return true;
        }
        if (!normalized.contains(":") || normalized.contains("%")) {
            return false;
        }
        try {
            InetAddress address = InetAddress.getByName(normalized);
            return address.isLoopbackAddress();
        } catch (UnknownHostException invalid) {
            return false;
        }
    }

    private static boolean isIpv4LoopbackLiteral(String host) {
        String[] octets = host.split("\\.", -1);
        if (octets.length != 4 || !octets[0].equals("127")) {
            return false;
        }
        for (String octet : octets) {
            if (octet.isEmpty() || octet.length() > 3 || !octet.chars().allMatch(Character::isDigit)) {
                return false;
            }
            int value;
            try {
                value = Integer.parseInt(octet);
            } catch (NumberFormatException invalid) {
                return false;
            }
            if (value < 0 || value > 255) {
                return false;
            }
        }
        return true;
    }

    static final class RejectedRequestException extends RuntimeException {
        private RejectedRequestException(String message) {
            super(message);
        }
    }
}
