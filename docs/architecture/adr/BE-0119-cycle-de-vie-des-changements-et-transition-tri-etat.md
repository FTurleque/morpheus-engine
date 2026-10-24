---
id: BE-0119
title: "Évaluer le cycle de vie d'un changement par une machine d'état canonique et une transition tri-état"
domain: backend
rules: false
---

# Évaluer le cycle de vie d'un changement par une machine d'état canonique et une transition tri-état

- Statut : **Acceptée**
- Date : 2026-10-24
- Remplace : ADR-0013, ADR-0032, ADR-0078

## Context

MORPHEUS doit représenter la progression d'un changement, pas seulement son contenu. Un champ libre ou un statut
recopié d'un provider ne permet ni de savoir si un changement est prêt à être implémenté, ni de dire ce qui manque, ni
d'expliquer pourquoi une transition est bloquée, ni d'exposer un état cohérent à JARVIS ou de comparer des providers.
Les providers emploient des vocabulaires différents et des cycles plus ou moins détaillés.

Le cycle normalisé ne doit pas faire de MORPHEUS un gestionnaire de workflow généraliste, imposer à un provider toutes
les étapes, confondre progression, état temporel et vérification, ni interdire un workflow plus simple. MORPHEUS
observe un workflow sans posséder le droit de le modifier ; JARVIS peut consommer ces faits, MORPHEUS n'orchestre pas
à sa place.

Plusieurs faits nécessaires à la machine d'état ne sont que partiellement observables dans un snapshot publié et
valent `TRUE`, `FALSE` ou `UNAVAILABLE`. Convertir `UNAVAILABLE` en `false` produirait un faux blocage ; déduire un
état de cycle de vie des tâches, des chemins ou des archives inventerait un fait.

Options écartées :

- **statut libre fourni par le provider** comme contrat public : impossible à interpréter uniformément ;
- **trois états `OPEN / DONE / ARCHIVED`** : trop pauvres pour distinguer spécification, design, implémentation et
  vérification ;
- **workflow entièrement configurable sans état canonique** : trop complexe et difficile à consommer par des agents.

L'option retenue est une machine d'état canonique, évaluée en lecture seule, dont les faits manquants restent
explicites.

## Decision

### États canoniques

`ChangeLifecycleState` compte exactement dix états :

| État | Sens |
|---|---|
| `DRAFT` | intention incomplète ou en formulation |
| `PROPOSED` | proposition explicite pouvant encore manquer de spécification détaillée |
| `SPECIFIED` | exigences, contraintes et critères suffisamment définis pour concevoir la solution |
| `DESIGNED` | décisions de conception nécessaires traitées |
| `PLANNED` | travail suffisamment découpé pour commencer l'exécution |
| `IMPLEMENTING` | réalisation effectivement commencée selon une source explicite |
| `VERIFYING` | réalisation confrontée aux critères d'acceptation |
| `COMPLETED` | changement réalisé et vérifié selon les règles applicables |
| `ARCHIVED` | changement terminé conservé comme historique |
| `ABANDONED` | changement explicitement non poursuivi |

Le cycle nominal est `DRAFT → PROPOSED → SPECIFIED → DESIGNED → PLANNED → IMPLEMENTING → VERIFYING → COMPLETED →
ARCHIVED`.

`ChangeLifecycle` porte un `ChangeId`, un état et une `ChangeAbandonmentReason` optionnelle. `ABANDONED` exige une
raison, et une raison est refusée hors de `ABANDONED`. Les raisons sont `REJECTED`, `OBSOLETE`, `DUPLICATE`,
`NOT_FEASIBLE`, `NO_LONGER_NEEDED`, `SUPERSEDED_BY_OTHER_CHANGE` et `UNKNOWN`. Un changement abandonné peut être relié
au changement qui le remplace par `SUPERSEDES` (ADR-0118). `ChangeProposal` ne porte pas le cycle de vie.

### Dimensions séparées

```text
ChangeLifecycleState != TemporalState
ChangeLifecycleState != KnowledgeSnapshotState
ChangeLifecycleState != task checkbox
COMPLETED != CURRENT
ARCHIVED  != CURRENT
```

