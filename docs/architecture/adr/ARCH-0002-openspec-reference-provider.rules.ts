/// <reference path="../rules.d.ts" />

// Le provider OpenSpec est en lecture : il ne déclare aucune capacité d'écriture ni de surveillance.

export default {
  rules: {
    "openspec-provider-declares-no-write-capability": {
      description: "Le provider OpenSpec ne mentionne aucune capacité d'écriture",
      severity: "error",
      async check(ctx) {
        const pattern = /\b(WATCH_CHANGES|WRITE_CHANGE|WRITE_TASK_STATE|ARCHIVE_CHANGE)\b/;
        const scope = "morpheus-provider-openspec/src/main/java/**/*.java";
        for (const match of await ctx.grepFiles(pattern, scope)) {
          ctx.report.violation({
            message: `Capacité d'écriture ou de surveillance dans le provider OpenSpec : ${match.content.trim()}`,
            file: match.file,
            line: match.line,
            fix: "Le provider de référence reste en lecture (ADR-0002, ADR-0008).",
          });
        }
      },
    },
  },
} satisfies RuleSet;
