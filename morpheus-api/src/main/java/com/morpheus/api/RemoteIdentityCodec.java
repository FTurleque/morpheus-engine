package com.morpheus.api;

import com.morpheus.api.MorpheusRemoteIdentityFile.Identity;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Deterministic text format of the remote identity snapshot.
 *
 * <p>This component owns the line grammar and nothing else: it never touches the filesystem, never generates or
 * hashes token material, and never decides whether a mutation is allowed. Reading and writing therefore stay
 * exactly symmetric, and the structural invariants -- field count, principal shape, verifier shape, expiry shape,
 * uniqueness of principal and of verifier, identity ceiling -- are asserted once for both directions.</p>
 *
 * <p>The three-field entry is a non-expiring credential and stays valid input. Dropping it would silently expire
 * credentials an operator never asked to change, which is how a remote server locks its own administrators out.</p>
 */
final class RemoteIdentityCodec {
    static final int MAX_IDENTITIES = 256;
    static final String HEADER = "# MORPHEUS remote identities: principal|role|sha256(token)[|expiresAt]";
    private static final Pattern PRINCIPAL = Pattern.compile("[A-Za-z0-9._@-]{1,128}");
    private static final Pattern TOKEN_VERIFIER = Pattern.compile("[0-9a-f]{64}");

    private RemoteIdentityCodec() {
    }

    /**
     * Reads every identity line, refusing the first structurally invalid one by line number.
     *
     * <p>Comment and blank lines are skipped without shifting the reported line number, so a diagnostic names the
     * line an operator sees in an editor rather than the nth parsed entry.</p>
     */
    static List<Identity> parse(List<String> lines) {
        Objects.requireNonNull(lines, "lines");
        List<Identity> identities = new ArrayList<>();
        Set<String> principals = new HashSet<>();
        Set<String> verifiers = new HashSet<>();
        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index).trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            identities.add(parseEntry(line, index + 1, principals, verifiers));
            if (identities.size() > MAX_IDENTITIES) {
                throw new IllegalArgumentException("remote auth file exceeds " + MAX_IDENTITIES + " identities");
            }
        }
        return List.copyOf(identities);
    }

    private static Identity parseEntry(String line, int lineNumber, Set<String> principals, Set<String> verifiers) {
        String[] fields = line.split("\\|", -1);
        if (fields.length != 3 && fields.length != 4) {
            throw new IllegalArgumentException("invalid remote auth entry at line " + lineNumber);
        }
        String principal = requirePrincipal(fields[0].trim());
        MorpheusRemoteRole role;
        try {
            role = MorpheusRemoteRole.valueOf(fields[1].trim());
        } catch (IllegalArgumentException failure) {
            throw new IllegalArgumentException("invalid remote role at line " + lineNumber, failure);
        }
        String verifier = fields[2].trim().toLowerCase(Locale.ROOT);
        if (!TOKEN_VERIFIER.matcher(verifier).matches()) {
            throw new IllegalArgumentException("invalid token SHA-256 at line " + lineNumber);
        }
        Optional<Instant> expiresAt = fields.length == 4
                ? Optional.of(parseExpiry(fields[3].trim(), lineNumber))
                : Optional.empty();
        if (!principals.add(principal)) {
            throw new IllegalArgumentException("duplicate remote principal: " + principal);
        }
        if (!verifiers.add(verifier)) {
            throw new IllegalArgumentException("duplicate remote token hash");
        }
        return new Identity(principal, role, HexFormat.of().parseHex(verifier), expiresAt);
    }

    /**
     * Refuses a fourth field that carries no usable instant.
     *
     * <p>A blank or unparseable expiry is not "no expiry": treating it as one would silently promote a credential
     * an operator meant to bound into a permanent one.</p>
     */
    private static Instant parseExpiry(String expiryText, int lineNumber) {
        if (expiryText.isEmpty()) {
            throw new IllegalArgumentException("blank remote identity expiry at line " + lineNumber);
        }
        try {
            return Instant.parse(expiryText);
        } catch (RuntimeException failure) {
            throw new IllegalArgumentException("invalid remote identity expiry at line " + lineNumber, failure);
        }
    }

    /**
     * Renders the identity snapshot in principal order, revalidating what the parser enforces.
     *
     * <p>Writing is the last point at which a duplicate can be stopped before it reaches disk, so the uniqueness
     * checks are repeated here rather than trusted from whoever assembled the list.</p>
     */
    static List<String> format(List<Identity> identities) {
        Objects.requireNonNull(identities, "identities");
        if (identities.size() > MAX_IDENTITIES) {
            throw new IllegalArgumentException("remote auth file exceeds " + MAX_IDENTITIES + " identities");
        }
        Set<String> principals = new HashSet<>();
        Set<String> verifiers = new HashSet<>();
        List<String> lines = new ArrayList<>();
        lines.add(HEADER);
        for (Identity identity : identities.stream().sorted(Comparator.comparing(Identity::principal)).toList()) {
            String verifier = HexFormat.of().formatHex(identity.tokenHash());
            if (!principals.add(identity.principal())) {
                throw new IllegalArgumentException("duplicate remote principal: " + identity.principal());
            }
            if (!verifiers.add(verifier)) {
                throw new IllegalArgumentException("duplicate remote token hash");
            }
            String entry = identity.principal() + "|" + identity.role().name() + "|" + verifier;
            if (identity.expiresAt().isPresent()) entry += "|" + identity.expiresAt().orElseThrow();
            lines.add(entry);
        }
        return List.copyOf(lines);
    }

    static String requirePrincipal(String principal) {
        if (principal == null || !PRINCIPAL.matcher(principal.trim()).matches()) {
            throw new IllegalArgumentException("principal must match " + PRINCIPAL.pattern());
        }
        return principal.trim();
    }

    static Instant requireFutureExpiry(Instant expiresAt) {
        Instant expiry = Objects.requireNonNull(expiresAt, "expiresAt");
        if (!Instant.now().isBefore(expiry)) {
            throw new IllegalArgumentException("remote identity expiry must be in the future");
        }
        return expiry;
    }
}
