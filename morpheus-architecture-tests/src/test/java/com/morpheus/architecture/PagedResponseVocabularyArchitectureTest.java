package com.morpheus.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ADR-0151: no paginated response of {@code morpheus-mcp} or {@code morpheus-api} invents a name. The intention
 * aims at text in map literals, not at a compiled dependency ({@code .claude/rules/architecture.md}), so every rule
 * here reads sources.
 */
class PagedResponseVocabularyArchitectureTest {
    private static final List<String> MODULES = List.of("morpheus-mcp", "morpheus-api");
    private static final List<String> VOCABULARY = List.of("offset", "limit", "totalMatches", "hasMore", "items");
    private static final List<String> SPELLED_ONLY_BY_THE_FACTORY = List.of("\"totalMatches\"", "\"hasMore\"");
    private static final List<String> REFUSED_SPELLINGS_OF_A_PAGE_TOTAL = List.of(
            "\"specificationCount\"", "\"totalCount\"", "\"totalItems\"", "\"totalResults\"", "\"total\"", "\"count\"");
    private static final Pattern PAGE_KEY = Pattern.compile("page\\.put\\(\"(\\w+)\"");

    @Test
    void eachAdapterSpellsThePageOnceInTheCanonicalOrder() throws IOException {
        for (String module : MODULES) {
            String factory = Files.readString(factory(module));
            List<String> keys = new ArrayList<>();
            Matcher matcher = PAGE_KEY.matcher(factory);
            while (matcher.find()) {
                keys.add(matcher.group(1));
            }
            assertEquals(VOCABULARY, keys, module + " PagedEnvelope must spell exactly the canonical page keys");
        }
    }

    @Test
    void theTwoSiblingCopiesOfTheFactoryAreTheSameCode() throws IOException {
        assertEquals(
                normalized(Files.readString(factory("morpheus-mcp"))),
                normalized(Files.readString(factory("morpheus-api"))),
                "morpheus-mcp and morpheus-api may not share code, so their PagedEnvelope copies must stay identical");
    }

    @Test
    void noResponseOutsideTheFactoryBuildsAPageByHand() throws IOException {
        List<String> violations = new ArrayList<>();
        for (Path source : productionSourcesExceptTheFactories()) {
            String text = Files.readString(source);
            for (String literal : SPELLED_ONLY_BY_THE_FACTORY) {
                if (text.contains(literal)) {
                    violations.add(relative(source) + " spells " + literal);
                }
            }
        }
        assertEquals(List.of(), violations, "a paginated response is built by PagedEnvelope, never by hand");
    }

    @Test
    void noResponseSpellsThePageTotalAnotherWay() throws IOException {
        List<String> violations = new ArrayList<>();
        for (String module : MODULES) {
            try (Stream<Path> sources = Files.walk(RepositoryRoot.locate().resolve(module).resolve("src/main/java"))) {
                for (Path source : sources.filter(path -> path.toString().endsWith(".java")).sorted().toList()) {
                    String text = Files.readString(source);
                    for (String literal : REFUSED_SPELLINGS_OF_A_PAGE_TOTAL) {
                        if (text.contains(literal)) {
                            violations.add(relative(source) + " spells " + literal);
                        }
                    }
                }
            }
        }
        assertEquals(List.of(), violations, "the total of a page is totalMatches, in every transport");
    }

    @Test
    void theCurrentSpecificationToolEmbedsItsPageAsAValue() throws IOException {
        String service = Files.readString(ProductionSources.mainSource("morpheus-mcp", "MorpheusSpecificationMcpTools"));
        assertTrue(service.contains("\"specifications\", PagedEnvelope.slice("),
                "get_current_specification carries its page under the key that names the collection");
    }

    private List<Path> productionSourcesExceptTheFactories() throws IOException {
        List<Path> result = new ArrayList<>();
        for (String module : MODULES) {
            Path factory = factory(module);
            try (Stream<Path> sources = Files.walk(RepositoryRoot.locate().resolve(module).resolve("src/main/java"))) {
                sources.filter(path -> path.toString().endsWith(".java"))
                        .filter(path -> !path.equals(factory))
                        .sorted()
                        .forEach(result::add);
            }
        }
        assertTrue(result.size() > 20, "the scan must actually see the adapters' sources: " + result.size());
        return result;
    }

    private Path factory(String module) {
        Path factory = ProductionSources.mainSource(module, "PagedEnvelope");
        assertTrue(Files.isRegularFile(factory), "missing " + factory);
        return factory;
    }

    private String normalized(String source) {
        return source.replaceFirst("package com\\.morpheus\\.(mcp|api)(\\.[a-z]+)*;", "package <adapter>;")
                .replace("morpheus-mcp", "<adapter>")
                .replace("morpheus-api", "<adapter>")
                .replace("\r\n", "\n");
    }

    private String relative(Path source) {
        return RepositoryRoot.locate().relativize(source).toString().replace('\\', '/');
    }
}
