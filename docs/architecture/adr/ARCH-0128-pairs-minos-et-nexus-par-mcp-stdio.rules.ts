/// <reference path="../rules.d.ts" />

// L'image portable n'embarque ni MINOS ni NEXUS.

export default {
  rules: {
    "portable-image-refuses-peer-engines": {
      description: "Les deux scripts refusent com/minos et com/nexus dans le JAR",
      severity: "error",
      async check(ctx) {
        for (const file of ["distribution/build-portable.ps1", "distribution/build-portable.sh"]) {
          const script = await ctx.readFile(file);
          for (const engine of ["com/minos/", "com/nexus/"].filter((prefix) => !script.includes(prefix))) {
            ctx.report.violation({
              message: `${file} ne refuse plus les classes ${engine} dans le JAR du launcher.`,
              file,
              fix: "Rétablir le refus : les pairs restent des processus externes (ADR-0128).",
            });
          }
        }
      },
    },
  },
} satisfies RuleSet;
