/// <reference path="../rules.d.ts" />

// Le cœur ne dépend d'aucun client LLM : aucun POM ne déclare de bibliothèque d'appel à un modèle.

export default {
  rules: {
    "no-llm-client-dependency": {
      description: "Aucun POM ne déclare de client LLM",
      severity: "error",
      async check(ctx) {
        const pattern = new RegExp(
          "<groupId>\\s*(com\\.openai|com\\.anthropic|dev\\.langchain4j|" +
          "com\\.theokanning|io\\.github\\.ollama4j|org\\.springframework\\.ai|" +
          "com\\.google\\.cloud\\.vertexai|com\\.azure\\.ai)",
          "",
        );
        const scope = "**/pom.xml";
        for (const match of await ctx.grepFiles(pattern, scope)) {
          ctx.report.violation({
            message: `Client LLM déclaré dans un POM : ${match.content.trim()}`,
            file: match.file,
            line: match.line,
            fix: "Retirer la dépendance : le cœur fonctionne sans LLM (ADR-0004).",
          });
        }
      },
    },
  },
} satisfies RuleSet;
