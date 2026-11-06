package com.morpheus.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * A technology family, the one vocabulary the family gates read: the types it is reached through, the calls, and the
 * fields of {@code System} it reads (ADR-0150).
 *
 * <p>{@link ApplicationTechnologyBoundaryArchitectureTest} gives each family of {@link #VOCABULARY} a regime in the
 * application, {@link CliTechnologyBoundaryArchitectureTest} one in {@code com.morpheus.cli}. A family defined once
 * cannot mean two things in two gates. {@code Path}, {@code LinkOption}, {@code IOException} and in-memory streams are
 * vocabulary, not access.</p>
 */
record TechnologyFamily(String name, Predicate<String> types, CallMatcher calls, Set<String> systemFields) {

    static final TechnologyFamily SQL = ofTypes("SQL",
            name -> name.startsWith("java.sql.") || name.startsWith("javax.sql."));
    static final TechnologyFamily JSON_LIBRARY = ofTypes("JSON library",
            name -> name.startsWith("tools.jackson.") || name.startsWith("com.fasterxml."));
    static final TechnologyFamily PROCESS_EXECUTION = ofTypes("process execution",
            name -> Set.of("java.lang.ProcessBuilder", "java.lang.Process", "java.lang.ProcessHandle",
                    "java.lang.Runtime").contains(name));
    static final TechnologyFamily OUTBOUND_NETWORK = ofTypes("outbound network",
            name -> name.startsWith("java.net.http.") || name.startsWith("javax.net.")
                    || Set.of("java.net.URL", "java.net.URLConnection", "java.net.HttpURLConnection",
                    "java.net.Socket", "java.net.ServerSocket", "java.net.DatagramSocket",
                    "java.net.InetAddress").contains(name));
    static final TechnologyFamily THREADS = new TechnologyFamily("threads",
            name -> Set.of("java.util.concurrent.Executors", "java.util.concurrent.ExecutorService",
                    "java.util.concurrent.ScheduledExecutorService", "java.util.concurrent.ThreadPoolExecutor",
                    "java.util.concurrent.ForkJoinPool", "java.util.concurrent.CompletableFuture",
                    "java.lang.Thread$Builder", "java.util.Timer").contains(name),
            (owner, method, arity) -> owner.equals("java.lang.Thread")
                    && Set.of("<init>", "ofVirtual", "ofPlatform", "startVirtualThread").contains(method),
            Set.of());
    static final TechnologyFamily FILE_ACCESS = ofTypes("file access",
            name -> Set.of("java.nio.file.Files", "java.nio.file.FileSystems", "java.nio.file.FileSystem",
                    "java.nio.file.WatchService", "java.nio.file.WatchKey", "java.nio.file.DirectoryStream",
                    "java.io.File", "java.io.FileInputStream", "java.io.FileOutputStream", "java.io.FileReader",
                    "java.io.FileWriter", "java.io.RandomAccessFile").contains(name)
                    || name.startsWith("java.nio.file.attribute.") || name.startsWith("java.nio.file.spi.")
                    || name.startsWith("java.nio.channels."));
    static final TechnologyFamily PROCESS_ENVIRONMENT = ofCalls("process environment",
            (owner, method, arity) -> owner.equals("java.lang.System")
                    && Set.of("getenv", "getProperty", "getProperties", "setProperty").contains(method));
    static final TechnologyFamily STANDARD_STREAMS = new TechnologyFamily("standard streams", name -> false,
            (owner, method, arity) -> owner.equals("java.lang.System")
                    && Set.of("console", "setOut", "setErr", "setIn").contains(method),
            Set.of("out", "err", "in"));
    static final TechnologyFamily WALL_CLOCK = ofCalls("wall clock", (owner, method, arity) ->
            (owner.startsWith("java.time.") && method.equals("now") && arity == 0)
                    || (owner.equals("java.time.Clock") && method.startsWith("system"))
                    || (owner.equals("java.lang.System") && method.equals("currentTimeMillis")));
    static final TechnologyFamily REFLECTION_AND_CLASS_LOADING = new TechnologyFamily("reflection and class loading",
            name -> name.startsWith("java.lang.reflect.") || name.startsWith("java.lang.invoke.")
                    || Set.of("java.util.ServiceLoader", "java.lang.ClassLoader", "java.net.URLClassLoader")
                    .contains(name),
            (owner, method, arity) -> owner.equals("java.lang.Class") && method.equals("forName"),
            Set.of());

    /** Every family a family gate gives a regime to, in a stable order. */
    static final List<TechnologyFamily> VOCABULARY = List.of(SQL, JSON_LIBRARY, PROCESS_EXECUTION, OUTBOUND_NETWORK,
            THREADS, FILE_ACCESS, PROCESS_ENVIRONMENT, STANDARD_STREAMS, WALL_CLOCK, REFLECTION_AND_CLASS_LOADING);

    @FunctionalInterface
    interface CallMatcher {
        boolean matches(String owner, String method, int arity);
    }

    static TechnologyFamily ofTypes(String name, Predicate<String> types) {
        return new TechnologyFamily(name, types, (owner, method, arity) -> false, Set.of());
    }

    static TechnologyFamily ofCalls(String name, CallMatcher calls) {
        return new TechnologyFamily(name, typeName -> false, calls, Set.of());
    }

    boolean reachedBy(JavaClass origin) {
        return !accessesOf(origin).isEmpty();
    }

    List<String> accessesOf(JavaClass origin) {
        Stream<String> typeAccesses = origin.getDirectDependenciesFromSelf().stream()
                .filter(dependency -> types.test(dependency.getTargetClass().getName()))
                .map(dependency -> dependency.getDescription());
        Stream<String> callAccesses = origin.getCodeUnitCallsFromSelf().stream()
                .filter(call -> calls.matches(call.getTargetOwner().getName(), call.getName(),
                        call.getTarget().getRawParameterTypes().size()))
                .map(call -> call.getDescription());
        Stream<String> fieldAccesses = origin.getFieldAccessesFromSelf().stream()
                .filter(access -> access.getTargetOwner().getName().equals("java.lang.System")
                        && systemFields.contains(access.getName()))
                .map(access -> access.getDescription());
        return Stream.of(typeAccesses, callAccesses, fieldAccesses).flatMap(stream -> stream).distinct().sorted()
                .toList();
    }

    /** The condition a rule refuses with {@code never(...)}: each access is an event that names it. */
    ArchCondition<JavaClass> reach() {
        return new ArchCondition<>("reach " + name) {
            @Override
            public void check(JavaClass item, ConditionEvents events) {
                List<String> accesses = accessesOf(item);
                if (accesses.isEmpty()) {
                    events.add(SimpleConditionEvent.violated(item, item.getName() + " does not reach " + name));
                }
                accesses.forEach(access -> events.add(SimpleConditionEvent.satisfied(item,
                        item.getName() + " reaches " + name + " through " + access)));
            }
        };
    }

    /** A nested, anonymous or lambda-holding class belongs to the top-level class that declares it. */
    static String topLevelName(JavaClass javaClass) {
        String name = javaClass.getName();
        int nested = name.indexOf('$');
        return nested < 0 ? name : name.substring(0, nested);
    }
}
