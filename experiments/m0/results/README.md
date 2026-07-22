# Résultats M0 — MORPHEUS

Date : 22 juillet 2026

## Synthèse

| Expérience | Sujet | Statut | Décision principale |
|---|---|---|---|
| E01 | Provider detection | **PASS** | capability-based selection retenue |
| E02 | Domain mapping | **PASS** | normalisation provider-agnostic viable |
| E03 | Stable identity | **PASS** | identité distincte du locator/contenu/external ID |
| E03b | UUIDv7 | **PASS** | UUIDv7 proposé comme `DomainIdentity` |
| E04 | Current reconstruction | **PASS** | CURRENT / PROPOSED / HISTORICAL séparés |
| E04b | Change lifecycle | **PASS** | machine d'état canonique + design facultatif conditionnel |
| E05 | Knowledge snapshots | **PASS** | activation atomique observable + rebuild |
| E05b | Rebuild / retention | **PASS** | store reconstructible, rétention minimale définie |
| E06 | Traceability | **PASS** | liens typés, traversées, unresolved conservé |
| E06b | Store-backed traceability | **PASS** | même contrat mémoire / SQLite |
| E07 | Memory store | **PASS** | backend de référence pour tests |
| E08 | Persistent store | **PASS** | SQLite candidat viable, adoption traitée par ADR |
| E09 | Graph store need | **PASS** | graph DB **NOT_NEEDED_FOR_MVP** |
| E10 | Lexical search | **PASS** | recherche déterministe suffisante au MVP |
| E11 | Incremental ingestion | **PASS** | fingerprint + provenance, watcher non requis |
| E12 | Diagnostics | **PASS** | diagnostics structurés et transportables |
| E13 | Compact context | **PASS** | vue compacte MORPHEUS, ranking reste NEXUS |
| E14 | External references | **PASS** | résolution cross-engine optionnelle |

---

## Résultats structurants

### Provider / OpenSpec

```text
OpenSpec-first, not OpenSpec-locked
```

Le schéma `spec-driven` est supporté par le provider expérimental.

Un schéma non supporté échoue explicitement.

Les capabilities sont annoncées selon ce que le provider sait réellement normaliser ; `Scenario` n'est pas automatiquement promu en `AcceptanceCriterion`.

### Domaine

Deux providers distincts produisent la même enveloppe MORPHEUS :

```text
current
proposed
historical
diagnostics
```

### Identité

La continuité d'identité ne dépend pas du chemin, du titre ou du contenu.

Les ambiguïtés ne fusionnent jamais silencieusement.

Format retenu pour décision finale :

```text
UUIDv7
```

### États

```text
TemporalState:
CURRENT / PROPOSED / HISTORICAL
```

reste orthogonal à :

```text
ChangeLifecycleState
VerificationStatus
ResolutionState
SnapshotStatus
```

### Snapshots

```text
BUILDING -> VALIDATING -> READY -> ACTIVE
```

L'activation est atomique au niveau observable.

Le store est reconstructible depuis les sources.

### Stockage

Deux implémentations expérimentales :

```text
memory
SQLite
```

Le backend mémoire devient référence des tests contractuels.

SQLite est suffisamment viable pour être proposé comme backend persistant initial, mais son schéma JSON de spike est rejeté comme schéma de production.

### Graphe

```text
conceptual graph = yes
mandatory graph database = no
```

E09 conclut :

```text
GRAPH_STORE_FOR_MVP = NOT_NEEDED
```

### Search

Recherche lexicale déterministe suffisante au MVP.

La recherche sémantique reste future et optionnelle.

### Incrémental

```text
SHA-256 inventory
+
provenance
```

suffit comme base M0.

Watcher non requis pour le MVP.

### Cross-engine

Une `ExternalReference` existe sans que MINOS soit disponible.

La résolution live est optionnelle et peut devenir `RESOLVED`, `UNRESOLVED` ou `STALE` sans perdre l'historique.

---

## Fondation produit recommandée à la sortie M0

```text
Language             : Java
Compatibility        : Java 21 source / bytecode
Compiler JDK         : Java 21+ via --release 21
Build                : Maven 3.9.16 + Maven Wrapper
Persistent store     : SQLite behind SpecificationKnowledgeStore
Memory store         : contract-test reference
Graph DB             : none for MVP
Server framework     : none in foundation
DI framework         : none required
LLM                  : none required
DomainIdentity       : UUIDv7
```

---

## Limite d'environnement de validation

L'environnement d'exécution utilisé pour cette étude possède :

```text
OpenJDK 21.0.10
```

mais ne possède pas Maven et n'autorise pas le téléchargement réseau direct de Maven depuis le shell.

Conséquence :

> le premier commit de fondation M1 doit obligatoirement exécuter le Maven Wrapper sur Windows et CI avant que du code fonctionnel significatif soit ajouté.

Cette limite ne change pas la décision d'outil ; elle devient un gate de bootstrap M1.

---

## Conclusion

Les expériences M0 ne mettent en évidence aucun obstacle structurel à l'architecture :

```text
Provider
  ↓
Normalization
  ↓
MORPHEUS Domain
  ↓
KnowledgeSnapshot
  ↓
SpecificationKnowledgeStore
  ↓
Query / Traceability / Context
```

M0 dispose désormais des preuves nécessaires pour une revue finale et une fondation M1 contrôlée.
