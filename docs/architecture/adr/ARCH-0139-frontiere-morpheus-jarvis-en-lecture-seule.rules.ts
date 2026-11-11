/// <reference path="../rules.d.ts" />

// MORPHEUS ne dépend jamais de JARVIS, jusque dans ses POM.

export default {
  rules: {
    "no-jarvis-dependency": {
      description: "Aucun POM ne mentionne JARVIS",
      severity: "error",
      async check(ctx) {
        const pattern = /jarvis/i;
        const scope = "**/pom.xml";
        for (const match of await ctx.grepFiles(pattern, scope)) {
          ctx.report.violation({
            message: `Un POM mentionne JARVIS : ${match.content.trim()}`,
            file: match.file,
            line: match.line,
            fix: "Retirer la dépendance : JARVIS consomme MORPHEUS, jamais l'inverse (ADR-0139).",
          });
        }
      },
    },
  },
} satisfies RuleSet;
