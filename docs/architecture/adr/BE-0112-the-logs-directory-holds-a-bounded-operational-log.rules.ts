/// <reference path="../rules.d.ts" />

// Le journal opérationnel passe par SLF4J, jamais par un LoggerFinder.

export default {
  rules: {
    "no-logger-finder": {
      description: "Aucune source de production ne déclare de LoggerFinder",
      severity: "error",
      async check(ctx) {
        const pattern = /\bLoggerFinder\b/;
        const scope = "**/src/main/java/**/*.java";
        for (const match of await ctx.grepFiles(pattern, scope)) {
          ctx.report.violation({
            message: `LoggerFinder dans une source de production : ${match.content.trim()}`,
            file: match.file,
            line: match.line,
            fix: "Journaliser par SLF4J (ADR-0112).",
          });
        }
      },
    },
  },
} satisfies RuleSet;
