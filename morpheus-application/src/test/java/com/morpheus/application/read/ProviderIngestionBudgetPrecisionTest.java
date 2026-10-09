package com.morpheus.application.read;

import com.morpheus.application.files.SafeWorkspaceFileResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Every refusal of the ingestion budget says which budget, for which source, with the value and the bound -- and each
 * bound is exact (PIT-AUD-3: the tests asserted a type or a fragment, so a bound off by one, a value miscounted or a
 * refusal attributed to the wrong budget survived).
 */
class ProviderIngestionBudgetPrecisionTest {
    @TempDir
    Path workspace;

    @Test
    void eachBoundMustBePositiveAndTheDocumentBoundMayEqualTheAggregate() {
        assertRefused("maxDocumentBytes must be >= 1", () -> new ProviderIngestionBudget(0, 1, 1, 1, 1, 1, 1));
        assertRefused("maxFiles must be >= 1", () -> new ProviderIngestionBudget(1, 0, 1, 1, 1, 1, 1));
        assertRefused("maxAggregateBytes must be >= 1", () -> new ProviderIngestionBudget(1, 1, 0, 1, 1, 1, 1));
        assertRefused("maxLines must be >= 1", () -> new ProviderIngestionBudget(1, 1, 1, 0, 1, 1, 1));
        assertRefused("maxBlocks must be >= 1", () -> new ProviderIngestionBudget(1, 1, 1, 1, 0, 1, 1));
        assertRefused("maxEntities must be >= 1", () -> new ProviderIngestionBudget(1, 1, 1, 1, 1, 0, 1));
        assertRefused("maxEvidenceBytes must be >= 1", () -> new ProviderIngestionBudget(1, 1, 1, 1, 1, 1, 0));
        assertDoesNotThrow(() -> new ProviderIngestionBudget(5, 1, 5, 1, 1, 1, 1));
    }

    @Test
    void eachDirectCheckNamesItsBudgetTheValueAndTheBound() {
        ProviderIngestionBudget budget = new ProviderIngestionBudget(10, 2, 20, 3, 4, 5, 6);

        assertLimit("provider ingestion document bytes exceeds budget for doc: 11 > 10",
                () -> budget.requireDocumentBytes(11, "doc"));
        assertLimit("provider ingestion file count exceeds budget for corpus: 3 > 2",
                () -> budget.requireFiles(3, "corpus"));
        assertLimit("provider ingestion aggregate bytes exceeds budget for corpus: 21 > 20",
                () -> budget.requireAggregateBytes(21, "corpus"));
        assertLimit("provider ingestion line count exceeds budget for doc: 4 > 3", () -> budget.requireLines(4, "doc"));
        assertLimit("provider ingestion block count exceeds budget for doc: 5 > 4", () -> budget.requireBlocks(5, "doc"));
        assertLimit("provider ingestion entity count exceeds budget for doc: 6 > 5",
                () -> budget.requireEntities(6, "doc"));
        assertLimit("provider ingestion evidence bytes exceeds budget for doc: 7 > 6",
                () -> budget.requireEvidenceBytes(7, "doc"));
    }

    @Test
    void aWholeDocumentIsCheckedForItsEncodedBytesAndItsLines() {
        ProviderIngestionBudget budget = new ProviderIngestionBudget(4, 1, 4, 2, 1, 1, 1);
        String twoBytes = new String(Character.toChars(0xE9));

        assertDoesNotThrow(() -> budget.requireUtf8Document("ab" + twoBytes, "doc"));
        assertLimit("provider ingestion document bytes exceeds budget for doc: 5 > 4",
                () -> budget.requireUtf8Document("abc" + twoBytes, "doc"));
        ProviderIngestionBudget lines = new ProviderIngestionBudget(10, 1, 10, 2, 1, 1, 1);
        assertLimit("provider ingestion line count exceeds budget for doc: 3 > 2",
                () -> lines.requireUtf8Document("a\nb\nc", "doc"));
    }

