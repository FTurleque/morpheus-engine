/// <reference path="../rules.d.ts" />

// Une composition multi-provider ne sélectionne jamais par précédence implicite.

export default {
  rules: {
    "no-implicit-precedence-selection": {
      description: "Aucune source de production n'emploie SELECTED_BY_PRECEDENCE",
      severity: "error",
      async check(ctx) {
        const pattern = /SELECTED_BY_PRECEDENCE/;
        const scope = "**/src/main/java/**/*.java";
        for (const match of await ctx.grepFiles(pattern, scope)) {
          ctx.report.violation({
            message: `Sélection par précédence implicite : ${match.content.trim()}`,
            file: match.file,
            line: match.line,
            fix: "Signaler le conflit au lieu de choisir (ADR-0084).",
          });
        }
      },
    },
  },
} satisfies RuleSet;
