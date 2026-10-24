/// <reference path="../rules.d.ts" />

// Le produit embarque un seul fournisseur SLF4J, le sien.

export default {
  rules: {
    "no-other-slf4j-provider": {
      description: "Aucun POM ne déclare un autre fournisseur SLF4J",
      severity: "error",
      async check(ctx) {
        const pattern = new RegExp(
          "<artifactId>\\s*(slf4j-simple|slf4j-jdk14|slf4j-reload4j|slf4j-log4j12|" +
          "logback-classic|log4j-slf4j2?-impl)\\s*<",
          "",
        );
        const scope = "**/pom.xml";
        for (const match of await ctx.grepFiles(pattern, scope)) {
          ctx.report.violation({
            message: `Second fournisseur SLF4J déclaré : ${match.content.trim()}`,
            file: match.file,
            line: match.line,
            fix: "Retirer la dépendance : le produit embarque son propre binding (ADR-0111).",
          });
        }
      },
    },
  },
} satisfies RuleSet;
