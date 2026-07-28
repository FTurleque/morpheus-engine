package com.morpheus.sdk.provider;

import com.morpheus.domain.provider.ProviderId;

import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.regex.Pattern;

/** Declarative provider-plugin metadata. Reading this type never implies plugin activation or trust. */
public record ProviderPluginMetadata(
        String pluginId,
        ProviderId providerId,
        String pluginVersion,
        int sdkApiVersion,
        String minMorpheusVersion,
        Optional<String> maxMorpheusVersion) {

    private static final Pattern ID = Pattern.compile("[a-z0-9][a-z0-9._-]{0,127}");
    private static final Pattern VERSION = Pattern.compile("[0-9]+\\.[0-9]+\\.[0-9]+(?:[-+][0-9A-Za-z.-]+)?");

    public ProviderPluginMetadata {
        pluginId = requireId(pluginId, "pluginId");
        Objects.requireNonNull(providerId, "providerId");
        pluginVersion = requireVersion(pluginVersion, "pluginVersion");
        if (sdkApiVersion < 1) {
            throw new IllegalArgumentException("sdkApiVersion must be positive");
        }
        minMorpheusVersion = requireVersion(minMorpheusVersion, "minMorpheusVersion");
        maxMorpheusVersion = Objects.requireNonNull(maxMorpheusVersion, "maxMorpheusVersion")
                .map(value -> requireVersion(value, "maxMorpheusVersion"));
    }

    public static ProviderPluginMetadata from(Properties properties) {
        Objects.requireNonNull(properties, "properties");
        return new ProviderPluginMetadata(
                required(properties, "plugin.id"),
                new ProviderId(required(properties, "provider.id")),
                required(properties, "plugin.version"),
                parseApiVersion(required(properties, "sdk.apiVersion")),
                required(properties, "morpheus.minVersion"),
                optional(properties, "morpheus.maxVersion"));
    }

    private static int parseApiVersion(String raw) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException failure) {
            throw new IllegalArgumentException("sdk.apiVersion must be an integer", failure);
        }
    }

    private static String required(Properties properties, String name) {
        String value = properties.getProperty(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("missing provider metadata: " + name);
        }
        return value.trim();
    }

    private static Optional<String> optional(Properties properties, String name) {
        String value = properties.getProperty(name);
        return value == null || value.isBlank() ? Optional.empty() : Optional.of(value.trim());
    }

    private static String requireId(String value, String name) {
        Objects.requireNonNull(value, name);
        String normalized = value.trim();
        if (!ID.matcher(normalized).matches()) {
            throw new IllegalArgumentException(name + " must match " + ID.pattern());
        }
        return normalized;
    }

    private static String requireVersion(String value, String name) {
        Objects.requireNonNull(value, name);
        String normalized = value.trim();
        if (!VERSION.matcher(normalized).matches()) {
            throw new IllegalArgumentException(name + " must be a semantic version (x.y.z with optional prerelease/build metadata)");
        }
        return normalized;
    }
}
