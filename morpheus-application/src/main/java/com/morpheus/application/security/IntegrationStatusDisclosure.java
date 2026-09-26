package com.morpheus.application.security;

import com.morpheus.application.context.AugmentedContextResult;
import com.morpheus.application.context.TechnicalContextObservation;
import com.morpheus.application.reference.ExternalIntegrationStatus;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * The single projection of an optional-integration status onto a boundary a remote caller or a model can read.
 *
 * <p>The MINOS and NEXUS launch settings are server-configured: the caller cannot choose them and cannot act on
 * them, yet the status carries where the server keeps its JAR, its home directory and its JVM. That was
 * projected on {@code GET /api/v1/integrations/{system}/status} and relayed verbatim by the two augmented-context
 * HTTP routes and the two augmented-context MCP tools, which carry the very same status object. Every surface that
 * lets a status cross such a boundary goes through here, so there is one predicate and one key vocabulary.</p>
 *
 * <p>A location is reported as configured rather than named, so an operator reading the response still learns why
 * an integration is unavailable. The CLI keeps the full settings, which is where an operator fixes them, and does
 * not call this class.</p>
 *
 * <p>The two integrations name the same settings differently ({@code jarPath}/{@code homeDirectory} for MINOS,
 * {@code jar}/{@code home} for NEXUS). The aliases are folded onto one vocabulary <em>before</em> the value is
 * examined: a key this class does not recognise is treated as a free-text detail and dropped when its value looks
 * like a pathname, which erased the NEXUS settings entirely and left a caller unable to tell "no JAR configured"
 * from "the API removed the line".</p>
 */
public final class IntegrationStatusDisclosure {
    private static final String JAR = "jarPath";
    private static final String HOME = "homeDirectory";
    private static final String JAVA = "javaCommand";

    /** Detail keys whose values are server filesystem locations, mapped onto their canonical name. */
    private static final Map<String, String> LOCATION_KEYS = Map.of(
            JAR, JAR,
            "jar", JAR,
            HOME, HOME,
            "home", HOME,
            JAVA, JAVA);

    private IntegrationStatusDisclosure() {
    }

    public static ExternalIntegrationStatus project(ExternalIntegrationStatus status) {
        Objects.requireNonNull(status, "status");
        return new ExternalIntegrationStatus(
                status.system(), status.state(), status.configured(), message(status), details(status.details()));
    }

    public static TechnicalContextObservation project(TechnicalContextObservation observation) {
        Objects.requireNonNull(observation, "observation");
        return new TechnicalContextObservation(project(observation.status()), observation.bundle());
    }

    public static AugmentedContextResult project(AugmentedContextResult result) {
        Objects.requireNonNull(result, "result");
        return new AugmentedContextResult(
                result.snapshot(), result.intentContext(), project(result.technicalContext()), result.persisted());
    }

    /**
     * An {@code UNAVAILABLE} status carries the launch failure's own message, which for a process or filesystem
     * failure names the executable or directory it could not reach. The state already says the integration is
     * unreachable, so a message that locates the server is replaced rather than relayed.
     */
    private static String message(ExternalIntegrationStatus status) {
        return ServerLocationDisclosure.namesAServerLocation(status.message())
                ? status.system() + " integration is not reachable; see the local CLI for the launch failure detail"
                : status.message();
    }

    private static Map<String, String> details(Map<String, String> details) {
        Map<String, String> projected = new LinkedHashMap<>();
        details.forEach((key, value) -> {
            String location = LOCATION_KEYS.get(key);
            if (location != null) {
                projected.put(location + "Configured", Boolean.toString(!value.isBlank()));
            } else if (!ServerLocationDisclosure.namesAServerLocation(value)) {
                projected.put(key, value);
            }
        });
        return Map.copyOf(projected);
    }
}
