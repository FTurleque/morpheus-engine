/// <reference path="../rules.d.ts" />

// La dérivation de traçabilité est déterministe.

export default {
  rules: {
    "derivation-is-deterministic": {
      description: "La dérivation ne génère pas d'identité aléatoire et ne lit pas l'horloge",
      severity: "error",
      async check(ctx) {
        const pattern = /\.generate\(\)|Instant\.now\(|currentTimeMillis\(/;
        const scope = "morpheus-application/src/main/java/**/DeterministicTraceabilityDerivationService.java";
        for (const match of await ctx.grepFiles(pattern, scope)) {
          ctx.report.violation({
            message: `Source non déterministe dans la dérivation : ${match.content.trim()}`,
            file: match.file,
            line: match.line,
            fix: "Dériver l'identité de la clé de dérivation (ADR-0039).",
          });
        }
      },
    },
  },
} satisfies RuleSet;
