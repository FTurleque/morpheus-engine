/// <reference path="../rules.d.ts" />

// MORPHEUS ne publie aucune image de conteneur.

export default {
  rules: {
    "no-container-image-definition": {
      description: "Aucun Dockerfile ni fichier compose dans le dépôt",
      severity: "error",
      async check(ctx) {
        const files = [
          ...(await ctx.glob("**/Dockerfile*")),
          ...(await ctx.glob("**/*compose*.y*ml")),
        ];
        for (const file of files.sort()) {
          ctx.report.violation({
            message: `${file} définit une image ou un déploiement de conteneur.`,
            file,
            fix: "Retirer le fichier : la distribution est native, sans image Docker (ADR-0134).",
          });
        }
      },
    },

    "no-container-build-in-pipelines": {
      description: "Ni workflow ni script de distribution ne construit d'image Docker",
      severity: "error",
      async check(ctx) {
        const pattern = /docker\s+(build|push|buildx)|uses:\s*docker\//i;
        const scope = "{.github/workflows/*.yml,distribution/**/*}";
        for (const match of await ctx.grepFiles(pattern, scope)) {
          ctx.report.violation({
            message: `Construction ou publication d'image Docker : ${match.content.trim()}`,
            file: match.file,
            line: match.line,
            fix: "Retirer l'étape : la distribution est native (ADR-0134).",
          });
        }
      },
    },
  },
} satisfies RuleSet;
