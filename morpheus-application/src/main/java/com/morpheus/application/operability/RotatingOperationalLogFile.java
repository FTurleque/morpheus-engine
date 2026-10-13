package com.morpheus.application.operability;

import com.morpheus.application.security.LocalWritePermissionHardener;
import com.morpheus.application.security.LocalWritePermissionHardener.LocalWritePermissionException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Objects;

/**
 * The operational log as a file of the logs directory: one JSON event per line, bounded in size and in number.
 *
 * <p>The product published a logs directory - in its layout, in the {@code paths} command, in its user pages - and
 * wrote nothing in it. This sink is what the directory is for. The current file never grows past its bound: the event
 * that would cross it starts a new file, and the previous ones are kept as {@code .1} (the most recent) to
 * {@code .N}, the oldest being deleted. The directory and each file are private to the account that runs the
 * product.</p>
 *
 * <p>Nothing is held open between two events and the size is read from the file each time, so several MORPHEUS
 * processes of one account can append to the same log. An event that cannot be written is counted
 * ({@link #WRITE_FAILED}) and dropped: the log cannot report its own failure, and the product does not stop for it.</p>
 */
public final class RotatingOperationalLogFile implements OperationalEventSink {
    public static final String FILE_NAME = "morpheus-operational.jsonl";
    public static final String REFUSAL = "OPERATIONAL_LOG_FILE_UNUSABLE";
    public static final String WRITE_FAILED = "operational.log.write.failed";
    public static final long MAX_FILE_BYTES = 4L * 1024 * 1024;
    public static final int KEPT_GENERATIONS = 4;

    private final Path file;
    private final long maxFileBytes;
    private final int keptGenerations;
    private final OperationalMetrics metrics;
    private final StructuredOperationalEventSink format = new StructuredOperationalEventSink(System.err);
    private final LocalWritePermissionHardener hardener = new LocalWritePermissionHardener();

    private RotatingOperationalLogFile(Path file, long maxFileBytes, int keptGenerations, OperationalMetrics metrics) {
        this.file = file;
        this.maxFileBytes = maxFileBytes;
        this.keptGenerations = keptGenerations;
        this.metrics = metrics;
    }

    /**
     * Opens the log of a directory with the product's bounds, creating the directory and the file when they are
     * missing.
     *
     * @throws OperationalLogFileException starting with {@link #REFUSAL} when the directory cannot be created, is not
     *         private to this account, or the file cannot be written
     */
    public static RotatingOperationalLogFile open(Path directory, OperationalMetrics metrics) {
        return open(directory, MAX_FILE_BYTES, KEPT_GENERATIONS, metrics);
    }

    static RotatingOperationalLogFile open(
            Path directory, long maxFileBytes, int keptGenerations, OperationalMetrics metrics) {
        Objects.requireNonNull(directory, "directory");
        Objects.requireNonNull(metrics, "metrics");
        if (maxFileBytes < 1 || keptGenerations < 1) {
            throw new IllegalArgumentException("the operational log needs a positive size bound and generation count");
        }
        RotatingOperationalLogFile log = new RotatingOperationalLogFile(
                directory.toAbsolutePath().normalize().resolve(FILE_NAME), maxFileBytes, keptGenerations, metrics);
        try {
            log.hardener.hardenDirectory(log.file.getParent());
            log.createPrivateFileIfMissing();
        } catch (IOException | LocalWritePermissionException unusable) {
            throw new OperationalLogFileException(REFUSAL + ": the operational log cannot be written in "
                    + log.file.getParent() + " (" + unusable.getClass().getSimpleName() + "); name a directory this "
                    + "account alone can write with MORPHEUS_LOGS_DIR, or choose another "
                    + OperationalLogMode.ENVIRONMENT_VARIABLE, unusable);
        }
        return log;
    }

    public Path file() {
        return file;
    }

    @Override
    public synchronized void emit(OperationalEvent event) {
        byte[] line = (format.line(event) + "\n").getBytes(StandardCharsets.UTF_8);
        try {
            createPrivateFileIfMissing();
            long size = Files.size(file);
            if (size > 0 && size + line.length > maxFileBytes) {
                rotate();
                createPrivateFileIfMissing();
            }
            Files.write(file, line, StandardOpenOption.APPEND);
        } catch (IOException | LocalWritePermissionException lost) {
            metrics.increment(WRITE_FAILED);
        }
    }

    private void createPrivateFileIfMissing() throws IOException {
        if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
            Files.createFile(file);
            hardener.hardenFile(file);
        }
    }

    private void rotate() throws IOException {
        Files.deleteIfExists(generation(keptGenerations));
        for (int number = keptGenerations - 1; number >= 1; number--) {
            Path older = generation(number);
            if (Files.exists(older, LinkOption.NOFOLLOW_LINKS)) {
                Files.move(older, generation(number + 1), StandardCopyOption.REPLACE_EXISTING);
            }
        }
        Files.move(file, generation(1), StandardCopyOption.REPLACE_EXISTING);
    }

    private Path generation(int number) {
        return file.resolveSibling(FILE_NAME + "." + number);
    }
}
