package com.morpheus.api;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongSupplier;

/**
 * Short-lived, metadata-invalidated cache for the remote identity snapshot.
 *
 * <p>Every request still checks the auth-file identity/metadata. A secure ACL-aware reload is performed whenever the
 * file changes and at least once per revalidation interval, so revocations written through MORPHEUS remain immediate
 * without repeating expensive ACL/principal traversal for every invalid bearer token.</p>
 */
final class MorpheusRemoteIdentitySnapshotCache {
    static final Duration DEFAULT_REVALIDATION_INTERVAL = Duration.ofSeconds(1);

    private final Path authFile;
    private final long revalidationNanos;
    private final IdentityLoader loader;
    private final LongSupplier nanoTime;
    private final AtomicReference<Snapshot> snapshot = new AtomicReference<>();

    MorpheusRemoteIdentitySnapshotCache(Path authFile) {
        this(authFile, DEFAULT_REVALIDATION_INTERVAL, MorpheusRemoteIdentityFile::load, System::nanoTime);
    }

    MorpheusRemoteIdentitySnapshotCache(
            Path authFile,
            Duration revalidationInterval,
            IdentityLoader loader,
            LongSupplier nanoTime) {
        this.authFile = Objects.requireNonNull(authFile, "authFile").toAbsolutePath().normalize();
        Objects.requireNonNull(revalidationInterval, "revalidationInterval");
        if (revalidationInterval.isZero() || revalidationInterval.isNegative()) {
            throw new IllegalArgumentException("revalidationInterval must be positive");
        }
        this.revalidationNanos = revalidationInterval.toNanos();
        this.loader = Objects.requireNonNull(loader, "loader");
        this.nanoTime = Objects.requireNonNull(nanoTime, "nanoTime");
    }

    Optional<MorpheusRemoteIdentityFile.Identity> authenticate(String token) {
        return MorpheusRemoteIdentityFile.authenticate(current(), token);
    }

    List<MorpheusRemoteIdentityFile.Identity> current() {
        FileStamp observed = readStamp(authFile);
        long now = nanoTime.getAsLong();
        Snapshot cached = snapshot.get();
        if (isReusable(cached, observed, now)) {
            return cached.identities();
        }
        synchronized (this) {
            observed = readStamp(authFile);
            now = nanoTime.getAsLong();
            cached = snapshot.get();
            if (isReusable(cached, observed, now)) {
                return cached.identities();
            }
            return reload(now);
        }
    }

    private List<MorpheusRemoteIdentityFile.Identity> reload(long now) {
        for (int attempt = 0; attempt < 3; attempt++) {
            FileStamp before = readStamp(authFile);
            List<MorpheusRemoteIdentityFile.Identity> identities = List.copyOf(loader.load(authFile));
            FileStamp after = readStamp(authFile);
            if (before.equals(after)) {
                snapshot.set(new Snapshot(after, now, identities));
                return identities;
            }
        }
        throw new IllegalArgumentException("remote auth file changed repeatedly while reloading");
    }

    private boolean isReusable(Snapshot cached, FileStamp observed, long now) {
        return cached != null
                && cached.stamp().equals(observed)
                && now - cached.loadedAtNanos() >= 0
                && now - cached.loadedAtNanos() < revalidationNanos;
    }

    private static FileStamp readStamp(Path authFile) {
        try {
            if (Files.isSymbolicLink(authFile) || !Files.isRegularFile(authFile, LinkOption.NOFOLLOW_LINKS)) {
                throw new IllegalArgumentException("remote auth file must be a regular non-symbolic file");
            }
            BasicFileAttributes attributes = Files.readAttributes(
                    authFile, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            return new FileStamp(
                    attributes.fileKey(),
                    attributes.size(),
                    attributes.lastModifiedTime(),
                    attributes.creationTime());
        } catch (IOException failure) {
            throw new IllegalArgumentException("cannot inspect remote auth file", failure);
        }
    }

    private record Snapshot(
            FileStamp stamp,
            long loadedAtNanos,
            List<MorpheusRemoteIdentityFile.Identity> identities) {
        private Snapshot {
            Objects.requireNonNull(stamp, "stamp");
            identities = List.copyOf(Objects.requireNonNull(identities, "identities"));
        }
    }

    private record FileStamp(Object fileKey, long size, FileTime lastModifiedTime, FileTime creationTime) {
        private FileStamp {
            if (size < 0) throw new IllegalArgumentException("remote auth file size must not be negative");
            Objects.requireNonNull(lastModifiedTime, "lastModifiedTime");
            Objects.requireNonNull(creationTime, "creationTime");
        }
    }

    @FunctionalInterface
    interface IdentityLoader {
        List<MorpheusRemoteIdentityFile.Identity> load(Path path);
    }
}
