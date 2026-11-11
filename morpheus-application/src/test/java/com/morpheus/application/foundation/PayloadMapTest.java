package com.morpheus.application.foundation;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PayloadMapTest {

    @Test
    void anOddNumberOfArgumentsIsRefusedByName() {
        IllegalArgumentException refusal = assertThrows(IllegalArgumentException.class,
                () -> PayloadMap.of("id", "a", "title"));

        assertEquals("payload map entries must be key/value pairs; received 3 arguments", refusal.getMessage());
    }

    @Test
    void anOddNumberOfArgumentsIsRefusedByNameWhenAbsentValuesAreAdmitted() {
        IllegalArgumentException refusal = assertThrows(IllegalArgumentException.class,
                () -> PayloadMap.admittingAbsentValues("source"));

        assertEquals("payload map entries must be key/value pairs; received 1 arguments", refusal.getMessage());
    }

    @Test
    void aKeyThatIsNotAStringIsRefusedWithItsPositionAndTypeRatherThanAClassCast() {
        IllegalArgumentException refusal = assertThrows(IllegalArgumentException.class,
                () -> PayloadMap.of("id", "a", 7, "seven"));

        assertEquals("payload map key at position 2 must be a String; received java.lang.Integer",
                refusal.getMessage());
    }

    @Test
    void anAbsentKeyIsRefusedWithItsPosition() {
        IllegalArgumentException refusal = assertThrows(IllegalArgumentException.class,
                () -> PayloadMap.admittingAbsentValues(null, "value"));

        assertEquals("payload map key at position 0 must be a String; received null", refusal.getMessage());
    }

    @Test
    void aKeyGivenTwiceIsRefusedWithTheKeyAndNeitherValueIsKept() {
        IllegalArgumentException refusal = assertThrows(IllegalArgumentException.class,
                () -> PayloadMap.of("id", "first", "title", "t", "id", "second"));

        assertEquals("payload map key given twice: id", refusal.getMessage());
    }

    @Test
    void theStrictConstructionRefusesAnAbsentValueWithItsKey() {
        IllegalArgumentException refusal = assertThrows(IllegalArgumentException.class,
                () -> PayloadMap.of("id", "a", "source", null));

        assertEquals("payload map value of key source must be present", refusal.getMessage());
    }

    @Test
    void thePermissiveConstructionKeepsAnAbsentValueUnderItsKey() {
        Map<String, Object> payload = PayloadMap.admittingAbsentValues("source", null, "target", "t");

        assertTrue(payload.containsKey("source"));
        assertNull(payload.get("source"));
        assertEquals("t", payload.get("target"));
        assertEquals(2, payload.size());
    }

    @Test
    void entriesIterateInTheOrderGiven() {
        List<String> keys = List.of("zeta", "alpha", "mu", "beta", "omega", "delta", "kappa", "epsilon");
        List<Object> entries = new ArrayList<>();
        for (String key : keys) {
            entries.add(key);
            entries.add(key.length());
        }

        assertEquals(keys, List.copyOf(PayloadMap.of(entries.toArray()).keySet()));
        assertEquals(keys, List.copyOf(PayloadMap.admittingAbsentValues(entries.toArray()).keySet()));
    }

    @Test
    void theBuiltMapRefusesModification() {
        Map<String, Object> payload = PayloadMap.of("id", "a");

        UnsupportedOperationException put = assertThrows(UnsupportedOperationException.class,
                () -> payload.put("title", "t"));
        UnsupportedOperationException remove = assertThrows(UnsupportedOperationException.class,
                () -> payload.remove("id"));

        assertEquals("java.util.Collections$UnmodifiableMap", put.getStackTrace()[0].getClassName());
        assertEquals("java.util.Collections$UnmodifiableMap", remove.getStackTrace()[0].getClassName());
        assertEquals(Map.of("id", "a"), payload);
    }

    @Test
    void theBuiltMapDoesNotFollowTheArgumentArray() {
        Object[] entries = {"id", "a"};
        Map<String, Object> payload = PayloadMap.of(entries);
        entries[1] = "changed";

        assertEquals("a", payload.get("id"));
    }

    @Test
    void noArgumentsBuildAnEmptyPayload() {
        assertEquals(new HashMap<>(), PayloadMap.of());
    }
}
