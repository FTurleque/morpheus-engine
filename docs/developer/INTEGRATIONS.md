# Intégrations cross-engine

MORPHEUS reste autonome. Les intégrations externes implémentent des ports explicites et ne transfèrent pas la propriété du domaine.

```text
MORPHEUS = specification facts + intent + lifecycle rules
           + controlled state invariants + provider composition facts
MINOS    = code intelligence
NEXUS    = context selection / ranking / fusion / compression
JARVIS   = sequencing / orchestration / action choice
```

## 1. Règle commune

Le domaine et l’application MORPHEUS ne dépendent jamais des classes MINOS, NEXUS ou JARVIS. Une observation externe live ne devient pas silencieusement une donnée persistée. L’absence d’un moteur optionnel reste distincte d’un échec MORPHEUS.

Les providers M18 OpenSpec et Structured Markdown sont des adapters MORPHEUS ; ils ne sont pas des moteurs cross-engine.

## 2. MINOS

MINOS résout une `ExternalReference` vers l’état observé du code indexé.

Contrats importants :

```text
FOUND
NOT_FOUND
UNAVAILABLE
AMBIGUOUS
REVISION_MISMATCH
UNSUPPORTED
```

`NOT_FOUND != UNAVAILABLE`.

La résolution reste live, `persisted=false`, et ne réécrit pas un snapshot publié.

Configuration :

```text
MORPHEUS_MINOS_JAR
MORPHEUS_MINOS_JAVA
MORPHEUS_MINOS_HOME
MORPHEUS_MINOS_TIMEOUT_SECONDS
```

## 3. NEXUS

MORPHEUS produit une intention structurée ; NEXUS construit le contexte technique sous contraintes et budget.

```text
MORPHEUS = construction de l’intention déterministe
NEXUS    = sélection / ranking / fusion / compression / budget technique
```

MORPHEUS ne reranke pas le `ContextBundle`, ne le fusionne pas une seconde fois et ne le persiste pas dans `KnowledgeSnapshot`.

Le mapping projet NEXUS est explicite. Le bundle reste `persisted=false`.

Configuration :

```text
MORPHEUS_NEXUS_JAR
MORPHEUS_NEXUS_JAVA
MORPHEUS_NEXUS_HOME
MORPHEUS_NEXUS_TIMEOUT_SECONDS
```

## 4. JARVIS

JARVIS consomme les faits et décisions MORPHEUS mais reste propriétaire du choix et du séquencement des actions.

### Évaluation read-only

```text
GET  /api/v1/projects/{projectId}/changes/{changeId}/orchestration
POST /api/v1/projects/{projectId}/changes/{changeId}/transition-check
```

Le `transition-check` retourne :

```text
ALLOWED
BLOCKED
UNKNOWN
REQUIRES_INPUT
```

Il n’applique aucune transition.

### Controlled write M17

La mutation est un endpoint distinct :

```text
POST /api/v1/projects/{projectId}/changes/{changeId}/lifecycle-transitions
```

Elle exige :

```text
WRITE_CHANGE capability
confirmation explicite
expectedRevision / CAS
idempotencyKey
transition réellement ALLOWED
audit append-only
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

Invariants :

```text
transition evaluation != lifecycle mutation
READ_CHANGES != WRITE_CHANGE
ALLOWED != applied
published snapshot != operational lifecycle state
stale revision != overwrite
idempotent retry != duplicate mutation/audit
MORPHEUS rules != JARVIS action sequencing
```

JARVIS choisit qu’une action soit tentée ; MORPHEUS vérifie ses invariants puis applique éventuellement son propre état opérationnel.

## 5. Composition provider M18

La composition MORPHEUS est distincte du ranking/fusion NEXUS et de l’orchestration JARVIS.

```text
OpenSpec + Structured Markdown
        ↓
ProviderContribution
        ↓
MultiProviderCompositionService
        ↓
CompositionSnapshotState
        ↓
Memory / SQLite V012
```

Invariants :

```text
provider identifier != DomainIdentity
source path != identity
precedence != provenance erasure
conflict != silent last-write-wins
ambiguous continuity must be surfaced
optional provider absence != project failure when optional
```

## 6. Propriété des données

| Donnée/capacité | Propriétaire | Persistée MORPHEUS ? |
|---|---|---:|
| requirement normalisé | MORPHEUS | oui |
| traceability link | MORPHEUS | oui |
| état de composition/provider provenance | MORPHEUS | oui, snapshot-scoped |
| ExternalReference | MORPHEUS | oui |
| observation symbole MINOS | MINOS | non |
| ContextBundle NEXUS | NEXUS | non |
| décision de transition | MORPHEUS | calculée |
| lifecycle operational state/audit | MORPHEUS | oui si commande appliquée |
| choix de l’action suivante | JARVIS | non |

## 7. Failure semantics

Conserver distinctement : succès métier, résultat négatif, indisponibilité et contrat invalide/incompatible.

Exemples :

- MINOS `NOT_FOUND` est un résultat métier négatif ;
- MINOS `UNAVAILABLE` signifie résolution indisponible ;
- NEXUS absent signifie contexte technique indisponible seulement ;
- provider optionnel absent n’est pas un échec projet ;
- `NOT_AUTHORIZED` indique l’absence de `WRITE_CHANGE`, pas une panne transport ;
- un conflit provider est un fait explicite à exposer.

## 8. Règles d’architecture

Les tests d’architecture interdisent les dépendances inverses vers les adapters et moteurs externes, ainsi que la fuite des types provider dans le domaine/application.

Gate M18 : **Architecture 170/170 PASS**.

## 9. Validation actuelle

```text
MINOS Integration      8/8 PASS
NEXUS Integration      7/7 PASS
M18 TOTAL            418/418 PASS
Architecture         170/170 PASS
Packaging/smokes      PASS
```

Code M18 testé : `7e8caacff567f51354fcb88bd7505a6d135071c0`.  
Merge M18 : `30f11ac3ffc522bcc0c71e31216a3fb70f0631d7`.

## 10. Voir aussi

- [Architecture](ARCHITECTURE.md)
- [API HTTP](API.md)
- [MCP](MCP.md)
- [Guide utilisateur des intégrations](../user/INTEGRATIONS.md)
- [ADR-0084](../adr/0084-provider-neutral-multi-provider-composition.md)