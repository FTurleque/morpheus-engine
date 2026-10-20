package com.morpheus.cli;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The command paths the CLI serves, declared once.
 *
 * <p>A path is the words that select a behaviour before any option: {@code version}, {@code portfolio list},
 * {@code policy pack create}. {@code MorpheusMain.run} refuses any other path as an unknown command before it asks a
 * sub-command whether it handles the arguments, so an action added to a sub-command's own {@code switch} is not
 * reachable until it is declared here. The {@code cli} column of {@code contracts/public-surfaces.tsv} is compared with
 * this list, and so is the embedded help.</p>
 *
 * <p>{@code api} and {@code mcp} are declared although {@code MorpheusMain.main} starts them before {@code run} is
 * reached: they are commands a user types.</p>
 */
public final class CliCommandPaths {
    private static final List<String> PATHS = List.of(
            "help",
            "version",
            "paths",
            "projects list",
            "projects add",
            "sync",
            "sync-status",
            "requirements find",
            "changes list",
            "changes get",
            "constraints list",
            "constraints evaluate",
            "decisions list",
            "tasks list",
            "trace-requirement",
            "change-context",
            "analyze-change",
            "quality",
            "product-info",
            "update-check",
            "reason adapters",
            "reason analyze",
            "reason help",
            "provider-plugins discover",
            "provider-plugins probe",
            "server identity create",
            "server identity list",
            "server identity rotate",
            "server identity role",
            "server identity revoke",
            "server identity migrate-legacy",
            "server backup create",
            "server backup verify",
            "server restore",
            "policy pack create",
            "policy pack list",
            "policy pack get",
            "policy pack versions",
            "policy pack update",
            "policy activate",
            "policy activations",
            "policy deactivate",
            "policy override put",
            "policy override list",
            "policy override remove",
            "policy evaluate",
            "policy dry-run",
            "policy audit",
            "query execute",
            "views create",
            "views list",
            "views get",
            "views versions",
            "views update",
            "views archive",
            "views execute",
            "export query",
            "export view",
            "portfolio create",
            "portfolio add-project",
            "portfolio missing",
            "portfolio freshness",
            "portfolio add-reference",
            "portfolio list",
            "portfolio overview",
            "portfolio members",
            "portfolio references",
            "portfolio conflicts",
            "portfolio traverse",
            "composition sync",
            "composition status",
            "composition conflicts",
            "lifecycle apply",
            "acceptance-criteria list",
            "change-orchestration state",
            "change-orchestration transition-check",
            "minos-status",
            "external-references list",
            "external-references resolve",
            "nexus-status",
            "augmented-context requirement",
            "augmented-context change",
            "api",
            "mcp");

    private static final Map<String, String> FORMS_OF_A_COMMAND = Map.of(
            "--help", "help",
            "-h", "help",
            "--version", "version");

    private CliCommandPaths() {
    }

    public static List<String> paths() {
        return PATHS;
    }

    /**
     * The refusal of an invocation whose command path is not declared, if it is not. A path that stops short of a
     * declared one ({@code portfolio}, {@code policy pack}) is left to its command, which says which action it lacks;
     * words after a declared path are left to the command too. The words are those before the first option, the global
     * layout and {@code --json} options being skipped wherever they stand.
     */
    static Optional<String> refusalOf(String[] args) {
        List<String> words = commandWords(args);
        if (words.isEmpty() || startsWithADeclaredPath(words) || isIncompletePath(words)) {
            return Optional.empty();
        }
        return Optional.of("unknown command: " + String.join(" ", words));
    }

    private static boolean startsWithADeclaredPath(List<String> words) {
        for (int length = words.size(); length >= 1; length--) {
            if (PATHS.contains(String.join(" ", words.subList(0, length)))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isIncompletePath(List<String> words) {
        String prefix = String.join(" ", words) + " ";
        return PATHS.stream().anyMatch(path -> path.startsWith(prefix));
    }

    /** {@code --help}, {@code -h} and {@code --version} select the commands {@code help} and {@code version}. */
    private static List<String> commandWords(String[] args) {
        List<String> words = new ArrayList<>();
        for (int index = 0; index < args.length; index++) {
            String token = args[index];
            if (token.equals("--json") || isLayoutOptionWithAttachedValue(token)) {
                continue;
            }
            if (token.equals("--data-dir") || token.equals("--config-dir") || token.equals("--db")) {
                index++;
                continue;
            }
            if (token.startsWith("-")) {
                if (words.isEmpty()) {
                    words.add(FORMS_OF_A_COMMAND.getOrDefault(token, token));
                }
                break;
            }
            words.add(token);
        }
        return List.copyOf(words);
    }

    private static boolean isLayoutOptionWithAttachedValue(String token) {
        return token.startsWith("--data-dir=") || token.startsWith("--config-dir=") || token.startsWith("--db=");
    }
}
