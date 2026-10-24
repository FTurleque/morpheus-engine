# Archgate

[Archgate](https://cli.archgate.dev) lit des ADR et exécute des règles TypeScript (`*.rules.ts`) qui leur sont
rattachées. Dans MORPHEUS, il lit **le registre lui-même**, `docs/architecture/adr/`
([ADR-0132](../architecture/adr/GEN-0132-tenir-le-registre-d-adr-au-format-archgate.md)) : il n'existe pas de second
registre sous `.archgate/`. Ses règles tiennent des contraintes qu'aucun test de `morpheus-architecture-tests` ne tient ;
elles complètent les tests, elles ne les dupliquent pas.

## Ce qui fait foi

| Sujet | Source de vérité |
|---|---|
| Décisions d'architecture | [`docs/architecture/adr/`](../architecture/adr/README.md), registre unique, lu par Archgate |
| Contrôle exécuté par la CI | les tests de `morpheus-architecture-tests` et des modules (ArchUnit et assertions textuelles), joués par `./mvnw clean verify` |
| Contrôle Archgate | `docs/architecture/adr/{ID}-{slug}.rules.ts`, joué à la demande, hors CI |

Quand une règle Archgate et un test divergent, le test a raison et la règle se corrige.

## Disposition

```text
.archgate/
  config.json                paths.adrs = docs/architecture/adr, domaine personnalisé security (SEC), branche de base origin/develop
  adrs/.gitkeep              exigé par la CLI pour reconnaître le projet ; rien d'autre n'y vit
  lint/                      réservé par Archgate aux règles de linter (vide)
  rules.d.ts                 généré, ignoré par Git
docs/architecture/
  rules.d.ts                 généré dans le parent du dossier d'ADR, ignoré par Git
  adr/
    {ID}-{slug}.md           ADR en vigueur, {ID} = préfixe du domaine + numéro (ARCH-0109)
    {ID}-{slug}.rules.ts     règle compagnon, quand l'en-tête de l'ADR porte rules: true
    README.md                index ; sans en-tête, Archgate le signale comme non lu
    archive/                 ADR retirés, avec leur bandeau ; non lu par Archgate
    gabarit/template.md      gabarit ; non lu par Archgate
    tsconfig.json            vérification de type des règles par l'éditeur et par tsc ; Archgate ne s'en sert pas
```

Le format d'un ADR (en-tête YAML, sections `Context`, `Decision`, `Do's and Don'ts`, `Consequences`,
`Compliance and Enforcement`, `References`) est décrit dans le [gabarit](../architecture/adr/gabarit/template.md) et
dans l'[index du registre](../architecture/adr/README.md).

## Commandes

```bash
archgate adr list
```

```bash
archgate review-context
```

```bash
archgate check --verbose
```

```bash
archgate check --base HEAD --output json
```

`archgate adr list` liste les ADR reconnus ; un ADR absent de la liste a un en-tête invalide. `archgate review-context`
donne, pour les fichiers modifiés, les sections `Decision` et `Do's and Don'ts` des ADR concernés. `archgate check`
exécute les règles (`--staged` sur les fichiers indexés, `--adr <id>` pour un seul ADR, `--output json` pour un outil).

Un ADR dont l'en-tête porte `files:` n'est exécuté que si un fichier de cette portée a changé. Toutes les règles
s'exécutent seulement sur un arbre propre avec `--base HEAD` ; sur un arbre modifié, lancer la commande dans un
`git worktree` détaché. Code de sortie : 0 conforme, 1 violation, 2 erreur d'une règle ; `total: 0` dans la sortie JSON
signifie « aucune règle exécutée », pas « conforme ». Les avertissements `briefingWarnings` signalent une section
`Decision` plus longue que ce que `review-context` résume ; ils n'échouent pas.

## Règles

