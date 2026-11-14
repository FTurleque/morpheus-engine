package com.morpheus.domain.identity;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DomainIdentityTest {

    @Test
    void generatesRfc9562UuidV7() {
        DomainIdentity identity = DomainIdentity.generate();

        assertEquals(7, identity.value().version());
        assertEquals(2, identity.value().variant());
    }

    @Test
    void canonicalStringRoundTripsWithoutChangingIdentity() {
        DomainIdentity identity = DomainIdentity.generate();

        assertEquals(identity, DomainIdentity.parse(identity.toString()));
    }

    @Test
    void rejectsNonUuidV7Values() {
        UUID uuidV4 = UUID.randomUUID();

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, () -> new DomainIdentity(uuidV4));
        assertEquals("DomainIdentity must be an RFC 9562 UUIDv7", failure.getMessage());
    }

    @Test
    void generatedIdentitiesRemainOpaqueAndDistinct() {
        DomainIdentity left = DomainIdentity.generate();
        DomainIdentity right = DomainIdentity.generate();

        assertNotEquals(left, right);
    }

    @Test
    void identitiesGeneratedInOneMillisecondCompareInTheOrderOfGeneration() {
        List<DomainIdentity> generated = new ArrayList<>();
        for (int i = 0; i < 4_096; i++) {
            generated.add(DomainIdentity.generate());
        }

        int sameMillisecondPairs = 0;
        for (int i = 1; i < generated.size(); i++) {
            DomainIdentity earlier = generated.get(i - 1);
            DomainIdentity later = generated.get(i);
            if (unixMillis(earlier) == unixMillis(later)) {
                sameMillisecondPairs++;
            }
            assertTrue(earlier.compareTo(later) < 0,
                    "identity " + i + " (" + later + ") must sort after identity " + (i - 1) + " (" + earlier + ")");
            assertTrue(earlier.toString().compareTo(later.toString()) < 0,
                    "the text of identity " + i + " must sort after the text of identity " + (i - 1));
        }
        assertTrue(sameMillisecondPairs > 0, "the loop must generate two identities within one millisecond");
    }

    private static long unixMillis(DomainIdentity identity) {
        return identity.value().getMostSignificantBits() >>> 16;
    }
}
