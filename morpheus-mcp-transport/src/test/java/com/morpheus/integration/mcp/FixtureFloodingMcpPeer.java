package com.morpheus.integration.mcp;

import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/** Child-process fixture that emits more inbound messages than one permanently active handler budget permits. */
public final class FixtureFloodingMcpPeer {
    private FixtureFloodingMcpPeer() {
    }

    public static void main(String[] args) throws Exception {
        PrintWriter output = new PrintWriter(System.out, true, StandardCharsets.UTF_8);
        output.println("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/flood\",\"params\":{\"sequence\":1}}");
        output.println("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/flood\",\"params\":{\"sequence\":2}}");
        TimeUnit.SECONDS.sleep(30);
    }
}
