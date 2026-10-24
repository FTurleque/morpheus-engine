/// <reference path="../rules.d.ts" />

// La résolution live d'une référence externe ne modifie pas le snapshot.

export default {
  rules: {
    "live-resolution-writes-no-reference": {
      description: "Aucun service de résolution n'écrit dans le store des références",
      severity: "error",
      async check(ctx) {
        const pattern = /\.putReference\(/;
        const scope = "morpheus-application/src/main/java/com/morpheus/application/reference/*ResolutionService.java";
        for (const match of await ctx.grepFiles(pattern, scope)) {
          ctx.report.violation({
            message: `La résolution live écrit dans le store des références : ${match.content.trim()}`,
            file: match.file,
            line: match.line,
            fix: "Rendre le résultat sans le persister (ADR-0142).",
          });
        }
      },
    },
  },
} satisfies RuleSet;
