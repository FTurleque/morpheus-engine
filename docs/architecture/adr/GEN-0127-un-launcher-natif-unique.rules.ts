/// <reference path="../rules.d.ts" />

// L'image portable vérifie les classes du launcher unique.

export default {
  rules: {
    "portable-image-checks-launcher-classes": {
      description: "Les deux scripts vérifient MorpheusMcpServer et MorpheusHttpServer",
      severity: "error",
      async check(ctx) {
        const required = ["com/morpheus/mcp/MorpheusMcpServer.class", "com/morpheus/api/MorpheusHttpServer.class"];
        for (const file of ["distribution/build-portable.ps1", "distribution/build-portable.sh"]) {
          const script = await ctx.readFile(file);
          for (const entry of required.filter((name) => !script.includes(name))) {
            ctx.report.violation({
              message: `${file} ne vérifie plus ${entry} dans le JAR du launcher.`,
              file,
              fix: "Rétablir la classe dans la liste des entrées exigées (ADR-0127).",
            });
          }
        }
      },
    },
  },
} satisfies RuleSet;
