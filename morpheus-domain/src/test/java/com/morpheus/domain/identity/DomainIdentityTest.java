package com.morpheus.domain.identity;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

        assertThrows(IllegalArgumentException.class, () -> new DomainIdentity(uuidV4));
    }

    @Test
    void generatedIdentitiesRemainOpaqueAndDistinct() {
        DomainIdentity left = DomainIdentity.generate();
        DomainIdentity right = DomainIdentity.generate();

        assertNotEquals(left, right);
    }
}
