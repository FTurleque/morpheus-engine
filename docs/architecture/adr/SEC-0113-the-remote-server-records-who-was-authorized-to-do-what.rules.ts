/// <reference path="../rules.d.ts" />

// Le guide de l'opérateur nomme le refus d'une mutation sans journal d'accès.

export default {
  rules: {
    "operator-guide-names-the-journal-refusal": {
      description: "docs/user/TEAM_REMOTE_SERVER.md nomme ACCESS_JOURNAL_UNAVAILABLE",
      severity: "error",
      async check(ctx) {
        const file = "docs/user/TEAM_REMOTE_SERVER.md";
        const pattern = /ACCESS_JOURNAL_UNAVAILABLE/;
        if (!pattern.test(await ctx.readFile(file))) {
          ctx.report.violation({
            message: "Le guide de l'opérateur ne nomme plus le refus ACCESS_JOURNAL_UNAVAILABLE",
            file,
            fix: "Documenter le refus 503 ACCESS_JOURNAL_UNAVAILABLE (ADR-0113).",
          });
        }
      },
    },
  },
} satisfies RuleSet;
