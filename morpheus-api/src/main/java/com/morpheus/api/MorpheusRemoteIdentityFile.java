package com.morpheus.api;

import com.morpheus.api.RemoteIdentityAudit.RetainedAudit;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * M26 reference identity file.
 *
 * <p>The file persists principal, role, SHA-256(token) and an optional expiry instant. Generated bearer tokens are
 * returned once to the caller and are never written to disk. Legacy three-field identity entries remain accepted and
 * represent non-expiring credentials. Mutations are serialized both inside this JVM and across cooperating MORPHEUS
 * processes through an owner-hardened sidecar file lock. The remote server reloads the current identity snapshot for
 * each authentication request, so revoke/rotate/role/expiry changes become effective without restart. The secret-free
 * audit is retained as a bounded rolling window inside the same atomic snapshot so audit growth can never prevent an
 * urgent credential rotation or revocation. Neither can audit corruption: historical audit entries are evidence, not
 * authority, so an unreadable one is quarantined and recorded as such rather than failing the mutation it precedes.</p>
 *
 * <p>This type is the public facade and the only place where those policies are decided. The mechanisms they are
 * decided over live in four bounded package-private components, none of which can reach the others: the text format
 * in {@link RemoteIdentityCodec}, token material in {@link RemoteIdentityCredentialService}, mutation evidence in
 * {@link RemoteIdentityAudit}, and filesystem custody in {@link RemoteIdentityFileStore}. Splitting them that way is
 * what makes each invariant testable on its own -- a parser that cannot open a file, and a store that cannot read a
 * token -- while the ADMIN and expiry rules stay visible together, here, where an operator-facing command lands.</p>
 */
public final class MorpheusRemoteIdentityFile {
    public static final int MAX_FILE_BYTES = RemoteIdentityFileStore.MAX_FILE_BYTES;
    public static final int MAX_IDENTITIES = RemoteIdentityCodec.MAX_IDENTITIES;
    public static final int MAX_AUDIT_RECORDS = RemoteIdentityAudit.MAX_AUDIT_RECORDS;
    public static final int TOKEN_BYTES = RemoteIdentityCredentialService.TOKEN_BYTES;

    private MorpheusRemoteIdentityFile() {
    }

