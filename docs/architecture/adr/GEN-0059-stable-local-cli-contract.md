---
id: GEN-0059
title: "Contrat CLI local stable, scriptable et explicite"
domain: general
rules: false
---

# Contrat CLI local stable, scriptable et explicite

- Statut : **Acceptée**
- Date : 2026-07-24

## Context

MORPHEUS dispose de services applicatifs et de vues compactes stabilisés. La distribution locale exige une surface
utilisable par un humain et par des scripts, sans déplacer la logique métier dans la CLI.

Options écartées :

- **Un framework CLI externe** : une dépendance runtime de plus, et un contrat moins explicite qu'un petit parseur
  maîtrisé.
- **Garder une restitution ordonnée en conservant la map ordonnée jusqu'au rendu** : la propriété reposerait sur une
  structure de données que rien ne protège — un `copyOf` ajouté par réflexe d'immuabilité la rouvrirait sans bruit —
  et, pour les statuts d'intégration, obligerait à changer un record applicatif partagé par trois adaptateurs là où la
  sortie fautive est une ligne de la CLI.

## Decision

### Launcher et adaptateur

Le launcher officiel est `com.morpheus.cli.MorpheusMain`. L'adaptateur CLI s'appuie sur `MorpheusCli`, `CliRuntime`,
`CliLayout` et `CliExitCode`. La CLI câble les ports, stores et services existants ; les règles de requête, de
qualité, de traçabilité et d'analyse restent dans `morpheus-application`.

### Commandes

La liste des commandes n'est pas tenue dans cet ADR : `CliCommandPaths` déclare une fois pour toutes les chemins de
commande servis (`help`, `version`, `paths`, `projects list`, `sync`, `requirements find`…), et
`contracts/public-surfaces.tsv` les rattache aux capacités publiques. Une commande ajoutée à la CLI est déclarée dans
`CliCommandPaths` et documentée dans l'aide embarquée.

### Sorties

Par défaut, la sortie est humaine, stable et compacte. Avec `--json`, ce sont des records déterministes sérialisés
par `CanonicalJsonSerializer` ou les vues compactes existantes.

```text
stdout = résultat normal
stderr = erreurs et diagnostics d'exécution
```

La CLI ne mélange pas d'erreur structurée dans un flux JSON de succès.

**Une restitution a un ordre déclaré, jamais celui d'une structure.** Une sortie, texte ou JSON, ne dépend pas de
l'ordre d'itération de la collection qui porte ses valeurs (l'ordre des collections immuables du JDK part d'un sel
tiré par JVM, et `CanonicalJsonSerializer` trie les clés de map mais pas les collections). L'ordre est déclaré par le
code qui produit la restitution :

- `MorpheusServerCli` rend chaque identité selon `IDENTITY_FIELDS` ;
- `minos-status` et `nexus-status` rendent les détails d'un statut d'intégration par clé triée, l'ordre que le JSON
  canonique emploie déjà ;
- `ProviderCapabilitySet` itère dans l'ordre de déclaration de `ProviderCapability` (`EnumSet` non modifiable) ;
  l'ordre est fixé dans le domaine parce que la collection atteint aussi le JSON, que le JSON canonique ne trie pas.

### Codes de sortie

```text
0  SUCCESS
2  USAGE
3  NOT_FOUND
4  STATE_ERROR
5  IO_ERROR
10 INTERNAL_ERROR
```

Ces valeurs, déclarées par `CliExitCode`, sont un contrat pour les scripts. Ce que chaque code signifie et quand il
s'applique relève d'ADR-0130.

### Disposition locale

Priorité :

```text
option CLI > variable MORPHEUS_* > défaut de l'OS
```

Options globales : `--data-dir`, `--config-dir`, `--db`. Variables : `MORPHEUS_DATA_DIR`, `MORPHEUS_CONFIG_DIR`,
`MORPHEUS_DB`, ainsi que `MORPHEUS_LOGS_DIR` et `MORPHEUS_BACKUPS_DIR`. Les défauts par OS sont ceux d'ADR-0088.
Un `--data-dir` explicite (ou `MORPHEUS_DATA_DIR`) regroupe par défaut configuration, logs,
sauvegardes et base sous ce répertoire, ce qui permet un mode portable. `CliLayout` est la seule résolution.

### Persistance

