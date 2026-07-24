# ADR-0059 — Contrat CLI local stable, scriptable et explicite

- Statut : **Proposée — gate Windows PASS, validation Linux pending**
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

Discipline :

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

## Preuve Windows intermédiaire — 24 juillet 2026

```text
MorpheusCliTest   4/4 PASS
MorpheusMainTest  2/2 PASS
MORPHEUS CLI      6/6 PASS
TOTAL           298/298 PASS
BUILD SUCCESS
```

Le packaging Windows a également exécuté avec succès le launcher packagé en sortie humaine et JSON :

```text
MORPHEUS 0.1.0-SNAPSHOT
{"version":"0.1.0-SNAPSHOT"}
```

La preuve Linux reste requise avant acceptation finale M9 de cette ADR.

## Critères d'acceptation

ADR acceptée lorsque le gate prouve :

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
12. build complet vert.