    @Test
    void encodedBytesAreCountedAsUtf8EncodesEveryWidth() {
        String supplementary = new String(Character.toChars(0x1F600));
        List<String> samples = List.of(
                "a",
                new String(Character.toChars(0xE9)),
                new String(Character.toChars(0x20AC)),
                supplementary,
                "a" + new String(Character.toChars(0x7FF)) + new String(Character.toChars(0x800)) + supplementary);
        for (String sample : samples) {
            assertEquals(sample.getBytes(StandardCharsets.UTF_8).length, ProviderIngestionBudget.utf8Bytes(sample),
                    () -> "UTF-8 width of " + sample.codePoints().boxed().toList());
        }
        // A lone surrogate is not encodable; it is counted as the single replacement byte the encoder writes.
        assertEquals(1, ProviderIngestionBudget.utf8Bytes(String.valueOf((char) 0xD800)));
        assertEquals(1, ProviderIngestionBudget.utf8Bytes(String.valueOf((char) 0xDC00)));
        assertEquals(2, ProviderIngestionBudget.utf8Bytes((char) 0xD800 + "a"));
    }

    @Test
    void theLastAggregateByteCanBeReadAndTheNextReadIsRefusedBeforeReading() throws Exception {
        write("a.md", "12345");
        write("b.md", "6");
        write("c.md", "7");
        var session = new ProviderIngestionBudget(6, 10, 6, 10, 10, 10, 10).open(files());

        session.readDocument(Path.of("a.md"));
        assertEquals("6", session.readDocument(Path.of("b.md")));

        assertLimit("provider ingestion aggregate bytes exceeds budget for c.md: 7 > 6",
                () -> session.readDocument(Path.of("c.md")));
    }

    @Test
    void theLastEvidenceByteCanBeReadAndTheNextEvidenceReadIsRefusedBeforeReading() throws Exception {
        write("a.txt", "1234");
        write("b.txt", "5");
        write("c.txt", "6");
        var session = new ProviderIngestionBudget(10, 10, 100, 10, 10, 10, 5).open(files());

        session.readEvidence(Path.of("a.txt"));
        assertEquals("5", session.readEvidence(Path.of("b.txt")));

        assertLimit("provider ingestion evidence bytes exceeds budget for c.txt: 6 > 5",
                () -> session.readEvidence(Path.of("c.txt")));
        assertEquals(5, session.evidenceBytes());
    }

    /** The remaining aggregate equals the item bound: the aggregate, not the document, is what the read ran out of. */
    @Test
    void aReadStoppedByTheRemainingAggregateIsAnAggregateOverrunEvenAtTheItemBound() throws Exception {
        write("a.md", "1234");
        write("b.md", "56789");
        var session = new ProviderIngestionBudget(4, 10, 8, 10, 10, 10, 10).open(files());

        session.readDocument(Path.of("a.md"));

        assertLimit("provider ingestion aggregate bytes exceeds budget for b.md: 9 > 8",
                () -> session.readDocument(Path.of("b.md")));
        assertEquals(4, session.aggregateBytes());
    }

    /** The remaining evidence equals the item bound: the evidence budget is what the read ran out of. */
    @Test
    void anEvidenceReadStoppedByTheRemainingEvidenceIsAnEvidenceOverrunEvenAtTheItemBound() throws Exception {
        write("a.txt", "1");
        write("b.txt", "234567");
        var session = new ProviderIngestionBudget(5, 10, 100, 10, 10, 10, 6).open(files());

        session.readEvidence(Path.of("a.txt"));

        assertLimit("provider ingestion evidence bytes exceeds budget for b.txt: 7 > 6",
                () -> session.readEvidence(Path.of("b.txt")));
    }

    @Test
    void anEvidenceFileLargerThanTheDocumentBoundIsADocumentOverrunWithTheDocumentBound() throws Exception {
        write("big.txt", "123456");
        var session = new ProviderIngestionBudget(5, 10, 1_000, 10, 10, 10, 100).open(files());

        assertLimit("provider ingestion document bytes exceeds budget for big.txt: 6 > 5",
                () -> session.readEvidence(Path.of("big.txt")));
    }

