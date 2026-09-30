package com.morpheus.application.files;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Path;
import java.util.StringJoiner;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WorkspaceRelativePathTextTest {

    @Test
    void aPathBuiltFromComponentsIsNamedWithForwardSlashesOnEveryPlatform() {
        assertEquals("docs/proof.md", WorkspaceRelativePathText.of(Path.of("docs", "proof.md")));
    }

    @Test
    void aBackslashIsASeparatorOnlyWhereThePlatformSaysSo() {
        Path written = Path.of("docs\\proof.md");
        StringJoiner components = new StringJoiner("/");
        written.forEach(name -> components.add(name.toString()));

        String text = WorkspaceRelativePathText.of(written);

        assertEquals(components.toString(), text);
        assertEquals(File.separatorChar == '\\' ? "docs/proof.md" : "docs\\proof.md", text);
    }
}
