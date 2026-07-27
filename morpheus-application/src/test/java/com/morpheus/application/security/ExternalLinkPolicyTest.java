package com.morpheus.application.security;

import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExternalLinkPolicyTest {

    @Test
    void safeDefaultsNeverFollowNetworkLinksImplicitly() {
        ExternalLinkPolicy policy = ExternalLinkPolicy.safeDefaults();

        assertFalse(policy.followNetworkLinks());
        assertFalse(policy.mayFollow(URI.create("https://example.invalid/spec.md")));
        assertFalse(policy.mayFollow(URI.create("http://example.invalid/spec.md")));
        assertFalse(policy.mayFollow(URI.create("ftp://example.invalid/spec.md")));
        assertTrue(policy.mayFollow(URI.create("relative/spec.md")));
        assertTrue(policy.mayFollow(URI.create("file:///local/spec.md")));
    }
}
