package com.morpheus.api;

import com.morpheus.api.MorpheusRemoteIdentityFile.Identity;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertLinesMatch;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The identity grammar, exercised without a filesystem.
 *
 * <p>Reading and writing are the same contract seen from two sides, so both directions are checked against the same
 * structural rules here. What the codec refuses is what never reaches an authentication decision.</p>
 */
class RemoteIdentityCodecTest {
    private static final String HASH_A = "a".repeat(64);
    private static final String HASH_B = "b".repeat(64);

    @Test
    void anEmptyFileParsesToNoIdentitiesRatherThanFailing() {
        assertTrue(RemoteIdentityCodec.parse(List.of()).isEmpty());
        assertTrue(RemoteIdentityCodec.parse(List.of("", "   ", "# a comment")).isEmpty());
    }

    @Test
    void aThreeFieldEntryIsANonExpiringCredential() {
        List<Identity> identities = RemoteIdentityCodec.parse(List.of("ops|ADMIN|" + HASH_A));

        assertEquals(1, identities.size());
        assertEquals("ops", identities.get(0).principal());
        assertEquals(MorpheusRemoteRole.ADMIN, identities.get(0).role());
        assertEquals(Optional.empty(), identities.get(0).expiresAt());
    }

    @Test
    void aFourFieldEntryCarriesItsExpiry() {
        Instant expiry = Instant.parse("2030-01-01T00:00:00Z");

        List<Identity> identities = RemoteIdentityCodec.parse(List.of("ops|READ|" + HASH_A + "|" + expiry));

        assertEquals(Optional.of(expiry), identities.get(0).expiresAt());
    }

    @Test
    void anEntryWithTwoOrFiveFieldsIsRefusedByLineNumber() {
        assertTrue(refusal(List.of("# header", "ops|ADMIN"))
                .contains("invalid remote auth entry at line 2"));
        assertTrue(refusal(List.of("ops|ADMIN|" + HASH_A + "|2030-01-01T00:00:00Z|extra"))
                .contains("invalid remote auth entry at line 1"));
    }

    @Test
    void aPrincipalOutsideTheAllowedShapeIsRefused() {
        assertTrue(refusal(List.of("bad principal|ADMIN|" + HASH_A)).contains("principal must match"));
        assertTrue(refusal(List.of("|ADMIN|" + HASH_A)).contains("principal must match"));
        assertTrue(refusal(List.of("x".repeat(129) + "|ADMIN|" + HASH_A)).contains("principal must match"));
        assertThrows(IllegalArgumentException.class, () -> RemoteIdentityCodec.requirePrincipal(null));
    }

    @Test
    void aPrincipalIsAcceptedAtItsBoundaryAndTrimmed() {
        assertEquals("x".repeat(128), RemoteIdentityCodec.requirePrincipal("x".repeat(128)));
        assertEquals("ops", RemoteIdentityCodec.requirePrincipal("  ops  "));
    }

    @Test
    void anUnknownRoleIsRefusedRatherThanDowngraded() {
        assertTrue(refusal(List.of("ops|SUPERUSER|" + HASH_A)).contains("invalid remote role at line 1"));
        assertTrue(refusal(List.of("ops||" + HASH_A)).contains("invalid remote role at line 1"));
    }

    @Test
    void aVerifierThatIsNotALowercaseSha256IsRefused() {
        assertTrue(refusal(List.of("ops|READ|abc")).contains("invalid token SHA-256 at line 1"));
        assertTrue(refusal(List.of("ops|READ|" + "z".repeat(64))).contains("invalid token SHA-256 at line 1"));
        assertTrue(refusal(List.of("ops|READ|" + "a".repeat(63))).contains("invalid token SHA-256 at line 1"));
        assertTrue(refusal(List.of("ops|READ|" + "a".repeat(65))).contains("invalid token SHA-256 at line 1"));
    }

    /** An operator writing the verifier in upper case wrote the same verifier, and it stays one entry. */
    @Test
    void anUppercaseVerifierIsNormalisedRatherThanTreatedAsADifferentOne() {
        List<Identity> identities = RemoteIdentityCodec.parse(List.of("ops|READ|" + HASH_A.toUpperCase(Locale.ROOT)));

        assertEquals(HASH_A, HexFormat.of().formatHex(identities.get(0).tokenHash()));
        assertTrue(refusal(List.of(
                "first|READ|" + HASH_A,
                "second|READ|" + HASH_A.toUpperCase(Locale.ROOT)))
                .contains("duplicate remote token hash"));
    }

    @Test
    void anExpiryThatCarriesNoUsableInstantIsRefusedRatherThanReadAsPermanent() {
        assertTrue(refusal(List.of("ops|READ|" + HASH_A + "|")).contains("blank remote identity expiry at line 1"));
        assertTrue(refusal(List.of("ops|READ|" + HASH_A + "|   ")).contains("blank remote identity expiry at line 1"));
        assertTrue(refusal(List.of("ops|READ|" + HASH_A + "|not-an-instant"))
                .contains("invalid remote identity expiry at line 1"));
        assertTrue(refusal(List.of("ops|READ|" + HASH_A + "|2030-13-45"))
                .contains("invalid remote identity expiry at line 1"));
    }

