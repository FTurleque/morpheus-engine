package com.morpheus.application.security;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.attribute.GroupPrincipal;
import java.nio.file.attribute.UserPrincipal;
import java.nio.file.attribute.UserPrincipalLookupService;
import java.nio.file.attribute.UserPrincipalNotFoundException;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    @Test
    void resolvesWindowsTrustByLookupIdentityWithoutDependingOnHostPlatform() {
        Set<UserPrincipal> resolved = LocalWritePermissionHardener.resolveTrustedWindowsPrincipals(
                new SuccessfulLookupService(), true);

        assertEquals(8, resolved.size());
        assertTrue(resolved.stream().anyMatch(principal -> principal.getName().equals("NT AUTHORITY\\SYSTEM")));
        assertTrue(resolved.stream().anyMatch(principal -> principal.getName().equals("BUILTIN\\Administrators")));
        assertTrue(LocalWritePermissionHardener.resolveTrustedWindowsPrincipals(
                new SuccessfulLookupService(), false).isEmpty());
    }

    @Test
    void unresolvedWindowsAliasesRemainUntrusted() {
        assertTrue(LocalWritePermissionHardener.resolveTrustedWindowsPrincipals(
                new RejectingLookupService(), true).isEmpty());
    }

    private static class NamedPrincipal implements UserPrincipal {
        private final String name;

        private NamedPrincipal(String name) {
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }
    }

    private static final class NamedGroupPrincipal extends NamedPrincipal implements GroupPrincipal {
        private NamedGroupPrincipal(String name) {
            super(name);
        }
    }

    private static final class SuccessfulLookupService extends UserPrincipalLookupService {
        @Override
        public UserPrincipal lookupPrincipalByName(String name) {
            return new NamedPrincipal(name);
        }

        @Override
        public GroupPrincipal lookupPrincipalByGroupName(String group) {
            return new NamedGroupPrincipal(group);
        }
    }

    private static final class RejectingLookupService extends UserPrincipalLookupService {
        @Override
        public UserPrincipal lookupPrincipalByName(String name) throws IOException {
            throw new UserPrincipalNotFoundException(name);
        }

        @Override
        public GroupPrincipal lookupPrincipalByGroupName(String group) throws IOException {
            throw new UserPrincipalNotFoundException(group);
        }
    }
}
