/// <reference path="../rules.d.ts" />

// La découverte de la racine de workspace ne lance aucun processus.

export default {
  rules: {
    "workspace-discovery-starts-no-process": {
      description: "Le paquet application.discovery ne lance aucun processus",
      severity: "error",
      async check(ctx) {
        const pattern = /ProcessBuilder|Runtime\.getRuntime/;
        const scope = "morpheus-application/src/main/java/com/morpheus/application/discovery/**/*.java";
        for (const match of await ctx.grepFiles(pattern, scope)) {
          ctx.report.violation({
            message: `Lancement de processus dans la découverte : ${match.content.trim()}`,
            file: match.file,
            line: match.line,
            fix: "Lire le système de fichiers seulement (ADR-0020).",
          });
        }
      },
    },
  },
} satisfies RuleSet;
