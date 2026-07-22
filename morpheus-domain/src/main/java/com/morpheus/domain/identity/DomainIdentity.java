package com.morpheus.domain.identity;

import java.security.SecureRandom;
import java.util.Objects;
import java.util.UUID;

/** Canonical opaque MORPHEUS identity encoded as RFC 9562 UUIDv7. */
public record DomainIdentity(UUID value) implements Comparable<DomainIdentity> {
    private static final SecureRandom RANDOM = new SecureRandom();

    public DomainIdentity {
        Objects.requireNonNull(value, "value");
        if (value.version() != 7 || value.variant() != 2) {
            throw new IllegalArgumentException("DomainIdentity must be an RFC 9562 UUIDv7");
        }
    }

    public static DomainIdentity generate() {
        long unixMillis = System.currentTimeMillis() & 0x0000FFFFFFFFFFFFL;
        long randomA = RANDOM.nextInt(1 << 12);
        long mostSignificantBits = (unixMillis << 16) | (0x7L << 12) | randomA;

        long randomB = RANDOM.nextLong() & 0x3FFFFFFFFFFFFFFFL;
        long leastSignificantBits = 0x8000000000000000L | randomB;

        return new DomainIdentity(new UUID(mostSignificantBits, leastSignificantBits));
    }

    public static DomainIdentity parse(String value) {
        Objects.requireNonNull(value, "value");
        return new DomainIdentity(UUID.fromString(value.trim()));
    }

    @Override
    public int compareTo(DomainIdentity other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
