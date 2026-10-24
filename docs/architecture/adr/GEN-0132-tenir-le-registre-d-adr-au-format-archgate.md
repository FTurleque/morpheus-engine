---
id: GEN-0132
title: "Tenir le registre d'ADR au format Archgate, à l'emplacement arc42"
domain: general
rules: true
files: ["docs/architecture/adr/**", ".archgate/**"]
---

# Tenir le registre d'ADR au format Archgate, à l'emplacement arc42

- Statut : **Acceptée**
- Date : 2026-10-24

## Context

Le registre des décisions de MORPHEUS est `docs/architecture/adr/`, à l'emplacement que prévoit arc42 (section 9,
`docs/architecture/arc42/09-decisions.md`). Archgate lit des ADR et exécute des règles TypeScript qui leur sont
rattachées ; `.archgate/config.json` le fait lire ce dossier (`paths.adrs`). Son schéma impose un en-tête YAML en
ligne 1 (`id`, `title`, `domain`, `rules`, `files` facultatif) ; sa convention nomme un ADR `{ID}-{slug}.md` avec un
`id` formé du préfixe du domaine et d'un numéro ; `archgate review-context` résume aux agents les sections
intitulées `Decision` et `Do's and Don'ts`. Archgate ne lit pas les sous-dossiers du registre.

Les numéros des ADR sont uniques dans tout le registre, archive comprise, et cités sous la forme `ADR-NNNN` par le
code, les tests, les règles de `.claude/` et la documentation.

Options considérées pour l'identifiant :

- `ADR-NNNN` pour tous : numéro conservé, mais `id` et nom de fichier hors de la convention Archgate ;
- préfixe du domaine et numérotation par domaine (`ARCH-001`) : convention Archgate stricte, au prix d'une
  renumérotation de tout le registre et de toutes ses citations ;
- préfixe du domaine et numéro unique conservé (`ARCH-0109`) : convention `{ID}-{slug}.md`, numéros inchangés.

## Decision

1. `docs/architecture/adr/` est le registre unique. `.archgate/config.json` y pointe (`paths.adrs`) et déclare le
   domaine `security` (préfixe `SEC`) ; `.archgate/adrs/` ne contient que `.gitkeep`.
2. Un ADR en vigueur s'appelle `{PRÉFIXE}-{NNNN}-{slug}.md` et son `id` vaut `{PRÉFIXE}-{NNNN}` : préfixe du domaine
   (`architecture` → `ARCH`, `backend` → `BE`, `data` → `DATA`, `general` → `GEN`, `security` → `SEC`) et numéro à
   quatre chiffres, unique dans le registre et l'archive, pris à la suite du plus haut numéro attribué, jamais
   renuméroté ni réutilisé. Le texte cite un ADR sous la forme `ADR-NNNN`.
3. L'en-tête YAML est en ligne 1 ; le titre `#` reprend exactement le `title` ; la ligne d'index de
   `docs/architecture/adr/README.md` reprend ce même titre. Le statut s'écrit dans le corps
   (`- Statut : **<verdict>**`), dans le vocabulaire que déclare l'index.
4. Les sections portent les titres d'Archgate : `Context`, `Decision`, `Do's and Don'ts` (`Do`, `Don't`),
   `Consequences`, `Compliance and Enforcement`, `References` ; le texte est en français, au présent. Aucune section
   d'amendement : une décision se lit dans un seul ADR actif.
5. Un ADR remplacé, rejeté ou exécuté une fois pour toutes part dans `docs/architecture/adr/archive/`, que
   Archgate ne lit pas, avec un bandeau qui nomme ce qui le remplace ; il n'est jamais supprimé. Le filtre « quand
   écrire un ADR » et le cycle de vie sont ceux de `.claude/rules/adr.md`.
6. Une règle Archgate vit à côté de son ADR (`{ID}-{slug}.rules.ts`, `rules: true`), porte un identifiant en
   kebab-case anglais et a été vue en échec sur une violation. Elle n'est écrite que pour une contrainte qu'aucun test
   de `morpheus-architecture-tests/` ne tient déjà ; sinon la section `Compliance and Enforcement` nomme le test.
7. Le gabarit est `docs/architecture/adr/gabarit/template.md` ; `archgate adr create` n'est pas utilisé.

## Do's and Don'ts

### Do

- Copier `gabarit/template.md`, prendre le numéro qui suit le plus haut numéro attribué (registre et archive),
  nommer le fichier `{PRÉFIXE}-{NNNN}-{slug}.md` et écrire `id: {PRÉFIXE}-{NNNN}`.
- Garder l'en-tête YAML en ligne 1, un titre `#` égal au `title`, et la même chaîne dans la ligne d'index.
- Passer le filtre de `.claude/rules/adr.md` avant d'écrire.
- Remplacer par un ADR consolidé un ADR modifié en partie, et archiver l'ancien avec son bandeau.
- Nommer dans `Compliance and Enforcement` le test ou la règle qui tient chaque contrainte vérifiable.

### Don't

- Ne pas renuméroter un ADR ni réutiliser un numéro.
- Ne pas écrire d'ADR dans `.archgate/adrs/`, ni utiliser `archgate adr create`.
- Ne pas ajouter de section d'amendement à un ADR en vigueur.
- Ne pas déclarer `rules: true` sans règle compagnon vue en échec.
- Ne pas écrire une règle Archgate qui refait le contrôle d'un test d'architecture.

## Consequences

### Positives

- Un seul fichier par décision, lu par les humains, l'index, arc42, Archgate et les agents.
- Les numéros et toutes les citations `ADR-NNNN` restent valides ; le préfixe dit le domaine.

### Négatives

- Les titres de section sont en anglais dans des documents en français.
- `README.md` vit dans le registre sans en-tête : Archgate le signale comme non lu, ce qui interdit le mode
  `--strict`.

### Risques

- Une évolution du schéma Archgate impose de reprendre les en-têtes : relire `archgate adr list` à chaque montée de
  version de la CLI.
- Une règle bornée par `files` ne s'exécute que si un fichier de son périmètre a changé, ou sur un arbre propre avec
  `archgate check --base HEAD`.

## Compliance and Enforcement

- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/AdrIndexCoherenceTest.java` : une ligne
  d'index par ADR du registre et réciproquement, numéro unique entre registre et archive, statuts concordants et pris
  dans le vocabulaire de l'index, aucun ADR remplacé ou rejeté dans le registre, aucun ADR en vigueur dans l'archive,
  remplaçant existant.
- Règle Archgate `GEN-0132-tenir-le-registre-d-adr-au-format-archgate.rules.ts` : `adr-id-matches-file-name`
  (nom `{PRÉFIXE}-{NNNN}-…`, en-tête en ligne 1, `id` égal, préfixe du domaine), `adr-heading-matches-title`,
  `adr-index-row-names-the-title`, `adr-has-archgate-sections`, `adr-has-no-amendment-section`,
  `archgate-adrs-holds-only-gitkeep`.
- `archgate adr list` liste chaque ADR en vigueur ; un ADR absent de la liste a un en-tête invalide.
- Revue : le filtre de `.claude/rules/adr.md`, la rédaction au présent, le bandeau d'un ADR archivé.

## References

- [Index du registre](README.md), [archive](archive/README.md), [gabarit](gabarit/template.md).
- `.claude/rules/adr.md`, `.claude/rules/documentation.md`.
- [Fiche Archgate](../../developer/ARCHGATE.md), [arc42 §9](../arc42/09-decisions.md).
