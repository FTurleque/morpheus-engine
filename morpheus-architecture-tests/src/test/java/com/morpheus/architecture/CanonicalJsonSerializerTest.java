package com.morpheus.architecture;

import com.morpheus.application.query.compact.CanonicalJsonSerializer;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CanonicalJsonSerializerTest {
    private final CanonicalJsonSerializer serializer = new CanonicalJsonSerializer();

    @Test
    void serializesRecordsInDeclarationOrderAndMapsByLexicographicKey() {
        LinkedHashMap<String, String> details = new LinkedHashMap<>();
        details.put("zeta", "last");
        details.put("alpha", "first");
        Sample sample = new Sample("value", details, Optional.empty(), List.of(2, 1));

        String json = serializer.toJson(sample);

        assertEquals(
                "{\"name\":\"value\",\"details\":{\"alpha\":\"first\",\"zeta\":\"last\"},\"missing\":null,\"numbers\":[2,1]}",
                json);
        assertArrayEquals(json.getBytes(StandardCharsets.UTF_8), serializer.toUtf8(sample));
        assertEquals(json, serializer.toJson(sample));
    }

    @Test
    void escapesJsonStringsDeterministicallyIncludingControlCharactersAndSurrogates() {
        String value = "quote\" slash\\ line\n tab\t ctrl\u0001 emoji🙂";

        assertEquals(
                "\"quote\\\" slash\\\\ line\\n tab\\t ctrl\\u0001 emoji\\uD83D\\uDE42\"",
                serializer.toJson(value));
    }

    @Test
    void rejectsUnsupportedTypesNonStringMapKeysAndNonFiniteNumbers() {
        assertThrows(IllegalArgumentException.class, () -> serializer.toJson(new Object()));
        assertThrows(IllegalArgumentException.class, () -> serializer.toJson(Map.of(1, "value")));
        assertThrows(IllegalArgumentException.class, () -> serializer.toJson(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> serializer.toJson(Float.POSITIVE_INFINITY));
    }

    private record Sample(
            String name,
            Map<String, String> details,
            Optional<String> missing,
            List<Integer> numbers) {
    }
}
