package com.morpheus.sdk.provider;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

/**
 * Explicit, metadata-only discovery of provider plugin JARs.
 *
 * <p>This class never creates a ClassLoader or ServiceLoader. Reading a directory therefore does not execute plugin
 * code.</p>
 */
public final class ProviderPluginDiscovery {
    private final ProviderPluginCompatibility compatibility = new ProviderPluginCompatibility();

    public ProviderPluginDiscoveryResult discover(Path pluginDirectory) {
        Path directory = pluginDirectory.toAbsolutePath().normalize();
        if (!Files.exists(directory)) {
            return new ProviderPluginDiscoveryResult(
                    directory,
                    List.of(),
                    List.of(ProviderPluginDiagnostic.info(
                            "PLUGIN_DIRECTORY_NOT_FOUND",
                            "Provider plugin directory does not exist; no optional plugins were discovered",
                            Map.of("directory", directory.toString()))));
        }
        if (!Files.isDirectory(directory)) {
            return new ProviderPluginDiscoveryResult(
                    directory,
                    List.of(),
                    List.of(ProviderPluginDiagnostic.error(
                            "PLUGIN_PATH_NOT_DIRECTORY",
                            "Provider plugin path is not a directory",
                            Map.of("directory", directory.toString()))));
        }

        List<ProviderPluginDiagnostic> diagnostics = new ArrayList<>();
        List<Path> jars;
        try (Stream<Path> entries = Files.list(directory)) {
            jars = entries
                    .filter(Files::isRegularFile)
                    .filter(this::isJar)
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .limit((long) ProviderSdk.MAX_PLUGIN_JARS + 1L)
                    .toList();
        } catch (IOException failure) {
            return new ProviderPluginDiscoveryResult(
                    directory,
                    List.of(),
                    List.of(ProviderPluginDiagnostic.error(
                            "PLUGIN_DIRECTORY_READ_FAILED",
                            "Cannot inspect provider plugin directory",
                            Map.of("directory", directory.toString(), "reason", safeMessage(failure)))));
        }

        if (jars.size() > ProviderSdk.MAX_PLUGIN_JARS) {
            diagnostics.add(ProviderPluginDiagnostic.warning(
                    "PLUGIN_SCAN_LIMIT_REACHED",
                    "Provider plugin scan was truncated at the configured JAR limit",
                    Map.of("limit", Integer.toString(ProviderSdk.MAX_PLUGIN_JARS))));
            jars = jars.subList(0, ProviderSdk.MAX_PLUGIN_JARS);
        }

        List<ProviderPluginCandidate> candidates = jars.stream().map(this::inspect).toList();
        return new ProviderPluginDiscoveryResult(directory, candidates, diagnostics);
    }

    private ProviderPluginCandidate inspect(Path jarPath) {
        Path jar = jarPath.toAbsolutePath().normalize();
        try {
            long size = Files.size(jar);
            if (size > ProviderSdk.MAX_PLUGIN_JAR_BYTES) {
                return invalid(jar, "PLUGIN_JAR_TOO_LARGE", "Provider plugin JAR exceeds the scan size limit", Map.of(
                        "sizeBytes", Long.toString(size),
                        "limitBytes", Long.toString(ProviderSdk.MAX_PLUGIN_JAR_BYTES)));
            }

            try (JarFile jarFile = new JarFile(jar.toFile(), false)) {
                JarEntry metadataEntry = jarFile.getJarEntry(ProviderSdk.METADATA_PATH);
                if (metadataEntry == null) {
                    return invalid(
                            jar,
                            "PLUGIN_METADATA_MISSING",
                            "Provider plugin JAR does not contain " + ProviderSdk.METADATA_PATH,
                            Map.of());
                }
                byte[] metadataBytes;
                try (var input = jarFile.getInputStream(metadataEntry)) {
                    metadataBytes = input.readNBytes((int) ProviderSdk.MAX_METADATA_BYTES + 1);
                }
                if (metadataBytes.length > ProviderSdk.MAX_METADATA_BYTES) {
                    return invalid(
                            jar,
                            "PLUGIN_METADATA_TOO_LARGE",
                            "Provider plugin metadata exceeds the configured size limit",
                            Map.of("limitBytes", Long.toString(ProviderSdk.MAX_METADATA_BYTES)));
                }

                Properties properties = new Properties();
                try (var reader = new java.io.StringReader(new String(metadataBytes, StandardCharsets.UTF_8))) {
                    properties.load(reader);
                }
                ProviderPluginMetadata metadata = ProviderPluginMetadata.from(properties);
                ProviderPluginCompatibility.Result result = compatibility.evaluate(metadata);
                return new ProviderPluginCandidate(
                        jar,
                        java.util.Optional.of(metadata),
                        result.compatible() ? ProviderPluginStatus.COMPATIBLE : ProviderPluginStatus.INCOMPATIBLE,
                        result.diagnostics());
            }
        } catch (IOException | IllegalArgumentException failure) {
            return invalid(
                    jar,
                    "PLUGIN_METADATA_INVALID",
                    "Provider plugin JAR metadata cannot be read or validated",
                    Map.of("reason", safeMessage(failure)));
        }
    }

    private boolean isJar(Path path) {
        return path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar");
    }

    private ProviderPluginCandidate invalid(Path jar, String code, String message, Map<String, String> details) {
        return new ProviderPluginCandidate(
                jar,
                java.util.Optional.empty(),
                ProviderPluginStatus.INVALID,
                List.of(ProviderPluginDiagnostic.error(code, message, details)));
    }

    private static String safeMessage(Exception failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
    }
}
