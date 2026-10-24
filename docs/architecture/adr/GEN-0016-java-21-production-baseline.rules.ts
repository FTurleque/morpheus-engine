/// <reference path="../rules.d.ts" />

// Le reactor compile en Java 21 et la CI installe un JDK 21.

export default {
  rules: {
    "compiler-release-is-21": {
      description: "Le POM racine fixe maven.compiler.release à 21",
      severity: "error",
      async check(ctx) {
        const file = "pom.xml";
        const pattern = /<maven\.compiler\.release>21<\/maven\.compiler\.release>/;
        if (!pattern.test(await ctx.readFile(file))) {
          ctx.report.violation({
            message: "Le POM racine ne fixe pas <maven.compiler.release>21</maven.compiler.release>",
            file,
            fix: "Rétablir la propriété à 21 (ADR-0016).",
          });
        }
      },
    },

    "workflows-install-java-21": {
      description: "Chaque java-version des workflows vaut 21",
      severity: "error",
      async check(ctx) {
        const pattern = /java-version:\s*(?!['\"]?21['\"]?\s*$)\S+/;
        const scope = ".github/workflows/*.yml";
        for (const match of await ctx.grepFiles(pattern, scope)) {
          ctx.report.violation({
            message: `Un workflow installe un autre JDK que 21 : ${match.content.trim()}`,
            file: match.file,
            line: match.line,
            fix: "Écrire java-version: '21' (ADR-0016).",
          });
        }
      },
    },

    "no-preview-features": {
      description: "Aucun POM n'active les fonctionnalités preview",
      severity: "error",
      async check(ctx) {
        const pattern = /--enable-preview/;
        const scope = "**/pom.xml";
        for (const match of await ctx.grepFiles(pattern, scope)) {
          ctx.report.violation({
            message: `--enable-preview dans un POM : ${match.content.trim()}`,
            file: match.file,
            line: match.line,
            fix: "Retirer --enable-preview : la baseline est Java 21 sans preview (ADR-0016).",
          });
        }
      },
    },
  },
} satisfies RuleSet;
