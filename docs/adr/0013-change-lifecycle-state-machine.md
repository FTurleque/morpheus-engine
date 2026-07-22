# ADR-0013 — Modéliser le cycle de vie d'un changement comme machine d'état explicite

- Statut : **Proposée — à valider pendant C0 et M0**
- Date : 22 juillet 2026
- Portée : domaine, validation, providers, orchestration future

---

## 1. Contexte

MORPHEUS doit représenter non seulement le contenu d'un changement, mais aussi sa progression.

Un simple champ libre ou un statut directement copié depuis un provider est insuffisant pour :

- savoir si un changement est prêt à être implémenté ;
- déterminer quelles informations manquent ;
- valider une transition ;
- expliquer pourquoi une transition est bloquée ;
- exposer un état cohérent à JARVIS ;
- comparer plusieurs providers ;
- conserver l'historique d'un changement.

Les providers peuvent utiliser des vocabulaires différents et des cycles de vie plus ou moins détaillés.

---

## 2. Problème

MORPHEUS doit disposer d'un cycle de vie normalisé sans :

- devenir un gestionnaire de workflow généraliste ;
- imposer qu'un provider supporte toutes les étapes ;
- confondre progression, état temporel et vérification ;
- rendre impossible la représentation d'un workflow plus simple ou plus riche.

---

## 3. Forces en présence

### Cohérence

Les consommateurs doivent interpréter les mêmes états de la même manière.

### Interopérabilité

Les providers doivent pouvoir mapper leurs statuts vers un modèle commun.

### Flexibilité

Tous les changements ne nécessitent pas forcément une phase de design séparée.

### Explicabilité

Une transition doit pouvoir indiquer ses préconditions et ses blocages.

### Read-first

MORPHEUS doit pouvoir observer un workflow sans posséder le droit de le modifier.

### Orchestration future

JARVIS pourra utiliser ces faits, mais MORPHEUS ne doit pas orchestrer à sa place.

---

## 4. Décision proposée

Adopter une machine d'état normalisée pour `ChangeProposal` avec les états candidats :

```text
DRAFT
PROPOSED
SPECIFIED
DESIGNED
PLANNED
IMPLEMENTING
VERIFYING
COMPLETED
ARCHIVED
ABANDONED
```

Le cycle nominal est :

```text
DRAFT
  ↓
PROPOSED
  ↓
SPECIFIED
  ↓
DESIGNED
  ↓
PLANNED
  ↓
IMPLEMENTING
  ↓
VERIFYING
  ↓
COMPLETED
  ↓
ARCHIVED
```

`ABANDONED` est un état terminal métier accessible depuis plusieurs étapes.

---

## 5. Dimensions orthogonales

Cette machine d'état ne remplace pas :

### État temporel

```text
CURRENT
PROPOSED
HISTORICAL
```

### Résolution

```text
RESOLVED
PARTIALLY_RESOLVED
UNRESOLVED
HEURISTIC
```

### Vérification

```text
NOT_VERIFIED
PARTIALLY_VERIFIED
VERIFIED
FAILED
UNKNOWN
```

### Snapshot technique

```text
BUILDING
VALIDATING
READY
ACTIVE
FAILED
RETIRED
```

Chaque dimension répond à une question différente.

---

## 6. Sémantique des états

### `DRAFT`

Intention incomplète ou en formulation.

### `PROPOSED`

Proposition explicite pouvant encore manquer de spécification détaillée.

### `SPECIFIED`

Exigences, contraintes et critères nécessaires suffisamment définis pour concevoir la solution.

### `DESIGNED`

Décisions de conception nécessaires traitées ou absence de design séparé explicitement justifiée.

### `PLANNED`

Travail suffisamment découpé pour commencer l'exécution.

### `IMPLEMENTING`

Réalisation effectivement commencée selon une source explicite.

### `VERIFYING`

Réalisation confrontée aux critères d'acceptation.

### `COMPLETED`

Changement considéré réalisé et vérifié selon les règles applicables.

