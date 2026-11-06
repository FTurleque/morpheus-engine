package com.morpheus.api.remote;

import com.morpheus.api.RepositoryRoot;
import com.morpheus.api.transport.TimedBoundedResponseWriter;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The bound a client of a relayed write is told is the one the constants give.
 *
 * <p>The facade starts its answer to a write, the upstream response or a refusal, at the latest at the deadline of its
 * call, counted from the moment the call is sent upstream, and ends it at the latest one total response-write budget
 * later. Both values are computed here from {@link MorpheusRemoteProxyTransport.UpstreamDeadline#WRITE} and
 * {@link TimedBoundedResponseWriter#RESPONSE_TOTAL_TIMEOUT}: a page that states another bound, or none, fails, so a
 * change of either constant cannot leave the documentation behind.</p>
 */
class RemoteWriteResponseBoundDocumentationTest {
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

    private static String read(String relative) throws IOException {
        Path page = RepositoryRoot.locate().resolve(relative);
        return Files.readString(page);
    }
}
