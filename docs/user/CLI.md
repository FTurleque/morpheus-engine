# Référence CLI MORPHEUS

Cette page décrit la CLI actuelle après intégration de M14.

## Invocation

Distribution portable :

```text
Windows  .\morpheus\morpheus.exe <commande>
Linux    ./morpheus/bin/morpheus <commande>
```

JAR autonome pour usage développeur :

```bash
java -jar morpheus-cli-0.1.0-SNAPSHOT-all.jar <commande>
```

## Options globales

```text
--json
--data-dir PATH
--config-dir PATH
--db PATH
```

`--json` rend stdout scriptable. Les erreurs restent sur stderr et le code de sortie reste contractuel.

Commandes utilitaires :

```bash
morpheus help
morpheus version
morpheus --version
morpheus paths
```

## Projets et synchronisation

```bash
morpheus projects list
morpheus projects add --workspace /path/to/project
morpheus sync --project <projectId>
morpheus sync --project <projectId> --revision <revision>
morpheus sync-status --project <projectId>
```

Le launcher officiel force une reconstruction complète conservatrice quand une synchronisation publiée doit être produite. Le snapshot ACTIVE précédent reste disponible tant que le candidat n’est pas validé.

## Requêtes métier

```bash
morpheus requirements find --project <projectId> --query "texte"
morpheus changes list --project <projectId>
morpheus changes get --project <projectId> --change <changeId>
morpheus constraints list --project <projectId> --change <changeId>
morpheus decisions list --project <projectId> --change <changeId>
morpheus tasks list --project <projectId> --change <changeId>
```

Pagination des listes/recherches :

```text
--offset N
--limit N   # 1..100
```

## Traçabilité et contexte

```bash
morpheus trace-requirement \
  --project <projectId> \
  --requirement <requirementId> \
  --depth 2

morpheus change-context \
  --project <projectId> \
  --change <changeId> \
  --depth 2
```

## Analyse de changement

```bash
morpheus analyze-change \
  --project <projectId> \
  --change <changeId> \
  --depth 2
```

L’analyse confronte le contenu proposé au snapshot CURRENT sans promotion implicite.

## Qualité

```bash
morpheus quality --project <projectId>
```

Les diagnostics sont dérivés et ne mutent pas le snapshot publié.

## MINOS — références de code

État de l’intégration :

```bash
morpheus --json minos-status
```

Références externes :

```bash
morpheus --json external-references list \
  --project <projectId> \
  --owner <domainIdentity>

morpheus --json external-references resolve \
  --project <projectId> \
  --reference <externalReferenceId>
```

MINOS est optionnel. Sans configuration, l’intégration est `DISABLED` et MORPHEUS continue de fonctionner.

## NEXUS — contexte technique augmenté

État :

```bash
morpheus --json nexus-status
```

Requirement :

```bash
morpheus --json augmented-context requirement \
  --project <projectId> \
  --requirement <requirementId> \
  --nexus-project <id-or-name> \
  [--budget N] \
  [--source TYPE] \
  [--constraint k=v] \
  [--explain]
```

Change :

```bash
morpheus --json augmented-context change \
  --project <projectId> \
  --change <changeId> \
  --nexus-project <id-or-name> \
  [--budget N] \
  [--source TYPE] \
  [--constraint k=v] \
  [--explain]
```

Sources NEXUS admises :

```text
FILE | SYMBOL | TEST | DOCUMENTATION | INSTRUCTION | SKILL | GIT
```

## JARVIS — contrat d’orchestration read-only

État observable d’un changement :

```bash
morpheus --json change-orchestration state \
  --project <projectId> \
  --change <changeId> \
  [--lifecycle <state>] \
  [--abandonment-reason <reason>]
```

Évaluation de transition :

```bash
morpheus --json change-orchestration transition-check \
  --project <projectId> \
  --change <changeId> \
  --from <state> \
  --to <state> \
  [--from-abandonment-reason <reason>] \
  [--abandonment-reason <reason>] \
  [--allow-backward] \
  [--allow-completed-reopen]
```

États lifecycle :

```text
DRAFT | PROPOSED | SPECIFIED | DESIGNED | PLANNED
IMPLEMENTING | VERIFYING | COMPLETED | ARCHIVED | ABANDONED
```

Décisions :

```text
ALLOWED | BLOCKED | UNKNOWN | REQUIRES_INPUT
```

Ces commandes n’appliquent aucune transition.

## API HTTP et MCP

```bash
morpheus api [--host HOST] [--port PORT]
morpheus mcp --stdio
```

Defaults API : `127.0.0.1:8765`, base `/api/v1`.

En mode MCP, `--json` n’est pas applicable : stdout est réservé au protocole MCP.

## Codes de sortie

| Code | Nom | Signification |
|---:|---|---|
| 0 | `SUCCESS` | commande réussie |
| 2 | `USAGE` | option, identité ou argument invalide |
| 3 | `NOT_FOUND` | projet, snapshot ou entité absente |
| 4 | `STATE_ERROR` | état persisté ou synchronisation incompatible |
| 5 | `IO_ERROR` | erreur d’I/O classifiée par l’adapter |
| 10 | `INTERNAL_ERROR` | erreur inattendue |

Pour l’automatisation, parser le JSON et le code de sortie, pas le texte humain d’erreur.

## Voir aussi

- [Démarrage rapide](QUICKSTART.md)
- [Intégrations optionnelles](INTEGRATIONS.md)
- [API HTTP](../developer/API.md)
- [MCP](../developer/MCP.md)
