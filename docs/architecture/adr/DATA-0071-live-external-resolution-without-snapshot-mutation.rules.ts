/// <reference path="../rules.d.ts" />

// La résolution live d'une référence externe ne modifie pas le snapshot.

export default {
  rules: {
    "live-resolution-writes-no-reference": {
      description: "ExternalReferenceResolutionService n'écrit aucune référence",
      severity: "error",
      async check(ctx) {
        const pattern = /putReference|ExternalReferenceStore/;
        const scope = "morpheus-application/src/main/java/com/morpheus/application/reference/ExternalReferenceResolutionService.java";
        for (const match of await ctx.grepFiles(pattern, scope)) {
          ctx.report.violation({
            message: `La résolution live écrit dans le store des références : ${match.content.trim()}`,
            file: match.file,
            line: match.line,
            fix: "Rendre le résultat sans le persister (ADR-0071).",
          });
        }
      },
    },
  },
} satisfies RuleSet;
