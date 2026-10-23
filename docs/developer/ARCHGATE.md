# Archgate

[Archgate](https://cli.archgate.dev) lit des ADR et exécute des règles TypeScript (`*.rules.ts`) qui leur sont
rattachées. Dans MORPHEUS, il est configuré pour lire **le registre lui-même**, `docs/architecture/adr/` : il n'existe
pas de second registre sous `.archgate/`.

## Ce qui fait foi

| Sujet | Source de vérité |
|---|---|
| Décisions d'architecture | [`docs/architecture/adr/`](../architecture/adr/README.md), registre unique |
| Contrôle exécuté par la CI | les tests de `morpheus-architecture-tests` (ArchUnit et assertions textuelles), joués par `./mvnw clean verify` |
| Contrôle Archgate | `docs/architecture/adr/<ADR>.rules.ts`, joué à la demande, hors CI |

Quand une règle Archgate et un test d'architecture divergent, le test d'architecture a raison et la règle Archgate
se corrige.

## État du registre

Archgate ne lit un ADR que si son fichier s'ouvre, en ligne 1, sur un en-tête YAML (`id`, `title`, `domain`, `rules`,
`files` facultatif). Les ADR de MORPHEUS n'en portent pas : `archgate adr list` répond `No ADRs found` et
`archgate check` les énumère dans `unparsedAdrs`. Le registre garde son format (titre `# ADR-NNNN — …`, ligne
`Statut`, sections en français) et ses noms de fichier `NNNN-slug.md`, que `AdrIndexCoherenceTest` et d'autres tests
d'architecture épinglent. Passer le registre au format Archgate est une décision de structure qui se prend par un ADR.

## Disposition

```text
.archgate/
  config.json         paths.adrs = docs/architecture/adr, domaine personnalisé security (SEC), branche de base origin/develop
  adrs/.gitkeep       exigé par la CLI pour reconnaître le projet ; rien d'autre n'y vit
  lint/               réservé par Archgate aux règles de linter (vide)
  rules.d.ts          généré, ignoré par Git
docs/architecture/
  rules.d.ts          généré dans le parent du dossier d'ADR, ignoré par Git
  adr/
    NNNN-slug.md      ADR
    NNNN-slug.rules.ts  règle compagnon, quand l'en-tête de l'ADR porte rules: true
    tsconfig.json     vérification de type des règles par l'éditeur et par tsc (ES2022, strict) ; Archgate ne s'en sert pas
```

Archgate ne lit pas les sous-dossiers du registre.

## Commandes

```bash
archgate --version
```

```bash
archgate adr list
```

```bash
archgate review-context
```

```bash
archgate check --verbose
```

`archgate adr list` liste les ADR reconnus ; `archgate review-context` donne, pour les fichiers modifiés, les sections
`Decision` et `Do's and Don'ts` des ADR concernés ; `archgate check` exécute les règles (`--staged` sur les fichiers
indexés, `--adr <id>` pour un seul ADR, `--output json` pour un outil).

Code de sortie de `check` : 0 conforme, 1 violation, 2 erreur interne. Sans `--strict`, seules les violations de
sévérité `error` échouent. `total: 0` dans la sortie JSON signifie « aucune règle exécutée », pas « conforme ».
Un ADR dont l'en-tête porte `files:` n'est exécuté que si un fichier de cette portée a changé.

## Ajouter une règle

1. Vérifier que l'invariant est déjà tenu par un test de `morpheus-architecture-tests`.
2. Donner à l'ADR concerné son en-tête YAML, puis créer `docs/architecture/adr/<même nom de base>.rules.ts`
   (identifiants de règle en kebab-case anglais ; `files` dans l'en-tête si la règle ne regarde qu'une partie du dépôt)
   et passer l'en-tête à `rules: true`.
3. La règle lit la source de vérité du test (POM, manifeste, fichier de configuration) ; elle ne recopie aucune liste.
4. Vérifier les types après un `archgate check`, qui génère `docs/architecture/rules.d.ts` :
   `npx --package typescript tsc -p docs/architecture/adr/tsconfig.json` ; aucune erreur attendue.
5. Prouver la règle au rouge : introduire la violation, constater l'échec de `archgate check`, revenir en arrière.
6. Rejouer `./mvnw test -pl morpheus-architecture-tests`.

## Limites

- Archgate n'est pas dans la CI : un invariant n'est exigé que par son test d'architecture.
- `archgate adr create` n'est pas utilisé : il numérote par domaine, alors que les numéros du registre sont uniques et
  jamais réattribués ; un ADR s'écrit à partir de [`template.md`](../architecture/adr/template.md).
- `README.md` et `template.md` vivent dans le registre sans en-tête : Archgate les signale comme non lus, ce qui
  interdit le mode `--strict`.
- Version éprouvée : Archgate 0.60.0.
