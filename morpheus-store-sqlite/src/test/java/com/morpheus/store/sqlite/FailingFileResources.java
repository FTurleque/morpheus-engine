package com.morpheus.store.sqlite;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.Channel;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Deterministic stand-ins for the two resources a server lease releases.
 *
 * <p>A real descriptor does not refuse to close and a real lock does not refuse to release, so the branches
 * that decide whether a lease may call itself closed are unreachable without these.</p>
 */
final class FailingFileResources {

    private FailingFileResources() {
    }

    /**
     * A channel whose close fails for the first {@code failures} attempts.
     *
     * <p>{@link java.nio.channels.spi.AbstractInterruptibleChannel#close()} is final and marks the channel
     * closed before it calls {@code implCloseChannel}, so a real channel never runs its close twice. The count
     * here therefore measures attempts the lease actually reached, not retries the JDK would allow.</p>
     */
    static FileChannel channelFailingCloses(AtomicInteger attempts, int failures) {
        return new CountingChannel(attempts, failures);
    }

    /** A lock whose release always fails, to prove the channel is still closed afterwards. */
    static FileLock lockFailingRelease(FileChannel channel, AtomicInteger attempts) {
        return new CountingLock(channel, attempts, true);
    }

    /** A lock that releases normally. */
    static FileLock lockReleasingNormally(FileChannel channel, AtomicInteger attempts) {
        return new CountingLock(channel, attempts, false);
    }

    private static final class CountingLock extends FileLock {
        private final AtomicInteger attempts;
        private final boolean failing;
        private boolean valid = true;

        private CountingLock(Channel channel, AtomicInteger attempts, boolean failing) {
            super((FileChannel) channel, 0L, Long.MAX_VALUE, false);
            this.attempts = attempts;
            this.failing = failing;
        }

        @Override
        public boolean isValid() {
            return valid;
        }

        @Override
        public void release() throws IOException {
            attempts.incrementAndGet();
            if (failing) {
                throw new IOException("injected lock release failure");
            }
            valid = false;
        }
    }

    private static final class CountingChannel extends FileChannel {
        private final AtomicInteger attempts;
        private final int failures;

        private CountingChannel(AtomicInteger attempts, int failures) {
            this.attempts = attempts;
            this.failures = failures;
        }

        @Override
        protected void implCloseChannel() throws IOException {
            if (attempts.incrementAndGet() <= failures) {
                throw new IOException("injected channel close failure");
            }
        }

        @Override
        public int read(ByteBuffer destination) {
            throw new UnsupportedOperationException();
        }

        @Override
        public long read(ByteBuffer[] destinations, int offset, int length) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int write(ByteBuffer source) {
            throw new UnsupportedOperationException();
        }

        @Override
        public long write(ByteBuffer[] sources, int offset, int length) {
            throw new UnsupportedOperationException();
        }

        @Override
        public long position() {
            throw new UnsupportedOperationException();
        }

        @Override
        public FileChannel position(long newPosition) {
            throw new UnsupportedOperationException();
        }

        @Override
        public long size() {
            throw new UnsupportedOperationException();
        }

        @Override
        public FileChannel truncate(long size) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void force(boolean metaData) {
            throw new UnsupportedOperationException();
        }

        @Override
        public long transferTo(long position, long count, WritableByteChannel target) {
            throw new UnsupportedOperationException();
        }

        @Override
        public long transferFrom(ReadableByteChannel source, long position, long count) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int read(ByteBuffer destination, long position) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int write(ByteBuffer source, long position) {
            throw new UnsupportedOperationException();
        }

        @Override
        public MappedByteBuffer map(MapMode mode, long position, long size) {
            throw new UnsupportedOperationException();
        }

        @Override
        public FileLock lock(long position, long size, boolean shared) {
            throw new UnsupportedOperationException();
        }

        @Override
        public FileLock tryLock(long position, long size, boolean shared) {
            throw new UnsupportedOperationException();
        }
    }
}
