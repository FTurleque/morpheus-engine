package com.morpheus.domain.evidence;

import com.morpheus.domain.source.SourceLocator;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EvidenceTest {
    private final EvidenceId id = EvidenceId.generate();
    private final SourceLocator source = SourceLocator.file("specs/a.md");

    @Test
    void evidenceKeepsItsRangeAndATrimmedExcerptHash() {
        SourceRange range = new SourceRange(3, 7);
        Evidence evidence = new Evidence(id, source, Optional.of(range), Optional.of("  abc123 "));

        assertEquals(id, evidence.id());
        assertEquals(source, evidence.source());
        assertEquals(Optional.of(range), evidence.range());
        assertEquals(Optional.of("abc123"), evidence.excerptHash());
    }

    @Test
    void aBlankExcerptHashIsReadAsNoHash() {
        assertEquals(Optional.empty(), new Evidence(id, source, Optional.empty(), Optional.of(" ")).excerptHash());
    }

    @Test
    void anAbsentComponentIsRefusedByName() {
        assertEquals("id", assertThrows(NullPointerException.class,
                () -> new Evidence(null, source, Optional.empty(), Optional.empty())).getMessage());
        assertEquals("source", assertThrows(NullPointerException.class,
                () -> new Evidence(id, null, Optional.empty(), Optional.empty())).getMessage());
        assertEquals("range", assertThrows(NullPointerException.class,
                () -> new Evidence(id, source, null, Optional.empty())).getMessage());
        assertEquals("excerptHash", assertThrows(NullPointerException.class,
                () -> new Evidence(id, source, Optional.empty(), null)).getMessage());
    }

    @Test
    void aRangeMayCoverASingleLine() {
        SourceRange range = new SourceRange(1, 1);

        assertEquals(1, range.startLine());
        assertEquals(1, range.endLine());
    }

    @Test
    void aRangeStartsAtLineOneAndNeverEndsBeforeItStarts() {
        assertEquals("startLine must be >= 1", assertThrows(IllegalArgumentException.class,
                () -> new SourceRange(0, 4)).getMessage());
        assertEquals("endLine must be >= startLine", assertThrows(IllegalArgumentException.class,
                () -> new SourceRange(5, 4)).getMessage());
    }
}