### `ARCHIVED`

Changement terminé conservé comme historique.

### `ABANDONED`

Changement explicitement non poursuivi dans son état actuel.

---

## 7. Transitions

Les transitions nominales sont contrôlées.

MORPHEUS doit pouvoir répondre :

```text
canTransition(change, targetState)
validateTransition(change, targetState)
getBlockingConditions(change, targetState)
```

Ces opérations restent conceptuelles pendant C0.

Une transition peut être :

```text
ALLOWED
ALLOWED_WITH_WARNINGS
BLOCKED
UNKNOWN
```

---

## 8. Transitions arrière

Certaines transitions arrière sont légitimes :

```text
SPECIFIED -> PROPOSED
DESIGNED -> SPECIFIED
PLANNED -> DESIGNED
IMPLEMENTING -> PLANNED
VERIFYING -> IMPLEMENTING
COMPLETED -> VERIFYING  (exceptionnelle)
```

Elles ne doivent pas être traitées comme des erreurs techniques.

Elles représentent une révision de l'intention ou de la réalisation.

---

## 9. Étapes facultatives

Le modèle normalisé contient `DESIGNED`, mais un changement simple peut ne pas nécessiter de document de design distinct.

Deux stratégies restent possibles :

### A. Transition explicite rapide

```text
SPECIFIED -> DESIGNED -> PLANNED
```

avec une décision « no separate design required ».

### B. Transition autorisée conditionnelle

```text
SPECIFIED -> PLANNED
```

si une politique indique que le design séparé est non requis.

M0 doit déterminer laquelle produit le meilleur compromis entre rigueur et bruit.

---

## 10. Mapping provider

Le provider conserve son état source :

```text
providerState
```

et produit :

```text
normalizedLifecycleState
mappingResolution
```

Exemple :

```text
providerState = "active"
normalized = IMPLEMENTING
resolution = PARTIALLY_RESOLVED
```

si le mot `active` ne garantit pas exactement la même sémantique.

Le mapping doit être testable et documenté.

---

## 11. Absence de statut provider

Si la source ne fournit pas de statut explicite, MORPHEUS peut :

1. utiliser la structure du format si elle fournit une correspondance déterministe ;
2. dériver un état avec provenance `DERIVED` ;
3. retourner un état partiellement résolu ;
4. ne pas inventer un état si aucune preuve suffisante n'existe.

Un simple emplacement de fichier ne doit être interprété comme statut que si le provider documente cette convention.

---

## 12. Conditions de transition

Exemples candidats :

### `PROPOSED -> SPECIFIED`

Vérifier :

- exigences identifiables ;
- contraintes critiques connues ;
- critères d'acceptation minimaux pour les exigences critiques.

### `SPECIFIED -> DESIGNED`

Vérifier :

- ambiguïtés bloquantes résolues ;
- décisions structurantes identifiées.

### `PLANNED -> IMPLEMENTING`

Vérifier :

- plan/tâches suffisants selon la politique ;
- aucun bloqueur connu.

### `VERIFYING -> COMPLETED`

Vérifier par défaut :

- aucun critère bloquant en `FAILED` ;
- aucun critère bloquant non vérifié ;
- exceptions explicitement justifiées.

Les règles exactes pourront être configurées par politique.

---

## 13. Observation vs commande

Le cœur read-only observe :

```text
source changed -> provider maps -> lifecycle state observed
```

Une future écriture devra suivre :

```text
request transition
  ↓
validate MORPHEUS rules
  ↓
check provider capability
  ↓
write source
  ↓
re-read / confirm
  ↓
new observed state
```

MORPHEUS ne doit jamais considérer une mutation réussie uniquement parce qu'une API d'écriture a renvoyé `200 OK` ; l'état doit être confirmable dans la source ou par une preuve adaptée.

---

## 14. Événements de transition

Le moteur devrait pouvoir conserver un historique léger :

```text
ChangeStateTransition
- changeId
- fromState
- toState
- observedAt
- reason?
- sourceRevision?
- provenance
```

