package com.morpheus.api;

import java.util.Locale;

/**
 * Exact {@code application/json} media-type admission for HTTP request bodies.
 *
 * <p>A prefix test on the raw header value admits every type whose name merely starts with the same characters,
 * so {@code application/jsonp}, {@code application/json-patch+json} and {@code application/jsonmalicious} are all
 * accepted by it. This parses the header instead: the type and subtype must match exactly, and a {@code charset}
 * parameter is honoured only when it names the UTF-8 encoding the decoders actually read.</p>
 */
final class JsonMediaType {
    private static final String TYPE = "application/json";

    private JsonMediaType() {
    }

    /**
     * True when {@code header} names exactly {@code application/json}, optionally with MIME parameters.
     *
     * <p>Parameters other than {@code charset} are ignored rather than rejected: they carry no decoding meaning
     * here, and the body is parsed under the same strict Jackson configuration regardless.</p>
     */
    static boolean isJson(String header) {
        if (header == null) {
            return false;
        }
        int parameterStart = header.indexOf(';');
        String essence = (parameterStart < 0 ? header : header.substring(0, parameterStart))
                .trim()
                .toLowerCase(Locale.ROOT);
        if (!TYPE.equals(essence)) {
            return false;
        }
        return parameterStart < 0 || charsetIsUtf8(header.substring(parameterStart + 1));
    }

    /** A declared charset must be UTF-8; the request body is decoded as UTF-8 and nothing else. */
    private static boolean charsetIsUtf8(String parameters) {
        for (String parameter : parameters.split(";")) {
            int separator = parameter.indexOf('=');
            if (separator >= 0) {
                String name = parameter.substring(0, separator).trim().toLowerCase(Locale.ROOT);
                if ("charset".equals(name)) {
                    String value = unquote(parameter.substring(separator + 1).trim()).toLowerCase(Locale.ROOT);
                    if (!"utf-8".equals(value) && !"utf8".equals(value)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private static String unquote(String value) {
        if (value.length() >= 2 && value.charAt(0) == '"' && value.charAt(value.length() - 1) == '"') {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }
}
