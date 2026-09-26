# ADR-0059 — Contrat CLI local stable, scriptable et explicite

- Statut : **Acceptée — M9**
- Date : 24 juillet 2026
- Dépend de : ADR-0043, ADR-0045, ADR-0046, ADR-0047, ADR-0052, ADR-0058
- Portée : M9 — surface utilisateur CLI

## Contexte

MORPHEUS dispose de services applicatifs et de vues compactes stabilisés, mais `morpheus-cli` ne contenait avant M9 qu'un marqueur d'adapter. La distribution locale exige une surface utilisable par un humain et par des scripts sans déplacer la logique métier dans la CLI.

## Décision

Introduire le launcher officiel :

```text
com.morpheus.cli.MorpheusMain
```

et l'adapter :

```text
MorpheusCli
CliRuntime
CliLayout
CliExitCode
```

La CLI câble les ports/stores/services existants ; les règles de requête, qualité, traçabilité et analyse restent dans `morpheus-application`.

## Commandes M9

```text
help
version
paths
projects list
projects add --workspace PATH
sync --project ID [--revision REV] [--force]
sync-status --project ID [--max-age-minutes N]
requirements find --project ID [--query TEXT] [--offset N] [--limit N]
changes list --project ID [--offset N] [--limit N]
changes get --project ID --change ID
constraints list --project ID --change ID [--offset N] [--limit N]
decisions list --project ID --change ID [--offset N] [--limit N]
tasks list --project ID --change ID [--offset N] [--limit N]
trace-requirement --project ID --requirement ID [--depth N]
change-context --project ID --change ID [--depth N]
analyze-change --project ID --change ID [--depth N]
quality --project ID
```

## Sorties

Par défaut : sortie humaine stable et compacte.

Avec `--json` : DTOs/records déterministes sérialisés avec `CanonicalJsonSerializer` ou les vues compactes M5/M6/M8 existantes.

```text
stdout = résultat normal
stderr = erreurs/diagnostics d'exécution CLI
```

La CLI ne mélange pas les erreurs structurées à un flux JSON de succès.

## Exit codes

```text
0  SUCCESS
2  USAGE
3  NOT_FOUND
4  STATE_ERROR
5  IO_ERROR
10 INTERNAL_ERROR
```

Ces valeurs constituent un contrat M9 pour les scripts.

## Layout local

Priorité :

```text
option CLI > variable MORPHEUS_* > default OS
```

Options globales :

```text
--data-dir
--config-dir
--db
```

Variables :

```text
MORPHEUS_DATA_DIR
MORPHEUS_CONFIG_DIR
MORPHEUS_DB
```

Windows : `%LOCALAPPDATA%/Morpheus` pour data, `%APPDATA%/Morpheus` pour config.

Linux : XDG (`$XDG_DATA_HOME`, `$XDG_CONFIG_HOME`) avec fallback `~/.local/share/morpheus` et `~/.config/morpheus`.

Un `--data-dir` explicite regroupe par défaut config et DB sous ce répertoire afin de permettre un mode portable.

## Persistance

Toutes les commandes d'une invocation ouvrent les adapters SQLite nécessaires sur **le même chemin DB**. Chaque ressource est fermée explicitement à la fin de l'invocation.

## Frontières

La CLI ne contient pas : règles de couverture, algorithme de trace, comparaison M8, parsing OpenSpec métier, lifecycle de snapshot ou politique de qualité. Elle orchestre les services existants.

Pas de framework CLI externe en M9 : le parseur volontairement petit évite une nouvelle dépendance runtime et garde un contrat explicite.

## Preuves finales — 24 juillet 2026

Windows :

```text
MorpheusCliTest   4/4 PASS
MorpheusMainTest  2/2 PASS
MORPHEUS CLI      6/6 PASS
TOTAL           298/298 PASS
BUILD SUCCESS
```

Linux/WSL :