Le cycle de vie ne remplace ni l'état temporel (ADR-0136), ni l'état technique du snapshot (ADR-0143), ni la
résolution (ADR-0118), ni la vérification (ADR-0081). Une transition ne modifie jamais une projection temporelle.
`COMPLETED` ne signifie pas que les deltas du changement sont devenus `CURRENT` : achèvement de l'implémentation et
promotion de la baseline sont deux événements distincts (ADR-0035). Une tâche marquée `completed` ne fait pas avancer le
cycle de vie.

### Machine d'état

`ChangeLifecycleStateMachine` (paquet `com.morpheus.application.lifecycle`) répond à « la transition est-elle
possible ? » et « pourquoi est-elle bloquée ? » à partir d'une `ChangeLifecycleTransitionRequest` : cycle source, état
cible, `ChangeLifecycleFacts`, `ChangeLifecyclePolicy` et raison d'abandon éventuelle. Elle rend une
`ChangeLifecycleTransitionDecision` autorisée, ou bloquée avec des `ChangeLifecycleBlocker` nommés. Elle ne modifie
aucune source et n'est pas un orchestrateur.

Préconditions des transitions nominales :

| Transition | Bloquée quand |
|---|---|
| `DRAFT → PROPOSED` | jamais |
| `PROPOSED → SPECIFIED` | exigences non identifiées, contraintes critiques inconnues ou critères d'acceptation non définis |
| `SPECIFIED → DESIGNED` | design requis et décisions de design indisponibles |
| `SPECIFIED → PLANNED` | design requis, ou plan absent |
| `DESIGNED → PLANNED` | plan absent |
| `PLANNED → IMPLEMENTING` | bloqueur connu |
| `IMPLEMENTING → VERIFYING` | jamais |
| `VERIFYING → COMPLETED` | critère d'acceptation bloquant en échec ou non vérifié |
| `COMPLETED → ARCHIVED` | jamais |

L'étape de design est facultative par une transition conditionnelle : `SPECIFIED → PLANNED` n'est possible que si le
design n'est pas requis et qu'un plan existe.

Les retours arrière canoniques sont `SPECIFIED → PROPOSED`, `DESIGNED → SPECIFIED`, `PLANNED → DESIGNED`,
`IMPLEMENTING → PLANNED`, `VERIFYING → IMPLEMENTING` et `COMPLETED → VERIFYING`. Ils représentent une révision, pas une
erreur, et ne sont autorisés que si la politique active `allowBackwardTransitions` ; `COMPLETED → VERIFYING` exige en
plus `allowCompletedReopen`. Les deux sont désactivés par défaut (`ChangeLifecyclePolicy.forwardOnly()`).

Tout état de `DRAFT` à `VERIFYING` peut passer à `ABANDONED` avec une raison. La seule réouverture est
`ABANDONED → PROPOSED`. `ARCHIVED` n'est jamais rouvert. Toute autre transition, y compris vers l'état courant, est
refusée comme `INVALID_TRANSITION`.

### Provenance de l'état et mapping provider

Le statut propre à un provider n'est jamais le contrat public. Aucune transition ni aucun état n'est déduit d'un
provider, d'un chemin, d'une archive, d'un horodatage ou d'une tâche : un emplacement de fichier ne vaut statut que si
le provider documente cette convention, et MORPHEUS n'invente pas d'état sans preuve suffisante. Un mapping provider,
lorsqu'il existe, conserve l'état source et produit l'état normalisé avec sa résolution ; il est documenté et testé.

L'évaluation d'une transition reçoit l'état comme une `ChangeLifecycleObservation` :

```text
état absent  -> UNAVAILABLE      (source = UNAVAILABLE)
état fourni  -> état canonique   (source = CALLER_SUPPLIED)
```

Les raisons d'abandon source et cible sont transmises séparément par l'appelant et ne sont jamais inventées.

### Évaluation tri-état

`ChangeTransitionEvaluationService` évalue une transition en lecture seule et rend un
`ChangeTransitionEvaluationState` :

