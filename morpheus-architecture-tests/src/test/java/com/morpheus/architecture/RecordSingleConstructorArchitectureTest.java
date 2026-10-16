package com.morpheus.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A record of the product offers one public way to be constructed: its canonical constructor.
 *
 * <p>A second public constructor fills in a component the caller did not choose -- {@code UNKNOWN} semantics, an empty
 * list, an identity that never expires -- and the call site does not say so. A caller that wants such a value asks a
 * named static factory for it, and the name states the assumption.</p>
 *
 * <p>The property is structural, so it is read on the compiled records: each public constructor is compared to the
 * record's components. A scan for the word a Javadoc used to carry would forbid a comment, and would be satisfied by
 * deleting it. The set judged is the production classes this module imports; {@link ImportedClasspathCompletenessTest}
 * holds that set to the reactor and names the one module absent from it.</p>
 */
class RecordSingleConstructorArchitectureTest {

    private static final JavaClasses PRODUCTION = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.morpheus");

    /** Records that still declare a second public constructor, by binary name, each with the reason. Only shrinks. */
    private static final Map<String, String> RECORDS_WITH_A_SECOND_PUBLIC_CONSTRUCTOR = Map.of();

    @Test
    void everyRecordOfTheProductDeclaresNoPublicConstructorBesideItsCanonicalOne() {
        List<Class<?>> records = productionRecords();
        assertTrue(records.size() > 200,
                "too few records examined (" + records.size() + "): the rule would pass on nothing");

        Set<String> violations = new TreeSet<>();
        for (Class<?> record : records) {
            if (!RECORDS_WITH_A_SECOND_PUBLIC_CONSTRUCTOR.containsKey(record.getName())) {
                violations.addAll(secondPublicConstructors(record));
            }
        }
        assertEquals(Set.of(), violations,
                "a record is constructed through its canonical constructor; replace each of these by a named static "
                        + "factory that states what it assumes, or remove it when no production class calls it");
    }

    @Test
    void everyListedRecordStillDeclaresASecondPublicConstructor() {
        Map<String, Class<?>> byName = productionRecords().stream()
                .collect(Collectors.toMap(Class::getName, record -> record));
        Set<String> stale = new TreeSet<>();
        RECORDS_WITH_A_SECOND_PUBLIC_CONSTRUCTOR.forEach((name, reason) -> {
            assertFalse(reason.isBlank(), "a record listed with a second public constructor needs its reason: " + name);
            Class<?> record = byName.get(name);
            if (record == null || secondPublicConstructors(record).isEmpty()) {
                stale.add(name);
            }
        });
        assertEquals(Set.of(), stale,
                "these listed records no longer declare a second public constructor, or no longer exist: remove them "
                        + "from RECORDS_WITH_A_SECOND_PUBLIC_CONSTRUCTOR");
    }

    @Test
    void aSecondPublicConstructorIsReportedWithItsSignatureAndANonPublicOneIsNot() {
        assertEquals(Set.of(), secondPublicConstructors(CanonicalOnly.class));
        assertEquals(Set.of(), secondPublicConstructors(CompactCanonical.class));
        assertEquals(Set.of(), secondPublicConstructors(PrivateSecond.class));
        assertEquals(
                Set.of(PublicSecond.class.getName() + "(String)"),
                secondPublicConstructors(PublicSecond.class));
        assertEquals(
                Set.of(SameArityOtherTypes.class.getName() + "(int, int)"),
                secondPublicConstructors(SameArityOtherTypes.class));
    }

    private static List<Class<?>> productionRecords() {
        return PRODUCTION.stream()
                .filter(JavaClass::isRecord)
                .<Class<?>>map(JavaClass::reflect)
                .toList();
    }

    static Set<String> secondPublicConstructors(Class<?> record) {
        List<Class<?>> canonical = Arrays.stream(record.getRecordComponents())
                .<Class<?>>map(RecordComponent::getType)
                .toList();
        Set<String> second = new TreeSet<>();
        for (Constructor<?> constructor : record.getConstructors()) {
            List<Class<?>> parameters = List.of(constructor.getParameterTypes());
            if (!parameters.equals(canonical)) {
                second.add(record.getName() + parameters.stream()
                        .map(Class::getSimpleName)
                        .collect(Collectors.joining(", ", "(", ")")));
            }
        }
        return second;
    }

    public record CanonicalOnly(String name, int count) {
    }

    public record CompactCanonical(String name, int count) {
        public CompactCanonical {
            if (count < 0) {
                throw new IllegalArgumentException("count must not be negative");
            }
        }
    }

    public record PrivateSecond(String name, int count) {
        private PrivateSecond(String name) {
            this(name, 0);
        }

        static PrivateSecond withoutCount(String name) {
            return new PrivateSecond(name);
        }
    }

    public record PublicSecond(String name, int count) {
        public PublicSecond(String name) {
            this(name, 0);
        }
    }

    public record SameArityOtherTypes(String name, int count) {
        public SameArityOtherTypes(int name, int count) {
            this(Integer.toString(name), count);
        }
    }
}
