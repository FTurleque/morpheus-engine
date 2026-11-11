/// <reference path="../rules.d.ts" />

// L'installeur n'ajoute MORPHEUS au PATH que si l'utilisateur le choisit.

export default {
  rules: {
    "path-task-is-opt-in": {
      description: "La tâche addtopath de l'installeur est décochée par défaut",
      severity: "error",
      async check(ctx) {
        const file = "distribution/windows/MORPHEUS.iss";
        const pattern = /^Name:\s*"addtopath";.*Flags:\s*unchecked/m;
        if (!pattern.test(await ctx.readFile(file))) {
          ctx.report.violation({
            message: "La tâche addtopath n'est plus décochée par défaut",
            file,
            fix: "Rétablir Flags: unchecked (ADR-0088).",
          });
        }
      },
    },
  },
} satisfies RuleSet;
