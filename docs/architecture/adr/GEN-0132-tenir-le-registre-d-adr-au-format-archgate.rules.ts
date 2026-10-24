/// <reference path="../rules.d.ts" />

// Règles d'ADR-0132. AdrIndexCoherenceTest tient déjà l'index, l'unicité des numéros et les statuts ; ces règles
// tiennent ce qu'aucun test ne lit : nom et en-tête au format Archgate, titre, sections, absence d'amendement et
// .archgate/adrs/ réduit à .gitkeep.

const REGISTRY = "docs/architecture/adr";
const INDEX = `${REGISTRY}/README.md`;
const CONFIG = ".archgate/config.json";
const ADR_FILE = /^([A-Z]+)-(\d{4})-[^/]+\.md$/;
const BRIDGE_DIRECTORY = ".archgate/adrs";
const REQUIRED_SECTIONS = [
  "Context",
  "Decision",
  "Do's and Don'ts",
  "Consequences",
  "Compliance and Enforcement",
  "References",
];
const AMENDMENT_HEADING = /^#{2,6}\s+.*\bamendement\b/i;
// Préfixes des domaines intégrés à Archgate ; les domaines du projet sont lus dans CONFIG.
const BUILT_IN_PREFIXES: Record<string, string> = {
  architecture: "ARCH",
  backend: "BE",
  frontend: "FE",
  data: "DATA",
  general: "GEN",
};

function fileName(path: string): string {
  return path.split("/").pop() as string;
}

async function registryAdrs(ctx: RuleContext): Promise<string[]> {
  return (await ctx.glob(`${REGISTRY}/*.md`)).filter((file) => fileName(file) !== "README.md").sort();
}