Toutes les opérations d'une invocation ouvrent la base sur **le même chemin**. Chaque ressource est fermée
explicitement à la fin de l'invocation. La construction des stores d'une opération suit ADR-0109.

### Frontières

La CLI ne contient ni règle de couverture, ni algorithme de trace, ni comparaison d'analyse de changement, ni parsing
métier OpenSpec, ni lifecycle de snapshot, ni politique de qualité : elle orchestre les services existants. Elle
n'utilise pas de framework CLI externe.

## Do's and Don'ts

### Do

- Déclarer tout nouveau chemin de commande dans `CliCommandPaths` et dans l'aide embarquée.
- Écrire le résultat sur `stdout` et les erreurs sur `stderr`.
- Sérialiser toute sortie `--json` par `CanonicalJsonSerializer` ou une vue compacte existante.
- Fixer l'ordre d'une restitution dans le code qui la produit (liste de champs déclarée, tri, `EnumSet`).
- Résoudre les répertoires par `CliLayout`, en respectant la priorité option > variable > défaut.

### Don't

- Ne pas implémenter de règle métier dans la CLI.
- Ne pas rendre une map ou un ensemble immuable du JDK par son ordre d'itération.
- Ne pas mélanger une erreur structurée à un flux JSON de succès.
- Ne pas changer la valeur numérique d'un code de `CliExitCode`.
- Ne pas ajouter de framework CLI externe.

## Consequences

### Positives

- Surface scriptable stable : codes de sortie, flux séparés, JSON canonique.
- Une même invocation produit la même sortie texte d'une JVM à l'autre.
- Mode portable par un seul répertoire de données.

### Négatives

- Le parseur maison est à maintenir.
- La liste des champs d'une identité existe deux fois, dans la vue et dans `IDENTITY_FIELDS` : une clé ajoutée à l'une
  sans l'autre disparaîtrait du texte en restant dans le JSON.

### Risques

- Un test qui compare deux exécutions dans une même JVM ne prouve rien sur l'ordre (le sel y est constant) : les tests
  assertent l'ordre attendu, jamais la répétition d'une sortie.

## Compliance and Enforcement

- `morpheus-cli/src/test/java/com/morpheus/cli/CliCommandPathsTest.java` : chaque chemin déclaré est routé et
  documenté dans l'aide embarquée ; un chemin non déclaré est refusé comme commande inconnue.
- `morpheus-architecture-tests/src/test/java/com/morpheus/cli/PublicSurfaceManifestCoversEveryServedCliCommandTest.java`
  : la colonne `cli` de `contracts/public-surfaces.tsv` et `CliCommandPaths` se couvrent dans les deux sens.
- `morpheus-cli/src/test/java/com/morpheus/cli/command/MorpheusServerCliTest.java`
  (`anIdentityIsRenderedInTheDeclaredOrderWhateverTheIterationOrderOfItsMap`,
  `theTextAndJsonIdentityListingsCarryTheSameFields`) : ordre déclaré d'une identité, mêmes champs en texte et en JSON.
- `morpheus-domain/src/test/java/com/morpheus/domain/provider/ProviderCapabilitySetTest.java` : ordre de déclaration
  de toutes les capacités.
- `morpheus-cli/src/test/java/com/morpheus/cli/MorpheusMainTest.java` et
  `morpheus-cli/src/test/java/com/morpheus/cli/MorpheusCliTest.java` : launcher, options globales et disposition.
- Revue : stabilité des valeurs de `CliExitCode`.

## References

- [ADR-0130](BE-0130-contrat-des-options-de-la-cli.md) — contrat des options et des codes de sortie de la CLI.
- [ADR-0088](GEN-0088-product-release-installation-and-persistent-data-separation.md) — emplacements par défaut.
- [ADR-0109](ARCH-0109-transport-adapters-are-named-composition-roots.md) — racines de composition et runtime.
- [ADR-0123](ARCH-0123-vues-compactes-et-json-canonique.md) — vues compactes et JSON canonique.
- [ADR-0137](ARCH-0137-lectures-et-recherche-sur-le-snapshot-publie.md) — requête lexicale déterministe et lectures
  métier sur le snapshot publié.
- [ADR-0124](ARCH-0124-qualite-findings-et-faits-tri-etat.md) — qualité.
- [VALIDATION_M9](../../project/validation/VALIDATION_M9.md).
