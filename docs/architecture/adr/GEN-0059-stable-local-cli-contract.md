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

L'ordre d'une restitution est déclaré par le code qui la produit parce que l'ordre des collections immuables du JDK
part d'un sel tiré par JVM, et que `CanonicalJsonSerializer` trie les clés de map mais pas les collections. Les détails
d'un statut d'intégration sont triés par clé, l'ordre que le JSON canonique emploie déjà ; l'ordre de
`ProviderCapabilitySet` est fixé dans le domaine parce que la collection atteint aussi le JSON, qui ne la trie pas.

Les chemins servis vont de `help`, `version`, `paths` à `projects list`, `sync`, `requirements find`… Le regroupement
sous `--data-dir` (ou `MORPHEUS_DATA_DIR`) permet un mode portable.

## Decision

1. **Launcher** : `com.morpheus.cli.MorpheusMain`, sur `MorpheusCli`, `CliRuntime`, `CliLayout` et `CliExitCode`.
   La CLI câble les ports, stores et services existants, sans framework CLI externe.
2. **Frontières** : la CLI ne porte ni règle de couverture, ni algorithme de trace, ni comparaison d'analyse de changement, ni parsing
   métier OpenSpec, ni lifecycle de snapshot, ni politique de qualité (`morpheus-application`).
3. **Commandes** : `CliCommandPaths` déclare les chemins servis, `contracts/public-surfaces.tsv` les rattache aux
   capacités ; une commande ajoutée y est déclarée et documentée dans l'aide.
4. **Sorties** : humaine, stable et compacte par défaut ; `--json` sérialise par `CanonicalJsonSerializer` ou une vue
   compacte. Résultat sur `stdout`, erreurs et diagnostics sur `stderr` ; aucune erreur structurée dans un JSON de
   succès.
5. **Ordre déclaré** : une sortie, texte ou JSON, ne suit jamais l'ordre d'itération d'une collection ; son code le
   déclare. `MorpheusServerCli` rend une identité selon `IDENTITY_FIELDS` ; `minos-status` et `nexus-status` trient
   les détails par clé ; `ProviderCapabilitySet` suit l'ordre de `ProviderCapability` (`EnumSet` non modifiable).
6. **Codes de sortie** : `0 SUCCESS`, `2 USAGE`, `3 NOT_FOUND`, `4 STATE_ERROR`, `5 IO_ERROR`, `10 INTERNAL_ERROR`,
   de `CliExitCode`, sont un contrat ; leur sens relève d'ADR-0130.
7. **Disposition** : option CLI > variable `MORPHEUS_*` > défaut de l'OS (ADR-0088). Options `--data-dir`,
   `--config-dir`, `--db` ; variables `MORPHEUS_DATA_DIR`, `MORPHEUS_CONFIG_DIR`, `MORPHEUS_DB`, `MORPHEUS_LOGS_DIR`,
   `MORPHEUS_BACKUPS_DIR`. `--data-dir` (ou `MORPHEUS_DATA_DIR`) regroupe par défaut configuration, logs, sauvegardes
   et base. `CliLayout` seul résout.
8. **Persistance** : une invocation ouvre la base sur un seul chemin et ferme chaque ressource (stores : ADR-0162).

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
- [ADR-0162](ARCH-0162-racines-de-composition-nommees-paquets-sans-cycle-hors-une-paire-bornee-et-decisions-dans-le-domaine.md) — racines de composition et runtime.
- [ADR-0123](ARCH-0123-vues-compactes-et-json-canonique.md) — vues compactes et JSON canonique.
- [ADR-0137](ARCH-0137-lectures-et-recherche-sur-le-snapshot-publie.md) — requête lexicale déterministe et lectures
  métier sur le snapshot publié.
- [ADR-0124](ARCH-0124-qualite-findings-et-faits-tri-etat.md) — qualité.
- [VALIDATION_M9](../../project/validation/VALIDATION_M9.md).
