package com.morpheus.cli;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** A process that logs the way an embedded library does, and writes nothing itself. */
final class FixtureThirdPartyLogProcess {
    private FixtureThirdPartyLogProcess() {
    }

    public static void main(String[] arguments) {
        Logger logger = LoggerFactory.getLogger("io.example.Fixture");
        logger.info("connected");
        logger.warn("handshake with {} failed", "peer");
    }
}
