package com.morpheus.api;

import com.morpheus.application.security.LocalWritePermissionHardener;

import java.io.IOException;
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
import java.util.Base64;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * M26 reference identity file.
 *
 * <p>The file persists only principal, role and SHA-256(token). Generated bearer tokens are returned once to
 * the caller and are never written to disk. Identity mutations are atomic within this JVM; a running remote
 * server keeps its startup snapshot and must be restarted after administrative changes.</p>
 */
public final class MorpheusRemoteIdentityFile {
    public static final int MAX_FILE_BYTES = 256 * 1024;
    public static final int MAX_IDENTITIES = 256;
    public static final int TOKEN_BYTES = 32;
    private static final int MAX_PRESENTED_TOKEN_CHARS = 1024;
    private static final Pattern PRINCIPAL = Pattern.compile("[A-Za-z0-9._@-]{1,128}");
    private static final String AUDIT_PREFIX = "# audit|";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Object MUTATION_LOCK = new Object();

    private MorpheusRemoteIdentityFile() {
    }

    public record Identity(String principal, MorpheusRemoteRole role, byte[] tokenHash) {
        public Identity {
            principal = requirePrincipal(principal);
            role = Objects.requireNonNull(role, "role");
            tokenHash = Objects.requireNonNull(tokenHash, "tokenHash").clone();
            if (tokenHash.length != 32) {
                throw new IllegalArgumentException("tokenHash must contain exactly 32 bytes");
            }
        }

        @Override
        public byte[] tokenHash() {
            return tokenHash.clone();
        }
    }

    public record GeneratedCredential(String principal, MorpheusRemoteRole role, String token) {
        public GeneratedCredential {
            principal = requirePrincipal(principal);
            role = Objects.requireNonNull(role, "role");
            if (token == null || token.isBlank()) {
                throw new IllegalArgumentException("generated token must not be blank");
            }
        }
    }

    public enum Mutation {
        CREATE,
        REVOKE,
        ROTATE,
        ROLE_CHANGED
    }

    /** Secret-free mutation evidence persisted atomically with the identity snapshot. */
    public record AuditRecord(Instant at, Mutation mutation, String principal, MorpheusRemoteRole role) {
        public AuditRecord {
            at = Objects.requireNonNull(at, "at");
            mutation = Objects.requireNonNull(mutation, "mutation");
            principal = requirePrincipal(principal);
            role = Objects.requireNonNull(role, "role");
        }
    }

    public static List<Identity> load(Path authFile) {
        Path file = secureExistingFile(authFile);
        try {
            long bytes = Files.size(file);
            if (bytes > MAX_FILE_BYTES) {
                throw new IllegalArgumentException("remote auth file exceeds " + MAX_FILE_BYTES + " bytes");
            }
            return parse(Files.readAllLines(file, StandardCharsets.UTF_8));
        } catch (IOException failure) {
            throw new IllegalArgumentException("cannot read remote auth file", failure);
        }
    }

    public static GeneratedCredential create(Path authFile, String principal, MorpheusRemoteRole role) {
        Objects.requireNonNull(authFile, "authFile");
        String normalizedPrincipal = requirePrincipal(principal);
        Objects.requireNonNull(role, "role");
        synchronized (MUTATION_LOCK) {
            Path file = normalizedFile(authFile);
            List<Identity> existing = Files.exists(file, LinkOption.NOFOLLOW_LINKS) ? load(file) : List.of();
            if (existing.stream().anyMatch(identity -> identity.principal().equals(normalizedPrincipal))) {
                throw new IllegalArgumentException("remote principal already exists: " + normalizedPrincipal);
            }
            if (existing.size() >= MAX_IDENTITIES) {
                throw new IllegalArgumentException("remote auth file already contains the maximum number of identities");
            }
            GeneratedCredential credential = newCredential(normalizedPrincipal, role);
            List<Identity> updated = new ArrayList<>(existing);
            updated.add(identity(credential));
            write(file, updated, new AuditRecord(Instant.now(), Mutation.CREATE, normalizedPrincipal, role));
            return credential;
        }
    }

    public static List<Identity> revoke(Path authFile, String principal) {
        String normalizedPrincipal = requirePrincipal(principal);
        synchronized (MUTATION_LOCK) {
            Path file = secureExistingFile(authFile);
            List<Identity> existing = load(file);
            Identity target = requireIdentity(existing, normalizedPrincipal);
            if (target.role() == MorpheusRemoteRole.ADMIN && adminCount(existing) == 1) {
                throw new IllegalArgumentException("cannot revoke the last ADMIN identity");
            }
            List<Identity> updated = existing.stream()
                    .filter(identity -> !identity.principal().equals(normalizedPrincipal))
                    .toList();
            write(file, updated,
                    new AuditRecord(Instant.now(), Mutation.REVOKE, normalizedPrincipal, target.role()));
            return updated;
        }
    }

    public static GeneratedCredential rotate(Path authFile, String principal) {
        String normalizedPrincipal = requirePrincipal(principal);
        synchronized (MUTATION_LOCK) {
            Path file = secureExistingFile(authFile);
            List<Identity> existing = load(file);
            Identity target = requireIdentity(existing, normalizedPrincipal);
            GeneratedCredential credential = newCredential(normalizedPrincipal, target.role());
            List<Identity> updated = existing.stream()
                    .map(identity -> identity.principal().equals(normalizedPrincipal) ? identity(credential) : identity)
                    .toList();
            write(file, updated,
                    new AuditRecord(Instant.now(), Mutation.ROTATE, normalizedPrincipal, target.role()));
            return credential;
        }
    }

