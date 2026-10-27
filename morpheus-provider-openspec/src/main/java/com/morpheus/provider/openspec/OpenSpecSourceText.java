package com.morpheus.provider.openspec;

import com.morpheus.application.read.ProviderIngestionBudget;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * How the readers of this provider take the text of a source: its lines, read within the ingestion budget, and the
 * content of a section, joined from the lines that are neither blank nor a heading.
 */
final class OpenSpecSourceText {
    private OpenSpecSourceText() {
    }

    static List<String> readAllLines(Path workspaceRoot, Path source, ProviderIngestionBudget.Session budget) {
        try {
            return budget.readDocument(workspaceRoot.relativize(source))
                    .lines()
                    .toList();
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Cannot read OpenSpec source: " + OpenSpecSourceAttribution.relayable(exception), exception);
        }
    }

    static String joinContent(List<String> lines, int startInclusive, int endExclusive) {
        List<String> content = new ArrayList<>();
        for (int index = startInclusive; index < endExclusive; index++) {
            String line = lines.get(index).trim();
            if (!line.isEmpty() && !line.startsWith("#")) {
                content.add(line);
            }
        }
        return String.join(" ", content).trim();
    }
}
