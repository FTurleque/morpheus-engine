package com.morpheus.architecture.d2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.morpheus.architecture.CoherenceCheck;
import com.morpheus.architecture.RepositoryRoot;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * A rule of the Dependency-Check suppression file removes only what it declares, on an anchored target, with its reason
 * written down.
 *
 * <p>The suppression file switches detections of the CVE gate off, and the scan is the only other reader of its
 * content: a widened rule, a rule of a new form or a second exemption from {@code failBuildOnUnusedSuppressionRule}
 * would otherwise enter on review alone (AUD-TST-21). Two forms are admitted. An association rule is one anchored
 * {@code <packageUrl regex="true">} that matches exactly {@code pkg:maven/io.github.fturleque/<module>@<version>} for a
 * module and the version of the root POM, and one {@code <cpe>} naming a vendor and a product. A named-vulnerability
 * rule is one {@code <filePath regex="true">} that matches the native libraries extracted from the
 * {@code sqlite-jdbc} JAR at {@code <sqlite-jdbc.version>} and neither the driver JAR nor MORPHEUS code, and one
 * {@code <vulnerabilityName>} that is a CVE identifier: the narrow form AUD-DEP-27 settled on, which this contract
 * keeps from being widened. Every rule carries non-blank notes, and the notes of a {@code base="true"} rule name
 * {@code failBuildOnUnusedSuppressionRule}. The exempted rules equal {@link #EXEMPTIONS}, a table of facts that grows
 * only by a diff of this class.</p>
 *
 * <p>The form bounds what one rule removes; {@link #SUPPRESSED_DETECTIONS} bounds how many there are. It is the exact
 * set of the targets the file switches off, compared in both directions, and its size equals the key
 * {@value #SUPPRESSED_DETECTIONS_KEY} of {@value #SUPPRESSION_RATCHETS}, which is only ever lowered: a well-formed rule
 * added to the file fails until the table names it and the key is raised, and the pull request justifies that raise.
 * A rule removed lowers the key in the same change, so the extent cannot grow back to it.</p>
 *
 * <p>Intent: a property of an XML configuration file, where the text is the only hold - parsed, not a bytecode rule.
 * Problems are collected and reported together, so a change that breaks two rules sees both.</p>
 */
@CoherenceCheck
class DependencyCheckSuppressionContractTest {

    private static final String SUPPRESSIONS = "config/dependency-check-suppressions.xml";

    private static final String UNUSED_RULE_CHECK = "failBuildOnUnusedSuppressionRule";

    private static final String PURL_PREFIX = "pkg:maven/io.github.fturleque/";

    /**
     * The rules exempted from {@code failBuildOnUnusedSuppressionRule}, each identified by its target without the
     * reactor version: the module and the CPE of an association rule, the CVE of a named-vulnerability rule.
     */
    private static final Set<String> EXEMPTIONS = Set.of(
            "morpheus-store-sqlite cpe:/a:sqlite:sqlite",
            "CVE-2026-119586");

    /**
     * Every detection the file switches off, each identified as {@link #EXEMPTIONS} identifies its rules. Growing this
     * set raises {@value #SUPPRESSED_DETECTIONS_KEY}; the reason of each entry is in the notes of its rule.
     */
    private static final Set<String> SUPPRESSED_DETECTIONS = Set.of(
            "morpheus-store-sqlite cpe:/a:sqlite:sqlite",
            "CVE-2026-119586",
            "morpheus-cli cpe:/a:github:cli");

    static final String SUPPRESSION_RATCHETS = "config/dependency-check-suppression-ratchets.properties";

    static final String SUPPRESSED_DETECTIONS_KEY = "suppressedDetections";

    private static final Set<String> ADMITTED_ATTRIBUTES = Set.of("base", "until");

    private static final Pattern CVE = Pattern.compile("CVE-[0-9]{4}-[0-9]{4,}");

    private static final Pattern VENDOR_AND_PRODUCT_CPE = Pattern.compile("cpe:/a:[a-z0-9_.\\-]+:[a-z0-9_.\\-]+");

    private static final String REGEX_METACHARACTERS = ".^$|?*+()[]{}\\";

    /** What the contract reads from the root POM. */
    record Reactor(Set<String> modules, String version, String driverVersion) {
    }

    @Test
    void theSuppressionFileOfTheRevisionHoldsToTheContract() throws Exception {
        Path root = RepositoryRoot.locate();
        Document suppressions = parse(Files.readAllBytes(root.resolve(SUPPRESSIONS)));

        assertEquals(List.of(), problems(suppressions, reactor(root), EXEMPTIONS),
                SUPPRESSIONS + " must hold to the suppression contract");
    }

    @Test
    void theDetectionsTheFileSwitchesOffAreExactlyTheTableAndTheTableHoldsTheSizeItsRatchetAllows() throws Exception {
        Path root = RepositoryRoot.locate();
        Document suppressions = parse(Files.readAllBytes(root.resolve(SUPPRESSIONS)));
        Properties ratchets = new Properties();
        try (var reader = Files.newBufferedReader(root.resolve(SUPPRESSION_RATCHETS))) {
            ratchets.load(reader);
        }
        assertEquals(Set.of(SUPPRESSED_DETECTIONS_KEY), ratchets.stringPropertyNames(),
                SUPPRESSION_RATCHETS + " holds exactly the key " + SUPPRESSED_DETECTIONS_KEY);

        List<String> problems = new ArrayList<>(extentProblems(suppressions, reactor(root), SUPPRESSED_DETECTIONS));
        problems.addAll(ratchetProblems(SUPPRESSED_DETECTIONS.size(),
                Integer.parseInt(ratchets.getProperty(SUPPRESSED_DETECTIONS_KEY).trim())));
        assertEquals(List.of(), problems, "the extent of " + SUPPRESSIONS + " is the table of suppressed detections, "
                + "whose size is the key of " + SUPPRESSION_RATCHETS);
    }

    @Test
    void aWellFormedRuleOutsideTheTableIsRefusedNamingItsTargetAndAnEntryWithoutRuleIsNamed() throws Exception {
        Document file = parse(suppressions(
                association("^pkg:maven/io\\.github\\.fturleque/morpheus-cli@9\\.8\\.7$", "cpe:/a:github:cli")
                        + association("^pkg:maven/io\\.github\\.fturleque/morpheus-api@9\\.8\\.7$",
                                "cpe:/a:apache:api")
                        + namedVulnerability(
                                ".*sqlite-jdbc-[0-9][0-9.]*\\.jar[/\\\\].*sqlitejdbc\\.(?:dll|so|dylib|jnilib)$",
                                "CVE-2026-0003")));
        List<String> problems = extentProblems(file, FICTIONAL,
                Set.of("morpheus-cli cpe:/a:github:cli", "CVE-2026-0002"));

        assertEquals(List.of(), problems(file, FICTIONAL, Set.of()), "every rule of the fabricated file is well formed");
        assertNamed(problems, "rule #2", "morpheus-api cpe:/a:apache:api", "outside the table");
        assertNamed(problems, "rule #3", "CVE-2026-0003", "outside the table");
        assertNamed(problems, "CVE-2026-0002", "no rule");
        assertEquals(3, problems.size(), problems::toString);
    }

    @Test
    void aTableLargerThanItsKeyIsRefusedAsGrownAndASmallerOneAsALowerKeyToWrite() {
        assertEquals(List.of(), ratchetProblems(3, 3));
        assertNamed(ratchetProblems(4, 3), "holds 4", "key of 3", "grew");
        assertNamed(ratchetProblems(2, 3), "holds 2", "key of 3", "lower");
    }

    @Test
    void aRuleWithoutCpeIsRefusedAsAnUnadmittedForm() throws Exception {
        List<String> problems = problemsOf(rule("",
                "<packageUrl regex=\"true\">^pkg:maven/io\\.github\\.fturleque/morpheus-cli@9\\.8\\.7$</packageUrl>"));

        assertNamed(problems, "rule #1", "refused form", "[packageUrl]");
    }

    @Test
    void everyOtherMatchingElementOfTheSchemaIsRefusedAsAnUnadmittedForm() throws Exception {
        String purl = "<packageUrl regex=\"true\">^pkg:maven/io\\.github\\.fturleque/morpheus-cli@9\\.8\\.7$</packageUrl>";
        assertNamed(problemsOf(rule("", purl + "<cpe>cpe:/a:github:cli</cpe><cvssBelow>7</cvssBelow>")),
                "rule #1", "refused form", "cvssBelow");
        assertNamed(problemsOf(rule("", "<sha1>0000000000000000000000000000000000000000</sha1>"
                + "<cpe>cpe:/a:github:cli</cpe>")), "rule #1", "refused form", "sha1");
        assertNamed(problemsOf(rule("", "<gav regex=\"true\">^io\\.github\\.fturleque:morpheus-cli:.*$</gav>"
                + "<cpe>cpe:/a:github:cli</cpe>")), "rule #1", "refused form", "gav");
        assertNamed(problemsOf(rule("", purl + "<cpe>cpe:/a:github:cli</cpe><cpe>cpe:/a:sqlite:sqlite</cpe>")),
                "rule #1", "refused form", "[packageUrl, cpe, cpe]");
        assertNamed(problemsOf(rule("", purl + "<vulnerabilityName>CVE-2026-0001</vulnerabilityName>")),
                "rule #1", "refused form", "[packageUrl, vulnerabilityName]");
    }

    @Test
    void anAttributeOtherThanBaseAndUntilIsRefused() throws Exception {
        List<String> problems = problemsOf("<suppress unknown=\"x\"><notes>reason</notes>"
                + "<packageUrl regex=\"true\">^pkg:maven/io\\.github\\.fturleque/morpheus-cli@9\\.8\\.7$</packageUrl>"
                + "<cpe>cpe:/a:github:cli</cpe></suppress>");

        assertNamed(problems, "rule #1", "attribute unknown");
    }

    @Test
    void untilIsAdmittedWithoutBeingRequired() throws Exception {
        List<String> problems = problemsOf("<suppress until=\"2027-01-01Z\"><notes>reason</notes>"
                + "<packageUrl regex=\"true\">^pkg:maven/io\\.github\\.fturleque/morpheus-cli@9\\.8\\.7$</packageUrl>"
                + "<cpe>cpe:/a:github:cli</cpe></suppress>");

        assertEquals(List.of(), problems);
    }

    @Test
    void aFileWithoutAnyRuleDoesNotPassEmpty() throws Exception {
        List<String> problems = problemsOf("");

        assertNamed(problems, "no suppression rule was read");
    }

    @Test
    void aWidenedPackageUrlIsRefusedNamingTheMetacharacter() throws Exception {
        List<String> problems = problemsOf(association("^pkg:maven/io\\.github\\.fturleque/morpheus-.*$",
                "cpe:/a:github:cli"));

        assertNamed(problems, "rule #1", "metacharacter '.'");
    }

    @Test
    void anUnanchoredPackageUrlIsRefused() throws Exception {
        assertNamed(problemsOf(association("pkg:maven/io\\.github\\.fturleque/morpheus-cli@9\\.8\\.7$",
                "cpe:/a:github:cli")), "rule #1", "anchored");
        assertNamed(problemsOf(association("^pkg:maven/io\\.github\\.fturleque/morpheus-cli@9\\.8\\.7",
                "cpe:/a:github:cli")), "rule #1", "anchored");
    }

    @Test
    void aPackageUrlOfAThirdPartyOrOfAnUndeclaredModuleIsRefusedNamingTheCoordinate() throws Exception {
        assertNamed(problemsOf(association("^pkg:maven/org\\.xerial/sqlite-jdbc@3\\.50\\.0\\.0$",
                "cpe:/a:sqlite:sqlite")), "rule #1", "pkg:maven/org.xerial/sqlite-jdbc@3.50.0.0");
        assertNamed(problemsOf(association("^pkg:maven/io\\.github\\.fturleque/morpheus-gone@9\\.8\\.7$",
                "cpe:/a:github:cli")), "rule #1", "morpheus-gone", "not a module of the root POM");
    }

    @Test
    void aPackageUrlLeftAtAnotherVersionThanTheReactorIsRefusedNamingBoth() throws Exception {
        List<String> problems = problemsOf(association("^pkg:maven/io\\.github\\.fturleque/morpheus-cli@9\\.8\\.6$",
                "cpe:/a:github:cli"));

        assertNamed(problems, "rule #1", "version 9.8.6", "reactor version 9.8.7");
    }

    @Test
    void aCpeBroaderThanOneVendorAndOneProductIsRefused() throws Exception {
        assertNamed(problemsOf(association("^pkg:maven/io\\.github\\.fturleque/morpheus-cli@9\\.8\\.7$",
                "cpe:/a:github")), "rule #1", "cpe:/a:github", "vendor and a product");
        assertNamed(problemsOf(rule("", "<packageUrl regex=\"true\">^pkg:maven/io\\.github\\.fturleque/"
                + "morpheus-cli@9\\.8\\.7$</packageUrl><cpe regex=\"true\">cpe:/a:.*</cpe>")),
                "rule #1", "vendor and a product");
    }

    @Test
    void aFilePathThatMatchesEverythingIsRefusedNamingTheJarItWronglyMatches() throws Exception {
        List<String> problems = problemsOf(namedVulnerability(".*", "CVE-2026-0001"));

        assertNamed(problems, "rule #1", "sqlite-jdbc-3.50.0.0.jar", "must not match");
    }

    @Test
    void aFilePathThatMissesTheExtractedLibrariesIsRefused() throws Exception {
        List<String> problems = problemsOf(namedVulnerability(".*sqlite-jdbc-[0-9.]*\\.jar[/\\\\].*sqlitejdbc\\.dll$",
                "CVE-2026-0001"));

        assertNamed(problems, "rule #1", "must match", "libsqlitejdbc.so");
    }

    @Test
    void aVulnerabilityNameThatIsNotACveIsRefusedNamingTheValue() throws Exception {
        String libraries = ".*sqlite-jdbc-[0-9][0-9.]*\\.jar[/\\\\].*sqlitejdbc\\.(?:dll|so|dylib|jnilib)$";
        assertNamed(problemsOf(namedVulnerability(libraries, "shell injection")), "rule #1", "'shell injection'");
        assertNamed(problemsOf(namedVulnerability(libraries, "  ")), "rule #1", "not a CVE identifier");
    }

    @Test
    void aRuleWithoutNotesOrWithBlankNotesIsRefused() throws Exception {
        String match = "<packageUrl regex=\"true\">^pkg:maven/io\\.github\\.fturleque/morpheus-cli@9\\.8\\.7$"
                + "</packageUrl><cpe>cpe:/a:github:cli</cpe>";
        assertNamed(problemsOf("<suppress>" + match + "</suppress>"), "rule #1", "notes");
        assertNamed(problemsOf("<suppress><notes>  \n </notes>" + match + "</suppress>"), "rule #1", "notes");
    }

    @Test
    void anExemptionWhoseNotesDoNotSayWhyIsRefused() throws Exception {
        List<String> problems = problems(parse(suppressions("<suppress base=\"true\"><notes>a reason</notes>"
                        + "<packageUrl regex=\"true\">^pkg:maven/io\\.github\\.fturleque/morpheus-cli@9\\.8\\.7$"
                        + "</packageUrl><cpe>cpe:/a:github:cli</cpe></suppress>")),
                FICTIONAL, Set.of("morpheus-cli cpe:/a:github:cli"));

        assertNamed(problems, "rule #1", UNUSED_RULE_CHECK);
    }

    @Test
    void anExemptionOutsideTheTableIsRefusedNamingTheRule() throws Exception {
        List<String> problems = problems(parse(suppressions("<suppress base=\"true\"><notes>exempt from "
                        + UNUSED_RULE_CHECK + "</notes><packageUrl regex=\"true\">^pkg:maven/io\\.github\\.fturleque/"
                        + "morpheus-cli@9\\.8\\.7$</packageUrl><cpe>cpe:/a:github:cli</cpe></suppress>")),
                FICTIONAL, Set.of());

        assertNamed(problems, "rule #1", "morpheus-cli cpe:/a:github:cli", "outside the table");
    }

    @Test
    void anEntryOfTheTableLeftWithoutAnExemptedRuleIsRefusedNamingTheEntry() throws Exception {
        List<String> problems = problems(parse(suppressions(association(
                        "^pkg:maven/io\\.github\\.fturleque/morpheus-cli@9\\.8\\.7$", "cpe:/a:github:cli"))),
                FICTIONAL, Set.of("morpheus-cli cpe:/a:github:cli", "CVE-2026-0002"));

        assertNamed(problems, "morpheus-cli cpe:/a:github:cli", "no exempted rule");
        assertNamed(problems, "CVE-2026-0002", "no exempted rule");
    }

    @Test
    void twoRulesOnTheSameTargetAreRefused() throws Exception {
        String rule = association("^pkg:maven/io\\.github\\.fturleque/morpheus-cli@9\\.8\\.7$", "cpe:/a:github:cli");

        assertNamed(problemsOf(rule + rule), "rule #2", "same target as an earlier rule");
    }

    @Test
    void theFabricatedCorpusOfBothFormsIsAdmitted() throws Exception {
        List<String> problems = problemsOf(
                association("^pkg:maven/io\\.github\\.fturleque/morpheus-cli@9\\.8\\.7$", "cpe:/a:github:cli")
                        + namedVulnerability(
                                ".*sqlite-jdbc-[0-9][0-9.]*\\.jar[/\\\\].*sqlitejdbc\\.(?:dll|so|dylib|jnilib)$",
                                "CVE-2026-0001"));

        assertEquals(List.of(), problems);
    }

    /** A reactor that is not the real one, so that the refusals do not depend on the current versions. */
    private static final Reactor FICTIONAL = new Reactor(
            new TreeSet<>(Set.of("morpheus-cli", "morpheus-store-sqlite", "morpheus-api")), "9.8.7", "3.50.0.0");

    static List<String> problems(Document suppressions, Reactor reactor, Set<String> exemptions) {
        List<String> problems = new ArrayList<>();
        Element rootElement = suppressions.getDocumentElement();
        if (!"suppressions".equals(rootElement.getLocalName())) {
            problems.add("the root element is <" + rootElement.getLocalName() + ">, not <suppressions>");
            return problems;
        }
        List<Element> rules = children(rootElement);
        if (rules.isEmpty()) {
            problems.add("no suppression rule was read");
            return problems;
        }
        Set<String> targets = new TreeSet<>();
        Set<String> exempted = new TreeSet<>();
        for (int index = 0; index < rules.size(); index++) {
            Element rule = rules.get(index);
            String name = "rule #" + (index + 1);
            if (!"suppress".equals(rule.getLocalName())) {
                problems.add(name + ": <" + rule.getLocalName() + "> is not a <suppress>");
                continue;
            }
            boolean base = "true".equals(rule.getAttribute("base"));
            attributeProblems(rule, ADMITTED_ATTRIBUTES, name, problems);
            notesProblems(rule, base, name, problems);
            String target = target(rule, reactor, name, problems);
            if (target == null) {
                continue;
            }
            if (!targets.add(target)) {
                problems.add(name + " (" + target + "): same target as an earlier rule");
            }
            if (base) {
                exempted.add(target);
                if (!exemptions.contains(target)) {
                    problems.add(name + " (" + target + "): base=\"true\" outside the table of exemptions; an "
                            + "exemption from " + UNUSED_RULE_CHECK + " enters only by a change of this contract");
                }
            }
        }
        for (String entry : new TreeSet<>(exemptions)) {
            if (!exempted.contains(entry)) {
                problems.add("the table of exemptions names " + entry + ", which no exempted rule targets");
            }
        }
        return problems;
    }

    /**
     * The targets the file switches off against {@code table}, in both directions. A rule of a refused form has no
     * target: {@link #problems} refuses it.
     */
    static List<String> extentProblems(Document suppressions, Reactor reactor, Set<String> table) {
        List<String> problems = new ArrayList<>();
        Set<String> switchedOff = new TreeSet<>();
        List<Element> rules = children(suppressions.getDocumentElement());
        for (int index = 0; index < rules.size(); index++) {
            String name = "rule #" + (index + 1);
            String target = target(rules.get(index), reactor, name, new ArrayList<>());
            if (target == null) {
                continue;
            }
            switchedOff.add(target);
            if (!table.contains(target)) {
                problems.add(name + " (" + target + "): outside the table of suppressed detections; a detection is "
                        + "switched off only by a change of this contract that raises " + SUPPRESSED_DETECTIONS_KEY);
            }
        }
        for (String entry : new TreeSet<>(table)) {
            if (!switchedOff.contains(entry)) {
                problems.add("the table of suppressed detections names " + entry + ", which no rule targets");
            }
        }
        return problems;
    }

    /** The size of the table against its key, which is only ever lowered. */
    static List<String> ratchetProblems(int size, int key) {
        if (size > key) {
            return List.of("the table of suppressed detections holds " + size + " for a key of " + key + ": it grew; "
                    + "raise " + SUPPRESSED_DETECTIONS_KEY + " in " + SUPPRESSION_RATCHETS + " and justify it in the "
                    + "pull request");
        }
        if (size < key) {
            return List.of("the table of suppressed detections holds " + size + " for a key of " + key + ": lower "
                    + SUPPRESSED_DETECTIONS_KEY + " in the same change, so that it cannot grow back");
        }
        return List.of();
    }

    private static void notesProblems(Element rule, boolean base, String name, List<String> problems) {
        List<Element> notes = children(rule).stream().filter(child -> "notes".equals(child.getLocalName())).toList();
        if (notes.size() != 1 || notes.get(0).getTextContent().isBlank()) {
            problems.add(name + ": a rule carries exactly one <notes> with its reason, found " + notes.size()
                    + (notes.size() == 1 ? " blank" : ""));
            return;
        }
        if (base && !notes.get(0).getTextContent().contains(UNUSED_RULE_CHECK)) {
            problems.add(name + ": the notes of a base=\"true\" rule must say why it is exempted from "
                    + UNUSED_RULE_CHECK);
        }
    }

    /** The identity of the rule's target, or {@code null} with the problem recorded when the rule is refused. */
    private static String target(Element rule, Reactor reactor, String name, List<String> problems) {
        List<Element> matching = children(rule).stream().filter(child -> !"notes".equals(child.getLocalName()))
                .toList();
        List<String> form = matching.stream().map(Element::getLocalName).toList();
        if (form.equals(List.of("packageUrl", "cpe"))) {
            return associationTarget(matching.get(0), matching.get(1), reactor, name, problems);
        }
        if (form.equals(List.of("filePath", "vulnerabilityName"))) {
            return vulnerabilityTarget(matching.get(0), matching.get(1), reactor, name, problems);
        }
        problems.add(name + ": refused form, found " + form + "; admitted are [packageUrl, cpe] and "
                + "[filePath, vulnerabilityName]");
        return null;
    }

    private static String associationTarget(Element packageUrl, Element cpe, Reactor reactor, String name,
            List<String> problems) {
        int before = problems.size();
        attributeProblems(packageUrl, Set.of("regex"), name + " <packageUrl>", problems);
        attributeProblems(cpe, Set.of(), name + " <cpe>", problems);
        if (!"true".equals(packageUrl.getAttribute("regex"))) {
            problems.add(name + ": <packageUrl> must be a regular expression (regex=\"true\")");
        }
        String cpeValue = cpe.getTextContent().trim();
        if (!VENDOR_AND_PRODUCT_CPE.matcher(cpeValue).matches()) {
            problems.add(name + ": <cpe> '" + cpeValue + "' must name a vendor and a product, as cpe:/a:<vendor>:"
                    + "<product>, and nothing broader");
        }
        String expression = packageUrl.getTextContent().trim();
        if (!expression.startsWith("^") || !expression.endsWith("$") || expression.length() < 2) {
            problems.add(name + ": <packageUrl> '" + expression + "' must be anchored by ^ and $");
            return null;
        }
        String literal = literalOf(expression.substring(1, expression.length() - 1), name, problems);
        if (literal == null) {
            return null;
        }
        if (!literal.startsWith(PURL_PREFIX) || literal.indexOf('@') < 0) {
            problems.add(name + ": <packageUrl> matches " + literal + ", which is not a module of "
                    + PURL_PREFIX.substring(0, PURL_PREFIX.length() - 1));
            return null;
        }
        String coordinate = literal.substring(PURL_PREFIX.length());
        String module = coordinate.substring(0, coordinate.indexOf('@'));
        String version = coordinate.substring(coordinate.indexOf('@') + 1);
        if (!reactor.modules().contains(module)) {
            problems.add(name + ": <packageUrl> matches " + literal + ", and " + module
                    + " is not a module of the root POM");
        }
        if (!reactor.version().equals(version)) {
            problems.add(name + ": <packageUrl> carries version " + version + ", the reactor version "
                    + reactor.version() + " (root POM <version>)");
        }
        return problems.size() == before ? module + " " + cpeValue : null;
    }

    /** The only string an expression matches, or {@code null} when it holds a metacharacter other than {@code \.}. */
    private static String literalOf(String expression, String name, List<String> problems) {
        StringBuilder literal = new StringBuilder();
        for (int at = 0; at < expression.length(); at++) {
            char character = expression.charAt(at);
            if (character == '\\' && at + 1 < expression.length() && expression.charAt(at + 1) == '.') {
                literal.append('.');
                at++;
            } else if (REGEX_METACHARACTERS.indexOf(character) >= 0) {
                problems.add(name + ": <packageUrl> holds the metacharacter '" + character + "'; the only one "
                        + "admitted is the escaped dot");
                return null;
            } else {
                literal.append(character);
            }
        }
        return literal.toString();
    }

    private static String vulnerabilityTarget(Element filePath, Element vulnerabilityName, Reactor reactor,
            String name, List<String> problems) {
        int before = problems.size();
        attributeProblems(filePath, Set.of("regex"), name + " <filePath>", problems);
        attributeProblems(vulnerabilityName, Set.of(), name + " <vulnerabilityName>", problems);
        String cve = vulnerabilityName.getTextContent().trim();
        if (!CVE.matcher(cve).matches()) {
            problems.add(name + ": <vulnerabilityName> '" + cve + "' is not a CVE identifier");
        }
        if (!"true".equals(filePath.getAttribute("regex"))) {
            problems.add(name + ": <filePath> must be a regular expression (regex=\"true\")");
            return null;
        }
        Pattern pattern;
        try {
            pattern = Pattern.compile(filePath.getTextContent().trim());
        } catch (PatternSyntaxException invalid) {
            problems.add(name + ": <filePath> is not a regular expression: " + invalid.getDescription());
            return null;
        }
        String driver = "sqlite-jdbc-" + reactor.driverVersion() + ".jar";
        String linuxRepository = "/home/runner/.m2/repository/org/xerial/sqlite-jdbc/" + reactor.driverVersion() + "/";
        String windowsRepository = "C:\\Users\\runner\\.m2\\repository\\org\\xerial\\sqlite-jdbc\\"
                + reactor.driverVersion() + "\\";
        List<String> extracted = List.of(
                windowsRepository + driver + "\\org\\sqlite\\native\\Windows\\x86_64\\sqlitejdbc.dll",
                linuxRepository + driver + "/org/sqlite/native/Linux/x86_64/libsqlitejdbc.so");
        for (String library : extracted) {
            if (!pattern.matcher(library).matches()) {
                problems.add(name + ": <filePath> must match the native library extracted from the driver, "
                        + library);
            }
        }
        List<String> outside = new ArrayList<>(List.of(linuxRepository + driver, windowsRepository + driver));
        for (String module : reactor.modules()) {
            String jar = module + "-" + reactor.version() + ".jar";
            outside.add("/home/runner/work/morpheus-engine/" + module + "/target/" + jar);
            outside.add("/home/runner/work/morpheus-engine/" + module + "/target/" + jar
                    + "/org/sqlite/native/Linux/x86_64/libsqlitejdbc.so");
        }
        List<String> matched = outside.stream().filter(path -> pattern.matcher(path).matches()).toList();
        if (!matched.isEmpty()) {
            problems.add(name + ": <filePath> must not match the driver JAR nor MORPHEUS code, and matches "
                    + matched.get(0) + " (" + matched.size() + " of the " + outside.size() + " paths tried)");
        }
        return problems.size() == before ? cve : null;
    }

    private static void attributeProblems(Element element, Set<String> admitted, String name, List<String> problems) {
        NamedNodeMap attributes = element.getAttributes();
        for (int at = 0; at < attributes.getLength(); at++) {
            String attribute = ((Attr) attributes.item(at)).getName();
            if (!admitted.contains(attribute) && !attribute.startsWith("xmlns")) {
                problems.add(name + ": attribute " + attribute + " is not admitted; admitted are "
                        + new TreeSet<>(admitted));
            }
        }
    }

    private static List<Element> children(Element parent) {
        List<Element> elements = new ArrayList<>();
        NodeList nodes = parent.getChildNodes();
        for (int at = 0; at < nodes.getLength(); at++) {
            if (nodes.item(at).getNodeType() == Node.ELEMENT_NODE) {
                elements.add((Element) nodes.item(at));
            }
        }
        return elements;
    }

    private static Reactor reactor(Path root) throws Exception {
        Element project = parse(Files.readAllBytes(root.resolve("pom.xml"))).getDocumentElement();
        String version = only(project, "version").getTextContent().trim();
        Set<String> modules = new TreeSet<>();
        for (Element module : children(only(project, "modules"))) {
            modules.add(module.getTextContent().trim());
        }
        String driverVersion = only(only(project, "properties"), "sqlite-jdbc.version").getTextContent().trim();
        assertTrue(!modules.isEmpty() && !version.isEmpty() && !driverVersion.isEmpty(),
                "the root POM must declare its modules, its version and <sqlite-jdbc.version>");
        return new Reactor(modules, version, driverVersion);
    }

    private static Element only(Element parent, String localName) {
        List<Element> found = children(parent).stream().filter(child -> localName.equals(child.getLocalName()))
                .toList();
        assertEquals(1, found.size(), () -> "<" + parent.getLocalName() + "> must hold exactly one <" + localName
                + ">");
        return found.get(0);
    }

    private static Document parse(byte[] xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        return factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml));
    }

    private static List<String> problemsOf(String rules) throws Exception {
        return problems(parse(suppressions(rules)), FICTIONAL, Set.of());
    }

    private static byte[] suppressions(String rules) {
        return ("<?xml version=\"1.0\" encoding=\"UTF-8\"?><suppressions "
                + "xmlns=\"https://jeremylong.github.io/DependencyCheck/dependency-suppression.1.4.xsd\">" + rules
                + "</suppressions>").getBytes(StandardCharsets.UTF_8);
    }

    private static String rule(String attributes, String matching) {
        return "<suppress" + attributes + "><notes>a reason</notes>" + matching + "</suppress>";
    }

    private static String association(String packageUrl, String cpe) {
        return rule("", "<packageUrl regex=\"true\">" + packageUrl + "</packageUrl><cpe>" + cpe + "</cpe>");
    }

    private static String namedVulnerability(String filePath, String cve) {
        return rule("", "<filePath regex=\"true\">" + filePath + "</filePath><vulnerabilityName>" + cve
                + "</vulnerabilityName>");
    }

    private static void assertNamed(List<String> problems, String... fragments) {
        assertTrue(problems.stream().anyMatch(problem -> List.of(fragments).stream().allMatch(problem::contains)),
                () -> "expected one problem naming " + List.of(fragments) + ", got " + problems);
    }
}
