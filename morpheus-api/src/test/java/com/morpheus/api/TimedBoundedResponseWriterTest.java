package com.morpheus.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The response deadline, exercised against real sockets rather than a stand-in.
 *
 * <p>The whole mechanism rests on one specified behaviour -- interrupting a thread blocked on an
 * {@link java.nio.channels.InterruptibleChannel} closes that channel and raises
 * {@link java.nio.channels.ClosedByInterruptException} -- so a test against an {@code OutputStream} that never
 * blocks would prove nothing at all. These tests connect two real TCP channels on the loopback interface and
 * make the peer behave the way a hostile or broken client behaves: stop reading, read slowly, or trickle.</p>
 */
class TimedBoundedResponseWriterTest {
    private static final int PAYLOAD_BYTES = 64 * 1024 * 1024;

    /**
     * A peer that stops reading does not hold the writer past its stall budget.
     *
     * <p>This is the {@code jdk.httpserver} failure this class exists for: the write blocks on a full socket, and
     * without a deadline it blocks for as long as the client cares to stay connected -- holding a request slot, a
     * concurrency permit and a proxy response slot behind it.</p>
     */
    @Test
    @Timeout(60)
    void aPeerThatStopsReadingIsCutOffAtTheStallBudget() throws Exception {
        try (Peers peers = Peers.connected();
             TimedBoundedResponseWriter writer =
                     new TimedBoundedResponseWriter(Duration.ofMillis(300), Duration.ofSeconds(30))) {

            long startNanos = System.nanoTime();
            assertThrows(
                    TimedBoundedResponseWriter.ResponseWriteTimeoutException.class,
                    () -> writer.write(progress -> writeUntilBlocked(peers, progress)));
            long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);

            assertTrue(elapsedMillis < 30_000, () -> "the stall budget must fire well before the total one, took "
                    + elapsedMillis + "ms");
            assertFalse(peers.server.isOpen(), "the reclaimed connection must actually be closed");
            assertFalse(Thread.currentThread().isInterrupted(),
                    "the deadline must not leave its interrupt on the calling thread");
        }
    }

    /**
     * A slow peer that keeps draining is served in full.
     *
     * <p>A deadline that could not tell "slow" from "stopped" would be a bound on the client's bandwidth, and the
     * first honest client on a bad link would meet it. Progress is what separates them.</p>
     */
    @Test
    @Timeout(60)
    void aSlowButProgressingPeerIsServedInFull() throws Exception {
        int payload = 4 * 1024 * 1024;
        try (Peers peers = Peers.connected();
             TimedBoundedResponseWriter writer =
                     new TimedBoundedResponseWriter(Duration.ofMillis(500), Duration.ofSeconds(45))) {

            AtomicBoolean draining = new AtomicBoolean(true);
            Thread reader = drainSlowly(peers, draining);
            try {
                assertDoesNotThrow(() -> writer.write(progress -> writeExactly(peers, payload, progress)));
            } finally {
                draining.set(false);
                reader.join(TimeUnit.SECONDS.toMillis(10));
            }
        }
    }

    /**
     * A peer that trickles forever is bounded too.
     *
     * <p>Rearming on progress is what makes a slow client legitimate; without a second budget it would also make
     * an unlimited one legitimate, as long as it accepted one chunk per stall window indefinitely.</p>
     */
    @Test
    @Timeout(60)
    void aPeerThatTricklesForeverIsBoundedByTheTotalBudget() throws Exception {
        try (Peers peers = Peers.connected();
             TimedBoundedResponseWriter writer =
                     new TimedBoundedResponseWriter(Duration.ofMillis(400), Duration.ofMillis(1200))) {

            AtomicBoolean draining = new AtomicBoolean(true);
            Thread reader = drainSlowly(peers, draining);
            try {
                assertThrows(
                        TimedBoundedResponseWriter.ResponseWriteTimeoutException.class,
                        () -> writer.write(progress -> writeUntilBlocked(peers, progress)));
            } finally {
                draining.set(false);
                reader.join(TimeUnit.SECONDS.toMillis(10));
            }
        }
    }

    /**
     * An ordinary write failure stays an ordinary write failure.
     *
     * <p>Reporting every broken connection as a deadline would hide the difference between a client the facade
     * cut off and a client that vanished, which is the only thing the two counters are for.</p>
     */
    @Test
    @Timeout(30)
    void aFailureThatIsNotADeadlineIsPropagatedUnchanged() {
        try (TimedBoundedResponseWriter writer =
                     new TimedBoundedResponseWriter(Duration.ofSeconds(30), Duration.ofSeconds(60))) {
            IOException broken = new IOException("Broken pipe");

            IOException thrown = assertThrows(IOException.class, () -> writer.write(progress -> {
                throw broken;
            }));

            assertSame(broken, thrown);
            assertFalse(Thread.currentThread().isInterrupted());
        }
    }

    @Test
    void budgetsMustBePositiveAndOrdered() {
        Duration oneSecond = Duration.ofSeconds(1);
        Duration twoSeconds = Duration.ofSeconds(2);
        Duration negative = Duration.ofSeconds(-1);

        assertThrows(IllegalArgumentException.class,
                () -> new TimedBoundedResponseWriter(Duration.ZERO, oneSecond));
        assertThrows(IllegalArgumentException.class,
                () -> new TimedBoundedResponseWriter(oneSecond, negative));
        assertThrows(IllegalArgumentException.class,
                () -> new TimedBoundedResponseWriter(twoSeconds, oneSecond));
    }

    private static void writeUntilBlocked(Peers peers, TimedBoundedResponseWriter.Progress progress)
            throws IOException {
        writeExactly(peers, PAYLOAD_BYTES, progress);
    }

    private static void writeExactly(Peers peers, int bytes, TimedBoundedResponseWriter.Progress progress)
            throws IOException {
        byte[] chunk = new byte[8192];
        long remaining = bytes;
        while (remaining > 0) {
            ByteBuffer buffer = ByteBuffer.wrap(chunk, 0, (int) Math.min(chunk.length, remaining));
            while (buffer.hasRemaining()) {
                peers.server.write(buffer);
            }
            remaining -= Math.min(chunk.length, remaining);
            progress.made();
        }
    }

    /** A peer that keeps reading, a little at a time, until it is told to stop. */
    @SuppressWarnings("java:S2925")
    private static Thread drainSlowly(Peers peers, AtomicBoolean draining) {
        Thread reader = new Thread(() -> {
            ByteBuffer sink = ByteBuffer.allocate(4096);
            try {
                while (draining.get() && peers.client.read(sink) >= 0) {
                    sink.clear();
                    TimeUnit.MILLISECONDS.sleep(1);
                }
            } catch (IOException | RuntimeException expectedWhenTheWriterGivesUp) {
                // The writer closing its channel is the outcome under test, not a failure of the reader.
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        }, "timed-bounded-response-writer-test-reader");
        reader.setDaemon(true);
        reader.start();
        return reader;
    }

    /** Two connected loopback channels: one the facade writes to, one the client would read from. */
    private record Peers(ServerSocketChannel listener, SocketChannel client, SocketChannel server)
            implements AutoCloseable {

        private static Peers connected() throws IOException {
            ServerSocketChannel listener = ServerSocketChannel.open();
            listener.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
            SocketAddress address = listener.getLocalAddress();
            SocketChannel client = SocketChannel.open();
            // A small receive window keeps the amount that has to be written before the socket backs up small
            // enough for the test to stay quick, whatever the platform's send-buffer autotuning does.
            client.setOption(StandardSocketOptions.SO_RCVBUF, 4096);
            client.connect(address);
            SocketChannel server = listener.accept();
            return new Peers(listener, client, server);
        }

        @Override
        public void close() throws IOException {
            closeQuietly(server);
            closeQuietly(client);
            listener.close();
        }

        private static void closeQuietly(SocketChannel channel) {
            try {
                channel.close();
            } catch (IOException alreadyReclaimed) {
                // Closing a channel the deadline already closed is the expected path here.
            }
        }
    }
}
