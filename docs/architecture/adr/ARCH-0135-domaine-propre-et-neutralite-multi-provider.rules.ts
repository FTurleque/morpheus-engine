/// <reference path="../rules.d.ts" />

// Le domaine ne nomme aucun provider, et le provider synthétique sert la vérification, jamais la livraison.

export default {
  rules: {
    "domain-names-no-provider": {
      description: "morpheus-domain ne nomme ni paquet provider ni type OpenSpec",
      severity: "error",
      async check(ctx) {
        const pattern = /com\.morpheus\.provider|\bOpenSpec[A-Z]\w*/;
        const scope = "morpheus-domain/src/main/java/**/*.java";
        for (const match of await ctx.grepFiles(pattern, scope)) {
          ctx.report.violation({
            message: `Le domaine nomme un provider : ${match.content.trim()}`,
            file: match.file,
            line: match.line,
            fix: "Passer par les types du domaine (ADR-0135).",
          });
        }
      },
    },

    "synthetic-provider-is-test-scoped": {
      description: "Toute dépendance à morpheus-provider-synthetic est de portée test",
      severity: "error",
      async check(ctx) {
        // morpheus-coverage-report agrège la couverture de tous les modules et n'est pas livré.
        const aggregator = "morpheus-coverage-report/pom.xml";
        for (const file of (await ctx.glob("**/pom.xml")).filter((pom) => pom !== aggregator).sort()) {
          const pom = await ctx.readFile(file);
          for (const block of pom.match(/<dependency>[\s\S]*?<\/dependency>/g) ?? []) {
            if (block.includes("<artifactId>morpheus-provider-synthetic</artifactId>") && !block.includes("<scope>test</scope>")) {
              ctx.report.violation({
                message: `${file} dépend de morpheus-provider-synthetic hors de la portée test.`,
                file,
                fix: "Déclarer <scope>test</scope> : le provider synthétique ne part pas dans le produit (ADR-0135).",
              });
            }
          }
        }
      },
    },
  },
} satisfies RuleSet;
