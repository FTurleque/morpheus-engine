/// <reference path="../rules.d.ts" />

// Le reactor compile en Java 21 avec un JDK 21, et Maven passe par le wrapper épinglé, sans dépôt supplémentaire.

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
            fix: "Rétablir la propriété à 21 (ADR-0133).",
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
            fix: "Écrire java-version: '21' (ADR-0133).",
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
            fix: "Retirer --enable-preview : la baseline est Java 21 sans preview (ADR-0133).",
          });
        }
      },
    },

    "maven-version-is-enforced": {
      description: "L'Enforcer exige Maven [3.9.16,4.0.0)",
      severity: "error",
      async check(ctx) {
        const file = "pom.xml";
        const pattern = /<requireMavenVersion>\s*<version>\[3\.9\.16,4\.0\.0\)<\/version>/;
        if (!pattern.test(await ctx.readFile(file))) {
          ctx.report.violation({
            message: "Le POM racine n'exige plus Maven [3.9.16,4.0.0)",
            file,
            fix: "Rétablir requireMavenVersion (ADR-0133).",
          });
        }
      },
    },

    "wrapper-distribution-is-pinned": {
      description: "Le Maven Wrapper épingle sa distribution par SHA-256",
      severity: "error",
      async check(ctx) {
        const file = ".mvn/wrapper/maven-wrapper.properties";
        const pattern = /^distributionSha256Sum=[0-9a-f]{64}\s*$/m;
        if (!pattern.test(await ctx.readFile(file))) {
          ctx.report.violation({
            message: "maven-wrapper.properties ne porte pas de distributionSha256Sum",
            file,
            fix: "Épingler la distribution par son SHA-256 (ADR-0133).",
          });
        }
      },
    },

    "no-extra-maven-repository": {
      description: "Aucun POM ne déclare de dépôt Maven",
      severity: "error",
      async check(ctx) {
        const pattern = /<(plugin)?[rR]epositories>/;
        const scope = "**/pom.xml";
        for (const match of await ctx.grepFiles(pattern, scope)) {
          ctx.report.violation({
            message: `Dépôt Maven déclaré dans un POM : ${match.content.trim()}`,
            file: match.file,
            line: match.line,
            fix: "Retirer le dépôt : le build ne dépend que de Maven Central (ADR-0133).",
          });
        }
      },
    },

    "workflows-run-the-wrapper": {
      description: "Les workflows lancent Maven par le wrapper, jamais mvn nu",
      severity: "error",
      async check(ctx) {
        const pattern = /(^|[\s;&|])mvn\s/m;
        const scope = ".github/workflows/*.yml";
        for (const match of await ctx.grepFiles(pattern, scope)) {
          ctx.report.violation({
            message: `Un workflow appelle mvn nu : ${match.content.trim()}`,
            file: match.file,
            line: match.line,
            fix: "Appeler ./mvnw ou .\\mvnw.cmd (ADR-0133).",
          });
        }
      },
    },
  },
} satisfies RuleSet;
