package com.morpheus.application.security;

import org.junit.jupiter.api.Test;

import java.nio.file.attribute.UserPrincipal;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalWritePermissionOwnerTrustTest {

    @Test
    void ownerTrustUsesPrincipalIdentityInsteadOfDisplayName() {
        UserPrincipal runtime = new NamedPrincipal("runtime-user");
        UserPrincipal runtimeLookalike = new NamedPrincipal("runtime-user");
        UserPrincipal trusted = new NamedPrincipal("trusted-service");
        UserPrincipal untrusted = new NamedPrincipal("other-user");

        assertTrue(LocalWritePermissionHardener.isTrustedOwnerIdentity(runtime, runtime, Set.of()));
        assertFalse(LocalWritePermissionHardener.isTrustedOwnerIdentity(runtimeLookalike, runtime, Set.of()));
        assertTrue(LocalWritePermissionHardener.isTrustedOwnerIdentity(trusted, runtime, Set.of(trusted)));
        assertFalse(LocalWritePermissionHardener.isTrustedOwnerIdentity(untrusted, runtime, Set.of(trusted)));
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