function frontmatter(text: string): Record<string, string> | null {
  const lines = text.split(/\r?\n/);
  if (lines[0] !== "---") return null;
  const end = lines.indexOf("---", 1);
  if (end < 0) return null;
  const fields: Record<string, string> = {};
  for (const line of lines.slice(1, end)) {
    const match = /^([a-zA-Z]+):\s*(.*?)\s*$/.exec(line);
    if (match) fields[match[1]] = match[2].replace(/^"(.*)"$/, "$1").replace(/\\"/g, '"');
  }
  return fields;
}

function heading(text: string): string | undefined {
  return text.split(/\r?\n/).find((line) => line.startsWith("# "));
}

async function domainPrefixes(ctx: RuleContext): Promise<Record<string, string>> {
  const config = (await ctx.readJSON(CONFIG)) as { domains?: Record<string, string> };
  return { ...BUILT_IN_PREFIXES, ...(config.domains ?? {}) };
}

export default {
  rules: {
    "adr-id-matches-file-name": {
      description:
        "Un ADR du registre s'appelle {PRÉFIXE}-{NNNN}-{slug}.md, ouvre un en-tête YAML en ligne 1, " +
        "son id vaut {PRÉFIXE}-{NNNN} et le préfixe est celui de son domaine",
      severity: "error",
      async check(ctx) {
        const adrs = await registryAdrs(ctx);
        if (adrs.length === 0) {
          ctx.report.violation({ message: `Aucun ADR sous ${REGISTRY}/ : la règle ne peut rien vérifier.`, file: REGISTRY });
          return;
        }
        const prefixes = await domainPrefixes(ctx);
        for (const file of adrs) {
          const name = ADR_FILE.exec(fileName(file));
          if (name === null) {
            ctx.report.violation({
              message: `${fileName(file)} ne suit pas la convention {PRÉFIXE}-{NNNN}-{slug}.md.`,
              file,
              fix: "Renommer le fichier avec le préfixe de son domaine et son numéro ; un ADR retiré va dans archive/.",
            });
            continue;
          }
          const [, prefix, number] = name;
          const fields = frontmatter(await ctx.readFile(file));
          if (fields === null) {
            ctx.report.violation({
              message: "L'ADR n'ouvre pas d'en-tête YAML en ligne 1 : Archgate ne le lit pas.",
              file,
              line: 1,
              fix:
                `Commencer le fichier par ---, id: ${prefix}-${number}, title, domain, rules, --- ` +
                `(gabarit ${REGISTRY}/gabarit/template.md).`,
            });
            continue;
          }
          if (fields.id !== `${prefix}-${number}`) {
            ctx.report.violation({
              message: `id ${fields.id ?? "(absent)"} : l'id reprend le début du nom de fichier, ${prefix}-${number}.`,
              file,
              fix: `Écrire id: ${prefix}-${number} ; ne jamais renuméroter un ADR.`,
            });
          }
          const expected = prefixes[fields.domain ?? ""];
          if (expected !== prefix) {
            const domain = fields.domain ?? "(absent)";
            ctx.report.violation({
              message:
                `Le préfixe ${prefix} ne correspond pas au domaine ${domain} ` +
                `(préfixe attendu : ${expected ?? "domaine inconnu"}).`,
              file,
              fix:
                "Renommer le fichier et l'id avec le préfixe du domaine, " +
                `ou corriger le domaine (${CONFIG} pour un domaine du projet).`,
            });
          }
        }
      },
    },

    "adr-heading-matches-title": {
      description: "Le titre # de chaque ADR reprend exactement son title",
      severity: "error",
      async check(ctx) {
        for (const file of await registryAdrs(ctx)) {
          const text = await ctx.readFile(file);
          const fields = frontmatter(text);
          if (fields === null || fields.title === undefined) continue;
          const title = heading(text);
          if (title !== `# ${fields.title}`) {
            ctx.report.violation({
              message: `Titre « ${title ?? "(absent)"} » différent du title « ${fields.title} ».`,
              file,
              fix: `Écrire le titre # ${fields.title}.`,
            });
          }
        }
      },
    },

    "adr-index-row-names-the-title": {
      description: "La ligne d'index d'un ADR dans docs/architecture/adr/README.md reprend son title",
      severity: "error",
      async check(ctx) {
        const rows = new Map<string, string>();
        for (const line of (await ctx.readFile(INDEX)).split(/\r?\n/)) {
          const row = /^\|\s*\[ADR-\d{4}\]\(([^)]+)\)\s*\|\s*(.*?)\s*\|/.exec(line);
          if (row) rows.set(row[1], row[2]);
        }
        for (const file of await registryAdrs(ctx)) {
          const fields = frontmatter(await ctx.readFile(file));
          if (fields === null || fields.title === undefined) continue;
          const indexed = rows.get(fileName(file));
          if (indexed !== undefined && indexed !== fields.title) {
            ctx.report.violation({
              message: `L'index nomme « ${indexed} » ; le title de l'ADR est « ${fields.title} ».`,
              file: INDEX,
              fix: `Reprendre le title exact dans la ligne de ${fileName(file)}.`,
            });
          }
        }
      },
    },

    "adr-has-archgate-sections": {
      description: "Chaque ADR du registre a les sections d'Archgate, dont Decision et Do's and Don'ts lues par review-context",
      severity: "error",
      async check(ctx) {
        for (const file of await registryAdrs(ctx)) {
          const headings = new Set(
            (await ctx.readFile(file))
              .split(/\r?\n/)
              .map((line) => /^##\s+(.+?)\s*$/.exec(line)?.[1])
              .filter((title): title is string => title !== undefined),
          );
          for (const section of REQUIRED_SECTIONS) {
            if (!headings.has(section)) {
              ctx.report.violation({
                message: `Section « ## ${section} » absente.`,
                file,
                fix: `Titrer la section correspondante « ## ${section} » (gabarit ${REGISTRY}/gabarit/template.md).`,
              });
            }
          }
        }
      },
    },

    "adr-has-no-amendment-section": {
      description: "Un ADR en vigueur ne porte pas de section d'amendement : une décision se lit dans un seul ADR actif",
      severity: "error",
      async check(ctx) {
        for (const file of await registryAdrs(ctx)) {
          const lines = (await ctx.readFile(file)).split(/\r?\n/);
          lines.forEach((line, index) => {
            if (AMENDMENT_HEADING.test(line)) {
              ctx.report.violation({
                message: `Section d'amendement « ${line.trim()} » dans un ADR en vigueur.`,
                file,
                line: index + 1,
                fix: "Réécrire la décision en vigueur au présent, ou la consolider dans un nouvel ADR et archiver celui-ci.",
              });
            }
          });
        }
      },
    },

    "archgate-adrs-holds-only-gitkeep": {
      description: ".archgate/adrs/ ne contient que .gitkeep : aucun second registre",
      severity: "error",
      async check(ctx) {
        for (const file of (await ctx.glob(`${BRIDGE_DIRECTORY}/**/*`)).sort()) {
          if (file === `${BRIDGE_DIRECTORY}/.gitkeep`) continue;
          ctx.report.violation({
            message: `${file} : ${BRIDGE_DIRECTORY}/ ne contient que .gitkeep ; le registre est ${REGISTRY}/.`,
            file,
            fix: `Supprimer ce fichier ; écrire ou modifier l'ADR dans ${REGISTRY}/.`,
          });
        }
      },
    },
  },
} satisfies RuleSet;
