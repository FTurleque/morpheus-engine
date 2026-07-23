package com.morpheus.architecture;

import com.morpheus.application.query.compact.CanonicalJsonSerializer;
import com.morpheus.application.query.compact.CompactQueryTypes.WarningView;
import com.morpheus.application.query.compact.CompactWarningCode;
import com.morpheus.domain.diagnostic.DiagnosticSeverity;
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
    void serializesPublicCompactRecordsInDeclarationOrderAndMapsByLexicographicKey() {
        LinkedHashMap<String, String> details = new LinkedHashMap<>();
        details.put("zeta", "last");
        details.put("alpha", "first");
        WarningView warning = new WarningView(
                CompactWarningCode.CHANGE_NOT_FOUND,
                DiagnosticSeverity.WARNING,
                "value",
                details);

        String json = serializer.toJson(warning);

        assertEquals(
                "{\"code\":\"CHANGE_NOT_FOUND\",\"severity\":\"WARNING\",\"message\":\"value\",\"details\":{\"alpha\":\"first\",\"zeta\":\"last\"}}",
                json);
        assertArrayEquals(json.getBytes(StandardCharsets.UTF_8), serializer.toUtf8(warning));
        assertEquals(json, serializer.toJson(warning));
        assertEquals("null", serializer.toJson(Optional.empty()));
        assertEquals("[2,1]", serializer.toJson(List.of(2, 1)));
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
}
