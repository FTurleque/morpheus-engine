package com.morpheus.application.store;

import com.morpheus.domain.requirement.Requirement;
import com.morpheus.domain.specification.SpecificationId;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * What a page of CURRENT requirements keeps: the requirements of one specification, or all of them, whose lexical
 * corpus contains every term.
 *
 * <p>The corpus of a requirement is its key, title and statement joined by a space and normalized by
 * {@link #normalize(String)}. A term is a normalized word without whitespace and matches only as a substring of the
 * corpus: no character of it is a wildcard. A store that evaluates the match itself must give the same answer as
 * {@link #matches(Requirement)}, which is the definition.</p>
 */
public record RequirementPageCriteria(Optional<SpecificationId> specificationId, List<String> terms) {

    public RequirementPageCriteria {
        Objects.requireNonNull(specificationId, "specificationId");
        terms = List.copyOf(Objects.requireNonNull(terms, "terms"));
        for (String term : terms) {
            if (term.isEmpty() || !term.equals(normalize(term)) || term.codePoints().anyMatch(Character::isWhitespace)) {
                throw new IllegalArgumentException(
                        "a requirement page term must be a normalized word without whitespace: '" + term + "'");
            }
        }
    }

    public static RequirementPageCriteria all() {
        return new RequirementPageCriteria(Optional.empty(), List.of());
    }

    public static RequirementPageCriteria lexical(List<String> terms) {
        return new RequirementPageCriteria(Optional.empty(), terms);
    }

    public static RequirementPageCriteria ofSpecification(SpecificationId specificationId) {
        return new RequirementPageCriteria(Optional.of(specificationId), List.of());
    }

    public boolean matches(Requirement requirement) {
        Objects.requireNonNull(requirement, "requirement");
        if (specificationId.isPresent() && !specificationId.get().equals(requirement.specificationId())) {
            return false;
        }
        if (terms.isEmpty()) {
            return true;
        }
        String corpus = corpus(requirement.key(), requirement.title(), requirement.statement());
        return terms.stream().allMatch(corpus::contains);
    }

    /** The normalized text a term is searched in; a missing key contributes an empty field. */
    public static String corpus(Optional<String> key, String title, String statement) {
        return normalize(String.join(" ", key.orElse(""), title, statement));
    }

    public static String normalize(String value) {
        return value.strip().toLowerCase(Locale.ROOT);
    }
}
