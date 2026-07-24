# Cas d'usage — MORPHEUS

Statut : **Proposition C0 — à valider**

Date : 22 juillet 2026

Ce document précise les cas d'usage fonctionnels qui doivent guider le modèle de domaine, les contrats de stockage et les expérimentations M0.

> Les contrats techniques devront être dérivés de ces cas d'usage, et non l'inverse.

---

## 1. Principes de réponse

Toute réponse MORPHEUS destinée à une machine ou un agent doit pouvoir contenir, selon le cas :

```text
result
identity
state
version
provenance
evidence
resolution
traceability
warnings
```

Les résultats doivent être compacts par défaut. Le contenu source complet ne doit être retourné que sur demande explicite.

---

## UC-01 — Lire l'état courant d'une spécification

### Intention

Obtenir la représentation actuellement applicable d'une spécification sans confondre les changements proposés ou archivés.

### Entrée

```text
project
specification key ou identité
```

### Sortie minimale

```text
Specification
Requirements
Constraints
Scenarios
AcceptanceCriteria
Version
Provenance
```

### Règles

- seuls les éléments `CURRENT` sont retournés par défaut ;
- les éléments `PROPOSED` peuvent être demandés explicitement ;
- si l'état courant ne peut pas être reconstruit avec certitude, la réponse expose un diagnostic ;
- la version source utilisée doit être identifiable.

### Priorité

**MVP — critique**.

---

## UC-02 — Rechercher des exigences

### Intention

Trouver les exigences pertinentes à partir d'un identifiant, d'un texte, d'un titre ou de métadonnées.

### Entrées possibles

```text
project
query
filters
limit
```

### Filtres candidats

```text
temporalState
category
priority
specification
change
provider
resolution
```

### Sortie

Liste compacte d'exigences avec :

- identité ;
- titre ;
- extrait ;
- état temporel ;
- version ;
- provenance ;
- score ou mode de correspondance lorsqu'il existe.

### Priorité

**MVP — critique**.

---

## UC-03 — Lire un changement

### Intention

Comprendre précisément ce qu'un changement cherche à modifier et pourquoi.

### Entrée

```text
project
change key ou identité
```

### Sortie

```text
ChangeProposal
Rationale
LifecycleState
SpecificationDeltas
Requirements
Constraints
DesignDecisions
AcceptanceCriteria
ImplementationTasks
Traceability
Provenance
```

### Règles

- le changement reste `PROPOSED` tant que son effet n'est pas promu dans l'état courant ;
- l'état de cycle de vie est distinct de l'état temporel ;
- les éléments absents ou non résolus doivent être signalés.

### Priorité

**MVP — critique**.

---

## UC-04 — Lister les changements

### Intention

Obtenir une vue synthétique des changements connus d'un projet.

### Filtres candidats

```text
lifecycleState
temporalState
createdAfter
updatedAfter
provider
specification
```

### Sortie compacte

```text
id
key
title
lifecycleState
temporalState
updatedAt
coverageWarnings
```

### Priorité

**MVP**.

---

## UC-05 — Obtenir les contraintes applicables

### Intention

Répondre à :

> Quelles règles ne dois-je pas violer pour cette spécification ou ce changement ?

### Entrée

Une portée : projet, spécification, exigence, changement ou tâche.

### Sortie

Contraintes directes et héritées avec chemin explicatif.

### Exemple

```text
Change X
  <- constrained by Project constraint "local-first"
  <- constrained by Specification constraint "no cloud dependency"
```

### Priorité

**MVP**.

---

## UC-06 — Obtenir les décisions de conception applicables

### Intention

Retrouver les décisions qui encadrent une évolution.

### Sortie

- décision ;
- contexte ;
- rationale ;
- conséquences ;
- statut ;
- provenance ;
- relations vers exigences/changements.

### Priorité

**MVP**.

---

## UC-07 — Obtenir les critères d'acceptation

### Intention

Savoir comment prouver qu'une exigence ou un changement est correctement réalisé.

### Entrée

```text
scope = Requirement | ChangeProposal | Specification
```

### Sortie

```text
AcceptanceCriterion
VerificationKind
VerificationStatus
Evidence
Traceability
```

### Règle

L'existence d'un test relié ne signifie pas automatiquement que le critère est `VERIFIED`.

### Priorité

**MVP — critique**.

---

## UC-08 — Obtenir les tâches d'implémentation

### Intention

Connaître le plan de réalisation explicitement lié à un changement.

### Sortie

- tâches ;
- ordre éventuel ;
- dépendances ;
- état ;
- liens vers exigences et critères ;
- références externes éventuelles.

### Non-objectif

MORPHEUS ne calcule pas la capacité d'équipe, les sprints ou l'affectation RH.

### Priorité

**MVP**.

---

## UC-09 — Tracer une exigence

### Intention

Expliquer comment une exigence se propage dans les autres artefacts de connaissance.

