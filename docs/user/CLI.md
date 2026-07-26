# Référence CLI MORPHEUS

Cette page décrit la CLI officielle de MORPHEUS sur la baseline **M18 validée et intégrée**. Elle complète le [démarrage rapide](QUICKSTART.md) par une référence opérationnelle des commandes, options, identités et codes de sortie.

## 1. Invocation

Distribution portable :

```text
Windows  .\morpheus\morpheus.exe <commande>
Linux    ./morpheus/bin/morpheus <commande>
```

JAR autonome pour usage développeur :

```bash
java -jar morpheus-cli-0.1.0-SNAPSHOT-all.jar <commande>
```

Dans les exemples, `morpheus` désigne le launcher correspondant à la plateforme.

## 2. Forme générale

```text
morpheus [options-globales] <commande> [sous-commande] [options]
```

Options globales :

| Option | Rôle |
|---|---|
| `--json` | sortie machine-readable sur stdout |
| `--data-dir PATH` | remplace le répertoire de données |
| `--config-dir PATH` | remplace le répertoire de configuration |
| `--db PATH` | sélectionne explicitement la base SQLite |

Variables équivalentes :

```text
MORPHEUS_DATA_DIR
MORPHEUS_CONFIG_DIR
MORPHEUS_DB
```

Les options globales doivent précéder la commande.

## 3. Utilitaires

```bash
morpheus help
morpheus version
morpheus --version
morpheus paths
morpheus --json version
```

## 4. Identités

MORPHEUS ne confond pas chemin local et identité métier :

```text
workspace path != projectId
DomainIdentity != EntityVersionId
DomainIdentity != SourceLocator != ExternalReference
provider identifier != DomainIdentity
```

Après `projects add`, conserver le `projectId` retourné.

## 5. Projets

```bash
morpheus projects list
morpheus --json projects list
morpheus projects add --workspace /path/to/project
```

L’enregistrement ne publie pas encore de snapshot.

## 6. Synchronisation publiée

```bash
morpheus sync --project <projectId>
morpheus sync --project <projectId> --revision <revision>
morpheus sync-status --project <projectId>
```

Une synchronisation publiée est conservatrice : un candidat défaillant ne remplace pas l’ancien snapshot `ACTIVE`.

## 7. M18 — composition multi-provider

Commandes officielles exactes :

```bash
morpheus composition sync --project <projectId>
morpheus composition sync --project <projectId> --revision <revision>
morpheus composition status --project <projectId>
morpheus composition conflicts --project <projectId>
```

Mode JSON :

```bash
morpheus --json composition status --project <projectId>
morpheus --json composition conflicts --project <projectId>
```

`composition sync` construit la vue à partir des providers réels compatibles. M18 valide **OpenSpec + Structured Markdown** dans un même projet.

La composition conserve toutes les observations nécessaires et rend explicites :

```text
provider ownership
source precedence
provenance
content conflicts
ownership conflicts
type / identity conflicts
ambiguous continuity
```

Invariants :

```text
provider identifier != DomainIdentity
source path != identity
precedence != provenance erasure
conflict != silent last-write-wins
optional provider absence != project failure when optional
```

L’état de composition est persisté en Memory / SQLite V012 et reste snapshot-scoped.

## 8. Requirements

```bash
morpheus requirements find --project <projectId> --query "texte"
morpheus --json requirements find --project <projectId> --query "session"
```

Pagination lorsque disponible :

```text
--offset N
--limit N   # 1..100
```

## 9. Changements et artefacts

```bash
morpheus changes list --project <projectId>
morpheus changes get --project <projectId> --change <changeId>
morpheus constraints list --project <projectId> --change <changeId>
morpheus decisions list --project <projectId> --change <changeId>
morpheus tasks list --project <projectId> --change <changeId>
```

`Scenario != AcceptanceCriterion`.

## 10. Acceptance Criteria — M15

```bash
morpheus acceptance-criteria list --project <projectId>
morpheus acceptance-criteria list --project <projectId> --change <changeId>
morpheus acceptance-criteria list --project <projectId> --requirement <requirementId>
```

MORPHEUS n’infère jamais `VERIFIED` de la simple présence d’un test.

## 11. Contraintes — M16

```bash
morpheus constraints list --project <projectId> --change <changeId>
morpheus --json constraints evaluate \
  --project <projectId> \
  --change <changeId> \
  --target VERIFYING
```

```text
applicable != blocking
severity != blocking policy
UNKNOWN != BLOCKED
constraint text != executable policy
```

## 12. Traçabilité et analyse

```bash
morpheus trace-requirement \
  --project <projectId> \
  --requirement <requirementId> \
  --depth 2

morpheus change-context \
  --project <projectId> \
  --change <changeId> \
  --depth 2

morpheus analyze-change \
  --project <projectId> \
  --change <changeId> \
  --depth 2
```

```text
ANALYZE != APPLY
ANALYZE != PROMOTE
ANALYZE != ACTIVATE
```

## 13. Qualité

