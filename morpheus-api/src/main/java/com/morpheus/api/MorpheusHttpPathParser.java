package com.morpheus.api;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Turns the path of a request, as it was sent, into the decoded segments below the API prefix.
 *
 * <p>The only reader of a request path in this adapter: the local server, the routers that register their own context
 * and the remote role policy all obtain their segments here, so the code that authorizes a request and the code that
 * routes it cannot read one path two ways. The path is split first and each segment is decoded once afterwards: an
 * encoded {@code /} stays inside its segment, a doubly encoded character stays encoded once, and {@code +} is a plus
 * sign -- it means a space in a form, not in a path.</p>
 */
public final class MorpheusHttpPathParser {
    private static final MorpheusHttpPathParser API = new MorpheusHttpPathParser(MorpheusHttpRouteTable.API_PREFIX);

    private final List<String> prefixSegments;

    private MorpheusHttpPathParser(String apiPrefix) {
        this.prefixSegments = List.of(apiPrefix.substring(1).split("/"));
    }

    /** The one parser of the adapter: every reader of a path shares it, so none can read under another prefix. */
    public static MorpheusHttpPathParser api() {
        return API;
    }

    public List<String> segments(String rawPath) {
        if (!rawPath.startsWith("/")) throw ApiFailure.notFound("unknown API route: " + rawPath);
        String body = rawPath.substring(1);
        if (body.endsWith("/")) body = body.substring(0, body.length() - 1);
        List<String> decoded = new ArrayList<>();
        for (String segment : body.split("/", -1)) {
            if (segment.isEmpty()) throw ApiFailure.notFound("invalid API path");
            decoded.add(decodeOnce(segment));
        }
        if (decoded.size() < prefixSegments.size()
                || !decoded.subList(0, prefixSegments.size()).equals(prefixSegments)) {
            throw ApiFailure.notFound("unknown API route: " + rawPath);
        }
        return List.copyOf(decoded.subList(prefixSegments.size(), decoded.size()));
    }

    /**
     * The segments of a path this parser accepts, or nothing when it refuses the path. For a caller that asks whether
     * a path names anything, where the refusal is an answer and not a failure.
     */
    Optional<List<String>> segmentsIfRoutable(String rawPath) {
        try {
            return Optional.of(segments(rawPath));
        } catch (ApiFailure refusedPath) {
            return Optional.empty();
        }
    }

    private static String decodeOnce(String segment) {
        if (segment.indexOf('%') < 0) return segment;
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(segment.length());
        int index = 0;
        while (index < segment.length()) {
            if (segment.charAt(index) == '%') {
                bytes.write(escapedByte(segment, index));
                index += 3;
            } else {
                int end = segment.indexOf('%', index);
                if (end < 0) end = segment.length();
                bytes.writeBytes(segment.substring(index, end).getBytes(StandardCharsets.UTF_8));
                index = end;
            }
        }
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes.toByteArray()))
                    .toString();
        } catch (CharacterCodingException malformed) {
            throw ApiFailure.notFound("invalid API path");
        }
    }

    private static int escapedByte(String segment, int percent) {
        if (percent + 2 >= segment.length()) throw ApiFailure.notFound("invalid API path");
        int high = hexDigit(segment.charAt(percent + 1));
        int low = hexDigit(segment.charAt(percent + 2));
        if (high < 0 || low < 0) throw ApiFailure.notFound("invalid API path");
        return (high << 4) | low;
    }

    private static int hexDigit(char character) {
        if (character >= '0' && character <= '9') return character - '0';
        if (character >= 'a' && character <= 'f') return character - 'a' + 10;
        if (character >= 'A' && character <= 'F') return character - 'A' + 10;
        return -1;
    }
}
