/// <reference path="../rules.d.ts" />

// Le domaine ne nomme aucun type ni paquet de provider.

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
            fix: "Passer par les types du domaine (ADR-0001).",
          });
        }
      },
    },
  },
} satisfies RuleSet;