### Exemple

```text
Requirement R-17
  -> REFINED_BY Scenario S-3
  -> AFFECTED_BY Change C-12
  -> DECIDED_BY Decision D-4
  -> IMPLEMENTED_BY Task T-8
  -> LINKS_TO_CODE MINOS:Symbol:...
  -> VERIFIED_BY TestReference:...
```

### Entrées

```text
entity
relationTypes?
direction?
maxDepth?
```

### Sortie

Un sous-graphe explicable, avec provenance et résolution de chaque relation.

### Priorité

**MVP — critique**.

---

## UC-10 — Construire le contexte d'un changement

### Intention

Produire une vue compacte et structurée de tout ce qu'un consommateur doit connaître sur l'intention d'un changement.

### Sortie candidate

```text
Change summary
Rationale
Requirements
Constraints
Design decisions
Acceptance criteria
Implementation tasks
Traceability highlights
Unresolved references
Warnings
```

### Important

MORPHEUS sélectionne les informations directement liées à son domaine. La sélection globale multi-sources et le ranking de contexte restent la responsabilité de NEXUS.

### Priorité

**MVP — critique**.

---

## UC-11 — Comparer état courant et changement proposé

### Intention

Expliquer ce qui changerait si une proposition était appliquée.

### Entrée

```text
current specification version
change proposal
```

### Sortie

```text
ADDED
MODIFIED
REMOVED
UNCHANGED
```

pour les exigences, contraintes, scénarios et critères concernés.

### Priorité

**Post-MVP proche / M3-M8**.

---

## UC-12 — Reconstituer l'historique d'une spécification

### Intention

Répondre à :

> Comment cette exigence ou cette spécification a-t-elle évolué ?

### Sortie

Suite de versions ou événements avec liens `SUPERSEDES`, provenance et révisions sources.

### Priorité

**Post-MVP**.

---

## UC-13 — Détecter les lacunes de spécification

### Intention

Identifier des problèmes structuraux, par exemple :

- exigence sans critère d'acceptation ;
- changement sans rationale ;
- tâche sans exigence liée ;
- décision sans portée ;
- lien cassé ;
- changement déclaré terminé avec critères non vérifiés.

### Sortie

Diagnostics explicables, jamais présentés comme des certitudes métier lorsque la règle est heuristique.

### Priorité

**M6 — qualité et couverture**.

---

## UC-14 — Résoudre une référence vers MINOS

### Intention

Relier une intention à un élément de code sans intégrer le domaine MINOS dans MORPHEUS.

### Entrée

```text
ExternalReference(system=MINOS, ...)
```

### Sortie

Référence résolue ou diagnostic :

```text
RESOLVED
PARTIALLY_RESOLVED
UNRESOLVED
```

### Règle

MORPHEUS doit fonctionner même lorsque MINOS est indisponible.

### Priorité

**M12**.

---

## UC-15 — Fournir un paquet de connaissance à NEXUS

### Intention

Exposer à NEXUS des données MORPHEUS structurées, compactes et attribuées.

### Contenu possible

- exigences ;
- contraintes ;
- décisions ;
- changements ;
- critères ;
- chemins de traçabilité ;
- avertissements.

### Non-objectif

MORPHEUS ne décide pas du budget de tokens final ni du ranking entre données MORPHEUS, MINOS, Git, documentation et autres sources.

### Priorité

**M13**.

---

## UC-16 — Vérifier l'état d'un changement pour JARVIS

### Intention

Permettre à JARVIS de demander :

> Quelles étapes sont encore nécessaires avant que ce changement puisse être considéré comme prêt ou terminé ?

### Sortie candidate

```text
lifecycleState
missingArtifacts
unverifiedCriteria
unresolvedLinks
blockingConstraints
nextAllowedTransitions
```

### Règle

MORPHEUS expose les faits et règles de cycle de vie ; JARVIS reste responsable de l'orchestration.

### Priorité

**M14**.

---

# Priorités MVP

Le premier noyau utilisable doit couvrir au minimum :

1. UC-01 — état courant ;
2. UC-02 — recherche d'exigences ;
3. UC-03 — lecture d'un changement ;
4. UC-05 — contraintes ;
5. UC-07 — critères d'acceptation ;
6. UC-09 — traçabilité ;
7. UC-10 — contexte compact d'un changement.

---

# Critères transversaux

Pour les cas d'usage MVP :

- une réponse doit identifier sa version source ;
- une donnée importée doit exposer sa provenance ;
- une relation dérivée doit exposer son origine ;
- les états `CURRENT` et `PROPOSED` ne doivent jamais être fusionnés implicitement ;
- une absence de donnée doit être distinguée d'une donnée explicitement vide ;
- les références externes non résolues restent visibles ;
- les erreurs de provider ne doivent pas être converties silencieusement en absence de résultat ;
- la sortie machine doit être stable, compacte et indépendante d'OpenSpec.