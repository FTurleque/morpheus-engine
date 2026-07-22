package com.morpheus.application.identity;

/** Raised when one external identity key is claimed by two different MORPHEUS identities. */
public final class IdentityCollisionException extends RuntimeException {
    public IdentityCollisionException(String message) {
        super(message);
    }

    public IdentityCollisionException(String message, Throwable cause) {
        super(message, cause);
    }
}
