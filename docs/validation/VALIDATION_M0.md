# Validation M0 — MORPHEUS

Statut : **M0 VALIDÉE — fondation M1 autorisée sous gates explicites**

Date : 22 juillet 2026

---

## 1. Décision

La phase **M0 — Faisabilité technique** est considérée comme suffisamment démontrée pour autoriser le démarrage de **M1 — Découverte des projets et providers** sur une fondation produit contrôlée.

La validation repose sur :

- E01 à E14 ;
- E03b UUIDv7 ;
- E04b lifecycle ;
- E05b rebuild/rétention ;
- E06b traçabilité persistée ;
- les ADR détaillées ;
- l'étude de fondation technique.

La validation M0 signifie :

> **les hypothèses structurantes de C0 ont été confrontées à des prototypes, fixtures, tests contractuels et mesures suffisants pour décider de la fondation M1.**

Elle ne signifie pas que les spikes Python deviennent du code de production.

---

## 2. Porte M0

Question de sortie :

> **L'architecture provider → ingestion → domaine normalisé → knowledge store permet-elle à MORPHEUS de rester indépendant du format et du backend tout en fournissant des requêtes fiables, traçables, cohérentes et local-first ?**

Réponse :

```text
OUI — ADOPTER AVEC LES CONTRAINTES DOCUMENTÉES
```

---

## 3. Synthèse des expériences

Voir [`../experiments/m0/results/README.md`](../experiments/m0/results/README.md).

État :

```text
E01  PASS  provider detection
E02  PASS  domain mapping
E03  PASS  stable identity semantics
E03b PASS  UUIDv7 identity format
E04  PASS  current reconstruction
E04b PASS  change lifecycle
E05  PASS  knowledge snapshots
E05b PASS  rebuild / retention
E06  PASS  traceability
E06b PASS  store-backed traceability
E07  PASS  memory store
E08  PASS  SQLite persistent candidate
E09  PASS  graph store NOT_NEEDED_FOR_MVP
E10  PASS  lexical search
E11  PASS  incremental inventory/invalidation
E12  PASS  diagnostics
E13  PASS  compact context
E14  PASS  external references
```

---

## 4. Décisions de domaine

### Domaine indépendant des providers

**ADOPTER AVEC CONTRAINTE.**

E02 démontre deux providers produisant la même enveloppe normalisée.

Contrainte M1 : ajouter un test d'architecture Java empêchant le module `domain` de dépendre de `provider-openspec`, SQLite ou couches d'exposition.

### Temporal state

**ADOPTER.**

```text
CURRENT
PROPOSED
HISTORICAL
```

Les changements concurrents restent distincts ; archive/completed ne signifie pas promotion automatique en `CURRENT`.

### Lifecycle

**ADOPTER AVEC CONTRAINTES.**

Cycle canonique conservé.

Décision sur l'étape design :

```text
SPECIFIED -> PLANNED
```

est autorisé lorsque :

```text
design_required = false
```

Les transitions backward sont permises selon la politique documentée.

### DomainIdentity

**ADOPTER.**

Sémantique E03 conservée.

Format :

```text
UUIDv7
```

La valeur reste opaque ; son timestamp n'est jamais une donnée métier implicite.

---

## 5. Providers

### OpenSpec

**ADOPTER AVEC CONTRAINTES** comme premier provider de référence.

Contrainte initiale :

```text
schema supporté = spec-driven
```

Un schéma OpenSpec custom inconnu produit un diagnostic et n'est pas interprété approximativement.

Règle importante :

```text
Scenario != AcceptanceCriterion par défaut
```

Le provider n'annonce pas une capability qu'il ne sait pas normaliser sémantiquement.

### Capability negotiation

**ADOPTER.**

Sélection fondée sur :

- source ;
- schéma/version disponible ;
- capabilities required ;
- capabilities preferred ;
- local/remote ;
- configuration explicite ;
- fallback déterministe.