```bash
morpheus quality --project <projectId>
```

Les diagnostics sont dérivés et ne mutent pas le snapshot publié.

## 14. MINOS — références de code

```bash
morpheus --json minos-status
morpheus --json external-references list --project <projectId> --owner <domainIdentity>
morpheus --json external-references resolve --project <projectId> --reference <externalReferenceId>
```

MINOS reste optionnel. Une résolution live ne réécrit pas l’historique publié.

## 15. NEXUS — contexte technique augmenté

```bash
morpheus --json nexus-status

morpheus --json augmented-context requirement \
  --project <projectId> \
  --requirement <requirementId> \
  --nexus-project <id-or-name> \
  [--budget N] [--source TYPE] [--constraint k=v] [--explain]

morpheus --json augmented-context change \
  --project <projectId> \
  --change <changeId> \
  --nexus-project <id-or-name> \
  [--budget N] [--source TYPE] [--constraint k=v] [--explain]
```

Sources admises :

```text
FILE | SYMBOL | TEST | DOCUMENTATION | INSTRUCTION | SKILL | GIT
```

NEXUS reste propriétaire de la sélection, du ranking, de la fusion, de la compression et du budget technique.

## 16. JARVIS — orchestration read-only

Observer :

```bash
morpheus --json change-orchestration state \
  --project <projectId> \
  --change <changeId> \
  [--lifecycle <state>] \
  [--abandonment-reason <reason>]
```

Évaluer :

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

Décisions :

```text
ALLOWED
BLOCKED
UNKNOWN
REQUIRES_INPUT
```

Cette commande ne mute rien.

## 17. M17 — appliquer une transition lifecycle contrôlée

```bash
morpheus --json lifecycle apply \
  --project <projectId> \
  --change <changeId> \
  --expected-revision 0 \
  --to PROPOSED \
  --idempotency-key release-42-change-7-proposed \
  --actor jarvis \
  --confirm
```

Garde-fous :

```text
READ_CHANGES != WRITE_CHANGE
ALLOWED != applied
WRITE_CHANGE explicite
confirmation explicite
expectedRevision / CAS
idempotencyKey
transition M14-M16 réellement ALLOWED
audit persistant
```

Résultats :

```text
APPLIED
ALREADY_APPLIED
CONFLICT
NOT_AUTHORIZED
REQUIRES_CONFIRMATION
REJECTED
```

`ALREADY_APPLIED` ne crée ni seconde révision ni second audit.

## 18. API HTTP et MCP

```bash
morpheus api [--host HOST] [--port PORT]
morpheus mcp --stdio
```

Defaults API : `127.0.0.1:8765`, base `/api/v1`, contrat OpenAPI **1.7.0**.

Surfaces M18 :

```text
CLI  composition sync/status/conflicts
HTTP GET .../composition
HTTP GET .../composition/conflicts
MCP  get_composition_status
MCP  list_composition_conflicts
```

Catalogue MCP M18 : **22 tools read-only + 1 tool write explicite**.

En mode MCP, `--json` n’est pas applicable : `stdout` est réservé au protocole MCP.

## 19. Codes de sortie

| Code | Nom | Signification | Action typique |
|---:|---|---|---|
| 0 | `SUCCESS` | commande réussie | consommer la sortie |
| 2 | `USAGE` | option, identité ou argument invalide | corriger l’appel |
| 3 | `NOT_FOUND` | projet, snapshot ou entité absente | vérifier DB, projet, sync et identifiants |
| 4 | `STATE_ERROR` | état incompatible ou résultat métier non applicable | inspecter le JSON |
| 5 | `IO_ERROR` | erreur d’I/O classifiée | vérifier chemins, droits, processus externe |
| 10 | `INTERNAL_ERROR` | erreur inattendue | conserver stderr et contexte |

## 20. Patron PowerShell robuste

```powershell
$result = & morpheus --json composition status --project $projectId
if ($LASTEXITCODE -ne 0) {
    throw "MORPHEUS failed with exit code $LASTEXITCODE"
}
$data = $result | ConvertFrom-Json
```

Le contrat d’automatisation est : **code de sortie + JSON structuré**, pas la formulation humaine des messages.

## 21. Validation M18

```text
CLI              29/29 PASS
TOTAL            418/418 PASS
Architecture     170/170 PASS
Packaging/smokes PASS
```

Code réellement testé : `7e8caacff567f51354fcb88bd7505a6d135071c0`.  
Merge M18 : `30f11ac3ffc522bcc0c71e31216a3fb70f0631d7`.

## 22. Voir aussi

- [Guide utilisateur](README.md)
- [Démarrage rapide](QUICKSTART.md)
- [Intégrations optionnelles](INTEGRATIONS.md)
- [API HTTP](../developer/API.md)
- [MCP](../developer/MCP.md)
- [ADR-0083](../adr/0083-controlled-lifecycle-write-operations.md)
- [ADR-0084](../adr/0084-provider-neutral-multi-provider-composition.md)