| Résultat | Sens |
|---|---|
| `ALLOWED` | tous les faits requis sont observables et la machine autorise |
| `BLOCKED` | tous les faits requis sont observables et la machine bloque, ou une contrainte bloquante s'applique |
| `UNKNOWN` | au moins un fait requis vaut `UNAVAILABLE`, ou la sémantique bloquante d'une contrainte est inconnue |
| `REQUIRES_INPUT` | une information que l'appelant doit fournir manque, comme la raison d'un abandon |

Quand tous les faits requis sont connus, la décision délègue à `ChangeLifecycleStateMachine` au lieu de dupliquer ses
règles. `UNAVAILABLE` n'est jamais converti en `false` et `UNKNOWN` n'est jamais converti en `BLOCKED` ni en `ALLOWED`.
La sémantique des contraintes bloquantes relève d'ADR-0082 ; les faits de complétude, d'ADR-0124. L'évaluation ne
persiste rien. Une écriture de transition passe par le chemin contrôlé d'ADR-0083 : une décision `ALLOWED` ne vaut
jamais mutation, et une mutation n'est tenue pour réussie que confirmée par la source ou une preuve adaptée.

### État d'orchestration d'un changement

`ChangeOrchestrationStateService` (paquet `com.morpheus.application.orchestration`) agrège en lecture seule l'état
d'orchestration d'un changement, `ChangeOrchestrationState`, depuis le seul snapshot `ACTIVE` de son projet. Cette vue
est une observation : elle n'est jamais persistée comme état métier (le constructeur de `ChangeOrchestrationState`
refuse `persisted=true`), et la construire ne mute pas le snapshot ni ne déclenche de résolution live. Un artefact n'y
est déclaré manquant que si le fait correspondant vaut `FALSE` ; un fait `UNAVAILABLE` y figure comme indisponible,
jamais comme manquant. Ses transitions sont celles de l'évaluation tri-état ci-dessus, depuis l'état fourni par
l'appelant. Les champs de la vue et leur comportement sont spécifiés par
[`change-orchestration-state`](../../../openspec/specs/change-orchestration-state/spec.md).

## Do's and Don'ts

### Do

- Exprimer tout état de cycle de vie par une des dix valeurs de `ChangeLifecycleState`.
- Exiger une `ChangeAbandonmentReason` pour `ABANDONED`, et la refuser pour tout autre état.
- Faire passer toute décision de transition par `ChangeLifecycleStateMachine`.
- Nommer chaque blocage par un `ChangeLifecycleBlocker`.
- Activer explicitement `allowBackwardTransitions`, et `allowCompletedReopen` pour rouvrir un changement terminé.
- Rendre `UNKNOWN` dès qu'un fait requis ou la sémantique d'une contrainte est indisponible.
- Rendre `REQUIRES_INPUT` quand l'abandon demandé n'a pas de raison.
- Marquer la provenance de l'état observé (`CALLER_SUPPLIED` ou `UNAVAILABLE`).
- Construire l'état d'orchestration depuis le seul snapshot `ACTIVE`, et n'y déclarer manquant qu'un artefact dont le
  fait vaut `FALSE`.

### Don't

- Ne pas exposer un statut provider brut comme état public.
- Ne pas déduire un état de cycle de vie d'un chemin, d'une archive, d'un horodatage ou d'une tâche cochée.
- Ne pas modifier `TemporalState` lors d'une transition, ni assimiler `COMPLETED` ou `ARCHIVED` à `CURRENT`.
- Ne pas convertir `UNAVAILABLE` en `false`, ni `UNKNOWN` en `BLOCKED` ou en `ALLOWED`.
- Ne pas rouvrir `ARCHIVED`, ni rouvrir `ABANDONED` ailleurs que vers `PROPOSED`.
- Ne pas autoriser un retour arrière sans politique explicite.
- Ne pas inventer une raison d'abandon.
- Ne pas persister quoi que ce soit lors d'une évaluation de transition.
- Ne pas persister l'état d'orchestration, ni muter le snapshot ou déclencher une résolution live en le construisant.

## Consequences

### Positives

- Sémantique de statut stable, partagée par tous les providers et contrats publics.
- Diagnostic des changements incomplets et explication des blocages.
- JARVIS consomme des faits, des transitions autorisées et des blocages sans couplage au workflow d'un provider.
- Aucun faux blocage : un fait manquant reste visible comme tel.

### Négatives

