package com.morpheus.api;

import com.morpheus.application.security.LocalWritePermissionHardener;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
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
 * <p>The file persists only principal, role and SHA-256(token). The generated bearer token is returned once to
 * the caller and is never written to disk.</p>
 */
public final class MorpheusRemoteIdentityFile {
    public static final int MAX_FILE_BYTES = 256 * 1024;
    public static final int MAX_IDENTITIES = 256;
    public static final int TOKEN_BYTES = 32;
    private static final int MAX_PRESENTED_TOKEN_CHARS = 1024;
    private static final Pattern PRINCIPAL = Pattern.compile("[A-Za-z0-9._@-]{1,128}");
    private static final SecureRandom RANDOM = new SecureRandom();

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

    public static List<Identity> load(Path authFile) {
        Path file = secureExistingFile(authFile);
        try {
            long bytes = Files.size(file);
            if (bytes > MAX_FILE_BYTES) {
                throw new IllegalArgumentException("remote auth file exceeds " + MAX_FILE_BYTES + " bytes");
            }
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            List<Identity> identities = new ArrayList<>();
            Set<String> principals = new HashSet<>();
            Set<String> hashes = new HashSet<>();
            for (int index = 0; index < lines.size(); index++) {
                String line = lines.get(index).trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
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
                if (!principals.add(principal)) {
                    throw new IllegalArgumentException("duplicate remote principal: " + principal);
                }
                if (!hashes.add(hashText)) {
                    throw new IllegalArgumentException("duplicate remote token hash");
                }
                identities.add(new Identity(principal, role, HexFormat.of().parseHex(hashText)));
                if (identities.size() > MAX_IDENTITIES) {
                    throw new IllegalArgumentException("remote auth file exceeds " + MAX_IDENTITIES + " identities");
                }
            }
            return List.copyOf(identities);
        } catch (IOException failure) {
            throw new IllegalArgumentException("cannot read remote auth file", failure);
        }
    }

    public static GeneratedCredential create(Path authFile, String principal, MorpheusRemoteRole role) {
        Objects.requireNonNull(authFile, "authFile");
        String normalizedPrincipal = requirePrincipal(principal);
        Objects.requireNonNull(role, "role");
        Path file = authFile.toAbsolutePath().normalize();
        Path parent = file.getParent();
        if (parent == null) {
            throw new IllegalArgumentException("remote auth file must have a parent directory");
        }
        try {
            Files.createDirectories(parent);
            rejectSymbolic(file, "remote auth file");
            List<String> existing = Files.exists(file, LinkOption.NOFOLLOW_LINKS)
                    ? Files.readAllLines(file, StandardCharsets.UTF_8)
                    : List.of("# MORPHEUS remote identities: principal|role|sha256(token)");
            if (Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
                for (Identity identity : load(file)) {
                    if (identity.principal().equals(normalizedPrincipal)) {
                        throw new IllegalArgumentException("remote principal already exists: " + normalizedPrincipal);
                    }
                }
            }
            long identityCount = existing.stream()
                    .map(String::trim)
                    .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                    .count();
            if (identityCount >= MAX_IDENTITIES) {
                throw new IllegalArgumentException("remote auth file already contains the maximum number of identities");
            }

            byte[] tokenBytes = new byte[TOKEN_BYTES];
            RANDOM.nextBytes(tokenBytes);
            String token = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
            String hash = sha256Hex(token);
            List<String> updated = new ArrayList<>(existing);
            updated.add(normalizedPrincipal + "|" + role.name() + "|" + hash);
            String content = String.join(System.lineSeparator(), updated) + System.lineSeparator();
            if (content.getBytes(StandardCharsets.UTF_8).length > MAX_FILE_BYTES) {
                throw new IllegalArgumentException("remote auth file would exceed " + MAX_FILE_BYTES + " bytes");
            }

            Path temp = Files.createTempFile(parent, ".morpheus-auth-", ".tmp");
            try {
                Files.writeString(temp, content, StandardCharsets.UTF_8,
                        StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
                LocalWritePermissionHardener hardener = new LocalWritePermissionHardener();
                hardener.hardenDirectory(parent);
                hardener.hardenFile(temp);
                moveReplacing(temp, file);
                hardener.hardenFile(file);
            } finally {
                Files.deleteIfExists(temp);
            }
            return new GeneratedCredential(normalizedPrincipal, role, token);
        } catch (IOException failure) {
            throw new IllegalArgumentException("cannot update remote auth file", failure);
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
            if (equal) {
                matched = identity;
            }
        }
        return Optional.ofNullable(matched);
    }

    public static String sha256Hex(String token) {
        return HexFormat.of().formatHex(sha256Bytes(token));
    }

    private static byte[] sha256Bytes(String token) {
        Objects.requireNonNull(token, "token");
        try {
            return MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException("SHA-256 must be available", failure);
        }
    }

    private static Path secureExistingFile(Path authFile) {
        Objects.requireNonNull(authFile, "authFile");
        Path file = authFile.toAbsolutePath().normalize();
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

    private static void moveReplacing(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException unsupported) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
