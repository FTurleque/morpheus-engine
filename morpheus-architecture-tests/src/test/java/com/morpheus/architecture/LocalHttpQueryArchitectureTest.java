package com.morpheus.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalHttpQueryArchitectureTest {

    @Test
    void localHttpQueryParsingStaysExtractedAndRouteAgnostic() throws IOException {
        Path root = repositoryRoot();
        String server = Files.readString(root.resolve(
                "morpheus-api/src/main/java/com/morpheus/api/MorpheusHttpServer.java"));
        String query = Files.readString(root.resolve(
                "morpheus-api/src/main/java/com/morpheus/api/MorpheusHttpQuery.java"));

        assertTrue(server.contains("MorpheusHttpQuery query = MorpheusHttpQuery.parse("));
        assertFalse(server.contains("private record Query("));
        assertFalse(server.contains("new LinkedHashMap"));
        assertFalse(server.contains("duplicate query parameter:"));
        assertFalse(server.contains("query parameter name must not be blank"));
        assertFalse(server.contains("unknown query parameter:"));

        assertTrue(query.contains("final class MorpheusHttpQuery"));
        assertTrue(query.contains("static MorpheusHttpQuery parse(String rawQuery)"));
        assertTrue(query.contains("int intValue("));
        assertTrue(query.contains("long longValue("));
        assertTrue(query.contains("void rejectUnknown("));
        assertTrue(query.contains("duplicate query parameter:"));
        assertFalse(query.contains("HttpExchange"));
        assertFalse(query.contains("MorpheusApiService"));
        assertFalse(query.contains("MorpheusRemoteRoutePolicy"));
    }

    private Path repositoryRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null) {
            if (Files.isRegularFile(current.resolve("contracts/public-surfaces.tsv"))
                    && Files.isRegularFile(current.resolve("pom.xml"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("cannot locate MORPHEUS repository root");
    }
}
