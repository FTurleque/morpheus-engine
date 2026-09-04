package com.morpheus.api;

import com.morpheus.application.files.SafeWorkspaceFileResolver;
import com.morpheus.application.security.LocalWritePermissionHardener;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * M26 reference identity file.
 *
 * <p>The file persists principal, role, SHA-256(token) and an optional expiry instant. Generated bearer tokens are
 * returned once to the caller and are never written to disk. Legacy three-field identity entries remain accepted and
 * represent non-expiring credentials. Mutations are serialized both inside this JVM and across cooperating MORPHEUS
 * processes through an owner-hardened sidecar file lock. The remote server reloads the current identity snapshot for
 * each authentication request, so revoke/rotate/role/expiry changes become effective without restart. The secret-free
 * audit is retained as a bounded rolling window inside the same atomic snapshot so audit growth can never prevent an
 * urgent credential rotation or revocation.</p>
 */
public final class MorpheusRemoteIdentityFile {
    public static final int MAX_FILE_BYTES = 256 * 1024;
    public static final int MAX_IDENTITIES = 256;
    public static final int MAX_AUDIT_RECORDS = 512;
    public static final int TOKEN_BYTES = 32;
    private static final int MAX_PRESENTED_TOKEN_CHARS = 1024;
    private static final Pattern PRINCIPAL = Pattern.compile("[A-Za-z0-9._@-]{1,128}");
    private static final String AUDIT_PREFIX = "# audit|";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Object MUTATION_LOCK = new Object();

    private MorpheusRemoteIdentityFile() {
    }

    public record Identity(
            String principal,
            MorpheusRemoteRole role,
            byte[] tokenHash,
            Optional<Instant> expiresAt) {
        public Identity {
            principal = requirePrincipal(principal);
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
            principal = requirePrincipal(principal);
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
        EXPIRY_MIGRATED
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
            principal = requirePrincipal(principal);
            Objects.requireNonNull(role, "role");
        }
    }

    public static List<Identity> load(Path authFile) {
        return parse(readLinesSecurely(authFile, "cannot read remote auth file"));
    }

    public static GeneratedCredential create(Path authFile, String principal, MorpheusRemoteRole role) {
        return create(authFile, principal, role, Optional.empty());
    }

    public static GeneratedCredential create(
            Path authFile,
            String principal,
            MorpheusRemoteRole role,
            Instant expiresAt) {
        return create(authFile, principal, role, Optional.of(requireFutureExpiry(expiresAt)));
    }

    private static GeneratedCredential create(
            Path authFile,
            String principal,
            MorpheusRemoteRole role,
            Optional<Instant> expiresAt) {
        Objects.requireNonNull(authFile, "authFile");
        String normalizedPrincipal = requirePrincipal(principal);
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(expiresAt, "expiresAt");
        return mutate(authFile, file -> {
            List<Identity> existing = Files.exists(file, LinkOption.NOFOLLOW_LINKS) ? load(file) : List.of();
            if (existing.stream().anyMatch(identity -> identity.principal().equals(normalizedPrincipal))) {
                throw new IllegalArgumentException("remote principal already exists: " + normalizedPrincipal);
            }
            if (existing.size() >= MAX_IDENTITIES) {
                throw new IllegalArgumentException("remote auth file already contains the maximum number of identities");
            }
            GeneratedCredential credential = newCredential(normalizedPrincipal, role, expiresAt);
            List<Identity> updated = new ArrayList<>(existing);
            updated.add(identity(credential));
            write(file, updated, new AuditRecord(Instant.now(), Mutation.CREATE, normalizedPrincipal, role));
            return credential;
        });
    }

