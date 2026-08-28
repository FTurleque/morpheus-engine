package com.morpheus.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalHttpPathParserArchitectureTest {

    @Test
    void localHttpPathParsingStaysExtractedAndMethodPolicyRemainsInFacade() throws IOException {
        Path root = repositoryRoot();
        String server = Files.readString(root.resolve(
                "morpheus-api/src/main/java/com/morpheus/api/MorpheusHttpServer.java"));
        String parser = Files.readString(root.resolve(
                "morpheus-api/src/main/java/com/morpheus/api/MorpheusHttpPathParser.java"));

        assertTrue(server.contains("private final MorpheusHttpPathParser pathParser"));
        assertTrue(server.contains("return pathParser.segments(path);"));
        assertTrue(server.contains("private String allowedMethods(String path)"));
        assertFalse(server.contains("URLDecoder"));
        assertFalse(server.contains("StandardCharsets"));
        assertFalse(server.contains("new ArrayList"));
        assertFalse(server.contains("invalid API path"));
        assertFalse(server.contains("String suffix = path.substring(API_PREFIX.length())"));

        assertTrue(parser.contains("final class MorpheusHttpPathParser"));
        assertTrue(parser.contains("List<String> segments(String path)"));
        assertTrue(parser.contains("URLDecoder.decode"));
        assertTrue(parser.contains("invalid API path"));
        assertFalse(parser.contains("allowedMethods"));
        assertFalse(parser.contains("HttpExchange"));
        assertFalse(parser.contains("MorpheusApiService"));
        assertFalse(parser.contains("MorpheusRemoteRoutePolicy"));
        assertFalse(parser.contains("MorpheusHttpQuery"));
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
