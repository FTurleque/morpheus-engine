/// <reference path="../rules.d.ts" />

// Le produit embarque un seul fournisseur SLF4J, le sien ; le journal opérationnel ne passe jamais par un LoggerFinder.

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
            fix: "Retirer la dépendance : le produit embarque son propre binding (ADR-0146).",
          });
        }
      },
    },
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
            fix: "Journaliser par SLF4J (ADR-0146).",
          });
        }
      },
    },
  },
} satisfies RuleSet;
