package com.morpheus.application.security;

import org.junit.jupiter.api.Test;

import java.nio.file.attribute.UserPrincipal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalWritePermissionPrincipalIdentityTest {

    @Test
    void sameDisplayNameDoesNotMakeTwoDifferentPrincipalsTrusted() {
        UserPrincipal first = new NamedPrincipal("BUILTIN\\Administrators");
        UserPrincipal lookalike = new NamedPrincipal("BUILTIN\\Administrators");

        assertFalse(LocalWritePermissionHardener.samePrincipalIdentity(first, lookalike));
        assertTrue(LocalWritePermissionHardener.samePrincipalIdentity(first, first));
    }

    private static final class NamedPrincipal implements UserPrincipal {
        private final String name;

        private NamedPrincipal(String name) {
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }
    }
}