    public record Identity(
            String principal,
            MorpheusRemoteRole role,
            byte[] tokenHash,
            Optional<Instant> expiresAt) {
        public Identity {
            principal = RemoteIdentityCodec.requirePrincipal(principal);
            role = Objects.requireNonNull(role, "role");
            tokenHash = Objects.requireNonNull(tokenHash, "tokenHash").clone();
            Objects.requireNonNull(expiresAt, "expiresAt");
            if (tokenHash.length != 32) {
                throw new IllegalArgumentException("tokenHash must contain exactly 32 bytes");
            }
        }

        public Identity(String principal, MorpheusRemoteRole role, byte[] tokenHash) {
            this(principal, role, tokenHash, Optional.empty());
        }

        @Override
        public byte[] tokenHash() {
            return tokenHash.clone();
        }

        public boolean isExpiredAt(Instant instant) {
            Objects.requireNonNull(instant, "instant");
            return expiresAt.map(expiry -> !instant.isBefore(expiry)).orElse(false);
        }

        public boolean isActiveAt(Instant instant) {
            return !isExpiredAt(instant);
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Identity otherIdentity)) return false;
            return principal.equals(otherIdentity.principal)
                    && role == otherIdentity.role
                    && Arrays.equals(tokenHash, otherIdentity.tokenHash)
                    && expiresAt.equals(otherIdentity.expiresAt);
        }

        @Override
        public int hashCode() {
            return Objects.hash(principal, role, Arrays.hashCode(tokenHash), expiresAt);
        }

        /**
         * Diagnostic rendering without the stored verifier.
         *
         * <p>The token hash is the material an offline attacker needs; it has no diagnostic value that principal,
         * role and expiry do not already provide. Any log line, exception message or collection dump that
         * interpolates an identity must stay safe by construction.
         */
        @Override
        public String toString() {
            return "Identity[principal=" + principal + ", role=" + role
                    + ", tokenHash=<redacted>, expiresAt=" + expiresAt + "]";
        }
    }

    public record GeneratedCredential(
            String principal,
            MorpheusRemoteRole role,
            String token,
            Optional<Instant> expiresAt) {
        public GeneratedCredential {
            principal = RemoteIdentityCodec.requirePrincipal(principal);
            role = Objects.requireNonNull(role, "role");
            if (token == null || token.isBlank()) {
                throw new IllegalArgumentException("generated token must not be blank");
            }
            Objects.requireNonNull(expiresAt, "expiresAt");
        }

        public GeneratedCredential(String principal, MorpheusRemoteRole role, String token) {
            this(principal, role, token, Optional.empty());
        }

        /**
         * Diagnostic rendering without the bearer token.
         *
         * <p>The token is printed once, deliberately, by the credential commands through {@link #token()}. The
         * automatic record rendering must never become a second, accidental disclosure path.
         */
        @Override
        public String toString() {
            return "GeneratedCredential[principal=" + principal + ", role=" + role
                    + ", token=<redacted>, expiresAt=" + expiresAt + "]";
        }
    }

    public enum Mutation {
        CREATE,
        REVOKE,
        ROTATE,
        ROLE_CHANGED,
        EXPIRY_MIGRATED,

        /**
         * Unreadable historical audit entries were dropped so a credential mutation could proceed.
         *
         * <p>It carries no principal of its own because the entries it replaces could not be read, and reading
         * them is exactly what failed.</p>
         */
        AUDIT_QUARANTINED
    }

    /**
     * Outcome of a legacy expiry migration, secret-free by construction.
     *
     * <p>{@code retained} names the identities the migration deliberately left non-expiring. That set is not a
     * failure: it is how an operator keeps a break-glass credential while the rest of the file gains an expiry.</p>
     */
    public record LegacyMigration(
            boolean dryRun,
            Instant expiresAt,
            List<String> migrated,
            List<String> retained) {
        public LegacyMigration {
            Objects.requireNonNull(expiresAt, "expiresAt");
            migrated = List.copyOf(Objects.requireNonNull(migrated, "migrated"));
            retained = List.copyOf(Objects.requireNonNull(retained, "retained"));
        }
    }

    /** Secret-free mutation evidence persisted atomically with the identity snapshot. */
    public record AuditRecord(Instant at, Mutation mutation, String principal, MorpheusRemoteRole role) {
        public AuditRecord {
            at = Objects.requireNonNull(at, "at");
            mutation = Objects.requireNonNull(mutation, "mutation");
            principal = RemoteIdentityCodec.requirePrincipal(principal);
            Objects.requireNonNull(role, "role");
        }
    }

    public static List<Identity> load(Path authFile) {
        return RemoteIdentityCodec.parse(
                RemoteIdentityFileStore.readLines(authFile, "cannot read remote auth file"));
    }

    public static GeneratedCredential create(Path authFile, String principal, MorpheusRemoteRole role) {
        return create(authFile, principal, role, Optional.empty());
    }

    public static GeneratedCredential create(
            Path authFile,
            String principal,
            MorpheusRemoteRole role,
            Instant expiresAt) {
        return create(authFile, principal, role, Optional.of(RemoteIdentityCodec.requireFutureExpiry(expiresAt)));
    }

    private static GeneratedCredential create(
            Path authFile,
            String principal,
            MorpheusRemoteRole role,
            Optional<Instant> expiresAt) {
        Objects.requireNonNull(authFile, "authFile");
        String normalizedPrincipal = RemoteIdentityCodec.requirePrincipal(principal);
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(expiresAt, "expiresAt");
        return RemoteIdentityFileStore.withMutationLock(authFile, file -> {
            List<Identity> existing = RemoteIdentityFileStore.exists(file) ? load(file) : List.of();
            if (existing.stream().anyMatch(identity -> identity.principal().equals(normalizedPrincipal))) {
                throw new IllegalArgumentException("remote principal already exists: " + normalizedPrincipal);
            }
            if (existing.size() >= MAX_IDENTITIES) {
                throw new IllegalArgumentException("remote auth file already contains the maximum number of identities");
            }
            GeneratedCredential credential =
                    RemoteIdentityCredentialService.newCredential(normalizedPrincipal, role, expiresAt);
            List<Identity> updated = new ArrayList<>(existing);
            updated.add(RemoteIdentityCredentialService.identity(credential));
            write(file, updated, new AuditRecord(Instant.now(), Mutation.CREATE, normalizedPrincipal, role));
            return credential;
        });
    }

    public static List<Identity> revoke(Path authFile, String principal) {
        String normalizedPrincipal = RemoteIdentityCodec.requirePrincipal(principal);
        return RemoteIdentityFileStore.withMutationLock(authFile, file -> {
            Path existingFile = RemoteIdentityFileStore.secureExistingFile(file);
            List<Identity> existing = load(existingFile);
            Identity target = requireIdentity(existing, normalizedPrincipal);
            if (target.role() == MorpheusRemoteRole.ADMIN && target.isActiveAt(Instant.now()) && adminCount(existing) == 1) {
                throw new IllegalArgumentException("cannot revoke the last active ADMIN identity");
            }
            List<Identity> updated = existing.stream()
                    .filter(identity -> !identity.principal().equals(normalizedPrincipal))
                    .toList();
            write(existingFile, updated,
                    new AuditRecord(Instant.now(), Mutation.REVOKE, normalizedPrincipal, target.role()));
            return updated;
        });
    }

    /** Rotates token material while preserving the identity's current expiry policy. */
    public static GeneratedCredential rotate(Path authFile, String principal) {
        String normalizedPrincipal = RemoteIdentityCodec.requirePrincipal(principal);
        return RemoteIdentityFileStore.withMutationLock(authFile, file -> {
            Path existingFile = RemoteIdentityFileStore.secureExistingFile(file);
            List<Identity> existing = load(existingFile);
            Identity target = requireIdentity(existing, normalizedPrincipal);
            return rotateTo(existingFile, existing, target, target.expiresAt());
        });
    }

    /** Rotates token material and replaces the expiry. Optional.empty() explicitly makes the new credential permanent. */
    public static GeneratedCredential rotate(
            Path authFile,
            String principal,
            Optional<Instant> expiresAt) {
        String normalizedPrincipal = RemoteIdentityCodec.requirePrincipal(principal);
        Optional<Instant> normalizedExpiry = Objects.requireNonNull(expiresAt, "expiresAt")
                .map(RemoteIdentityCodec::requireFutureExpiry);
        return RemoteIdentityFileStore.withMutationLock(authFile, file -> {
            Path existingFile = RemoteIdentityFileStore.secureExistingFile(file);
            List<Identity> existing = load(existingFile);
            Identity target = requireIdentity(existing, normalizedPrincipal);
            return rotateTo(existingFile, existing, target, normalizedExpiry);
        });
    }

    /**
     * Replaces one identity's token material, keeping its role.
     *
     * <p>Rotation never changes who the principal is or what it may do: an operator rotating a compromised token
     * is answering a leak, not making an authorization decision.</p>
     */
    private static GeneratedCredential rotateTo(
            Path file,
            List<Identity> existing,
            Identity target,
            Optional<Instant> expiresAt) {
        GeneratedCredential credential =
                RemoteIdentityCredentialService.newCredential(target.principal(), target.role(), expiresAt);
        Identity rotated = RemoteIdentityCredentialService.identity(credential);
        List<Identity> updated = existing.stream()
                .map(identity -> identity.principal().equals(target.principal()) ? rotated : identity)
                .toList();
        write(file, updated,
                new AuditRecord(Instant.now(), Mutation.ROTATE, target.principal(), target.role()));
        return credential;
    }

    public static List<Identity> changeRole(Path authFile, String principal, MorpheusRemoteRole newRole) {
        String normalizedPrincipal = RemoteIdentityCodec.requirePrincipal(principal);
        Objects.requireNonNull(newRole, "newRole");
        return RemoteIdentityFileStore.withMutationLock(authFile, file -> {
            Path existingFile = RemoteIdentityFileStore.secureExistingFile(file);
            List<Identity> existing = load(existingFile);
            Identity target = requireIdentity(existing, normalizedPrincipal);
            if (target.role() == MorpheusRemoteRole.ADMIN
                    && target.isActiveAt(Instant.now())
                    && newRole != MorpheusRemoteRole.ADMIN
                    && adminCount(existing) == 1) {
                throw new IllegalArgumentException("cannot change the role of the last active ADMIN identity");
            }
            List<Identity> updated = existing.stream()
                    .map(identity -> identity.principal().equals(normalizedPrincipal)
                            ? new Identity(identity.principal(), newRole, identity.tokenHash(), identity.expiresAt())
                            : identity)
                    .toList();
            write(existingFile, updated,
                    new AuditRecord(Instant.now(), Mutation.ROLE_CHANGED, normalizedPrincipal, newRole));
            return updated;
        });
    }

    /**
     * Gives an explicit expiry to identities that have none, without touching their token material.
     *
     * <p>The three-field entry is a non-expiring credential, and it stays valid input: nothing here happens
     * implicitly, because silently expiring a credential an operator never asked to change is how a remote
     * server locks its own administrators out. The migration is explicit, reports exactly what it would do
     * before it does it, and rotates nothing -- every client keeps working, it simply now has a deadline.</p>
     *
     * <p>It refuses to schedule an ADMIN lockout. If applying it would leave no ADMIN identity still active
     * after {@code expiresAt}, the whole migration fails: an operator must first give one administrator a later
     * expiry, or exclude it with {@code principals}, so a way back into the server outlives the deadline.</p>
     *
     * @param principals identities to migrate, or empty to migrate every non-expiring identity
     */
    public static LegacyMigration migrateLegacyExpiry(
            Path authFile,
            Instant expiresAt,
            Set<String> principals,
            boolean dryRun) {
        Instant expiry = RemoteIdentityCodec.requireFutureExpiry(expiresAt);
        Set<String> selected = new LinkedHashSet<>();
        for (String principal : Objects.requireNonNull(principals, "principals")) {
            selected.add(RemoteIdentityCodec.requirePrincipal(principal));
        }
        return RemoteIdentityFileStore.withMutationLock(authFile, file -> {
            List<Identity> existing = load(RemoteIdentityFileStore.secureExistingFile(file));
            for (String principal : selected) {
                requireIdentity(existing, principal);
            }

            List<String> migrated = existing.stream()
                    .filter(identity -> identity.expiresAt().isEmpty())
                    .filter(identity -> selected.isEmpty() || selected.contains(identity.principal()))
                    .map(Identity::principal)
                    .toList();
            List<Identity> updated = existing.stream()
                    .map(identity -> migrated.contains(identity.principal())
                            ? new Identity(identity.principal(), identity.role(), identity.tokenHash(),
                                    Optional.of(expiry))
                            : identity)
                    .toList();
            requireAdministratorOutliving(updated, expiry);

            List<String> retained = updated.stream()
                    .filter(identity -> identity.expiresAt().isEmpty())
                    .map(Identity::principal)
                    .toList();
            if (!dryRun && !migrated.isEmpty()) {
                Instant at = Instant.now();
                // One atomic write for the whole migration: writing per identity would leave the file in a
                // partially migrated state if any intermediate write failed.
                write(file, updated, migrated.stream()
                        .map(principal -> new AuditRecord(
                                at, Mutation.EXPIRY_MIGRATED, principal, requireIdentity(updated, principal).role()))
                        .toList());
            }
            return new LegacyMigration(dryRun, expiry, migrated, retained);
        });
    }

    /**
     * A remote server refuses to start without an active ADMIN identity, so a migration that leaves none after
     * the deadline is a lockout scheduled for that date rather than an error at that date.
     */
    private static void requireAdministratorOutliving(List<Identity> identities, Instant expiry) {
        boolean survives = identities.stream()
                .filter(identity -> identity.role() == MorpheusRemoteRole.ADMIN)
                .anyMatch(identity -> identity.expiresAt().isEmpty()
                        || identity.expiresAt().orElseThrow().isAfter(expiry));
        if (!survives) {
            throw new IllegalArgumentException(
                    "migration would leave no ADMIN identity active after " + expiry
                            + "; give one administrator a later expiry or exclude it from the migration");
        }
    }

    public static List<AuditRecord> audit(Path authFile) {
        return RemoteIdentityAudit.parseStrict(
                RemoteIdentityFileStore.readLines(authFile, "cannot read remote identity audit"));
    }

    public static Optional<Identity> authenticate(List<Identity> identities, String token) {
        return RemoteIdentityCredentialService.authenticate(identities, token, Instant.now());
    }

    public static String sha256Hex(String token) {
        return RemoteIdentityCredentialService.sha256Hex(token);
    }

    static Path mutationLockPath(Path authFile) {
        return RemoteIdentityFileStore.mutationLockPath(authFile);
    }

    private static Identity requireIdentity(List<Identity> identities, String principal) {
        return identities.stream()
                .filter(identity -> identity.principal().equals(principal))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("remote principal does not exist: " + principal));
    }

    private static long adminCount(List<Identity> identities) {
        Instant now = Instant.now();
        return identities.stream()
                .filter(identity -> identity.role() == MorpheusRemoteRole.ADMIN)
                .filter(identity -> identity.isActiveAt(now))
                .count();
    }

    private static void write(Path file, List<Identity> identities, AuditRecord auditRecord) {
        write(file, identities, List.of(Objects.requireNonNull(auditRecord, "auditRecord")));
    }

    /**
     * Publishes the identity snapshot and its audit window as one atomic replacement.
     *
     * <p>The audit is salvaged rather than parsed strictly here. Preserving it strictly meant a single unreadable
     * {@code # audit|} line -- from a partial write, a hand edit, or a truncated copy -- made every later mutation
     * of the file fail, so a credential known to be compromised could not be revoked while the credential itself
     * stayed perfectly valid. Ordering that the wrong way makes the audit a denial of service against the
     * operation the audit exists to record.</p>
     */
    private static void write(Path file, List<Identity> identities, List<AuditRecord> auditRecords) {
        Objects.requireNonNull(auditRecords, "auditRecords");
        List<String> lines = new ArrayList<>(RemoteIdentityCodec.format(identities));
        RetainedAudit salvaged = RemoteIdentityFileStore.exists(file)
                ? RemoteIdentityAudit.salvage(
                        RemoteIdentityFileStore.readLines(file, "cannot preserve remote identity audit"))
                : new RetainedAudit(List.of(), 0);
        lines.addAll(RemoteIdentityAudit.format(RemoteIdentityAudit.retain(salvaged, auditRecords)));

        RemoteIdentityFileStore.writeAtomically(
                file, String.join(System.lineSeparator(), lines) + System.lineSeparator());
    }
}
