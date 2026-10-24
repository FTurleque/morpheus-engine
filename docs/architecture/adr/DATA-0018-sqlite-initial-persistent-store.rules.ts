/// <reference path="../rules.d.ts" />

// SQLite reste derrière son adaptateur : le domaine et l'application ne déclarent pas son pilote.

export default {
  rules: {
    "inner-layers-do-not-declare-sqlite": {
      description: "morpheus-domain et morpheus-application ne déclarent pas sqlite-jdbc",
      severity: "error",
      async check(ctx) {
        const pattern = /sqlite-jdbc/;
        const scope = "{morpheus-domain,morpheus-application}/pom.xml";
        for (const match of await ctx.grepFiles(pattern, scope)) {
          ctx.report.violation({
            message: `Pilote SQLite déclaré dans une couche intérieure : ${match.content.trim()}`,
            file: match.file,
            line: match.line,
            fix: "Retirer la dépendance : SQLite vit dans morpheus-store-sqlite (ADR-0018).",
          });
        }
      },
    },
  },
} satisfies RuleSet;
