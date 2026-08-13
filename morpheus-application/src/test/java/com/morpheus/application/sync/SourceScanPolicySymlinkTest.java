package com.morpheus.application.sync;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SourceScanPolicySymlinkTest {
    @Test
    void symbolicLinkTraversalCannotBeEnabled() {
        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> new SourceScanPolicy(Set.of(), true));

        assertTrue(failure.getMessage().contains("symbolic-link traversal is not supported"));
    }
}