```text
MorpheusCliTest   4/4 PASS
MorpheusMainTest  2/2 PASS
MORPHEUS CLI      6/6 PASS
TOTAL           298/298 PASS
BUILD SUCCESS
```

Les launchers packagés ont également réussi en sortie humaine et JSON sur Windows et Linux :

```text
MORPHEUS 0.1.0-SNAPSHOT
{"version":"0.1.0-SNAPSHOT"}
```

Validation complète : [`../validation/VALIDATION_M9.md`](../validation/VALIDATION_M9.md).

## Critères d'acceptation

Les critères suivants sont satisfaits :

1. help/version ;
2. codes de sortie ;
3. stdout/stderr ;
4. layout Windows/Linux ;
5. registre projet ;
6. sync OpenSpec + reopen SQLite ;
7. requirements/changes/contraintes/décisions/tâches ;
8. trace + change-context ;
9. analyse M8 ;
10. qualité M6 ;
11. `--json` ;
12. builds complets Windows et Linux verts.

**Décision : ADR-0059 acceptée.**

## Amendement du 26 septembre 2026 (CLI-5) — une restitution texte a un ordre de champs déclaré

« Sortie humaine stable » ne tenait pas pour `server identity list`. Chaque identité était construite dans une
`LinkedHashMap` (`principal`, `role`, `expiresAt`, `expired`, `nonExpiring`) puis figée par `Map.copyOf`, et la sortie
texte la rendait par `toString()`. L'ordre d'itération des maps immuables du JDK part d'un sel tiré une fois par JVM :
deux exécutions sur un fichier d'identités inchangé imprimaient les mêmes champs dans un ordre différent. Constaté en
rejouant l'ancien rendu dans quatre JVM successives : quatre ordres distincts. Le mode `--json` n'était pas touché,
`CanonicalJsonSerializer` triant les clés.

La même forme existait chez deux voisins : `minos-status` et `nexus-status` impriment en texte les `details` d'un
`ExternalIntegrationStatus`, que le record fige lui aussi par `Map.copyOf`. Hors configuration, `minos-status` en porte
déjà deux (`javaCommand`, `timeoutSeconds`).

**Décision.** Une restitution texte ne dépend pas de l'ordre d'itération de la structure qui porte ses valeurs. L'ordre
est **déclaré dans le code de rendu** : `MorpheusServerCli.identityLine` rend chaque identité selon `IDENTITY_FIELDS`,
et les deux commandes de statut rendent leurs détails par clé triée. Les autres vues de `MorpheusServerCli`
(`credentialView`, `mutationView`, `backupView`, la vue de `migrate-legacy`, l'enveloppe de `identity list`) sont des
`LinkedHashMap` de premier niveau jamais figées, et leurs listes (`migrated`, `retainedNonExpiring`) suivent l'ordre du
fichier d'identités : leur ordre tenait déjà.

### Alternative écartée

- **Garder la map ordonnée jusqu'au rendu** (retirer le `Map.copyOf`). Elle ferme le constat, mais la propriété repose
  sur une structure de données que rien ne protège : un `copyOf` rajouté par réflexe d'immuabilité le rouvre sans
  bruit. Pour les statuts d'intégration elle obligerait en plus à changer le record applicatif partagé par trois
  adaptateurs, là où la sortie fautive est une ligne du CLI.

**Preuve.** `MorpheusServerCliTest#anIdentityIsRenderedInTheDeclaredOrderWhateverTheIterationOrderOfItsMap` donne au
rendu une `TreeMap` (ordre alphabétique, différent de l'ordre déclaré) et attend l'ordre déclaré ; le test de bout en
bout asserte l'ordre des champs, pas la répétition d'une sortie — dans une même JVM le sel est constant, et comparer
deux exécutions ne prouverait rien. Les tests de statut passent six détails : le rendu d'origine ne les rend dans
l'ordre trié qu'une fois sur 720.
