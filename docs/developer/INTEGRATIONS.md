# Intégrations cross-engine

MORPHEUS reste autonome. Les intégrations externes implémentent des ports explicites et ne transfèrent pas la propriété du domaine.

```text
MORPHEUS = specification facts + intent + lifecycle rules + controlled state invariants + provider composition facts
MINOS    = code intelligence
NEXUS    = context selection / ranking / fusion / compression
JARVIS   = sequencing / orchestration / action choice
```

## 1. Règle d’architecture commune

```text
application -> port -> adapter -> moteur externe
```

Conséquences :

- `com.minos.*`, `com.nexus.*` et `com.jarvis.*` ne deviennent pas des types du domaine/application MORPHEUS ;
- l’indisponibilité d’un moteur externe reste explicite ;
- une observation live ne devient pas silencieusement persistée ;
- les adapters ne réimplémentent pas les responsabilités du moteur distant.

## 2. MINOS

MINOS résout une référence de code vers l’état observé du code indexé.

```text
ExternalReference persisted
        ↓
ExternalReferenceResolver port
        ↓
MINOS MCP STDIO adapter
        ↓
FOUND | NOT_FOUND | UNAVAILABLE | AMBIGUOUS | REVISION_MISMATCH | UNSUPPORTED
```

Contraintes :

```text
aucune dépendance com.minos.*
MINOS non embarqué
matching exact sur symbolKey
observation live séparée de la référence persistée
persisted=false pour la résolution live
NOT_FOUND != UNAVAILABLE
```

## 3. NEXUS

MORPHEUS produit une intention structurée ; NEXUS choisit le contexte technique sous contraintes et budget.

```text
MORPHEUS = construction de l’intention déterministe
NEXUS    = sélection / ranking / fusion / compression / budget technique
```

MORPHEUS ne reranke pas le `ContextBundle`, ne réapplique pas son budget, ne le fusionne pas une seconde fois et ne le persiste pas dans `KnowledgeSnapshot`.

Chaque requête utilise un mapping projet NEXUS explicite ; aucun chemin local n’est converti implicitement en identité NEXUS.

## 4. JARVIS

MORPHEUS ne lance pas JARVIS. JARVIS consomme l’API HTTP locale MORPHEUS.

Frontière :

```text
MORPHEUS = facts + lifecycle rules + transition decisions + controlled state invariants
JARVIS   = sequencing + orchestration + action choice
```

Routes read-only M14 :

```text
GET  /api/v1/projects/{projectId}/changes/{changeId}/orchestration
POST /api/v1/projects/{projectId}/changes/{changeId}/transition-check
```

Décisions :

```text
ALLOWED
BLOCKED
UNKNOWN
REQUIRES_INPUT
```

Le POST `transition-check` n’applique rien.

### Mutation contrôlée M17

Surface distincte :

```text
POST /api/v1/projects/{projectId}/changes/{changeId}/lifecycle-transitions
```

Pipeline :

```text
idempotency
  ↓
WRITE_CHANGE capability
  ↓
confirmation
  ↓
expectedRevision / CAS
  ↓
transition evaluation
  ↓
state + audit atomiques
```

```text
READ_CHANGES != WRITE_CHANGE
ALLOWED != applied
published snapshot != operational lifecycle state
stale revision != overwrite
idempotent retry != duplicate mutation/audit
MORPHEUS rules != JARVIS action sequencing
```

Le choix et le sequencing restent JARVIS.

## 5. Composition provider M18

La composition multi-provider n’est pas une intégration cross-engine : elle appartient à MORPHEUS et compose des observations de spécification déjà normalisées.

```text
OpenSpec adapter            Structured Markdown adapter
       \                           /
        -> ProviderContribution <-
                  |
       MultiProviderCompositionService
                  |
       explicit precedence + provenance
                  |
          CompositionConflict*
                  |
          Memory / SQLite V012
```

Invariants :

```text
provider identifier != DomainIdentity
source path != identity
same logical entity may have multiple provider observations
precedence != provenance erasure
conflict != silent last-write-wins
ambiguous continuity must be surfaced
optional provider absence != project failure when optional
provider-specific types never leak into domain/application
```

Surfaces :

```text
CLI  composition sync | status | conflicts
MCP  get_composition_status | list_composition_conflicts
HTTP GET /api/v1/projects/{projectId}/composition
HTTP GET /api/v1/projects/{projectId}/composition/conflicts
OpenAPI 1.7.0
```

## 6. Matrice de propriété

| Donnée/capacité | Propriétaire | MORPHEUS persiste ? |
|---|---|---:|
| requirement normalisé | MORPHEUS | oui |
| traceability link | MORPHEUS | oui |
| provider composition state/conflicts | MORPHEUS | oui, snapshot-scoped |
| ExternalReference | MORPHEUS | oui |
| observation actuelle d’un symbole MINOS | MINOS | non |
| ContextBundle NEXUS | NEXUS | non |
| décision de transition | MORPHEUS | calcul / pas mutation implicite |
| état lifecycle opérationnel M17 | MORPHEUS | oui, séparé du snapshot |
| choix de l’action suivante | JARVIS | non |

## 7. Failure semantics

Une intégration robuste distingue :

```text
succès métier
résultat métier négatif
indisponibilité
contrat invalide/incompatible
```

Exemples :

```text
MINOS NOT_FOUND != MINOS UNAVAILABLE
NEXUS absent != MORPHEUS failure
JARVIS client fail-open != fait métier négatif
optional provider absence != required provider absence
```

## 8. Baseline validée

```text
M18             ✅ VALIDÉ / INTÉGRÉ — PR #86
Code validé     7e8caacff567f51354fcb88bd7505a6d135071c0
Merge           30f11ac3ffc522bcc0c71e31216a3fb70f0631d7
Tests           418/418 PASS
Architecture    170/170 PASS
Packaging       Windows + smokes + API health PASS
```

ADR associées : ADR-0069→0080 pour les intégrations M12→M14, ADR-0083 pour le write contrôlé M17, ADR-0084 pour la composition M18.