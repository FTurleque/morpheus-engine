package com.morpheus.application.product;

import java.util.Objects;

/** Build-derived product metadata shared by every public MORPHEUS adapter. */
public final class ProductMetadata {
    public static final String PRODUCT_NAME = "MORPHEUS";
    public static final String API_VERSION = "v1";
    public static final String DEFAULT_UPDATE_CHANNEL = "stable";
    public static final String PROJECT_VERSION_PROPERTY = "morpheus.project.version";
    public static final String DEVELOPMENT_VERSION = "development";

    private ProductMetadata() {
    }

    /**
     * Returns the packaged implementation version, falling back to Maven's test/runtime property and finally to an
     * explicit development marker. No adapter-specific historical semantic-version fallback is permitted.
     */
    public static String version() {
        String implementationVersion = ProductMetadata.class.getPackage().getImplementationVersion();
        if (implementationVersion != null && !implementationVersion.isBlank()) {
            return implementationVersion.trim();
        }
        String projectVersion = System.getProperty(PROJECT_VERSION_PROPERTY);
        if (projectVersion != null && !projectVersion.isBlank()) {
            return projectVersion.trim();
        }
        return DEVELOPMENT_VERSION;
    }

    public static boolean developmentRuntime() {
        return DEVELOPMENT_VERSION.equals(version());
    }

    public static ProductInfo current() {
        return new ProductInfo(PRODUCT_NAME, version(), API_VERSION, DEFAULT_UPDATE_CHANNEL);
    }

    public record ProductInfo(String name, String version, String apiVersion, String updateChannel) {
        public ProductInfo {
            name = requireText(name, "name");
            version = requireText(version, "version");
            apiVersion = requireText(apiVersion, "apiVersion");
            updateChannel = requireText(updateChannel, "updateChannel");
        }

        private static String requireText(String value, String name) {
            Objects.requireNonNull(value, name);
            if (value.isBlank()) {
                throw new IllegalArgumentException(name + " must not be blank");
            }
            return value.trim();
        }
    }
}
