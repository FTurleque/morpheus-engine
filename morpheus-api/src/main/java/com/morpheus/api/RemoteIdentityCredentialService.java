package com.morpheus.api;

import com.morpheus.api.MorpheusRemoteIdentityFile.GeneratedCredential;
import com.morpheus.api.MorpheusRemoteIdentityFile.Identity;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Token material: generation, verifier derivation and presentation checking.
 *
 * <p>This component never touches the filesystem. The bearer token exists only in the {@link GeneratedCredential}
 * returned to the caller; what leaves here towards persistence is always the SHA-256 verifier.</p>
 */
final class RemoteIdentityCredentialService {
    static final int TOKEN_BYTES = 32;

    /**
     * Ceiling on a presented bearer token, applied before it is hashed.
     *
     * <p>A generated token is 43 characters. Anything beyond this bound cannot be one of ours, so refusing it up
     * front keeps an unauthenticated caller from choosing how much input the digest has to consume.</p>
     */
    private static final int MAX_PRESENTED_TOKEN_CHARS = 1024;

    private static final SecureRandom RANDOM = new SecureRandom();

    private RemoteIdentityCredentialService() {
    }

    static GeneratedCredential newCredential(
            String principal,
            MorpheusRemoteRole role,
            Optional<Instant> expiresAt) {
        byte[] tokenBytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(tokenBytes);
        return new GeneratedCredential(
                principal,
                role,
                Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes),
                expiresAt);
    }

    /** Projects a freshly generated credential onto what is allowed to be persisted. */
    static Identity identity(GeneratedCredential credential) {
        Objects.requireNonNull(credential, "credential");
        return new Identity(
                credential.principal(),
                credential.role(),
                sha256Bytes(credential.token()),
                credential.expiresAt());
    }

    /**
     * Matches a presented token against the snapshot in constant time per candidate.
     *
     * <p>Every identity is compared even after a match so the work does not depend on the position of the matching
     * entry, and each comparison goes through {@link MessageDigest#isEqual} rather than an array or string equality
     * that returns as soon as two bytes differ.</p>
     */
    static Optional<Identity> authenticate(List<Identity> identities, String token, Instant now) {
        Objects.requireNonNull(identities, "identities");
        Objects.requireNonNull(now, "now");
        if (token == null || token.isBlank() || token.length() > MAX_PRESENTED_TOKEN_CHARS) {
            return Optional.empty();
        }
        byte[] candidate = sha256Bytes(token);
        Identity matched = null;
        for (Identity identity : identities) {
            boolean equal = MessageDigest.isEqual(candidate, identity.tokenHash());
            boolean active = identity.isActiveAt(now);
            if (equal && active) matched = identity;
        }
        return Optional.ofNullable(matched);
    }

    static byte[] sha256Bytes(String token) {
        Objects.requireNonNull(token, "token");
        try {
            return MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException("SHA-256 must be available", failure);
        }
    }

    static String sha256Hex(String token) {
        return HexFormat.of().formatHex(sha256Bytes(token));
    }
}
