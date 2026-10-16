package com.morpheus.coverage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A test of this reactor may be skipped only because JUnit disabled it on the running operating system.
 *
 * <p>This is the rule {@code scripts/validate-m20.ps1} applies to the reactor of the release rehearsal
 * ({@code Get-UnexplainedSkips}). The rehearsal runs at night only, so a test that skipped through an assumption on
 * Windows merged green and broke three rehearsals in a row before anyone read it (14/10 to 16/10/2026). Applied here,
 * after every other module of {@code clean verify} has written its Surefire reports, the same skip fails the pull
 * request on the platform where it happens.</p>
 *
 * <p>The reports are read rather than the sources because the property is one of an execution: an assumption over a
 * capability probe -- a symbolic link that cannot be created, a principal that cannot be resolved -- skips on one
 * machine and not on another, and no reading of its source tells which. A condition that only holds on one operating
 * system is declared with {@code @EnabledOnOs} / {@code @DisabledOnOs}, whose reason JUnit records with the prefix
 * accepted below.</p>
 *
 * <p>The module this gate runs in is outside its own reach: its reports are written after it. It holds no
 * assumption.</p>
 */
class SkippedTestReasonGateTest {
    private static final String OPERATING_SYSTEM_REASON = "Disabled on operating system";
    private static final String THIS_MODULE = "morpheus-coverage-report";

    @Test
    void everyTestThisReactorSkippedWasDisabledByAnOperatingSystemCondition() throws Exception {
        Path root = repoRoot();
        List<String> unexplained = unexplainedSkips(root, testedPopulation(root));

        assertTrue(unexplained.isEmpty(), () -> "these tests were skipped for another reason than an operating-system "
                + "condition, which the release rehearsal refuses (validate-m20.ps1). Declare a condition that only "
                + "holds on one operating system with @EnabledOnOs or @DisabledOnOs; an assumption that failed on "
                + "this machine means the test proved nothing here: " + String.join("; ", unexplained));
    }

    @Test
    void anOperatingSystemConditionIsAcceptedAndEveryOtherSkipIsNamedWithItsReason(@TempDir Path reactor)
            throws Exception {
        declareModules(reactor, "alpha");
        writeReport(reactor, "alpha", "com.example.AlphaTest", """
                <testcase name="windowsOnly" classname="com.example.AlphaTest">
                  <skipped message="Disabled on operating system: Linux ==&gt; NTFS junctions exist only on Windows"/>
                </testcase>
                <testcase name="posixOnly" classname="com.example.AlphaTest">
                  <skipped type="org.opentest4j.TestAbortedException"><![CDATA[org.opentest4j.TestAbortedException: Assumption failed: assumption is not true
                ]]></skipped>
                </testcase>
                <testcase name="switchedOff" classname="com.example.AlphaTest">
                  <skipped message="not ready"/>
                </testcase>
                <testcase name="ran" classname="com.example.AlphaTest"/>
                """);

        List<String> unexplained = unexplainedSkips(reactor, testedPopulation(reactor));

        assertEquals(List.of(
                "com.example.AlphaTest.posixOnly (org.opentest4j.TestAbortedException: Assumption failed: assumption is not true)",
                "com.example.AlphaTest.switchedOff (not ready)"), unexplained);
    }

    @Test
    void aTestedModuleWithoutReportsIsRefusedRatherThanReadAsHavingSkippedNothing(@TempDir Path reactor)
            throws Exception {
        declareModules(reactor, "alpha", "beta");
        writeReport(reactor, "alpha", "com.example.AlphaTest", "<testcase name=\"ran\" classname=\"com.example.AlphaTest\"/>");

        AssertionError refused = assertThrows(AssertionError.class,
                () -> unexplainedSkips(reactor, testedPopulation(reactor)));

        assertTrue(refused.getMessage().startsWith("no Surefire report was written by these tested modules"),
                refused.getMessage());
        assertTrue(refused.getMessage().endsWith(": beta"), refused.getMessage());
    }

    @Test
    void thePopulationIsEveryDeclaredModuleWithTestSourcesButThisOne(@TempDir Path reactor) throws Exception {
        declareModules(reactor, "alpha", "untested", THIS_MODULE);
        Files.delete(reactor.resolve("untested/src/test/java/SomeTest.java"));

        assertEquals(List.of("alpha"), testedPopulation(reactor));
    }