    public static List<Identity> revoke(Path authFile, String principal) {
        String normalizedPrincipal = requirePrincipal(principal);
        return mutate(authFile, file -> {
            Path existingFile = secureExistingFile(file);
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
        String normalizedPrincipal = requirePrincipal(principal);
        return mutate(authFile, file -> {
            Path existingFile = secureExistingFile(file);
            List<Identity> existing = load(existingFile);
            Identity target = requireIdentity(existing, normalizedPrincipal);
            GeneratedCredential credential = newCredential(normalizedPrincipal, target.role(), target.expiresAt());
            List<Identity> updated = existing.stream()
                    .map(identity -> identity.principal().equals(normalizedPrincipal) ? identity(credential) : identity)
                    .toList();
            write(existingFile, updated,
                    new AuditRecord(Instant.now(), Mutation.ROTATE, normalizedPrincipal, target.role()));
            return credential;
        });
    }

    /** Rotates token material and replaces the expiry. Optional.empty() explicitly makes the new credential permanent. */
    public static GeneratedCredential rotate(
            Path authFile,
            String principal,
            Optional<Instant> expiresAt) {
        String normalizedPrincipal = requirePrincipal(principal);
        Optional<Instant> normalizedExpiry = Objects.requireNonNull(expiresAt, "expiresAt")
                .map(MorpheusRemoteIdentityFile::requireFutureExpiry);
        return mutate(authFile, file -> {
            Path existingFile = secureExistingFile(file);
            List<Identity> existing = load(existingFile);
            Identity target = requireIdentity(existing, normalizedPrincipal);
            GeneratedCredential credential = newCredential(normalizedPrincipal, target.role(), normalizedExpiry);
            List<Identity> updated = existing.stream()
                    .map(identity -> identity.principal().equals(normalizedPrincipal) ? identity(credential) : identity)
                    .toList();
            write(existingFile, updated,
                    new AuditRecord(Instant.now(), Mutation.ROTATE, normalizedPrincipal, target.role()));
            return credential;
        });
    }

    public static List<Identity> changeRole(Path authFile, String principal, MorpheusRemoteRole newRole) {
        String normalizedPrincipal = requirePrincipal(principal);
        Objects.requireNonNull(newRole, "newRole");
        return mutate(authFile, file -> {
            Path existingFile = secureExistingFile(file);
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
        Instant expiry = requireFutureExpiry(expiresAt);
        Set<String> selected = new LinkedHashSet<>();
        for (String principal : Objects.requireNonNull(principals, "principals")) {
            selected.add(requirePrincipal(principal));
        }
        return mutate(authFile, file -> {
            List<Identity> existing = load(secureExistingFile(file));
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
        return parseAudit(readLinesSecurely(authFile, "cannot read remote identity audit"));
    }

    public static Optional<Identity> authenticate(List<Identity> identities, String token) {
        Objects.requireNonNull(identities, "identities");
        if (token == null || token.isBlank() || token.length() > MAX_PRESENTED_TOKEN_CHARS) {
            return Optional.empty();
        }
        byte[] candidate = sha256Bytes(token);
        Instant now = Instant.now();
        Identity matched = null;
        for (Identity identity : identities) {
            boolean equal = MessageDigest.isEqual(candidate, identity.tokenHash());
            boolean active = identity.isActiveAt(now);
            if (equal && active) matched = identity;
        }
        return Optional.ofNullable(matched);
    }

    public static String sha256Hex(String token) {
        return HexFormat.of().formatHex(sha256Bytes(token));
    }

    private static <T> T mutate(Path authFile, MutationWork<T> work) {
        Objects.requireNonNull(work, "work");
        synchronized (MUTATION_LOCK) {
            Path file = normalizedFile(authFile);
            Path parent = file.getParent();
            if (parent == null) throw new IllegalArgumentException("remote auth file must have a parent directory");
            Path lockFile = mutationLockPath(file);
            try {
                Files.createDirectories(parent);
                LocalWritePermissionHardener hardener = new LocalWritePermissionHardener();
                hardener.hardenDirectory(parent);
                rejectSymbolic(lockFile, "remote auth mutation lock");
                try (FileChannel channel = FileChannel.open(
                        lockFile,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.WRITE,
                        LinkOption.NOFOLLOW_LINKS)) {
                    hardener.hardenFile(lockFile);
                    try (FileLock ignored = channel.lock()) {
                        return work.run(file);
                    }
                }
            } catch (IOException failure) {
                throw new IllegalArgumentException("cannot lock remote auth file for mutation", failure);
            }
        }
    }

    static Path mutationLockPath(Path authFile) {
        Path file = normalizedFile(authFile);
        return file.resolveSibling(file.getFileName() + ".lock");
    }

    private static List<Identity> parse(List<String> lines) {
        List<Identity> identities = new ArrayList<>();
        Set<String> principals = new HashSet<>();
        Set<String> hashes = new HashSet<>();
        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index).trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] fields = line.split("\\|", -1);
            if (fields.length != 3 && fields.length != 4) {
                throw new IllegalArgumentException("invalid remote auth entry at line " + (index + 1));
            }
            String principal = requirePrincipal(fields[0].trim());
            MorpheusRemoteRole role;
            try {
                role = MorpheusRemoteRole.valueOf(fields[1].trim());
            } catch (IllegalArgumentException failure) {
                throw new IllegalArgumentException("invalid remote role at line " + (index + 1), failure);
            }
            String hashText = fields[2].trim().toLowerCase();
            if (!hashText.matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException("invalid token SHA-256 at line " + (index + 1));
            }
            Optional<Instant> expiresAt = Optional.empty();
            if (fields.length == 4) {
                String expiryText = fields[3].trim();
                if (expiryText.isEmpty()) {
                    throw new IllegalArgumentException("blank remote identity expiry at line " + (index + 1));
                }
                try {
                    expiresAt = Optional.of(Instant.parse(expiryText));
                } catch (RuntimeException failure) {
                    throw new IllegalArgumentException("invalid remote identity expiry at line " + (index + 1), failure);
                }
            }
            if (!principals.add(principal)) throw new IllegalArgumentException("duplicate remote principal: " + principal);
            if (!hashes.add(hashText)) throw new IllegalArgumentException("duplicate remote token hash");
            identities.add(new Identity(principal, role, HexFormat.of().parseHex(hashText), expiresAt));
            if (identities.size() > MAX_IDENTITIES) {
                throw new IllegalArgumentException("remote auth file exceeds " + MAX_IDENTITIES + " identities");
            }
        }
        return List.copyOf(identities);
    }

    private static GeneratedCredential newCredential(
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

    private static Identity identity(GeneratedCredential credential) {
        return new Identity(
                credential.principal(),
                credential.role(),
                sha256Bytes(credential.token()),
                credential.expiresAt());
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

    private static void write(Path file, List<Identity> identities, List<AuditRecord> auditRecords) {
        if (identities.size() > MAX_IDENTITIES) {
            throw new IllegalArgumentException("remote auth file exceeds " + MAX_IDENTITIES + " identities");
        }
        Set<String> principals = new HashSet<>();
        Set<String> hashes = new HashSet<>();
        List<Identity> ordered = identities.stream()
                .sorted(Comparator.comparing(Identity::principal))
                .toList();
        List<String> lines = new ArrayList<>();
        lines.add("# MORPHEUS remote identities: principal|role|sha256(token)[|expiresAt]");
        for (Identity identity : ordered) {
            String hash = HexFormat.of().formatHex(identity.tokenHash());
            if (!principals.add(identity.principal())) {
                throw new IllegalArgumentException("duplicate remote principal: " + identity.principal());
            }
            if (!hashes.add(hash)) throw new IllegalArgumentException("duplicate remote token hash");
            String entry = identity.principal() + "|" + identity.role().name() + "|" + hash;
            if (identity.expiresAt().isPresent()) entry += "|" + identity.expiresAt().orElseThrow();
            lines.add(entry);
        }

        List<AuditRecord> retainedAudit = new ArrayList<>();
        if (Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
            retainedAudit.addAll(parseAudit(readLinesSecurely(file, "cannot preserve remote identity audit")));
        }
        retainedAudit.addAll(Objects.requireNonNull(auditRecords, "auditRecords"));
        int firstRetained = Math.max(0, retainedAudit.size() - MAX_AUDIT_RECORDS);
        retainedAudit.subList(firstRetained, retainedAudit.size()).stream()
                .map(MorpheusRemoteIdentityFile::formatAudit)
                .forEach(lines::add);

        String content = String.join(System.lineSeparator(), lines) + System.lineSeparator();
        if (content.getBytes(StandardCharsets.UTF_8).length > MAX_FILE_BYTES) {
            throw new IllegalArgumentException("remote auth file would exceed " + MAX_FILE_BYTES + " bytes");
        }
        Path parent = file.getParent();
        if (parent == null) throw new IllegalArgumentException("remote auth file must have a parent directory");
        try {
            Files.createDirectories(parent);
            rejectSymbolic(file, "remote auth file");
            Path temp = Files.createTempFile(parent, ".morpheus-auth-", ".tmp");
            try {
                Files.writeString(temp, content, StandardCharsets.UTF_8,
                        StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
                LocalWritePermissionHardener hardener = new LocalWritePermissionHardener();
                hardener.hardenDirectory(parent);
                hardener.hardenFile(temp);
                Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                hardener.hardenFile(file);
            } finally {
                Files.deleteIfExists(temp);
            }
        } catch (IOException failure) {
            throw new IllegalArgumentException("cannot update remote auth file", failure);
        }
    }

    private static List<String> readLinesSecurely(Path authFile, String failureMessage) {
        Path file = secureExistingFile(authFile);
        Path parent = file.getParent();
        if (parent == null) throw new IllegalArgumentException("remote auth file must have a parent directory");
        try {
            // The parent chain is part of the file identity: a protected file can still be replaced when an
            // ancestor is writable. Revalidate it for every security-sensitive read.
            new LocalWritePermissionHardener().requireWriteProtectedDirectory(parent);
            String text = SafeWorkspaceFileResolver.rootedAt(parent)
                    .readUtf8(file.getFileName(), MAX_FILE_BYTES);
            return text.lines().toList();
        } catch (IOException | RuntimeException failure) {
            if (failure.getMessage() != null && failure.getMessage().contains("exceeds maximum input size")) {
                throw new IllegalArgumentException(
                        "remote auth file exceeds " + MAX_FILE_BYTES + " bytes", failure);
            }
            throw new IllegalArgumentException(failureMessage, failure);
        }
    }

    private static List<AuditRecord> parseAudit(List<String> lines) {
        List<AuditRecord> records = new ArrayList<>();
        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index).trim();
            if (!line.startsWith(AUDIT_PREFIX)) continue;
            String[] fields = line.substring(AUDIT_PREFIX.length()).split("\\|", -1);
            if (fields.length != 4) {
                throw new IllegalArgumentException("invalid remote identity audit at line " + (index + 1));
            }
            try {
                records.add(new AuditRecord(
                        Instant.parse(fields[0]),
                        Mutation.valueOf(fields[1]),
                        fields[2],
                        MorpheusRemoteRole.valueOf(fields[3])));
            } catch (IllegalArgumentException failure) {
                throw new IllegalArgumentException("invalid remote identity audit at line " + (index + 1), failure);
            }
        }
        return List.copyOf(records);
    }

    private static String formatAudit(AuditRecord auditRecord) {
        return AUDIT_PREFIX + auditRecord.at() + "|" + auditRecord.mutation().name() + "|"
                + auditRecord.principal() + "|" + auditRecord.role().name();
    }

    private static byte[] sha256Bytes(String token) {
        Objects.requireNonNull(token, "token");
        try {
            return MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException("SHA-256 must be available", failure);
        }
    }

    private static Path normalizedFile(Path authFile) {
        Objects.requireNonNull(authFile, "authFile");
        Path file = authFile.toAbsolutePath().normalize();
        Path parent = file.getParent();
        if (parent == null) throw new IllegalArgumentException("remote auth file must have a parent directory");
        if (Files.exists(file, LinkOption.NOFOLLOW_LINKS)) rejectSymbolic(file, "remote auth file");
        return file;
    }

    private static Path secureExistingFile(Path authFile) {
        Path file = normalizedFile(authFile);
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(file)) {
            throw new IllegalArgumentException("remote auth file must be a regular non-symbolic file");
        }
        return file;
    }

    private static void rejectSymbolic(Path path, String label) {
        if (Files.exists(path, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(path)) {
            throw new IllegalArgumentException(label + " must not be a symbolic link");
        }
    }

    private static String requirePrincipal(String principal) {
        if (principal == null || !PRINCIPAL.matcher(principal.trim()).matches()) {
            throw new IllegalArgumentException("principal must match " + PRINCIPAL.pattern());
        }
        return principal.trim();
    }

    private static Instant requireFutureExpiry(Instant expiresAt) {
        Instant expiry = Objects.requireNonNull(expiresAt, "expiresAt");
        if (!Instant.now().isBefore(expiry)) {
            throw new IllegalArgumentException("remote identity expiry must be in the future");
        }
        return expiry;
    }

    @FunctionalInterface
    private interface MutationWork<T> {
        T run(Path file);
    }
}