    public static List<Identity> changeRole(Path authFile, String principal, MorpheusRemoteRole newRole) {
        String normalizedPrincipal = requirePrincipal(principal);
        Objects.requireNonNull(newRole, "newRole");
        synchronized (MUTATION_LOCK) {
            Path file = secureExistingFile(authFile);
            List<Identity> existing = load(file);
            Identity target = requireIdentity(existing, normalizedPrincipal);
            if (target.role() == MorpheusRemoteRole.ADMIN
                    && newRole != MorpheusRemoteRole.ADMIN
                    && adminCount(existing) == 1) {
                throw new IllegalArgumentException("cannot change the role of the last ADMIN identity");
            }
            List<Identity> updated = existing.stream()
                    .map(identity -> identity.principal().equals(normalizedPrincipal)
                            ? new Identity(identity.principal(), newRole, identity.tokenHash())
                            : identity)
                    .toList();
            write(file, updated,
                    new AuditRecord(Instant.now(), Mutation.ROLE_CHANGED, normalizedPrincipal, newRole));
            return updated;
        }
    }

    public static List<AuditRecord> audit(Path authFile) {
        Path file = secureExistingFile(authFile);
        try {
            return parseAudit(Files.readAllLines(file, StandardCharsets.UTF_8));
        } catch (IOException failure) {
            throw new IllegalArgumentException("cannot read remote identity audit", failure);
        }
    }

    public static Optional<Identity> authenticate(List<Identity> identities, String token) {
        Objects.requireNonNull(identities, "identities");
        if (token == null || token.isBlank() || token.length() > MAX_PRESENTED_TOKEN_CHARS) {
            return Optional.empty();
        }
        byte[] candidate = sha256Bytes(token);
        Identity matched = null;
        for (Identity identity : identities) {
            boolean equal = MessageDigest.isEqual(candidate, identity.tokenHash());
            if (equal) matched = identity;
        }
        return Optional.ofNullable(matched);
    }

    public static String sha256Hex(String token) {
        return HexFormat.of().formatHex(sha256Bytes(token));
    }

    private static List<Identity> parse(List<String> lines) {
        List<Identity> identities = new ArrayList<>();
        Set<String> principals = new HashSet<>();
        Set<String> hashes = new HashSet<>();
        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index).trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] fields = line.split("\\|", -1);
            if (fields.length != 3) {
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
            if (!principals.add(principal)) throw new IllegalArgumentException("duplicate remote principal: " + principal);
            if (!hashes.add(hashText)) throw new IllegalArgumentException("duplicate remote token hash");
            identities.add(new Identity(principal, role, HexFormat.of().parseHex(hashText)));
            if (identities.size() > MAX_IDENTITIES) {
                throw new IllegalArgumentException("remote auth file exceeds " + MAX_IDENTITIES + " identities");
            }
        }
        return List.copyOf(identities);
    }

    private static GeneratedCredential newCredential(String principal, MorpheusRemoteRole role) {
        byte[] tokenBytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(tokenBytes);
        return new GeneratedCredential(
                principal,
                role,
                Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes));
    }

    private static Identity identity(GeneratedCredential credential) {
        return new Identity(
                credential.principal(),
                credential.role(),
                sha256Bytes(credential.token()));
    }

    private static Identity requireIdentity(List<Identity> identities, String principal) {
        return identities.stream()
                .filter(identity -> identity.principal().equals(principal))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("remote principal does not exist: " + principal));
    }

    private static long adminCount(List<Identity> identities) {
        return identities.stream().filter(identity -> identity.role() == MorpheusRemoteRole.ADMIN).count();
    }

    private static void write(Path file, List<Identity> identities, AuditRecord auditRecord) {
        if (identities.size() > MAX_IDENTITIES) {
            throw new IllegalArgumentException("remote auth file exceeds " + MAX_IDENTITIES + " identities");
        }
        Set<String> principals = new HashSet<>();
        Set<String> hashes = new HashSet<>();
        List<Identity> ordered = identities.stream()
                .sorted(Comparator.comparing(Identity::principal))
                .toList();
        List<String> lines = new ArrayList<>();
        lines.add("# MORPHEUS remote identities: principal|role|sha256(token)");
        for (Identity identity : ordered) {
            String hash = HexFormat.of().formatHex(identity.tokenHash());
            if (!principals.add(identity.principal())) {
                throw new IllegalArgumentException("duplicate remote principal: " + identity.principal());
            }
            if (!hashes.add(hash)) throw new IllegalArgumentException("duplicate remote token hash");
            lines.add(identity.principal() + "|" + identity.role().name() + "|" + hash);
        }
        if (Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
            try {
                parseAudit(Files.readAllLines(file, StandardCharsets.UTF_8)).stream()
                        .map(MorpheusRemoteIdentityFile::formatAudit)
                        .forEach(lines::add);
            } catch (IOException failure) {
                throw new IllegalArgumentException("cannot preserve remote identity audit", failure);
            }
        }
        lines.add(formatAudit(Objects.requireNonNull(auditRecord, "auditRecord")));
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

    private static String formatAudit(AuditRecord record) {
        return AUDIT_PREFIX + record.at() + "|" + record.mutation().name() + "|"
                + record.principal() + "|" + record.role().name();
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

}