- Davantage d'états à comprendre, et des politiques à fournir pour les étapes facultatives et les retours arrière.
- Un mapping provider peut être imparfait.
- Tant qu'aucun état n'est fourni, l'évaluation ne peut conclure qu'à `UNKNOWN`.

### Risques

- Modèle trop bureaucratique : l'étape de design reste facultative et les retours arrière sont réglés par politique.
- `COMPLETED` trompeur : vérification et état temporel restent des dimensions séparées.
- JARVIS dépendant du workflow exact : il ne code pas en dur de détail provider.

## Compliance and Enforcement

- `morpheus-domain/src/test/java/com/morpheus/domain/change/lifecycle/ChangeLifecycleTest.java` : exactement dix
  états, raison d'abandon exigée pour `ABANDONED` et refusée ailleurs, identité logique stable.
- `morpheus-application/src/test/java/com/morpheus/application/lifecycle/ChangeLifecycleStateMachineTest.java` : cycle
  nominal, préconditions, design facultatif, bloqueur connu, critères bloquants, retours arrière gouvernés par
  politique, réouverture exceptionnelle, abandon avec raison, réouverture depuis `ABANDONED`, `ARCHIVED` jamais rouvert,
  `TemporalState` non promu par `COMPLETED` ni par une tâche terminée.
- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/JarvisOrchestrationContractTest.java` : état
  jamais inféré, contrainte inconnue jamais convertie en `BLOCKED` ni en `ALLOWED` silencieux, abandon sans raison en
  `REQUIRES_INPUT`.
- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/ChangeLifecycleQualityContractTest.java` : faits
  tri-état identiques sur les deux stores, transition non évaluée quand un fait requis manque, aucune inférence depuis
  une tâche terminée.
- `morpheus-api/src/test/java/com/morpheus/api/MorpheusJarvisOrchestrationApiContractTest.java` : contrat HTTP de
  l'état d'orchestration.
- Constructeurs compacts de `ChangeLifecycle` et `ChangeLifecycleObservation` : rejets à la construction ; constructeur
  compact de `ChangeOrchestrationState` : refus de `persisted=true`.
- Revue : aucun provider n'expose son statut brut comme état public ; l'évaluation ne persiste rien.

## References

- [ADR-0136](ARCH-0136-etat-temporel-et-occurrences-versionnees.md) — distinction courant / proposé / historique.
- [ADR-0136](ARCH-0136-etat-temporel-et-occurrences-versionnees.md) — projection temporelle explicite.
- [ADR-0035](BE-0035-explicit-requirement-delta-application-and-promotion.md) — application et promotion des deltas.
- [ADR-0139](ARCH-0139-frontiere-morpheus-jarvis-en-lecture-seule.md) — frontière d'orchestration read-only
  avec JARVIS.
- [ADR-0081](DATA-0081-first-class-acceptance-verification-evidence.md) — vérification des critères d'acceptation.
- [ADR-0082](DATA-0082-explicit-constraint-semantics-and-blocking-policy.md) — sémantique des contraintes bloquantes.
- [ADR-0083](BE-0083-controlled-lifecycle-write-operations.md) — mutations lifecycle contrôlées.
- [ADR-0118](ARCH-0118-tracabilite-typee-et-taxonomie-controlee.md) — traçabilité typée.
- [ADR-0143](DATA-0143-snapshots-versionnes-et-concurrence-bornee.md) — snapshots versionnés.
- [ADR-0121](ARCH-0121-modele-normalise-neutre-vis-a-vis-des-providers.md) — modèle normalisé.
- [ADR-0124](ARCH-0124-qualite-findings-et-faits-tri-etat.md) — faits de qualité tri-état.
- [ADR-0142](DATA-0142-references-externes.md) — références externes du snapshot.
- [`change-orchestration-state`](../../../openspec/specs/change-orchestration-state/spec.md) — champs et comportement
  de l'état d'orchestration.
- [VALIDATION_M0](../../project/validation/VALIDATION_M0.md) — preuve du jalon M0.
- [VALIDATION_M3](../../project/validation/VALIDATION_M3.md) — preuve du jalon M3.
- [VALIDATION_M14](../../project/validation/VALIDATION_M14.md) — preuve du jalon M14.
