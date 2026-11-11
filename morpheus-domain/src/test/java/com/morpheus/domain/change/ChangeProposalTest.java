package com.morpheus.domain.change;

import com.morpheus.domain.DomainFixtures;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.provenance.Provenance;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ChangeProposalTest {
    private final ChangeId id = ChangeId.generate();
    private final ProjectSpecificationId projectId = ProjectSpecificationId.generate();
    private final Provenance provenance = DomainFixtures.provenance();

    @Test
    void aProposalKeepsItsTextTrimmedAndADeclaredKeyTrimmed() {
        ChangeProposal proposal = new ChangeProposal(id, projectId, Optional.of("  add-x  "), "  Add X ",
                " Because ", List.of(" scope "), List.of(" out "), List.of(" risk "), provenance);

        assertEquals(id, proposal.id());
        assertEquals(projectId, proposal.projectId());
        assertEquals(Optional.of("add-x"), proposal.key());
        assertEquals("Add X", proposal.title());
        assertEquals("Because", proposal.intent());
        assertEquals(List.of("scope"), proposal.scope());
        assertEquals(List.of("out"), proposal.outOfScope());
        assertEquals(List.of("risk"), proposal.risks());
        assertEquals(provenance, proposal.provenance());
    }

    @Test
    void aBlankKeyIsReadAsNoKey() {
        assertEquals(Optional.empty(), proposalWithKey(Optional.of("   ")).key());
    }

    @Test
    void theListsAreCopiesTheCallerCannotChangeAfterwards() {
        List<String> scope = new ArrayList<>(List.of("a"));
        ChangeProposal proposal = new ChangeProposal(id, projectId, Optional.empty(), "t", "i",
                scope, List.of(), List.of(), provenance);
        scope.add("b");

        assertEquals(List.of("a"), proposal.scope());
    }

    @Test
    void aBlankTitleOrIntentIsRefusedByName() {
        assertEquals("title must not be blank", assertThrows(IllegalArgumentException.class,
                () -> new ChangeProposal(id, projectId, Optional.empty(), " ", "i", List.of(), List.of(), List.of(),
                        provenance)).getMessage());
        assertEquals("intent must not be blank", assertThrows(IllegalArgumentException.class,
                () -> new ChangeProposal(id, projectId, Optional.empty(), "t", "", List.of(), List.of(), List.of(),
                        provenance)).getMessage());
    }

    @Test
    void aBlankListItemIsRefusedWithTheNameOfItsList() {
        assertEquals("scope item must not be blank", assertThrows(IllegalArgumentException.class,
                () -> new ChangeProposal(id, projectId, Optional.empty(), "t", "i", List.of(" "), List.of(),
                        List.of(), provenance)).getMessage());
        assertEquals("outOfScope item must not be blank", assertThrows(IllegalArgumentException.class,
                () -> new ChangeProposal(id, projectId, Optional.empty(), "t", "i", List.of(), List.of(""),
                        List.of(), provenance)).getMessage());
        assertEquals("risks item must not be blank", assertThrows(IllegalArgumentException.class,
                () -> new ChangeProposal(id, projectId, Optional.empty(), "t", "i", List.of(), List.of(),
                        List.of("\t"), provenance)).getMessage());
    }

    @Test
    void anAbsentComponentIsRefusedByName() {
        assertEquals("id", assertThrows(NullPointerException.class,
                () -> new ChangeProposal(null, projectId, Optional.empty(), "t", "i", List.of(), List.of(),
                        List.of(), provenance)).getMessage());
        assertEquals("projectId", assertThrows(NullPointerException.class,
                () -> new ChangeProposal(id, null, Optional.empty(), "t", "i", List.of(), List.of(), List.of(),
                        provenance)).getMessage());
        assertEquals("key", assertThrows(NullPointerException.class, () -> proposalWithKey(null)).getMessage());
        assertEquals("title", assertThrows(NullPointerException.class,
                () -> new ChangeProposal(id, projectId, Optional.empty(), null, "i", List.of(), List.of(), List.of(),
                        provenance)).getMessage());
        assertEquals("scope", assertThrows(NullPointerException.class,
                () -> new ChangeProposal(id, projectId, Optional.empty(), "t", "i", null, List.of(), List.of(),
                        provenance)).getMessage());
        assertEquals("provenance", assertThrows(NullPointerException.class,
                () -> new ChangeProposal(id, projectId, Optional.empty(), "t", "i", List.of(), List.of(), List.of(),
                        null)).getMessage());
    }

    private ChangeProposal proposalWithKey(Optional<String> key) {
        return new ChangeProposal(id, projectId, key, "t", "i", List.of(), List.of(), List.of(), provenance);
    }
}