Provider distant : opt-in obligatoire.

### Read-first

**ADOPTER.**

Le MVP et l'ensemble des preuves M0 fonctionnent sans écriture dans les sources.

Les capacités d'écriture restent séparées et différées.

---

## 6. Traceability

### `TraceabilityLink`

**ADOPTER.**

Concept first-class confirmé.

### Taxonomie

**ADOPTER AVEC CONTRAINTES.**

Taxonomie initiale :

```text
REFINES
DERIVES_FROM
CONSTRAINS
IMPLEMENTS
SATISFIES
VALIDATES
VERIFIED_BY
DECIDED_BY
DEPENDS_ON
AFFECTS
SUPERSEDES
LINKS_TO_CODE
LINKS_TO_TEST
RELATED_TO
```

Contraintes :

- `RELATED_TO` reste faible ;
- aucune transitivité automatique ;
- origin/resolution/confidence restent orthogonaux au type ;
- distinction `IMPLEMENTS / SATISFIES` réévaluable sur cas réels M4 ;
- nouvelle relation publique uniquement via décision contrôlée.

### Store-backed

Le même contrat de traversée est démontré sur mémoire et SQLite.

---

## 7. Knowledge store

### `SpecificationKnowledgeStore`

**ADOPTER.**

Le port est confirmé par deux implémentations expérimentales.

Aucun SQL/Cypher ne doit fuiter dans le domaine.

### Memory store

**ADOPTER** comme référence des tests contractuels.

### SQLite

**ADOPTER AVEC CONTRAINTES** comme backend persistant initial.

Contraintes :

- reste derrière le port ;
- le schéma JSON du spike est rejeté ;
- schéma M1 normalisé et migrable ;
- tests Windows requis ;
- concurrence multi-writer serveur non promise ;
- remplacement possible si une limite mesurée apparaît.

### Graph store

Décision :

```text
NOT_NEEDED_FOR_MVP
```

Le **modèle conceptuel de graphe** reste conservé.

Une graph database ne sera introduite que sur mesure démontrant un besoin.

---

## 8. Snapshots

**ADOPTER.**

Invariant :

```text
before activation -> Vn
 after activation -> Vn+1
```

jamais un mélange observable.

Cycle :

```text
BUILDING
VALIDATING
READY
ACTIVE
FAILED
RETIRED
```

Rétention minimale M0 :

```text
ACTIVE always kept
1 recent RETIRED kept by default
older RETIRED purgeable
```

La politique production reste ajustable selon volume.

Le store doit rester reconstruisible depuis les sources.

---

## 9. Local-first et IA

**ADOPTER.**

Les fonctions M0 ont été conçues sans :

- LLM ;
- embeddings ;
- service cloud ;
- provider réseau obligatoire.

Toute future capacité IA reste opt-in et doit marquer ses résultats dérivés/heuristiques.

---

## 10. Search et contexte

### Lexical search

**ADOPTER** comme capacité MVP déterministe.

Semantic search :

```text
NOT_REQUIRED_FOR_MVP
```

### Compact context

**ADOPTER.**

MORPHEUS fournit une vue compacte spécialisée.

Il ne prend pas en charge :

- ranking global ;
- budget de tokens ;
- fusion multi-engine ;
- compression NEXUS.

---

## 11. Incremental

**ADOPTER** la base :

```text
file fingerprints + provenance + invalidation
```

Un rename exact peut être reconnu par fingerprint univoque.

Un déplacement + modification reste ambigu tant que l'identité E03 ne fournit pas de preuve plus forte.

Watcher :

```text
NOT_REQUIRED_FOR_MVP
```

Réingestion complète = fallback obligatoire.

---

## 12. Diagnostics

**ADOPTER.**

Les erreurs et dégradations utilisent un catalogue structuré.

Les consommateurs automatiques s'appuient sur :

```text
code
severity
details
```

et non sur la chaîne du message humain.

