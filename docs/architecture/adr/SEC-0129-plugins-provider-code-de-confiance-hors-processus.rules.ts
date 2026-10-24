/// <reference path="../rules.d.ts" />

// Le runtime embarqué garde ses commandes natives, dont bin/java qui lance le probe.

export default {
  rules: {
    "embedded-runtime-keeps-native-commands": {
      description: "Aucun script de l'image portable ne passe --strip-native-commands",
      severity: "error",
      async check(ctx) {
        const pattern = /^(?!\s*#).*--strip-native-commands/m;
        const scope = "distribution/build-portable.{ps1,sh}";
        for (const match of await ctx.grepFiles(pattern, scope)) {
          ctx.report.violation({
            message: `--strip-native-commands retire bin/java du runtime embarqué : ${match.content.trim()}`,
            file: match.file,
            line: match.line,
            fix: "Retirer l'option : le probe lance bin/java (ADR-0129).",
          });
        }
      },
    },
  },
} satisfies RuleSet;
