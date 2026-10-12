package com.morpheus.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Inside the HTTP adapter, the text of a failure reaches a response through one class.
 *
 * <p>{@code BoundaryFailureMessage} replaces a message that names a server location by the simple name of the failure.
 * Four routers bypassed it with a private helper of their own, which kept every non-empty message: about twenty
 * routes relayed a pathname raised from below a service to the caller, and through the remote facade to a caller
 * outside the machine. The helper had the same name in each of them and nothing compared it with the filter.</p>
 *
 * <p>Two holds, because they do not see the same thing (ADR-0103). The rule reads the bytecode: a class of the adapter
 * that calls {@code getMessage()} on anything but a failure MORPHEUS authors is refused, whatever the helper is called.
 * The text refuses the helper's name in the adapter's sources, which is what a reviewer greps for.</p>
 */
class HttpFailureDisclosureArchitectureTest {
    private static final String ADAPTER_PACKAGE = "com.morpheus.api";
    private static final String FILTER = "com.morpheus.api.BoundaryFailureMessage";

    /** Failure types whose message is written by MORPHEUS for the caller, each with the reason it needs no filter. */
    private static final Map<String, String> AUTHORED_FOR_THE_CALLER = Map.of(
            "com.morpheus.api.ApiFailure",
            "built by the adapter with a status, a code and a message meant for the response; a foreign message enters "
                    + "it only through the filter",
            "com.morpheus.api.MorpheusRemoteHttpServer$RemoteFailure",
            "the remote facade's own refusal, built from an ApiFailure or from a literal",
            "com.morpheus.api.MorpheusRemoteRoutePolicy$RoutePolicyException",
            "a refusal of the remote route policy: a status, a code and one of three literal messages",
            "com.morpheus.api.MorpheusRemoteProxyTargetResolver$ResolutionException",
            "a refusal to resolve the proxied target, built from a code and a message the resolver writes",
            "com.morpheus.api.MorpheusRemoteProxyTransport$TransportException",
            "a failure of the upstream hop reported under the facade's own code and message, never the upstream's");

    private final JavaClasses classes = new ClassFileImporter().importPackages(ADAPTER_PACKAGE);

    @Test
    void onlyTheBoundaryFilterReadsTheMessageOfAFailureMorpheusDidNotAuthor() {
        List<String> offenders = new ArrayList<>();
        Set<String> authoredTypesRead = new TreeSet<>();
        int adapterClasses = 0;
        for (JavaClass type : classes) {
            if (!type.getPackageName().equals(ADAPTER_PACKAGE)) {
                continue;
            }
            adapterClasses++;
            for (JavaMethodCall call : type.getMethodCallsFromSelf()) {
                if (!call.getTarget().getName().equals("getMessage")
                        || !call.getTargetOwner().isAssignableTo(Throwable.class)) {
                    continue;
                }
                String failureType = call.getTargetOwner().getName();
                if (AUTHORED_FOR_THE_CALLER.containsKey(failureType)) {
                    authoredTypesRead.add(failureType);
                } else if (!type.getName().equals(FILTER)) {
                    offenders.add(call.getOrigin().getFullName() + " reads the message of " + failureType);
                }
            }
        }

        assertTrue(adapterClasses > 20, "too few classes of the HTTP adapter imported (" + adapterClasses
                + "): the rule would pass on nothing");
        assertTrue(offenders.isEmpty(), () -> "a failure message reaches the adapter's own code without passing "
                + "through BoundaryFailureMessage.safe; a message that names a server location would be relayed: "
                + offenders);
        AUTHORED_FOR_THE_CALLER.forEach((failureType, reason) -> {
            assertFalse(reason.isBlank(), "an authored failure type needs the reason it is exempt: " + failureType);
            assertTrue(authoredTypesRead.contains(failureType),
                    "an exemption names a failure type whose message nothing reads any more: " + failureType);
        });
    }

    @Test
    void noSourceOfTheAdapterDeclaresAMessageHelperOfItsOwn() throws IOException {
        Path sources = repositoryRoot().resolve("morpheus-api/src/main/java/com/morpheus/api");
        List<String> offenders = new ArrayList<>();
        int scanned = 0;
        try (Stream<Path> files = Files.list(sources)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).sorted().toList()) {
                scanned++;
                if (Files.readString(file).contains("safeMessage")) {
                    offenders.add(file.getFileName().toString());
                }
            }
        }

        assertTrue(scanned > 20, "too few sources of the HTTP adapter scanned (" + scanned + ")");
        assertTrue(offenders.isEmpty(), () -> "a private safeMessage keeps every non-empty message, pathnames "
                + "included; use BoundaryFailureMessage.safe: " + offenders);
    }

    private static Path repositoryRoot() throws IOException {
        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null) {
            if (Files.isRegularFile(current.resolve("contracts/public-surfaces.tsv"))
                    && Files.isRegularFile(current.resolve("pom.xml"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IOException("cannot locate MORPHEUS repository root from " + Path.of("").toAbsolutePath());
    }
}
