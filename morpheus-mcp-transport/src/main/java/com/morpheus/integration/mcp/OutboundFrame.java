package com.morpheus.integration.mcp;

import java.util.Arrays;
import java.util.Objects;

/** A single encoded outbound MCP STDIO frame, shared by the client and server bounded transports. */
record OutboundFrame(byte[] encoded) {
    OutboundFrame {
        encoded = Objects.requireNonNull(encoded, "encoded");
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof OutboundFrame that)) return false;
        return Arrays.equals(encoded, that.encoded);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(encoded);
    }

    @Override
    public String toString() {
        return "OutboundFrame[encoded=" + Arrays.toString(encoded) + "]";
    }
}
