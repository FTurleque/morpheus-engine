package com.morpheus.api;

import com.morpheus.application.context.DisabledTechnicalContextProvider;
import com.morpheus.application.lifecycle.mutation.ChangeWriteCapabilityObservation;
import com.morpheus.application.reference.ExternalIntegrationStatus;
import com.morpheus.application.reference.ExternalReferenceResolverRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A 405 the remote facade decides itself -- before any proxying -- names in {@code Allow} exactly the methods the
 * route accepts (RFC 9110 section 15.5.6), as the proxied 405s of the local server do.
 */
class MorpheusRemoteMethodNotAllowedTest {
    private static final List<String> PROBE_METHODS = List.of("GET", "POST", "PUT", "DELETE");

    @TempDir
    Path temp;

    @Test
    void everyRouteRefusesAnUnacceptedMethodWithItsExactAllowHeader() throws Exception {
        Path auth = temp.resolve("remote-auth.txt");
        var admin = MorpheusRemoteIdentityFile.create(auth, "admin", MorpheusRemoteRole.ADMIN);
        Path keyStore = RemoteHttpTestSupport.createKeyStore(temp.resolve("remote.p12"));
        HttpClient client = RemoteHttpTestSupport.trustedClient(keyStore);
        List<String> problems = new ArrayList<>();

        try (MorpheusRemoteHttpServer server = MorpheusRemoteHttpServer.start(
                temp.resolve("morpheus.db"),
                temp.resolve("backups"),
                temp.resolve("provider-plugins"),
                AllowedWorkspaceRoots.of(List.of(Files.createDirectory(temp.resolve("workspaces")))),
                "127.0.0.1",
                0,
                auth,
                keyStore,
                RemoteHttpTestSupport.KEYSTORE_PASSWORD.toCharArray(),
                1,
                new ExternalReferenceResolverRegistry(List.of()),
                () -> new ExternalIntegrationStatus("MINOS", "DISABLED", false, "test", Map.of()),
                new DisabledTechnicalContextProvider("NEXUS", "test"),
                project -> ChangeWriteCapabilityObservation.denied("test"))) {
            for (Map.Entry<String, Set<String>> route : MorpheusHttpRouteTable.declaredRoutes().entrySet()) {
                String refused = PROBE_METHODS.stream().filter(method -> !route.getValue().contains(method))
                        .findFirst().orElseThrow();
                URI uri = URI.create("https://127.0.0.1:" + server.port()
                        + route.getKey().replaceAll("\\{[^}/]+}", "id-1"));
                HttpResponse<String> response = RemoteHttpTestSupport.send(client, uri, refused, admin.token(), null);
                String allow = response.headers().firstValue("Allow").orElse(null);
                if (response.statusCode() != 405) {
                    problems.add(refused + " " + route.getKey() + " -> " + response.statusCode() + ", expected 405");
                } else if (allow == null || !route.getValue().equals(methods(allow))) {
                    problems.add(refused + " " + route.getKey() + " -> Allow: " + allow + ", expected "
                            + route.getValue());
                }
            }
        }

        assertEquals(List.of(), problems);
    }

    private static Set<String> methods(String allow) {
        Set<String> methods = new TreeSet<>();
        for (String method : allow.split(",")) {
            if (!method.isBlank()) methods.add(method.trim().toUpperCase(Locale.ROOT));
        }
        return methods;
    }
}
