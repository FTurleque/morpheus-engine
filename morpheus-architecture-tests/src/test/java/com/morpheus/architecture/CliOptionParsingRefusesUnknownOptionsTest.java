package com.morpheus.architecture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * An option the CLI does not read is refused, not ignored -- by every option parser of the module, not by one.
 *
 * <p>{@code SimpleOptions.parse} accepts any {@code --key value} pair; only {@code rejectUnknown} refuses the unknown
 * one, and calling it is optional. One adapter never called it, so {@code portfolio references --projet X} emptied the
 * optional project filter and returned every reference of the portfolio, exit code 0 (CLI-3). The first guard looked
 * for the literal {@code SimpleOptions.parse(} only, while the module has several parser families, each with its own
 * {@code rejectUnknown} (CLI-8). This guard <em>discovers</em> the parsers instead of naming them, with three rules:</p>
 *
 * <ol>
 *   <li><b>Permissive families.</b> A type that declares {@code void rejectUnknown(} is a parser that accepts any key.
 *   Every {@code <Family>.parse(} call must be followed, in the block that encloses it, by a {@code rejectUnknown}
 *   call; when a {@code switch} dispatches before any such call, every arm that does not merely throw must make it.
 *   Each family discovered must have at least one call site.</li>
 *   <li><b>Option switches.</b> A {@code switch} with a {@code case "--..."} label decides which options exist. It must
 *   have a {@code default} arm that throws. Handing the token on instead ({@code remaining.add(token)}) is accepted
 *   only from a switch over the global options ({@code --json --data-dir --config-dir --db}) in a parser of the raw
 *   argument vector ({@code String[]}), whose leftovers are the command a sub-parser reads.</li>
 *   <li><b>Terminal parsers.</b> A method named {@code parse} whose first parameter is a {@code List<String>} of
 *   option tokens either belongs to a family, or contains an option switch, or throws an {@code unknown ... option}
 *   refusal itself -- thrown, not merely mentioned.</li>
 * </ol>
 *
 * <p>Comments and string literals, text blocks included, are blanked before scanning, so a {@code rejectUnknown} in a
 * comment or in a message satisfies nothing. The rule is textual because the proposition is about a call that must
 * <em>follow</em> another in the same block, and about the shape of a {@code switch}; no dependency rule over compiled
 * classes expresses either (ADR-0103).</p>
 *
 * <p>What it does not cover: the <em>argument</em> of {@code rejectUnknown} -- a call allowing every option would
 * satisfy rule 1 -- nor its receiver; a dispatch written as a chain of {@code if} is checked for the presence of one
 * call, not per branch. The exact allowed sets are held by the adapters' own tests. Three parsers of the raw argument
 * vector are not seen: {@code MorpheusProductCli.parse} and {@code MorpheusProviderPluginCli.parse} refuse at the end
 * of a chain of {@code if}, {@code MorpheusServerCli.options} checks an allowlist in a method not named {@code parse};
 * each adapter test has an unknown-option case instead. The launch parsers ({@code ApiLaunchOptions},
 * {@code McpLaunchOptions}, {@code RemoteApiLaunchOptions}) pass rule 2 on a {@code default} that is unreachable:
 * their real refusal collects unknown tokens and throws after the loop, which the rule does not see. Nor does it see
 * a method reference ({@code SimpleOptions::parse}) or a switch whose labels are constants.</p>
 */
class CliOptionParsingRefusesUnknownOptionsTest {

    private static final Pattern FAMILY_DECLARATION = Pattern.compile("\\bvoid\\s+rejectUnknown\\s*\\(");
    private static final Pattern GUARD_CALL = Pattern.compile("\\.\\s*rejectUnknown\\s*\\(");
    private static final Pattern PARSE_CALL = Pattern.compile("\\b(\\w+)\\s*\\.\\s*parse\\s*\\(");
    private static final Pattern SWITCH = Pattern.compile("\\bswitch\\s*\\(");
    private static final Pattern TYPE_HEADER = Pattern.compile("\\b(?:class|interface|enum|record)\\s+(\\w+)");
    private static final Pattern THROWS_TAIL = Pattern.compile("\\)\\s*(?:throws\\s+[\\w.,\\s<>]+)?$");
    private static final Pattern ANONYMOUS = Pattern.compile("\\bnew\\s+[\\w.<>]+$");
    private static final Pattern LAST_IDENTIFIER = Pattern.compile("(\\w+)$");
    private static final Pattern TERMINAL_PARSE = Pattern.compile("\\bparse\\s*\\(\\s*(?:final\\s+)?List\\s*<\\s*String\\s*>");
    private static final Pattern OPTION_LABEL = Pattern.compile("\"--[^\"]*\"");
    private static final Pattern UNKNOWN_OPTION_REFUSAL =
            Pattern.compile("throw\\s+new\\s+[\\w.]+\\(\\s*\"unknown ([\\w-]+ )?options?\\b");
    private static final Pattern FORWARD = Pattern.compile("^\\w+\\s*\\.\\s*add\\s*\\(\\s*\\w+\\s*\\)\\s*;");
    private static final Set<String> CONTROL = Set.of("if", "for", "while", "switch", "catch", "synchronized", "try");
    /** The options every command accepts ahead of its name; only a switch over these may hand a token on. */
    private static final Set<String> GLOBAL_OPTIONS = Set.of("\"--json\"", "\"--data-dir\"", "\"--config-dir\"", "\"--db\"");

    @Test
    void everyOptionParserOfTheCliRefusesAnUnknownOption() throws IOException {
        Scan scan = scan(cliSources());

        assertFalse(scan.sitesByFamily().isEmpty(), "no parser family declares rejectUnknown: the rule would be vacuous");
        scan.sitesByFamily().forEach((family, sites) ->
                assertTrue(sites > 0, "the family " + family + " has no parse call site: the rule would be vacuous for it"));
        assertTrue(scan.optionSwitches() > 0, "the scan found no option switch: the rule would be vacuous");
        assertTrue(scan.terminalParsers() > 0, "the scan found no terminal parser: the rule would be vacuous");
        assertEquals(List.of(), scan.violations(), "an option parser of the CLI accepts a misspelled option silently");
    }

    @Test
    void aFamilyIsDiscoveredFromItsDeclarationNotFromItsName() {
        String family = "final class Flags {\n    static Flags parse(List<String> t) {\n        return new Flags();\n    }\n\n"
                + "    void rejectUnknown(Set<String> a) {\n    }\n}\n";
        String unguarded = "final class A {\n    int run() {\n        Flags o = Flags.parse(t);\n"
                + "        return o.required(\"x\").length();\n    }\n}\n";
        String sameNameOutsideTheFamily = "final class B {\n    int run() {\n        Options o = Options.parse(t);\n"
                + "        return 0;\n    }\n\n    private static final class Options {\n"
                + "        static Options parse(List<String> tokens) {\n"
                + "            throw new IllegalArgumentException(\"unknown option: \" + tokens);\n        }\n    }\n}\n";

        Scan scan = scan(Map.of("Flags.java", family, "A.java", unguarded, "B.java", sameNameOutsideTheFamily));

        assertEquals(Map.of("Flags", 1), scan.sitesByFamily());
        assertEquals(List.of("A.java:3 Flags.parse is not followed by rejectUnknown in the same method"), scan.violations());
    }

    @Test
    void aGuardInAnotherMethodACommentOrAStringSatisfiesNothing() {
        String elsewhere = "final class A {\n    int run() {\n        SimpleOptions o = SimpleOptions.parse(t);\n"
                + "        return o.required(\"x\").length();\n    }\n\n    int other() {\n"
                + "        o.rejectUnknown(Set.of());\n        return 0;\n    }\n}\n";
        String commented = "final class A {\n    int run() {\n        SimpleOptions o = SimpleOptions.parse(t);\n"
                + "        // o.rejectUnknown(Set.of());\n        log(\"o.rejectUnknown(x)\");\n        return 0;\n    }\n}\n";
        String guarded = "final class A {\n    int run() {\n        SimpleOptions o = SimpleOptions.parse(t);\n"
                + "        o.rejectUnknown(Set.of());\n        return 0;\n    }\n}\n";

        assertEquals(1, scan(withSimpleOptions("A.java", elsewhere)).violations().size());
        assertEquals(1, scan(withSimpleOptions("A.java", commented)).violations().size());
        assertEquals(List.of(), scan(withSimpleOptions("A.java", guarded)).violations());
    }

    @Test
    void aDispatchSwitchMustGuardEveryArmThatDoesNotThrow() {
        String oneArmUnguarded = "final class A {\n    int run(String action) {\n"
                + "        SimpleOptions o = SimpleOptions.parse(t);\n        return switch (action) {\n"
                + "            case \"a\" -> {\n                o.rejectUnknown(Set.of(\"x\"));\n                yield 1;\n            }\n"
                + "            case \"b\" -> o.optional(\"y\").map(String::length).orElse(0);\n"
                + "            default -> throw new IllegalArgumentException(\"unknown action\");\n        };\n    }\n}\n";
        String secondArmGuarded = oneArmUnguarded.replace("case \"b\" -> o.optional(\"y\").map(String::length).orElse(0);",
                "case \"b\" -> {\n                o.rejectUnknown(Set.of(\"y\"));\n                yield 2;\n            }");
        String guardedBeforeTheSwitch = oneArmUnguarded.replace("        return switch (action) {",
                "        o.rejectUnknown(Set.of(\"x\", \"y\"));\n        return switch (action) {");

        assertEquals(List.of("A.java:3 SimpleOptions.parse reaches a switch arm without rejectUnknown"),
                scan(withSimpleOptions("A.java", oneArmUnguarded)).violations());
        assertEquals(List.of(), scan(withSimpleOptions("A.java", secondArmGuarded)).violations());
        assertEquals(List.of(), scan(withSimpleOptions("A.java", guardedBeforeTheSwitch)).violations());
    }

    @Test
    void anOptionSwitchWithoutARefusingDefaultIsRefused() {
        String noDefault = "final class A {\n    static A parse(List<String> tokens) {\n        for (String token : tokens) {\n"
                + "            switch (token) {\n                case \"--project\" -> take(token);\n            }\n"
                + "        }\n        return new A();\n    }\n}\n";
        String forwardingTerminal = noDefault.replace("case \"--project\" -> take(token);",
                "case \"--project\" -> take(token);\n                default -> remaining.add(token);");
        String forwardingArgv = forwardingTerminal.replace("parse(List<String> tokens)", "parse(String[] tokens)");
        String refusing = noDefault.replace("case \"--project\" -> take(token);",
                "case \"--project\" -> take(token);\n"
                        + "                default -> throw new IllegalArgumentException(\"unknown option: \" + token);");

        assertEquals(List.of("A.java:4 option switch has no default arm"), scan(Map.of("A.java", noDefault)).violations());
        assertEquals(List.of("A.java:4 option switch default neither throws nor forwards the global options of a String[] parser"),
                scan(Map.of("A.java", forwardingTerminal)).violations());
        assertEquals(List.of("A.java:4 option switch default neither throws nor forwards the global options of a String[] parser"),
                scan(Map.of("A.java", forwardingArgv)).violations(), "a String[] parser of command options must refuse");
        assertEquals(List.of(), scan(Map.of("A.java", forwardingArgv.replace("\"--project\"", "\"--json\", \"--db\"")))
                .violations(), "a String[] parser of the global options hands the rest to a sub-parser");
        assertEquals(List.of(), scan(Map.of("A.java", refusing)).violations());
    }

    @Test
    void aGuardInASiblingArmOrAfterAnEscapedTextBlockIsSeenForWhatItIs() {
        String siblingArm = "final class A {\n    int run(String action) {\n        switch (action) {\n"
                + "            case \"a\" -> {\n                SimpleOptions o = SimpleOptions.parse(t);\n"
                + "                return 1;\n            }\n            case \"b\" -> {\n"
                + "                o.rejectUnknown(Set.of());\n                return 2;\n            }\n"
                + "            default -> throw new IllegalArgumentException(\"x\");\n        }\n    }\n}\n";
        String escapedTextBlock = "final class A {\n    private static final String S = \"\"\"\n        a \\\"\"\" b\n        \"\"\";\n\n"
                + "    int run() {\n        SimpleOptions o = SimpleOptions.parse(t);\n        return 0;\n    }\n}\n";

        assertEquals(List.of("A.java:5 SimpleOptions.parse is not followed by rejectUnknown in the same method"),
                scan(withSimpleOptions("A.java", siblingArm)).violations());
        assertEquals(List.of("A.java:7 SimpleOptions.parse is not followed by rejectUnknown in the same method"),
                scan(withSimpleOptions("A.java", escapedTextBlock)).violations());
    }

    @Test
    void aTerminalParserOutsideEveryFamilyMustRefuseByItself() {
        String silent = "final class A {\n    private static final class Opts {\n"
                + "        static Opts parse(List<String> tokens) {\n            return new Opts();\n        }\n    }\n}\n";
        String refusing = silent.replace("return new Opts();",
                "throw new IllegalArgumentException(\"unknown option: \" + tokens);");
        String mentioning = silent.replace("return new Opts();",
                "log(\"unknown option: \" + tokens);\n            return new Opts();");

        assertEquals(List.of("A.java:3 terminal parser Opts.parse neither belongs to a family nor refuses an unknown option"),
                scan(Map.of("A.java", silent)).violations());
        assertEquals(1, scan(Map.of("A.java", mentioning)).violations().size(), "a mention is not a refusal");
        assertEquals(List.of(), scan(Map.of("A.java", refusing)).violations());
    }

    private static Map<String, String> withSimpleOptions(String name, String text) {
        return Map.of(name, text, "SimpleOptions.java", "final class SimpleOptions {\n"
                + "    static SimpleOptions parse(List<String> t) {\n        return null;\n    }\n\n"
                + "    void rejectUnknown(Set<String> allowed) {\n    }\n}\n");
    }

    record Scan(Map<String, Integer> sitesByFamily, int optionSwitches, int terminalParsers, List<String> violations) {
    }

    static Scan scan(Map<String, String> sources) {
        Map<String, Unit> units = new TreeMap<>();
        sources.forEach((name, text) -> units.put(name, Unit.of(name, text.replace("\r\n", "\n"))));

        Map<String, Set<String>> nestedFamilies = new TreeMap<>();
        Set<String> topLevelFamilies = new TreeSet<>();
        Map<String, Integer> sitesByFamily = new TreeMap<>();
        for (Unit unit : units.values()) {
            Matcher declaration = FAMILY_DECLARATION.matcher(unit.code());
            while (declaration.find()) {
                Block type = unit.innermost(declaration.start(), Kind.TYPE);
                if (type == null) {
                    continue;
                }
                if (unit.isTopLevel(type)) {
                    topLevelFamilies.add(type.name());
                    sitesByFamily.putIfAbsent(type.name(), 0);
                } else {
                    nestedFamilies.computeIfAbsent(unit.name(), key -> new TreeSet<>()).add(type.name());
                    sitesByFamily.putIfAbsent(unit.name() + "#" + type.name(), 0);
                }
            }
        }

        List<String> violations = new ArrayList<>();
        int optionSwitches = 0;
        int terminalParsers = 0;
        for (Unit unit : units.values()) {
            Set<String> nested = nestedFamilies.getOrDefault(unit.name(), Set.of());
            Matcher call = PARSE_CALL.matcher(unit.code());
            while (call.find()) {
                String type = call.group(1);
                String family = nested.contains(type) ? unit.name() + "#" + type
                        : !unit.declaredTypes().contains(type) && topLevelFamilies.contains(type) ? type : null;
                if (family == null) {
                    continue;
                }
                sitesByFamily.merge(family, 1, Integer::sum);
                guardViolation(unit, call.end()).ifPresent(reason ->
                        violations.add(unit.name() + ":" + unit.line(call.start()) + " " + type + ".parse " + reason));
            }

            Matcher switchKeyword = SWITCH.matcher(unit.code());
            while (switchKeyword.find()) {
                List<Arm> arms = unit.arms(unit.switchBody(switchKeyword.end() - 1));
                if (!isOptionSwitch(arms)) {
                    continue;
                }
                optionSwitches++;
                String where = unit.name() + ":" + unit.line(switchKeyword.start()) + " option switch ";
                Arm fallback = arms.stream().filter(arm -> arm.label().equals("default")).findFirst().orElse(null);
                if (fallback == null) {
                    violations.add(where + "has no default arm");
                } else if (!fallback.throwsOnly() && !(FORWARD.matcher(fallback.content()).find()
                        && unit.header(unit.innermost(switchKeyword.start(), Kind.METHOD)).contains("String[]")
                        && onlyGlobalOptions(arms))) {
                    violations.add(where + "default neither throws nor forwards the global options of a String[] parser");
                }
            }

            for (Block method : unit.blocks()) {
                if (method.kind() != Kind.METHOD || !TERMINAL_PARSE.matcher(unit.header(method)).find()) {
                    continue;
                }
                terminalParsers++;
                Block owner = unit.innermost(method.open(), Kind.TYPE);
                String ownerName = owner == null ? "?" : owner.name();
                boolean family = nested.contains(ownerName)
                        || owner != null && unit.isTopLevel(owner) && topLevelFamilies.contains(ownerName);
                String body = unit.text().substring(method.open(), method.close());
                boolean refuses = UNKNOWN_OPTION_REFUSAL.matcher(body).find() || containsOptionSwitch(unit, method);
                if (!family && !refuses) {
                    violations.add(unit.name() + ":" + unit.line(method.open()) + " terminal parser " + ownerName
                            + ".parse neither belongs to a family nor refuses an unknown option");
                }
            }
        }
        return new Scan(sitesByFamily, optionSwitches, terminalParsers, violations);
    }

    private static boolean onlyGlobalOptions(List<Arm> arms) {
        return arms.stream().filter(arm -> arm.label().startsWith("case"))
                .allMatch(arm -> OPTION_LABEL.matcher(arm.label()).results()
                        .allMatch(label -> GLOBAL_OPTIONS.contains(label.group())));
    }

    private static boolean isOptionSwitch(List<Arm> arms) {
        return arms.stream().anyMatch(arm -> arm.label().startsWith("case") && OPTION_LABEL.matcher(arm.label()).find());
    }

    private static boolean containsOptionSwitch(Unit unit, Block method) {
        Matcher switchKeyword = SWITCH.matcher(unit.code()).region(method.open(), method.close());
        while (switchKeyword.find()) {
            if (isOptionSwitch(unit.arms(unit.switchBody(switchKeyword.end() - 1)))) {
                return true;
            }
        }
        return false;
    }

    private static Optional<String> guardViolation(Unit unit, int from) {
        Block method = unit.innermost(from, Kind.METHOD);
        if (method == null) {
            return Optional.of("is called outside a method");
        }
        int end = unit.innermostBlock(from).close();
        Matcher guard = GUARD_CALL.matcher(unit.code()).region(from, end);
        Matcher switchKeyword = SWITCH.matcher(unit.code()).region(from, end);
        int guardAt = guard.find() ? guard.start() : -1;
        int switchAt = switchKeyword.find() ? switchKeyword.start() : -1;
        if (guardAt >= 0 && (switchAt < 0 || guardAt < switchAt)) {
            return Optional.empty();
        }
        if (switchAt < 0) {
            return Optional.of("is not followed by rejectUnknown in the same method");
        }
        for (Arm arm : unit.arms(unit.switchBody(switchKeyword.end() - 1))) {
            if (!arm.throwsOnly() && !GUARD_CALL.matcher(arm.content()).find()) {
                return Optional.of("reaches a switch arm without rejectUnknown");
            }
        }
        return Optional.empty();
    }

    enum Kind { TYPE, METHOD, OTHER }

    record Block(int open, int close, Kind kind, String name) {
    }

    record Arm(String label, String content) {
        boolean throwsOnly() {
            return content.replaceFirst("^\\{\\s*", "").startsWith("throw ");
        }
    }

    /**
     * One source, with comments blanked ({@code text}) and, in addition, the contents of string and character
     * literals blanked ({@code code}). Blanking keeps offsets and line breaks, so a position means the same thing in
     * both, and braces are matched on {@code code} only.
     */
    record Unit(String name, String text, String code, List<Block> blocks, Set<String> declaredTypes) {
        static Unit of(String name, String source) {
            String code = blank(source, false);
            List<Block> blocks = blocks(code);
            Set<String> declared = new TreeSet<>();
            for (Block block : blocks) {
                if (block.kind() == Kind.TYPE && !block.name().isEmpty()) {
                    declared.add(block.name());
                }
            }
            return new Unit(name, blank(source, true), code, blocks, declared);
        }

        int line(int position) {
            return 1 + (int) code.substring(0, position).chars().filter(c -> c == '\n').count();
        }

        Block innermost(int position, Kind kind) {
            Block found = null;
            for (Block block : blocks) {
                if (block.kind() == kind && block.open() < position && position < block.close()
                        && (found == null || block.open() > found.open())) {
                    found = block;
                }
            }
            return found;
        }

        Block innermostBlock(int position) {
            Block found = null;
            for (Block block : blocks) {
                if (block.open() < position && position < block.close()
                        && (found == null || block.open() > found.open())) {
                    found = block;
                }
            }
            return found;
        }

        boolean isTopLevel(Block type) {
            return innermost(type.open(), Kind.TYPE) == null && name.equals(type.name() + ".java");
        }

        String header(Block block) {
            if (block == null) {
                return "";
            }
            int start = block.open() - 1;
            while (start >= 0 && ";{}".indexOf(code.charAt(start)) < 0) {
                start--;
            }
            return code.substring(start + 1, block.open()).trim();
        }

        Block switchBody(int openParen) {
            int brace = code.indexOf('{', matching(code, openParen));
            for (Block block : blocks) {
                if (block.open() == brace) {
                    return block;
                }
            }
            throw new IllegalStateException(name + ": switch without a body at line " + line(openParen));
        }

        /** The arms at the top level of a switch body: label ({@code case ...} or {@code default}) and content. */
        List<Arm> arms(Block body) {
            List<int[]> labels = new ArrayList<>();
            int depth = 0;
            for (int index = body.open() + 1; index < body.close(); index++) {
                char character = code.charAt(index);
                if (character == '{' || character == '(') {
                    depth++;
                } else if (character == '}' || character == ')') {
                    depth--;
                } else if (depth == 0 && (startsWord(index, "case") || startsWord(index, "default"))) {
                    int separator = index;
                    while (separator < body.close() && !code.startsWith("->", separator) && code.charAt(separator) != ':') {
                        separator++;
                    }
                    labels.add(new int[]{index, separator});
                    index = separator;
                }
            }
            List<Arm> arms = new ArrayList<>();
            for (int position = 0; position < labels.size(); position++) {
                int[] label = labels.get(position);
                int contentStart = label[1] + (code.startsWith("->", label[1]) ? 2 : 1);
                int contentEnd = position + 1 < labels.size() ? labels.get(position + 1)[0] : body.close();
                arms.add(new Arm(text.substring(label[0], label[1]).trim(), code.substring(contentStart, contentEnd).trim()));
            }
            return arms;
        }

        private boolean startsWord(int index, String word) {
            int end = index + word.length();
            return code.startsWith(word, index)
                    && (index == 0 || !Character.isJavaIdentifierPart(code.charAt(index - 1)))
                    && (end >= code.length() || !Character.isJavaIdentifierPart(code.charAt(end)));
        }

        private static List<Block> blocks(String code) {
            List<Block> blocks = new ArrayList<>();
            Deque<Integer> open = new ArrayDeque<>();
            for (int index = 0; index < code.length(); index++) {
                if (code.charAt(index) == '{') {
                    open.push(index);
                } else if (code.charAt(index) == '}' && !open.isEmpty()) {
                    blocks.add(classify(code, open.pop(), index));
                }
            }
            blocks.sort(Comparator.comparingInt(Block::open));
            return List.copyOf(blocks);
        }

        /** A type, a method (or constructor), or anything else: control block, lambda, switch arm, initializer. */
        private static Block classify(String code, int open, int close) {
            int start = open - 1;
            while (start >= 0 && ";{}".indexOf(code.charAt(start)) < 0) {
                start--;
            }
            String header = code.substring(start + 1, open).trim();
            Matcher type = TYPE_HEADER.matcher(header);
            if (type.find() && !header.contains("->") && !header.contains("=")) {
                return new Block(open, close, Kind.TYPE, type.group(1));
            }
            Matcher tail = THROWS_TAIL.matcher(header);
            if (header.endsWith("->") || !tail.find()) {
                return new Block(open, close, Kind.OTHER, "");
            }
            // The parenthesis may open before the header, as in for (int i = 0; i < n; i++) {
            int parenStart = matchingBackwards(code, code.indexOf(header, start + 1) + tail.start());
            String before = code.substring(Math.max(0, parenStart - 300), parenStart).trim();
            Matcher identifier = LAST_IDENTIFIER.matcher(before);
            if (!identifier.find() || CONTROL.contains(identifier.group(1))) {
                return new Block(open, close, Kind.OTHER, "");
            }
            if (ANONYMOUS.matcher(before).find()) {
                return new Block(open, close, Kind.TYPE, "");
            }
            return new Block(open, close, Kind.METHOD, identifier.group(1));
        }

        private static int matching(String code, int openParen) {
            int depth = 0;
            for (int index = openParen; index < code.length(); index++) {
                if (code.charAt(index) == '(') {
                    depth++;
                } else if (code.charAt(index) == ')' && --depth == 0) {
                    return index;
                }
            }
            throw new IllegalStateException("unbalanced ( at offset " + openParen);
        }

        private static int matchingBackwards(String code, int closeParen) {
            int depth = 0;
            for (int index = closeParen; index >= 0; index--) {
                if (code.charAt(index) == ')') {
                    depth++;
                } else if (code.charAt(index) == '(' && --depth == 0) {
                    return index;
                }
            }
            throw new IllegalStateException("unbalanced ) at offset " + closeParen);
        }

        static String blank(String source, boolean keepStrings) {
            StringBuilder out = new StringBuilder(source.length());
            int index = 0;
            while (index < source.length()) {
                char character = source.charAt(index);
                int end;
                if (source.startsWith("//", index)) {
                    end = source.indexOf('\n', index);
                    end = end < 0 ? source.length() : end;
                    spaces(out, source, index, end);
                } else if (source.startsWith("/*", index)) {
                    end = source.indexOf("*/", index + 2);
                    end = end < 0 ? source.length() : end + 2;
                    spaces(out, source, index, end);
                } else if (source.startsWith("\"\"\"", index)) {
                    end = index + 3;
                    while (end < source.length() && !source.startsWith("\"\"\"", end)) {
                        end += source.charAt(end) == '\\' ? 2 : 1;
                    }
                    end = Math.min(end + 3, source.length());
                    literal(out, source, index, end, 3, keepStrings);
                } else if (character == '"' || character == '\'') {
                    end = index + 1;
                    while (end < source.length() && source.charAt(end) != character && source.charAt(end) != '\n') {
                        end += source.charAt(end) == '\\' ? 2 : 1;
                    }
                    end = Math.min(end + 1, source.length());
                    literal(out, source, index, end, 1, keepStrings);
                } else {
                    out.append(character);
                    end = index + 1;
                }
                index = end;
            }
            return out.toString();
        }

        private static void literal(StringBuilder out, String source, int start, int end, int quote, boolean keep) {
            if (keep || end - start < 2 * quote) {
                out.append(source, start, end);
                return;
            }
            out.append(source, start, start + quote);
            spaces(out, source, start + quote, end - quote);
            out.append(source, end - quote, end);
        }

        private static void spaces(StringBuilder out, String source, int start, int end) {
            for (int index = start; index < end; index++) {
                out.append(source.charAt(index) == '\n' ? '\n' : ' ');
            }
        }
    }

    private static Map<String, String> cliSources() throws IOException {
        Path directory = repoRoot().resolve("morpheus-cli/src/main/java");
        Map<String, String> sources = new TreeMap<>();
        try (Stream<Path> tree = Files.walk(directory)) {
            for (Path path : tree.filter(file -> file.toString().endsWith(".java")).sorted().toList()) {
                sources.put(path.getFileName().toString(), Files.readString(path));
            }
        }
        return sources;
    }

    private static Path repoRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        if (Files.isRegularFile(current.resolve("pom.xml")) && Files.isDirectory(current.resolve("distribution"))) {
            return current;
        }
        Path parent = current.getParent();
        if (parent != null && Files.isRegularFile(parent.resolve("pom.xml"))
                && Files.isDirectory(parent.resolve("distribution"))) {
            return parent;
        }
        throw new IllegalStateException("MORPHEUS repository root not found from " + current);
    }
}
