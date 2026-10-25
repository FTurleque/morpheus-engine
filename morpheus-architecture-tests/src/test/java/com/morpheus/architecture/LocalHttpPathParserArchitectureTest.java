package com.morpheus.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalHttpPathParserArchitectureTest {

    @Test
    void localHttpPathParsingStaysExtractedAndDoesNotAbsorbAllowPolicy() throws IOException {
        String server = Files.readString(ProductionSources.mainSource("morpheus-api", "MorpheusHttpServer"));
        String parser = Files.readString(ProductionSources.mainSource("morpheus-api", "MorpheusHttpPathParser"));

        assertTrue(server.contains("private final MorpheusHttpPathParser pathParser"));
        assertTrue(server.contains("return pathParser.segments(path);"));
        assertFalse(server.contains("URLDecoder"));
        assertFalse(server.contains("StandardCharsets"));
        assertFalse(server.contains("new ArrayList"));
        assertFalse(server.contains("invalid API path"));
        assertFalse(server.contains("String suffix = path.substring(API_PREFIX.length())"));

        assertTrue(parser.contains("final class MorpheusHttpPathParser"));
        assertTrue(parser.contains("List<String> segments(String rawPath)"));
        assertFalse(parser.contains("URLDecoder"));
        assertTrue(parser.contains("invalid API path"));
        assertFalse(parser.contains("allowedMethods"));
        assertFalse(parser.contains("MorpheusHttpAllowedMethods"));
        assertFalse(parser.contains("HttpExchange"));
        assertFalse(parser.contains("MorpheusApiService"));
        assertFalse(parser.contains("MorpheusRemoteRoutePolicy"));
        assertFalse(parser.contains("MorpheusHttpQuery"));
    }
}
