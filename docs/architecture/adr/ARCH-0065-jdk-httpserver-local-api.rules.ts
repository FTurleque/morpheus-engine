/// <reference path="../rules.d.ts" />

// L'API locale repose sur jdk.httpserver, sans framework serveur.

export default {
  rules: {
    "no-server-framework-dependency": {
      description: "Aucun POM ne déclare de framework serveur",
      severity: "error",
      async check(ctx) {
        const pattern = new RegExp(
          "<groupId>\\s*(org\\.springframework|io\\.quarkus|io\\.micronaut|" +
          "com\\.google\\.inject|jakarta\\.servlet|javax\\.servlet|io\\.netty|" +
          "io\\.undertow|org\\.eclipse\\.jetty)",
          "",
        );
        const scope = "**/pom.xml";
        for (const match of await ctx.grepFiles(pattern, scope)) {
          ctx.report.violation({
            message: `Framework serveur déclaré dans un POM : ${match.content.trim()}`,
            file: match.file,
            line: match.line,
            fix: "Retirer la dépendance : l'API locale utilise jdk.httpserver (ADR-0065).",
          });
        }
      },
    },

    "portable-image-adds-jdk-httpserver": {
      description: "Les deux scripts de l'image portable ajoutent jdk.httpserver",
      severity: "error",
      async check(ctx) {
        for (const file of ["distribution/build-portable.ps1", "distribution/build-portable.sh"]) {
          if (!/--add-modules\s+\S*jdk\.httpserver/.test(await ctx.readFile(file))) {
            ctx.report.violation({
              message: `${file} n'ajoute pas jdk.httpserver au runtime embarqué.`,
              file,
              fix: "Passer jdk.httpserver à --add-modules (ADR-0065).",
            });
          }
        }
      },
    },
  },
} satisfies RuleSet;
