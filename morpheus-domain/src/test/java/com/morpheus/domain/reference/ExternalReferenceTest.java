package com.morpheus.domain.reference;

import com.morpheus.domain.DomainFixtures;
import com.morpheus.domain.identity.DomainIdentity;
import com.morpheus.domain.provenance.Provenance;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ExternalReferenceTest {
    private static final Instant FIRST_ATTEMPT = Instant.parse("2026-10-01T10:00:00Z");
    private static final Instant SECOND_ATTEMPT = Instant.parse("2026-10-02T10:00:00Z");

    private final ExternalReferenceId id = ExternalReferenceId.generate();
    private final DomainIdentity ownerId = DomainIdentity.generate();
    private final ExternalReferenceTarget target =
            new ExternalReferenceTarget("jira", Optional.of("MORPH"), "issue", "MORPH-1", Optional.empty());
    private final ResolvedExternalTarget resolved = new ResolvedExternalTarget(target, Map.of("status", "Open"));

    @Test
    void anUnvalidatedReferenceHasNotBeenAttemptedAndHasNoHistory() {
        Optional<Provenance> provenance = Optional.of(DomainFixtures.provenance());
        ExternalReference reference = ExternalReference.unvalidated(id, ownerId, target, provenance);

        assertEquals(id, reference.id());
        assertEquals(ownerId, reference.ownerId());
        assertEquals(target, reference.target());
        assertEquals(ExternalReferenceResolutionState.UNVALIDATED, reference.resolutionState());
        assertEquals(ExternalReferenceResolutionReason.NOT_ATTEMPTED, reference.resolutionReason());
        assertEquals(Optional.empty(), reference.resolvedTarget());
        assertEquals(provenance, reference.provenance());
        assertEquals(List.of(), reference.history());
    }

    @Test
    void eachTransitionAppendsAnEventFromThePreviousStateAndKeepsTheIdentity() {
        ExternalReference unvalidated = ExternalReference.unvalidated(id, ownerId, target, Optional.empty());

        ExternalReference resolvedReference = unvalidated.transition(ExternalReferenceResolutionState.RESOLVED,
                ExternalReferenceResolutionReason.RESOLVED, Optional.of(resolved), FIRST_ATTEMPT);
        ExternalReference stale = resolvedReference.transition(ExternalReferenceResolutionState.STALE,
                ExternalReferenceResolutionReason.TARGET_REVISION_MISMATCH, Optional.empty(), SECOND_ATTEMPT);

        assertEquals(Optional.of(resolved), resolvedReference.resolvedTarget());
        assertEquals(ExternalReferenceResolutionState.STALE, stale.resolutionState());
        assertEquals(ExternalReferenceResolutionReason.TARGET_REVISION_MISMATCH, stale.resolutionReason());
        assertEquals(Optional.empty(), stale.resolvedTarget());
        assertEquals(id, stale.id());
        assertEquals(ownerId, stale.ownerId());
        assertEquals(target, stale.target());
        assertEquals(List.of(
                new ExternalReferenceResolutionEvent(ExternalReferenceResolutionState.UNVALIDATED,
                        ExternalReferenceResolutionState.RESOLVED, ExternalReferenceResolutionReason.RESOLVED,
                        FIRST_ATTEMPT),
                new ExternalReferenceResolutionEvent(ExternalReferenceResolutionState.RESOLVED,
                        ExternalReferenceResolutionState.STALE,
                        ExternalReferenceResolutionReason.TARGET_REVISION_MISMATCH, SECOND_ATTEMPT)),
                stale.history());
        assertEquals(List.of(), unvalidated.history());
    }

    @Test
    void aResolvedReferenceMustCarryItsResolvedTarget() {
        ExternalReference unvalidated = ExternalReference.unvalidated(id, ownerId, target, Optional.empty());

        assertEquals("RESOLVED reference must contain a resolved target", assertThrows(
                IllegalArgumentException.class,
                () -> unvalidated.transition(ExternalReferenceResolutionState.RESOLVED,
                        ExternalReferenceResolutionReason.RESOLVED, Optional.empty(), FIRST_ATTEMPT)).getMessage());
    }

    @Test
    void onlyAResolvedReferenceMayCarryAResolvedTarget() {
        ExternalReference unvalidated = ExternalReference.unvalidated(id, ownerId, target, Optional.empty());

        assertEquals("only RESOLVED reference may contain a resolved target", assertThrows(
                IllegalArgumentException.class,
                () -> unvalidated.transition(ExternalReferenceResolutionState.UNRESOLVED,
                        ExternalReferenceResolutionReason.TARGET_NOT_FOUND, Optional.of(resolved), FIRST_ATTEMPT))
                .getMessage());
    }

    @Test
    void aTransitionRefusesAnAbsentArgumentByName() {
        ExternalReference reference = ExternalReference.unvalidated(id, ownerId, target, Optional.empty());

        assertEquals("newState", assertThrows(NullPointerException.class, () -> reference.transition(null,
                ExternalReferenceResolutionReason.NO_RESOLVER, Optional.empty(), FIRST_ATTEMPT)).getMessage());
        assertEquals("reason", assertThrows(NullPointerException.class, () -> reference.transition(
                ExternalReferenceResolutionState.UNRESOLVED, null, Optional.empty(), FIRST_ATTEMPT)).getMessage());
        assertEquals("newResolvedTarget", assertThrows(NullPointerException.class, () -> reference.transition(
                ExternalReferenceResolutionState.UNRESOLVED, ExternalReferenceResolutionReason.NO_RESOLVER, null,
                FIRST_ATTEMPT)).getMessage());
        assertEquals("occurredAt", assertThrows(NullPointerException.class, () -> reference.transition(
                ExternalReferenceResolutionState.UNRESOLVED, ExternalReferenceResolutionReason.NO_RESOLVER,
                Optional.empty(), null)).getMessage());
    }

    @Test
    void aReferenceRefusesAnAbsentComponentByName() {
        assertEquals("id", assertThrows(NullPointerException.class,
                () -> ExternalReference.unvalidated(null, ownerId, target, Optional.empty())).getMessage());
        assertEquals("ownerId", assertThrows(NullPointerException.class,
                () -> ExternalReference.unvalidated(id, null, target, Optional.empty())).getMessage());
        assertEquals("target", assertThrows(NullPointerException.class,
                () -> ExternalReference.unvalidated(id, ownerId, null, Optional.empty())).getMessage());
        assertEquals("provenance", assertThrows(NullPointerException.class,
                () -> ExternalReference.unvalidated(id, ownerId, target, null)).getMessage());
        assertEquals("resolutionState", assertThrows(NullPointerException.class,
                () -> new ExternalReference(id, ownerId, target, null, ExternalReferenceResolutionReason.NO_RESOLVER,
                        Optional.empty(), Optional.empty(), List.of())).getMessage());
        assertEquals("resolutionReason", assertThrows(NullPointerException.class,
                () -> new ExternalReference(id, ownerId, target, ExternalReferenceResolutionState.UNRESOLVED, null,
                        Optional.empty(), Optional.empty(), List.of())).getMessage());
        assertEquals("resolvedTarget", assertThrows(NullPointerException.class,
                () -> new ExternalReference(id, ownerId, target, ExternalReferenceResolutionState.UNRESOLVED,
                        ExternalReferenceResolutionReason.NO_RESOLVER, null, Optional.empty(), List.of()))
                .getMessage());
        assertEquals("history", assertThrows(NullPointerException.class,
                () -> new ExternalReference(id, ownerId, target, ExternalReferenceResolutionState.UNRESOLVED,
                        ExternalReferenceResolutionReason.NO_RESOLVER, Optional.empty(), Optional.empty(), null))
                .getMessage());
    }

    @Test
    void anEventRefusesAnAbsentComponentByName() {
        assertEquals("previousState", assertThrows(NullPointerException.class,
                () -> new ExternalReferenceResolutionEvent(null, ExternalReferenceResolutionState.STALE,
                        ExternalReferenceResolutionReason.TARGET_REMOVED, FIRST_ATTEMPT)).getMessage());
        assertEquals("newState", assertThrows(NullPointerException.class,
                () -> new ExternalReferenceResolutionEvent(ExternalReferenceResolutionState.RESOLVED, null,
                        ExternalReferenceResolutionReason.TARGET_REMOVED, FIRST_ATTEMPT)).getMessage());
        assertEquals("reason", assertThrows(NullPointerException.class,
                () -> new ExternalReferenceResolutionEvent(ExternalReferenceResolutionState.RESOLVED,
                        ExternalReferenceResolutionState.STALE, null, FIRST_ATTEMPT)).getMessage());
        assertEquals("occurredAt", assertThrows(NullPointerException.class,
                () -> new ExternalReferenceResolutionEvent(ExternalReferenceResolutionState.RESOLVED,
                        ExternalReferenceResolutionState.STALE, ExternalReferenceResolutionReason.TARGET_REMOVED,
                        null)).getMessage());
    }

    @Test
    void aTargetUppercasesItsTokensAndTrimsItsText() {
        ExternalReferenceTarget normalized = new ExternalReferenceTarget(" github ", Optional.of(" org/repo "),
                " pull-request ", "  42  ", Optional.of(" abc "));

        assertEquals("GITHUB", normalized.system());
        assertEquals(Optional.of("org/repo"), normalized.project());
        assertEquals("PULL-REQUEST", normalized.resourceType());
        assertEquals("42", normalized.externalId());
        assertEquals(Optional.of("abc"), normalized.revision());
    }

    @Test
    void aTargetReadsABlankOptionalAsAbsent() {
        ExternalReferenceTarget normalized =
                new ExternalReferenceTarget("s", Optional.of("  "), "r", "e", Optional.of(""));

        assertEquals(Optional.empty(), normalized.project());
        assertEquals(Optional.empty(), normalized.revision());
    }

    @Test
    void aTargetRefusesABlankOrAbsentComponentByName() {
        assertEquals("system must not be blank", assertThrows(IllegalArgumentException.class,
                () -> new ExternalReferenceTarget(" ", Optional.empty(), "r", "e", Optional.empty())).getMessage());
        assertEquals("resourceType must not be blank", assertThrows(IllegalArgumentException.class,
                () -> new ExternalReferenceTarget("s", Optional.empty(), "", "e", Optional.empty())).getMessage());
        assertEquals("externalId must not be blank", assertThrows(IllegalArgumentException.class,
                () -> new ExternalReferenceTarget("s", Optional.empty(), "r", "\t", Optional.empty())).getMessage());
        assertEquals("system", assertThrows(NullPointerException.class,
                () -> new ExternalReferenceTarget(null, Optional.empty(), "r", "e", Optional.empty())).getMessage());
        assertEquals("project", assertThrows(NullPointerException.class,
                () -> new ExternalReferenceTarget("s", null, "r", "e", Optional.empty())).getMessage());
        assertEquals("revision", assertThrows(NullPointerException.class,
                () -> new ExternalReferenceTarget("s", Optional.empty(), "r", "e", null)).getMessage());
    }

    @Test
    void aResolvedTargetKeepsACopyOfItsAttributes() {
        Map<String, String> attributes = new HashMap<>(Map.of("status", "Open"));
        ResolvedExternalTarget copy = new ResolvedExternalTarget(target, attributes);
        attributes.put("assignee", "someone");

        assertEquals(target, copy.target());
        assertEquals(Map.of("status", "Open"), copy.attributes());
    }

    @Test
    void aResolvedTargetRefusesABlankAttributeKeyOrAnAbsentComponent() {
        assertEquals("attributes must contain non-blank keys and non-null values", assertThrows(
                IllegalArgumentException.class,
                () -> new ResolvedExternalTarget(target, Map.of(" ", "v"))).getMessage());
        assertEquals("target", assertThrows(NullPointerException.class,
                () -> new ResolvedExternalTarget(null, Map.of())).getMessage());
        assertEquals("attributes", assertThrows(NullPointerException.class,
                () -> new ResolvedExternalTarget(target, null)).getMessage());
    }
}