Cette structure est distincte d'un event sourcing complet.

---

## 15. Abandon

`ABANDONED` doit conserver une raison structurée lorsque disponible :

```text
REJECTED
OBSOLETE
DUPLICATE
NOT_FEASIBLE
NO_LONGER_NEEDED
SUPERSEDED_BY_OTHER_CHANGE
UNKNOWN
```

Un changement abandonné peut être relié au changement qui le remplace via `SUPERSEDES` ou une relation dédiée si nécessaire.

---

## 16. Promotion vers l'état courant

`COMPLETED` ne signifie pas automatiquement que tous les deltas du changement sont déjà représentés comme `CURRENT`.

Deux événements peuvent être distincts :

```text
implementation completed
specification baseline promoted
```

Cette distinction est nécessaire lorsque le provider archive un changement et met à jour les specs courantes dans des opérations séparées.

M0 doit observer le comportement du provider de référence avant de figer la règle finale.

---

## 17. Conséquences positives

- sémantique de statut stable ;
- meilleur diagnostic des changements incomplets ;
- compatibilité multi-provider ;
- préparation de JARVIS sans couplage ;
- capacité de vérifier les préconditions ;
- historique explicable ;
- meilleure qualité de contexte pour les agents.

---

## 18. Conséquences négatives

- davantage d'états à comprendre ;
- mapping provider potentiellement imparfait ;
- politiques nécessaires pour les étapes facultatives ;
- risques de rigidité si les transitions sont surcontraintes ;
- tests de workflow supplémentaires.

---

## 19. Alternatives étudiées

### A. Statut libre fourni par le provider

**Rejetée comme contrat public.**

Impossible à interpréter uniformément.

### B. Trois états `OPEN / DONE / ARCHIVED`

**Rejetée.**

Trop pauvre pour distinguer spécification, design, implémentation et vérification.

### C. Workflow complet configurable sans état canonique

**Différée.**

Trop complexe pour le MVP et difficile à consommer par les agents.

### D. Machine d'état canonique avec mapping provider

**Retenue.**

Elle fournit un vocabulaire commun tout en conservant la provenance de la source.

---

## 20. Risques et mitigations

### Risque — modèle trop bureaucratique

Mitigation : étapes facultatives/politiques explicites et validation sur projets réels.

### Risque — mapping OpenSpec artificiel

Mitigation : M0 doit tester la fidélité, et l'ADR peut être revue avant acceptation.

### Risque — JARVIS dépend du workflow exact

Mitigation : exposer faits, transitions autorisées et blocages ; JARVIS ne doit pas coder en dur des détails provider.

### Risque — `COMPLETED` trompeur

Mitigation : conserver `VerificationStatus` et `TemporalState` séparés.

---

## 21. Validation M0

Les fixtures doivent couvrir :

- cycle nominal ;
- étape facultative de design ;
- retour arrière ;
- abandon ;
- réouverture ;
- état source inconnu ;
- critères non vérifiés ;
- changement archivé ;
- promotion current séparée.

Comparer :

```text
provider state
expected normalized state
actual normalized state
resolution
reason
```

---

## 22. Critères d'acceptation

Cette ADR peut passer à **Acceptée** lorsque :

1. le provider de référence peut mapper ses états/conventions sans perte critique ;
2. les transitions MVP sont définies ;
3. la stratégie des étapes facultatives est tranchée ;
4. `COMPLETED` et promotion `CURRENT` sont clairement distingués ;
5. les règles de blocage des transitions critiques sont spécifiées ;
6. les changements abandonnés et réouverts sont représentables ;
7. les contrats publics restent indépendants des statuts provider-specific.

---

## 23. Impact sur les autres décisions

Cette ADR influence :

- modèle `ChangeProposal` ;
- UC-03, UC-04, UC-16 ;
- qualité/coverage ;
- provider capabilities ;
- écriture future ;
- JARVIS ;
- NEXUS ;
- snapshots et historique.