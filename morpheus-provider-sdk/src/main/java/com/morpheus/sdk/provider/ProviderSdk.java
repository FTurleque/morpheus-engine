package com.morpheus.sdk.provider;

/** Stable public constants for MORPHEUS provider plugins. */
public final class ProviderSdk {
    public static final int API_VERSION = 1;
    public static final String METADATA_PATH = "META-INF/morpheus-provider.properties";
    public static final int MAX_PLUGIN_JARS = 256;
    public static final long MAX_PLUGIN_JAR_BYTES = 64L * 1024L * 1024L;
    public static final long MAX_METADATA_BYTES = 16L * 1024L;

    private ProviderSdk() {
    }
}
