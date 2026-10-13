package com.morpheus.cli;

import com.morpheus.application.operability.LocalOperationalRuntime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

/**
 * A process that logs the way an embedded library does, and writes nothing itself. Given a directory, it first names
 * it as the directory of the operational log file, as the launcher does.
 */
final class FixtureThirdPartyLogProcess {
    private FixtureThirdPartyLogProcess() {
    }

    public static void main(String[] arguments) {
        if (arguments.length == 1) {
            LocalOperationalRuntime.writeLogFileIn(Path.of(arguments[0]));
        }
        Logger logger = LoggerFactory.getLogger("io.example.Fixture");
        logger.info("connected");
        logger.warn("handshake with {} failed", "peer");
    }
}
