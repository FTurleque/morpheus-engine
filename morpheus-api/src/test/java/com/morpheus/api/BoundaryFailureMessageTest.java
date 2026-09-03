package com.morpheus.api;

import org.junit.jupiter.api.Test;

import java.nio.file.AccessDeniedException;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * What a caller outside the machine is allowed to read from a failure MORPHEUS did not author for them.
 *
 * <p>Both HTTP servers relay the message of an unexpected failure, and the platform writes the pathname into
 * it. The decision is shared so the two servers cannot drift, and the weaker copy is always the one that leaks.
 * </p>
 */
class BoundaryFailureMessageTest {

    @Test
    void aMessageThatNamesNoLocationIsRelayedUnchanged() {
        assertEquals(
                "maxAgeMinutes must be between 1 and 43200",
                BoundaryFailureMessage.safe(new IllegalArgumentException("maxAgeMinutes must be between 1 and 43200")));
    }

    @Test
    void aMessageNamingAPosixPathnameIsReplacedByTheFailureType() {
        assertEquals(
                "AccessDeniedException",
                BoundaryFailureMessage.safe(
                        new AccessDeniedException("/srv/morpheus/private/workspace/classified/spec.md")));
    }

    @Test
    void aMessageNamingAWindowsPathnameIsReplacedByTheFailureType() {
        assertEquals(
                "IllegalStateException",
                BoundaryFailureMessage.safe(
                        new IllegalStateException("cannot read C:\\secret\\server\\workspace\\spec.md")));
    }

    @Test
    void anAbsentOrBlankMessageFallsBackToTheFailureType() {
        assertEquals("SQLException", BoundaryFailureMessage.safe(new SQLException()));
        assertEquals("IllegalStateException", BoundaryFailureMessage.safe(new IllegalStateException("   ")));
    }
}