    @Test
    void twoEntriesSharingAPrincipalOrAVerifierAreRefused() {
        assertTrue(refusal(List.of("ops|READ|" + HASH_A, "ops|ADMIN|" + HASH_B))
                .contains("duplicate remote principal: ops"));
        assertTrue(refusal(List.of("first|READ|" + HASH_A, "second|ADMIN|" + HASH_A))
                .contains("duplicate remote token hash"));
    }

    @Test
    void theIdentityCeilingIsEnforcedOnReadAndOnWrite() {
        List<String> lines = new ArrayList<>();
        for (int index = 0; index <= RemoteIdentityCodec.MAX_IDENTITIES; index++) {
            lines.add("p" + index + "|READ|" + verifier(index));
        }

        assertTrue(refusal(lines).contains("exceeds " + RemoteIdentityCodec.MAX_IDENTITIES + " identities"));

        List<Identity> tooMany = new ArrayList<>();
        for (int index = 0; index <= RemoteIdentityCodec.MAX_IDENTITIES; index++) {
            tooMany.add(new Identity("p" + index, MorpheusRemoteRole.READ, bytes(index)));
        }
        assertTrue(assertThrows(IllegalArgumentException.class, () -> RemoteIdentityCodec.format(tooMany))
                .getMessage().contains("exceeds " + RemoteIdentityCodec.MAX_IDENTITIES + " identities"));
    }

    @Test
    void exactlyTheCeilingIsAccepted() {
        List<String> lines = new ArrayList<>();
        for (int index = 0; index < RemoteIdentityCodec.MAX_IDENTITIES; index++) {
            lines.add("p" + index + "|READ|" + verifier(index));
        }

        assertEquals(RemoteIdentityCodec.MAX_IDENTITIES, RemoteIdentityCodec.parse(lines).size());
    }

    @Test
    void commentsAndBlankLinesDoNotShiftTheReportedLineNumber() {
        assertTrue(refusal(List.of("# header", "", "   ", "ops|NOPE|" + HASH_A))
                .contains("invalid remote role at line 4"));
    }

    @Test
    void formattingOrdersByPrincipalAndRoundTripsThroughParse() {
        Instant expiry = Instant.parse("2031-06-01T12:00:00Z");
        List<Identity> identities = List.of(
                new Identity("zulu", MorpheusRemoteRole.READ, bytes(2), Optional.of(expiry)),
                new Identity("alpha", MorpheusRemoteRole.ADMIN, bytes(1)));

        List<String> lines = RemoteIdentityCodec.format(identities);

        assertLinesMatch(List.of(
                RemoteIdentityCodec.HEADER,
                "alpha|ADMIN|" + verifier(1),
                "zulu|READ|" + verifier(2) + "|" + expiry), lines);
        assertEquals(
                List.of(identities.get(1), identities.get(0)),
                RemoteIdentityCodec.parse(lines));
    }

    @Test
    void formattingRefusesADuplicateItWasHandedRatherThanWritingIt() {
        List<Identity> duplicatePrincipal = List.of(
                new Identity("ops", MorpheusRemoteRole.READ, bytes(1)),
                new Identity("ops", MorpheusRemoteRole.ADMIN, bytes(2)));
        List<Identity> duplicateVerifier = List.of(
                new Identity("first", MorpheusRemoteRole.READ, bytes(3)),
                new Identity("second", MorpheusRemoteRole.ADMIN, bytes(3)));

        assertTrue(assertThrows(IllegalArgumentException.class, () -> RemoteIdentityCodec.format(duplicatePrincipal))
                .getMessage().contains("duplicate remote principal: ops"));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> RemoteIdentityCodec.format(duplicateVerifier))
                .getMessage().contains("duplicate remote token hash"));
    }

    @Test
    void anExpiryThatIsNotInTheFutureIsRefused() {
        assertThrows(IllegalArgumentException.class,
                () -> RemoteIdentityCodec.requireFutureExpiry(Instant.now().minusSeconds(1)));
        assertThrows(NullPointerException.class, () -> RemoteIdentityCodec.requireFutureExpiry(null));
        Instant future = Instant.now().plusSeconds(3600);
        assertEquals(future, RemoteIdentityCodec.requireFutureExpiry(future));
    }

    @Test
    void aTokenHashOfTheWrongLengthCannotBecomeAnIdentity() {
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> new Identity("ops", MorpheusRemoteRole.READ, new byte[31]))
                .getMessage().contains("exactly 32 bytes"));
    }

    private static String refusal(List<String> lines) {
        return assertThrows(IllegalArgumentException.class, () -> RemoteIdentityCodec.parse(lines)).getMessage();
    }

    private static byte[] bytes(int seed) {
        byte[] hash = new byte[32];
        hash[0] = (byte) (seed & 0xFF);
        hash[1] = (byte) ((seed >> 8) & 0xFF);
        return hash;
    }

    private static String verifier(int seed) {
        return HexFormat.of().formatHex(bytes(seed));
    }
}