    private static List<String> unexplainedSkips(Path root, List<String> population) throws Exception {
        List<String> unexplained = new ArrayList<>();
        List<String> withoutReports = new ArrayList<>();
        for (String module : population) {
            List<Path> reports = reports(root.resolve(module).resolve("target/surefire-reports"));
            if (reports.isEmpty()) {
                withoutReports.add(module);
            }
            for (Path report : reports) {
                NodeList cases = parse(report).getElementsByTagName("testcase");
                for (int index = 0; index < cases.getLength(); index++) {
                    Element testCase = (Element) cases.item(index);
                    NodeList skipped = testCase.getElementsByTagName("skipped");
                    if (skipped.getLength() == 0) {
                        continue;
                    }
                    String reason = reason((Element) skipped.item(0));
                    if (!reason.startsWith(OPERATING_SYSTEM_REASON)) {
                        unexplained.add(testCase.getAttribute("classname") + "." + testCase.getAttribute("name")
                                + " (" + reason + ")");
                    }
                }
            }
        }
        if (!withoutReports.isEmpty()) {
            throw new AssertionError("no Surefire report was written by these tested modules, so what they skipped "
                    + "cannot be read -- run ./mvnw clean verify over the whole reactor: "
                    + String.join(", ", withoutReports));
        }
        return unexplained;
    }

    /** A condition puts its reason in the message; an aborted assumption leaves it empty and writes its stack trace. */
    private static String reason(Element skipped) {
        String message = skipped.getAttribute("message");
        if (!message.isEmpty()) {
            return message;
        }
        return skipped.getTextContent().strip().lines().findFirst().orElse("no reason recorded");
    }

    private static List<Path> reports(Path directory) throws IOException {
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        try (var files = Files.list(directory)) {
            return files.filter(file -> {
                String name = file.getFileName().toString();
                return name.startsWith("TEST-") && name.endsWith(".xml");
            }).sorted().toList();
        }
    }

    private static List<String> testedPopulation(Path root) throws Exception {
        List<String> population = new ArrayList<>();
        Node child = parse(root.resolve("pom.xml")).getDocumentElement().getFirstChild();
        while (child != null) {
            if (child instanceof Element element && element.getTagName().equals("modules")) {
                Node declared = element.getFirstChild();
                while (declared != null) {
                    if (declared instanceof Element module && module.getTagName().equals("module")) {
                        String name = module.getTextContent().trim();
                        if (!name.equals(THIS_MODULE) && hasTestSources(root.resolve(name))) {
                            population.add(name);
                        }
                    }
                    declared = declared.getNextSibling();
                }
            }
            child = child.getNextSibling();
        }
        assertFalse(population.isEmpty(), () -> "no reactor module with test sources is declared under " + root);
        return List.copyOf(population);
    }

    private static boolean hasTestSources(Path module) throws IOException {
        Path sources = module.resolve("src/test/java");
        if (!Files.isDirectory(sources)) {
            return false;
        }
        try (var files = Files.walk(sources)) {
            return files.anyMatch(path -> path.getFileName().toString().endsWith(".java") && Files.isRegularFile(path));
        }
    }

    private static void declareModules(Path reactor, String... modules) throws IOException {
        StringBuilder pom = new StringBuilder("<project><modules>");
        for (String module : modules) {
            pom.append("<module>").append(module).append("</module>");
            Path sources = Files.createDirectories(reactor.resolve(module).resolve("src/test/java"));
            Files.writeString(sources.resolve("SomeTest.java"), "class SomeTest {}");
        }
        Files.writeString(reactor.resolve("pom.xml"), pom.append("</modules></project>").toString());
    }

    private static void writeReport(Path reactor, String module, String suite, String cases) throws IOException {
        Path reports = Files.createDirectories(reactor.resolve(module).resolve("target/surefire-reports"));
        Files.writeString(reports.resolve("TEST-" + suite + ".xml"),
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<testsuite name=\"" + suite + "\">\n" + cases
                        + "</testsuite>\n");
    }

    private static org.w3c.dom.Document parse(Path file) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        factory.setExpandEntityReferences(false);
        var builder = factory.newDocumentBuilder();
        builder.setEntityResolver((publicId, systemId) -> new InputSource(new StringReader("")));
        return builder.parse(file.toFile());
    }

    private static Path repoRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null) {
            if (Files.isRegularFile(current.resolve("pom.xml"))
                    && Files.isDirectory(current.resolve("morpheus-application"))
                    && Files.isDirectory(current.resolve("distribution"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("MORPHEUS repository root not found");
    }
}