    @Test
    void blocksEntitiesAndFilesAreCountedAndRefusedPastTheirBudget() {
        var session = new ProviderIngestionBudget(10, 3, 100, 10, 4, 5, 10).open(files());

        session.addBlocks(4, "doc");
        session.addEntities(5, "doc");
        assertEquals(4, session.blockCount());
        assertEquals(5, session.entityCount());
        assertEquals(0, session.lineCount());
        assertLimit("provider ingestion block count exceeds budget for doc: 5 > 4", () -> session.addBlocks(1, "doc"));
        assertLimit("provider ingestion entity count exceeds budget for doc: 6 > 5",
                () -> session.addEntities(1, "doc"));
        assertEquals(4, session.blockCount());
        assertEquals(5, session.entityCount());
        assertRefused("block count must not be negative", () -> session.addBlocks(-1, "doc"));
        assertRefused("entity count must not be negative", () -> session.addEntities(-1, "doc"));

        assertEquals(3, session.remainingFiles());
        assertDoesNotThrow(() -> session.requireAdditionalFiles(3, "listing"));
        assertLimit("provider ingestion file count exceeds budget for listing: 4 > 3",
                () -> session.requireAdditionalFiles(4, "listing"));
        assertRefused("file count must not be negative", () -> session.requireAdditionalFiles(-1, "listing"));
    }

    /** Zero is a count, not a negative one: every check accepts it. */
    @Test
    void aZeroCountIsAcceptedEverywhereANegativeOneIsRefused() {
        ProviderIngestionBudget budget = new ProviderIngestionBudget(1, 1, 1, 1, 1, 1, 1);
        var session = budget.open(files());

        assertDoesNotThrow(() -> budget.requireDocumentBytes(0, "doc"));
        assertDoesNotThrow(() -> session.addBlocks(0, "doc"));
        assertDoesNotThrow(() -> session.addEntities(0, "doc"));
        assertDoesNotThrow(() -> session.requireAdditionalFiles(0, "listing"));
        assertRefused("document bytes must not be negative", () -> budget.requireDocumentBytes(-1, "doc"));
    }

    @Test
    void theRemainingFilesAreTheBudgetLessTheFilesRead() throws Exception {
        write("a.md", "a");
        var session = new ProviderIngestionBudget(10, 3, 100, 10, 10, 10, 10).open(files());

        session.readDocument(Path.of("a.md"));

        assertEquals(2, session.remainingFiles());
    }

    @Test
    void theLastAsciiCharacterIsOneByte() {
        assertEquals(1, ProviderIngestionBudget.utf8Bytes(String.valueOf((char) 0x7F)));
        assertEquals(2, ProviderIngestionBudget.utf8Bytes(String.valueOf((char) 0x80)));
    }

    /** A document read past its bound is a document overrun, even when the evidence bound has the same value. */
    @Test
    void aDocumentReadIsNeverAttributedToTheEvidenceBudget() throws Exception {
        write("big.md", "123456");
        var session = new ProviderIngestionBudget(5, 10, 100, 10, 10, 10, 5).open(files());

        assertLimit("provider ingestion document bytes exceeds budget for big.md: 6 > 5",
                () -> session.readDocument(Path.of("big.md")));
    }

    private void write(String name, String content) throws Exception {
        Files.writeString(workspace.resolve(name), content, StandardCharsets.UTF_8);
    }

    private SafeWorkspaceFileResolver files() {
        try {
            return SafeWorkspaceFileResolver.rootedAt(workspace);
        } catch (java.io.IOException failure) {
            throw new AssertionError(failure);
        }
    }

    private static void assertLimit(String message, org.junit.jupiter.api.function.Executable action) {
        assertEquals(message, assertThrows(ProviderIngestionLimitException.class, action).getMessage());
    }

    private static void assertRefused(String message, org.junit.jupiter.api.function.Executable action) {
        assertEquals(message, assertThrows(IllegalArgumentException.class, action).getMessage());
    }
}
