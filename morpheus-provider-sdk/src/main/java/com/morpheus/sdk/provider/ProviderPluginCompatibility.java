package com.morpheus.sdk.provider;

import com.morpheus.application.product.ProductMetadata;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Deterministic compatibility evaluation. Compatibility never activates a plugin. */
public final class ProviderPluginCompatibility {

    public Result evaluate(ProviderPluginMetadata metadata) {
        return evaluate(metadata, ProductMetadata.version());
    }

    public Result evaluate(ProviderPluginMetadata metadata, String morpheusVersion) {
        Objects.requireNonNull(metadata, "metadata");
        String current = Objects.requireNonNull(morpheusVersion, "morpheusVersion").trim();
        List<ProviderPluginDiagnostic> diagnostics = new ArrayList<>();

        if (metadata.sdkApiVersion() != ProviderSdk.API_VERSION) {
            diagnostics.add(ProviderPluginDiagnostic.error(
                    "SDK_API_VERSION_MISMATCH",
                    "Plugin SDK API version is not supported by this MORPHEUS runtime",
                    Map.of(
                            "pluginSdkApiVersion", Integer.toString(metadata.sdkApiVersion()),
                            "runtimeSdkApiVersion", Integer.toString(ProviderSdk.API_VERSION))));
        }

        if (!ProductMetadata.DEVELOPMENT_VERSION.equals(current)) {
            SemanticVersion runtime = SemanticVersion.parse(current);
            SemanticVersion minimum = SemanticVersion.parse(metadata.minMorpheusVersion());
            if (runtime.compareTo(minimum) < 0) {
                diagnostics.add(ProviderPluginDiagnostic.error(
                        "MORPHEUS_VERSION_TOO_OLD",
                        "Plugin requires a newer MORPHEUS version",
                        Map.of("runtimeVersion", current, "minimumVersion", metadata.minMorpheusVersion())));
            }
            metadata.maxMorpheusVersion().ifPresent(maximumText -> {
                SemanticVersion maximum = SemanticVersion.parse(maximumText);
                if (runtime.compareTo(maximum) > 0) {
                    diagnostics.add(ProviderPluginDiagnostic.error(
                            "MORPHEUS_VERSION_TOO_NEW",
                            "Plugin does not declare compatibility with this MORPHEUS version",
                            Map.of("runtimeVersion", current, "maximumVersion", maximumText)));
                }
            });
        }

        return new Result(diagnostics.isEmpty(), List.copyOf(diagnostics));
    }

    public record Result(boolean compatible, List<ProviderPluginDiagnostic> diagnostics) {
        public Result {
            diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
            if (compatible && !diagnostics.isEmpty()) {
                throw new IllegalArgumentException("a compatible result cannot contain incompatibility diagnostics");
            }
        }
    }

    private record SemanticVersion(int major, int minor, int patch, String prerelease) implements Comparable<SemanticVersion> {
        static SemanticVersion parse(String raw) {
            String normalized = Objects.requireNonNull(raw, "version").trim();
            String withoutBuild = normalized.split("\\+", 2)[0];
            String[] releaseAndPre = withoutBuild.split("-", 2);
            String[] parts = releaseAndPre[0].split("\\.");
            if (parts.length != 3) {
                throw new IllegalArgumentException("unsupported semantic version: " + raw);
            }
            try {
                return new SemanticVersion(
                        Integer.parseInt(parts[0]),
                        Integer.parseInt(parts[1]),
                        Integer.parseInt(parts[2]),
                        releaseAndPre.length == 2 ? releaseAndPre[1] : "");
            } catch (NumberFormatException failure) {
                throw new IllegalArgumentException("unsupported semantic version: " + raw, failure);
            }
        }

        @Override
        public int compareTo(SemanticVersion other) {
            int result = Integer.compare(major, other.major);
            if (result != 0) {
                return result;
            }
            result = Integer.compare(minor, other.minor);
            if (result != 0) {
                return result;
            }
            result = Integer.compare(patch, other.patch);
            if (result != 0) {
                return result;
            }
            if (prerelease.isEmpty() && other.prerelease.isEmpty()) {
                return 0;
            }
            if (prerelease.isEmpty()) {
                return 1;
            }
            if (other.prerelease.isEmpty()) {
                return -1;
            }
            return prerelease.compareTo(other.prerelease);
        }
    }
}
