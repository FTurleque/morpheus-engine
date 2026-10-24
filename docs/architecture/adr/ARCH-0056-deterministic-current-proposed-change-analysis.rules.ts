/// <reference path="../rules.d.ts" />

// L'analyse de changement est déterministe : elle ne lit pas l'horloge.

export default {
  rules: {
    "change-analysis-reads-no-clock": {
      description: "Le paquet application.analysis ne lit pas l'horloge",
      severity: "error",
      async check(ctx) {
        const pattern = /Instant\.now\(|\bClock\b|currentTimeMillis\(|LocalDateTime\.now\(/;
        const scope = "morpheus-application/src/main/java/com/morpheus/application/analysis/**/*.java";
        for (const match of await ctx.grepFiles(pattern, scope)) {
          ctx.report.violation({
            message: `Lecture d'horloge dans l'analyse de changement : ${match.content.trim()}`,
            file: match.file,
            line: match.line,
            fix: "Recevoir l'instant en argument : l'analyse est déterministe (ADR-0056).",
          });
        }
      },
    },
  },
} satisfies RuleSet;