| ADR | Règles |
|---|---|
| [ADR-0002](../architecture/adr/ARCH-0002-openspec-reference-provider.md) | `openspec-provider-declares-no-write-capability` |
| [ADR-0004](../architecture/adr/SEC-0004-local-first-no-llm-core.md) | `no-llm-client-dependency` |
| [ADR-0018](../architecture/adr/DATA-0018-sqlite-initial-persistent-store.md) | `inner-layers-do-not-declare-sqlite` |
| [ADR-0020](../architecture/adr/BE-0020-workspace-root-resolution.md) | `workspace-discovery-starts-no-process` |
| [ADR-0021](../architecture/adr/DATA-0021-sqlite-schema-migrations-foundation.md) | `sql-migrations-keep-lf` |
| [ADR-0039](../architecture/adr/BE-0039-deterministic-traceability-derivation.md) | `derivation-is-deterministic` |
| [ADR-0065](../architecture/adr/ARCH-0065-jdk-httpserver-local-api.md) | `no-server-framework-dependency`, `portable-image-adds-jdk-httpserver` |
| [ADR-0084](../architecture/adr/ARCH-0084-provider-neutral-multi-provider-composition.md) | `no-implicit-precedence-selection` |
| [ADR-0088](../architecture/adr/GEN-0088-product-release-installation-and-persistent-data-separation.md) | `path-task-is-opt-in` |
| [ADR-0113](../architecture/adr/SEC-0113-the-remote-server-records-who-was-authorized-to-do-what.md) | `operator-guide-names-the-journal-refusal` |
| [ADR-0123](../architecture/adr/ARCH-0123-vues-compactes-et-json-canonique.md) | `inner-layers-have-no-jackson` |
| [ADR-0125](../architecture/adr/DATA-0125-detection-de-changement-des-sources.md) | `sync-freshness-reads-no-clock`, `sync-state-has-no-blind-upsert` |
| [ADR-0127](../architecture/adr/GEN-0127-un-launcher-natif-unique.md) | `portable-image-checks-launcher-classes` |
| [ADR-0128](../architecture/adr/ARCH-0128-pairs-minos-et-nexus-par-mcp-stdio.md) | `portable-image-refuses-peer-engines` |
| [ADR-0129](../architecture/adr/SEC-0129-plugins-provider-code-de-confiance-hors-processus.md) | `embedded-runtime-keeps-native-commands` |
| [ADR-0130](../architecture/adr/BE-0130-contrat-des-options-de-la-cli.md) | `user-guide-exit-codes-match-the-enum` |
| [ADR-0132](../architecture/adr/GEN-0132-tenir-le-registre-d-adr-au-format-archgate.md) | `adr-id-matches-file-name`, `adr-heading-matches-title`, `adr-index-row-names-the-title`, `adr-has-archgate-sections`, `adr-has-no-amendment-section`, `archgate-adrs-holds-only-gitkeep` |
| [ADR-0133](../architecture/adr/GEN-0133-toolchain-java-21-et-maven-wrapper.md) | `compiler-release-is-21`, `workflows-install-java-21`, `no-preview-features`, `maven-version-is-enforced`, `wrapper-distribution-is-pinned`, `no-extra-maven-repository`, `workflows-run-the-wrapper` |
| [ADR-0134](../architecture/adr/GEN-0134-distribution-native-autonome.md) | `no-container-image-definition`, `no-container-build-in-pipelines` |
| [ADR-0135](../architecture/adr/ARCH-0135-domaine-propre-et-neutralite-multi-provider.md) | `domain-names-no-provider`, `synthetic-provider-is-test-scoped` |
| [ADR-0138](../architecture/adr/ARCH-0138-analyse-de-changement-derivee-et-bornee.md) | `change-analysis-reads-no-clock` |
| [ADR-0139](../architecture/adr/ARCH-0139-frontiere-morpheus-jarvis-en-lecture-seule.md) | `no-jarvis-dependency` |
| [ADR-0142](../architecture/adr/DATA-0142-references-externes.md) | `live-resolution-writes-no-reference` |
| [ADR-0146](../architecture/adr/BE-0146-journal-operationnel-slf4j-et-fichier-borne.md) | `no-other-slf4j-provider`, `no-logger-finder` |

## Ajouter une règle

1. Vérifier qu'aucun test de `morpheus-architecture-tests` ni d'un module ne tient déjà la contrainte ; s'il en existe
   un, la section `Compliance and Enforcement` de l'ADR le nomme et aucune règle n'est écrite.
2. Créer `docs/architecture/adr/{ID}-{slug}.rules.ts` à côté de l'ADR (même nom de base, identifiants de règle en
   kebab-case anglais), passer son en-tête à `rules: true` et borner `files` aux fichiers que la règle lit.
3. La règle lit la source de vérité (POM, workflow, script, constante Java) ; elle ne recopie aucune liste.
4. Vérifier les types : `npx --package typescript tsc -p docs/architecture/adr/tsconfig.json` après un
   `archgate check`, qui génère `docs/architecture/rules.d.ts` ; aucune erreur attendue.
5. Prouver la règle au rouge : introduire la violation, constater l'échec de `archgate check --adr {ID}`, revenir en
   arrière.
6. Nommer la règle dans la section `Compliance and Enforcement` de l'ADR et dans la table ci-dessus.

## Limites

- Archgate n'est pas dans la CI : une contrainte n'est exigée par le build que si un test la tient.
- `ctx.ast` ne lit pas Java : une règle sur du code Java est une recherche textuelle ; une dépendance entre couches se
  tient par ArchUnit.
- `archgate adr create` n'est pas utilisé : il numérote par domaine, alors que les numéros du registre sont uniques et
  jamais réattribués.
- `README.md` vit dans le registre sans en-tête : Archgate le signale comme non lu, ce qui interdit le mode `--strict`.
- Version éprouvée : Archgate 0.60.0.
