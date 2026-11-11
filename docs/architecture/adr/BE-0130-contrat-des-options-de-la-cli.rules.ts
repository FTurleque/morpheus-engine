/// <reference path="../rules.d.ts" />

// Le guide utilisateur et l'énuméré CliExitCode nomment les mêmes codes de sortie.

export default {
  rules: {
    "user-guide-exit-codes-match-the-enum": {
      description: "La table « Codes de sortie » de docs/user/CLI.md égale CliExitCode",
      severity: "error",
      async check(ctx) {
        const enumFile = "morpheus-cli/src/main/java/com/morpheus/cli/command/CliExitCode.java";
        const guide = "docs/user/CLI.md";
        const declared = [...(await ctx.readFile(enumFile)).matchAll(/^\s*([A-Z_]+)\((\d+)\)[,;]/gm)]
          .map((m) => `${m[2]} ${m[1]}`)
          .sort();
        const text = await ctx.readFile(guide);
        const start = text.search(/^## [^\n]*Codes de sortie/m);
        const end = start < 0 ? -1 : text.indexOf("\n## ", start + 3);
        const section = start < 0 ? "" : text.slice(start, end < 0 ? text.length : end);
        const documented = [...section.matchAll(/^\|\s*(\d+)\s*\|\s*`([A-Z_]+)`\s*\|/gm)]
          .map((m) => `${m[1]} ${m[2]}`)
          .sort();
        if (declared.length === 0 || declared.join(",") !== documented.join(",")) {
          ctx.report.violation({
            message: `${guide} documente [${documented.join(", ")}] ; ${enumFile} déclare [${declared.join(", ")}].`,
            file: guide,
            fix: "Aligner la table « Codes de sortie » sur CliExitCode (ADR-0130).",
          });
        }
      },
    },
  },
} satisfies RuleSet;
