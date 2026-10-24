/// <reference path="../rules.d.ts" />

// La détection de changement est déterministe et l'état de sync s'écrit sous CAS.

export default {
  rules: {
    "sync-freshness-reads-no-clock": {
      description: "SyncFreshness et SyncFreshnessService ne lisent pas l'horloge",
      severity: "error",
      async check(ctx) {
        const pattern = /Instant\.now\(|currentTimeMillis\(/;
        const scope = "morpheus-application/src/main/java/com/morpheus/application/sync/SyncFreshness*.java";
        for (const match of await ctx.grepFiles(pattern, scope)) {
          ctx.report.violation({
            message: `Lecture d'horloge dans le calcul de fraîcheur : ${match.content.trim()}`,
            file: match.file,
            line: match.line,
            fix: "Recevoir l'instant en argument (ADR-0125).",
          });
        }
      },
    },

    "sync-state-has-no-blind-upsert": {
      description: "Le store SQLite de l'état de sync n'écrit ni upsert ni remplacement",
      severity: "error",
      async check(ctx) {
        const pattern = /DO\s+UPDATE|OR\s+REPLACE|REPLACE\s+INTO/i;
        const scope = "morpheus-store-sqlite/src/main/java/com/morpheus/store/sqlite/SqliteSyncStateStore.java";
        for (const match of await ctx.grepFiles(pattern, scope)) {
          ctx.report.violation({
            message: `Upsert aveugle sur l'état de sync : ${match.content.trim()}`,
            file: match.file,
            line: match.line,
            fix: "Écrire sous révision attendue : un conflit se signale (ADR-0125).",
          });
        }
      },
    },
  },
} satisfies RuleSet;
