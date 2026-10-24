/// <reference path="../rules.d.ts" />

// Jackson reste dans les adaptateurs : le domaine et l'application n'en dépendent pas.

export default {
  rules: {
    "inner-layers-have-no-jackson": {
      description: "morpheus-domain et morpheus-application ne mentionnent pas tools.jackson",
      severity: "error",
      async check(ctx) {
        const pattern = /tools\.jackson/;
        const scope = "{morpheus-domain,morpheus-application}/{pom.xml,src/main/java/**/*.java}";
        for (const match of await ctx.grepFiles(pattern, scope)) {
          ctx.report.violation({
            message: `Jackson dans une couche intérieure : ${match.content.trim()}`,
            file: match.file,
            line: match.line,
            fix: "Sérialiser par CanonicalJsonSerializer, Jackson vit dans les adaptateurs (ADR-0123).",
          });
        }
      },
    },
  },
} satisfies RuleSet;