---

## 13. Cross-engine

**ADOPTER.**

`ExternalReference` permet de conserver un lien MINOS/NEXUS/autre sans dépendance de domaine directe.

Résolution optionnelle :

```text
UNVALIDATED
UNRESOLVED
RESOLVED
STALE
```

Une indisponibilité MINOS n'empêche pas MORPHEUS de fonctionner.

---

## 14. Fondation de production

### Langage

```text
Java
```

### Baseline de compatibilité

```text
Java 21 source / bytecode
```

Le JDK de compilation peut être plus récent avec :

```text
--release 21
```

Cette décision aligne MORPHEUS avec la baseline Java déjà utilisée par NEXUS et n'abandonne aucune capacité M0 nécessaire.

### Build

```text
Maven 3.9.16
Maven Wrapper
compiler release = 21
```

Maven 4 sera réévalué après GA.

### Persistance

```text
SQLite via JDBC adapter
```

derrière `SpecificationKnowledgeStore`.

### Frameworks

```text
server framework = none in M1 foundation
DI framework = none required
```

---

## 15. Limite connue de l'environnement de cette validation

L'environnement d'exécution de la revue possède OpenJDK 21, mais pas Maven installé et ne permet pas le téléchargement Maven depuis le shell.

Le Maven Wrapper ne peut donc pas être exécuté ici.

Cette limite devient un **gate obligatoire du bootstrap M1** :

```text
Windows local build using mvnw.cmd
CI build using ./mvnw
Java release 21 verification
```

avant tout développement fonctionnel significatif M1.

---

## 16. ADR — statuts de sortie M0

### Acceptées

```text
ADR-0003 SpecificationKnowledgeStore
ADR-0004 local-first / no mandatory LLM
ADR-0005 traceability first-class
ADR-0006 CURRENT / PROPOSED / HISTORICAL
ADR-0007 cross-engine contracts
ADR-0008 read-first
ADR-0009 stable identity
ADR-0011 capability negotiation
ADR-0012 knowledge snapshots
ADR-0014 defer stack until evidence
ADR-0015 UUIDv7 DomainIdentity
ADR-0016 Java 21 compatibility baseline
```

### Acceptées avec contraintes

```text
ADR-0001 MORPHEUS-owned domain
ADR-0002 OpenSpec reference provider
ADR-0010 traceability taxonomy
ADR-0013 change lifecycle
ADR-0017 Maven build foundation
ADR-0018 SQLite initial persistent store
```

Les contraintes sont décrites dans cette validation et doivent être reportées dans les ADR concernées.

---

## 17. Gates de démarrage M1

Avant la première fonctionnalité M1 significative :

1. créer Maven Wrapper 3.9.16 ;
2. configurer Java release 21 ;
3. exécuter build sur Windows ;
4. exécuter build CI ;
5. ajouter test d'architecture empêchant `domain -> adapters` ;
6. porter les invariants M0 critiques en JUnit ;
7. créer le store mémoire de référence ;
8. créer le schéma SQLite initial versionné ;
9. vérifier le driver SQLite sous Windows ;
10. conserver les fixtures M0 comme corpus de non-régression.

Ces tâches constituent le **bootstrap de fondation M1**, pas une nouvelle phase M0.

---

## 18. Conclusion

> **M0 est validée. MORPHEUS dispose d'une fondation suffisamment justifiée pour entrer en M1.**

Architecture retenue :

```text
Specification source
        ↓
SpecificationProvider
        ↓
Normalization
        ↓
MORPHEUS Domain
        ↓
KnowledgeSnapshot
        ↓
SpecificationKnowledgeStore
    ┌───┴────┐
  Memory   SQLite
        ↓
Query / Search / Traceability / Context
```

Fondation :

```text
Java 21
Maven 3.9.16 + Wrapper
SQLite
UUIDv7
No mandatory graph DB
No mandatory LLM
No application framework in core
```
