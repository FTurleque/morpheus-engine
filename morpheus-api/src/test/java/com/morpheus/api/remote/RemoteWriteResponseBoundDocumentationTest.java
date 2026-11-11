package com.morpheus.api.remote;

import com.morpheus.api.RepositoryRoot;
import com.morpheus.api.transport.MorpheusHttpRouteTable;
import com.morpheus.api.transport.TimedBoundedResponseWriter;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The bound a client of a relayed write is told is the one the constants give.
 *
 * <p>The facade starts its answer to a write, the upstream response or a refusal, at the latest at the deadline of its
 * call, counted from the moment the call is sent upstream, and ends it at the latest one total response-write budget
 * later. Both values are computed here from {@link MorpheusRemoteProxyTransport.UpstreamDeadline#WRITE} and
 * {@link TimedBoundedResponseWriter#RESPONSE_TOTAL_TIMEOUT}: a page that states another bound, or none, fails, so a
 * change of either constant cannot leave the documentation behind.</p>
 *
 * <p>The remote OpenAPI declares the refusal of unknown outcome on exactly the operations the facade relays on the
 * writes' deadline, read from {@link MorpheusRemoteRoutePolicy} over the whole route table: a write route added
 * without it, or a read route given it, fails.</p>
 */
class RemoteWriteResponseBoundDocumentationTest {
    private static final String REMOTE_OPENAPI = "docs/developer/reference/openapi/morpheus-v1-remote-m26.yaml";
    private static final String UNKNOWN_OUTCOME = "RelayedWriteOutcomeUnknown";
    private static final Pattern PATH_LINE = Pattern.compile("^  (/\\S*):\\s*$");
    private static final Pattern METHOD_LINE = Pattern.compile("^    (get|post|put|delete|patch|head):\\s*$");
    /** Routes on the writes' deadline that the facade answers itself, with its own timeout, instead of relaying them. */
    private static final Map<String, String> SERVED_BY_THE_FACADE = Map.of("server/backups", "POST");
    private static final long START_SECONDS = MorpheusRemoteProxyTransport.UpstreamDeadline.WRITE.timeout().toSeconds();
    private static final long END_SECONDS =
            START_SECONDS + TimedBoundedResponseWriter.RESPONSE_TOTAL_TIMEOUT.toSeconds();

    @Test
    void theDeveloperGuideStatesTheComposedBoundOfARelayedWrite() throws IOException {
        String page = read("docs/developer/REMOTE_SERVER_PLATFORM.md");
        String bound = "commence au plus tard " + START_SECONDS + " s après l'envoi amont et se termine au plus tard "
                + END_SECONDS + " s après cet envoi";

        assertTrue(page.contains(bound), () -> "the developer guide must state: " + bound);
    }

    @Test
    void theUserGuideStatesTheComposedBoundOfARelayedWrite() throws IOException {
        String page = read("docs/user/TEAM_REMOTE_SERVER.md");
        String bound = "commence au plus tard " + START_SECONDS + " secondes après son envoi au traitement interne et "
                + "se termine au plus tard " + END_SECONDS + " secondes après cet envoi";

        assertTrue(page.contains(bound), () -> "the user guide must state: " + bound);
    }

    @Test
    void theRemoteOpenApiDeclaresTheUnknownOutcomeOnExactlyTheOperationsRelayedOnTheWritesDeadline()
            throws IOException {
        String openApi = read(REMOTE_OPENAPI);

        assertEquals(relayedOnTheWritesDeadline(), operationsDeclaringTheUnknownOutcome(openApi),
                "the remote OpenAPI must declare " + UNKNOWN_OUTCOME + " on every operation relayed on the writes' "
                        + "deadline, and on no other");
        Matcher component = Pattern.compile("(?m)^    " + UNKNOWN_OUTCOME + ":\\R((?:      .*\\R|\\R)+)")
                .matcher(openApi);
        assertTrue(component.find(), () -> "the remote OpenAPI must define the response " + UNKNOWN_OUTCOME);
        String described = component.group(1).replaceAll("\\s+", " ");
        for (String code : Set.of(
                "UPSTREAM_WRITE_TIMEOUT", "RESPONSE_BUDGET_WRITE_TIMEOUT", "read the state before retrying")) {
            assertTrue(described.contains(code), () -> UNKNOWN_OUTCOME + " must name " + code + ": " + described);
        }
    }

    /** Every route and method the facade relays on the writes' deadline, as {@code METHOD /template}. */
    private static Set<String> relayedOnTheWritesDeadline() {
        Set<String> relayed = new TreeSet<>();
        MorpheusHttpRouteTable.templates().forEach((template, methods) -> methods.forEach(method -> {
            MorpheusRemoteRoutePolicy.Authorization route =
                    MorpheusRemoteRoutePolicy.authorize(method, MorpheusHttpRouteTable.API_PREFIX + "/"
                            + template.replaceAll("\\{[^}/]+}", "x"));
            assertEquals(template, route.template(), "the sample path must name its own route");
            if (!route.upstreamDeadline().readOnly() && !SERVED_BY_THE_FACADE.containsKey(template)) {
                relayed.add(method + " " + normalized("/" + template));
            }
        }));
        SERVED_BY_THE_FACADE.forEach((template, method) -> assertTrue(
                MorpheusHttpRouteTable.templates().getOrDefault(template, Set.of()).contains(method)
                        && !MorpheusRemoteRoutePolicy.authorize(
                                method, MorpheusHttpRouteTable.API_PREFIX + "/" + template).upstreamDeadline().readOnly(),
                () -> method + " " + template + " is no longer a route on the writes' deadline: drop its exclusion"));
        return relayed;
    }

    /** Every operation of the document whose {@code 504} is the unknown-outcome response, as {@code METHOD /path}. */
    private static Set<String> operationsDeclaringTheUnknownOutcome(String openApi) {
        Set<String> declaring = new TreeSet<>();
        String path = null;
        String method = null;
        boolean in504 = false;
        for (String line : openApi.split("\\R")) {
            Matcher pathLine = PATH_LINE.matcher(line);
            Matcher methodLine = METHOD_LINE.matcher(line);
            if (line.startsWith("components:")) {
                break;
            } else if (pathLine.matches()) {
                path = normalized(pathLine.group(1));
                method = null;
            } else if (methodLine.matches()) {
                method = methodLine.group(1).toUpperCase(Locale.ROOT);
                in504 = false;
            } else if (line.startsWith("        '")) {
                in504 = line.startsWith("        '504':");
            }
            if (in504 && method != null && line.contains("#/components/responses/" + UNKNOWN_OUTCOME + "'")) {
                declaring.add(method + " " + path);
            }
        }
        return declaring;
    }

    private static String normalized(String path) {
        return path.replaceAll("\\{[^}/]+}", "{}");
    }

    private static String read(String relative) throws IOException {
        Path page = RepositoryRoot.locate().resolve(relative);
        return Files.readString(page);
    }
}
