# Référence CLI MORPHEUS

Cette page décrit la CLI officielle de MORPHEUS dans la baseline **M18 validée et intégrée**. Elle complète le [démarrage rapide](QUICKSTART.md) par une référence opérationnelle des commandes, options, identités et codes de sortie.

## 1. Invocation

Distribution portable :

```text
Windows  .\morpheus\morpheus.exe <commande>
Linux    ./morpheus/bin/morpheus <commande>
```

JAR autonome développeur :

```bash
java -jar morpheus-cli-0.1.0-SNAPSHOT-all.jar <commande>
```

Dans les exemples, `morpheus` désigne le launcher correspondant à la plateforme.

## 2. Forme générale

```text
morpheus [options-globales] <commande> [sous-commande] [options]
```

Options globales principales :

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

`--json` rend `stdout` scriptable. Les erreurs et diagnostics restent sur `stderr`.

## 3. Identités

```text
workspace path != projectId
provider identifier != DomainIdentity
source path != identity
DomainIdentity != EntityVersionId != SourceLocator != ExternalReference
SpecificationVersion != KnowledgeSnapshot
```

Après `projects add`, conserver le `projectId`. Les commandes métier utilisent les identifiants MORPHEUS retournés par les requêtes précédentes.

## 4. Utilitaires et projets

```bash
morpheus help
morpheus version
morpheus paths
morpheus projects list
morpheus projects add --workspace /path/to/project
```

L’enregistrement crée/résout l’identité locale du projet ; il ne publie pas encore un snapshot métier.

## 5. Synchronisation publiée

```bash
morpheus sync --project <projectId>
morpheus sync --project <projectId> --revision <revision>
morpheus sync-status --project <projectId>
```

Le launcher produit une reconstruction conservatrice lorsqu’un snapshot publié doit être produit. Un candidat échoué ne doit jamais remplacer l’`ACTIVE` valide précédent.

## 6. Requirements

```bash
morpheus requirements find \
  --project <projectId> \
  --query "texte"
```

Pagination lorsque disponible :

```text
--offset N
--limit N   # 1..100
```

## 7. Changements et artefacts

```bash
morpheus changes list --project <projectId>
morpheus changes get --project <projectId> --change <changeId>

morpheus constraints list --project <projectId> --change <changeId>
morpheus decisions list   --project <projectId> --change <changeId>
morpheus tasks list       --project <projectId> --change <changeId>
```

`Scenario != AcceptanceCriterion`.

## 8. Acceptance Criteria — M15

```bash
morpheus acceptance-criteria list --project <projectId>
morpheus acceptance-criteria list --project <projectId> --change <changeId>
morpheus acceptance-criteria list --project <projectId> --requirement <requirementId>
```

MORPHEUS n’invente ni critère d’acceptation ni statut `VERIFIED` depuis la seule existence d’un scénario ou d’un test.

## 9. Contraintes — M16

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
warning != blocker
UNKNOWN != BLOCKED
```

## 10. Traçabilité, contexte, analyse et qualité

```bash
morpheus trace-requirement --project <projectId> --requirement <requirementId> --depth 2
morpheus change-context --project <projectId> --change <changeId> --depth 2
morpheus analyze-change --project <projectId> --change <changeId> --depth 2
morpheus quality --project <projectId>
```

```text
ANALYZE != APPLY
ANALYZE != PROMOTE
ANALYZE != ACTIVATE
```

## 11. MINOS — références de code

```bash
morpheus --json minos-status
morpheus --json external-references list --project <projectId> --owner <domainIdentity>
morpheus --json external-references resolve --project <projectId> --reference <externalReferenceId>
```

Sans configuration, MINOS est `DISABLED`. La résolution live ne réécrit pas l’historique publié.

## 12. NEXUS — contexte technique augmenté

```bash
morpheus --json nexus-status

morpheus --json augmented-context requirement \
  --project <projectId> \
  --requirement <requirementId> \
  --nexus-project <id-or-name>

morpheus --json augmented-context change \
  --project <projectId> \
  --change <changeId> \
  --nexus-project <id-or-name>
```

MORPHEUS construit l’intention ; NEXUS reste propriétaire de la sélection, du ranking, de la fusion, de la compression et du budget technique.

## 13. JARVIS — orchestration read-only

Observer :

```bash
morpheus --json change-orchestration state \
  --project <projectId> \
  --change <changeId>
```

Évaluer :

```bash
morpheus --json change-orchestration transition-check \
  --project <projectId> \
  --change <changeId> \
  --from PROPOSED \
  --to SPECIFIED
```

Décisions :

```text
ALLOWED
BLOCKED
UNKNOWN
REQUIRES_INPUT
```

Cette commande **n’applique aucune transition**.

## 14. M17 — transition lifecycle contrôlée

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
transition réellement ALLOWED
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

`published snapshot != operational lifecycle state`.

## 15. M18 — composition multi-provider

M18 introduit une composition explicite entre plusieurs providers réels, validée avec **OpenSpec + Structured Markdown**.

Synchroniser la composition :

```bash
morpheus composition sync --project <projectId>
morpheus composition sync --project <projectId> --revision <revision>
```

Lire l’état :

```bash
morpheus composition status --project <projectId>
morpheus --json composition status --project <projectId>
```

Lister les conflits :

```bash
morpheus composition conflicts --project <projectId>
morpheus --json composition conflicts --project <projectId>
```

Sémantique :

```text
provider identifier != DomainIdentity
source path != identity
precedence != provenance erasure
conflict != silent last-write-wins
ambiguous continuity must be surfaced
optional provider absence != project failure when optional
```

La priorité sélectionne un candidat principal lorsque nécessaire ; elle ne supprime pas les autres observations ni leur provenance. Les conflits de contenu, ownership et type/identité restent explicites et requêtables.

## 16. Sorties JSON et codes de sortie

Règle d’automatisation :

1. lire le code de sortie ;
2. parser le JSON de `stdout` ;
3. utiliser `stderr` pour le diagnostic humain.

Codes principaux :

| Code | Sens |
|---:|---|
| 0 | succès |
| 2 | usage/argument invalide |
| 3 | ressource absente |
| 4 | état incompatible |
| 5 | erreur I/O classifiée |
| 10 | erreur interne inattendue |

## 17. Baseline validée

```text
M18             ✅ VALIDÉ / INTÉGRÉ — PR #86
Code validé     7e8caacff567f51354fcb88bd7505a6d135071c0
Merge           30f11ac3ffc522bcc0c71e31216a3fb70f0631d7
CLI tests       29/29 PASS
Total           418/418 PASS
Architecture    170/170 PASS
Packaging       Windows + smokes + API health PASS
```

Preuve : [`../validation/VALIDATION_M18.md`](../validation/VALIDATION_M18.md).