/// <reference path="../rules.d.ts" />

// Les migrations SQL gardent des fins de ligne LF, dont dépend leur somme de contrôle.

export default {
  rules: {
    "sql-migrations-keep-lf": {
      description: ".gitattributes fixe *.sql en LF",
      severity: "error",
      async check(ctx) {
        const file = ".gitattributes";
        const pattern = /^\*\.sql\s+text\s+eol=lf\s*$/m;
        if (!pattern.test(await ctx.readFile(file))) {
          ctx.report.violation({
            message: ".gitattributes ne fixe plus *.sql text eol=lf",
            file,
            fix: "Rétablir la ligne *.sql text eol=lf (ADR-0021).",
          });
        }
      },
    },
  },
} satisfies RuleSet;
