# Matrice d'expérimentation — M0 MORPHEUS

Statut : **Proposition C0 — à valider avant démarrage de M0**

Date : 22 juillet 2026

Cette matrice transforme les hypothèses d'architecture en expériences mesurables.

> Une expérimentation M0 n'est pas un mini-produit. Elle existe pour produire une preuve permettant de décider.

---

## 1. Décisions à éclairer

M0 doit permettre de trancher ou confirmer :

1. OpenSpec comme premier provider de référence ;
2. indépendance réelle du domaine MORPHEUS ;
3. stratégie d'identité stable ;
4. représentation current/proposed/historical ;
5. granularité des versions et snapshots ;
6. taxonomie de traçabilité ;
7. contrat `SpecificationKnowledgeStore` ;
8. backend persistant initial ;
9. capacités minimales du provider ;
10. performances compatibles avec un outil local-first.

Décisions possibles pour chaque hypothèse :

```text
ADOPTER
ADOPTER_AVEC_CONTRAINTES
REVOIR
REMPLACER
```

---

## 2. Jeux de données de référence

M0 doit utiliser plusieurs fixtures, pas un seul projet idéal.

### D1 — Minimal valide

- 1 spécification ;
- 3 exigences ;
- 2 scénarios ;
- 1 changement ;
- 3 tâches ;
- 2 critères d'acceptation.

Objectif : happy path et lisibilité des mappings.

### D2 — Multi-spécifications

- plusieurs domaines fonctionnels ;
- contraintes globales et locales ;
- changements touchant plusieurs specs ;
- décisions transverses.

Objectif : portée et traçabilité.

### D3 — Historique

- changement proposé ;
- changement terminé ;
- archive ;
- exigence remplacée ;
- renommage ;
- suppression.

Objectif : identité, versionnement et historique.

### D4 — Invalide / partiel

- fichier manquant ;
- clé dupliquée ;
- référence cassée ;
- document partiellement invalide ;
- version de format inconnue.

Objectif : diagnostics et robustesse.

### D5 — Volume

Plusieurs paliers générés :

```text
100 entités
1 000 entités
10 000 entités
100 000 entités si réaliste
```

avec plusieurs densités de liens.

Objectif : performance et stockage.

### D6 — Projet réel

Au moins un vrai repository utilisant OpenSpec ou un jeu réaliste dérivé manuellement des conventions supportées.

Objectif : éviter une architecture optimisée uniquement pour les fixtures.

---

# E01 — Détection d'une source OpenSpec

## Hypothèse

Un provider peut identifier de manière fiable une source OpenSpec et sa version de format sans coupler le cœur MORPHEUS à OpenSpec.

## Mesures

- taux de détection correcte ;
- faux positifs ;
- version détectée ;
- diagnostics ;
- temps de probe.

## Succès

- D1/D2/D3/D6 reconnus ;
- source non OpenSpec refusée proprement ;
- D4 produit un diagnostic explicite ;
- aucun type OpenSpec dans les contrats publics MORPHEUS.

## Décision éclairée

ADR provider de référence + capability negotiation.

---

# E02 — Mapping vers le domaine normalisé

## Hypothèse

Les artefacts utiles peuvent être représentés par les concepts MORPHEUS sans perte critique ni fuite du format source.

## Vérifier

- Specification ;
- Requirement ;
- Scenario ;
- Constraint ;
- ChangeProposal ;
- DesignDecision ;
- AcceptanceCriterion ;
- ImplementationTask ;
- Evidence ;
- TraceabilityLink.

## Mesures

- taux d'artefacts mappés ;
- artefacts non représentables ;
- métadonnées provider-only nécessaires ;
- ambiguïtés ;
- diagnostics.

## Succès

Au moins 95 % des artefacts des datasets D1-D3/D6 doivent être représentables sans modifier le domaine pour chaque particularité de fichier.

Les 5 % restants doivent pouvoir être conservés en métadonnées ou signalés explicitement sans casser l'ingestion.

---

# E03 — Identité stable

## Hypothèse

Une stratégie d'identité peut distinguer identité logique, version et emplacement de source.

## Scénarios

1. contenu inchangé, fichier déplacé ;
2. titre modifié ;
3. clé externe stable, texte modifié ;
4. clé externe changée mais contenu fortement similaire ;
5. duplication d'une exigence ;
6. suppression puis réapparition ;
7. archivage ;
8. deux providers produisant la même clé.

## Mesures

- faux maintien d'identité ;
- faux changement d'identité ;
- collisions ;
- cas ambigus ;
- besoin d'intervention explicite.

## Succès

Aucune stratégie ne sera acceptée si elle utilise uniquement le chemin source comme identité.

Le système doit pouvoir signaler `AMBIGUOUS` plutôt que fusionner silencieusement.

---

# E04 — Reconstruction de l'état courant

## Hypothèse

MORPHEUS peut séparer l'état courant des changements proposés et de l'historique.

## Scénarios

- spec courante sans changement ;
- changement modifiant une exigence ;
- changement supprimant une exigence ;
- changement abandonné ;
- changement archivé après application ;
- deux changements concurrents.

## Succès

Une requête `CURRENT` ne retourne jamais une modification seulement proposée.

Une requête de changement doit pouvoir présenter le delta sans modifier l'état courant.

---

# E05 — Versionnement et snapshot

## Hypothèse

Un snapshot logique permet de publier atomiquement un nouvel état sans fuite d'état partiel.

## Scénario

- ingérer V1 ;
- construire V2 ;
- simuler interruption au milieu de V2 ;
- vérifier que V1 reste l'état courant ;
- republier V2 ;
- vérifier l'idempotence ;
- comparer V1/V2.

## Mesures

- temps de publication ;
- coût disque ;
- coût de duplication ;
- cohérence ;
- temps de rollback logique.

---

# E06 — Traçabilité

## Hypothèse

Le modèle `TraceabilityLink` suffit pour les parcours prioritaires sans imposer un backend graphe.

## Requêtes

```text
Requirement -> AcceptanceCriterion
Requirement -> Change -> Task
Change -> Decision -> Constraint
Requirement -> ... -> ExternalReference
incoming/outgoing depth 1
depth 3
shortest explanatory path
```

## Mesures

- latence ;
- complexité de requête ;
- volume de liens ;
- coût de stockage ;
- capacité à restituer provenance/evidence.

## Décision éclairée

Backend relationnel/documentaire/graphe/hybride.

---

# E07 — Backend mémoire

## Hypothèse

Le contrat du store peut être implémenté sans dépendance infrastructurelle.

## Succès

Le backend mémoire doit passer 100 % des tests de contrat fonctionnels obligatoires :

```text
snapshot idempotency
current/proposed isolation
identity uniqueness
version activation
traceability traversal
unresolved reference preservation
historical retention
provenance preservation
```

Cette expérience valide principalement l'abstraction, pas la performance.

---

# E08 — Backend persistant simple

## Hypothèse

Un backend local embarqué peut satisfaire le MVP sans infrastructure externe obligatoire.

## Candidats

Au moins un candidat embarqué devra être testé. SQLite est un candidat naturel mais n'est pas décidé par avance.

## Mesures

- installation ;
- démarrage ;
- ingestion ;
- recherche ;
- traversée ;
- stockage ;
- sauvegarde ;
- reconstruction ;
- portabilité Windows/Linux/macOS.

## Succès

Le backend ne doit pas imposer de service séparé pour le fonctionnement de base si une solution embarquée satisfait les besoins.

---

# E09 — Option graph store

## Déclenchement

Cette expérience n'est menée que si E06/E08 montrent une limite significative du backend simple.

## Question

Un graph store apporte-t-il un gain suffisamment important pour justifier :

- une seconde technologie ;
- un service supplémentaire éventuel ;
- plus de distribution et maintenance ;
- une synchronisation supplémentaire ?

## Règle de décision

Pas d'adoption sur élégance architecturale seule. Gain mesurable obligatoire.

---

# E10 — Recherche lexicale

## Hypothèse

Une recherche textuelle simple suffit au MVP avant toute recherche vectorielle.

## Mesures

- exact key lookup ;
- title search ;
- statement search ;
- filtres ;
- latence ;
- pertinence sur D6.

## Succès

Le MVP doit rester utile sans embeddings.

---

# E11 — Ingestion incrémentale

## Hypothèse

La source peut être resynchronisée sans reconstruction complète dans les cas simples.

## Scénarios

```text
ADD
MODIFY
DELETE
MOVE
ARCHIVE
FORMAT_VERSION_CHANGE
```

## Mesures

- fichiers relus ;
- entités recalculées ;
- invalidations ;
- temps vs full rebuild ;
- cohérence finale.

## Règle

Si la fiabilité de l'incrémental est insuffisante, full rebuild reste le fallback officiel.

---

# E12 — Diagnostics

## Hypothèse

MORPHEUS peut distinguer absence d'information, erreur de format, référence cassée et capacité non supportée.

## Succès

Les datasets D4 doivent produire des diagnostics stables et testables sans transformer les erreurs en collections vides.

---

# E13 — Compact context

## Hypothèse

MORPHEUS peut fournir une vue de changement exploitable par un agent sans renvoyer tous les fichiers de spécification.

## Mesures

- taille JSON ;
- nombre d'entités ;
- informations obligatoires présentes ;
- capacité à approfondir via identités ;
- lisibilité machine.

## Objectif

Démontrer un gain net de contexte par rapport à la lecture brute des documents source.

---

# E14 — Cross-engine reference

## Statut

Exploration préparatoire uniquement pendant M0 ; intégration réelle différée.

## Vérifier

Le domaine doit pouvoir stocker :

```text
ExternalReference(system=MINOS, resourceType=SYMBOL, externalId=...)
```

sans dépendre d'une bibliothèque MINOS.

---

# 3. Tableau de synthèse

| Expérience | Hypothèse principale | Dataset | Décision |
|---|---|---|---|
| E01 | provider détectable | D1,D4,D6 | OpenSpec provider |
| E02 | domaine indépendant | D1-D3,D6 | modèle normalisé |
| E03 | identité stable | D3,D4 | stratégie identity |
| E04 | séparation temporelle | D3 | current/proposed |
| E05 | snapshots cohérents | D3,D5 | versioning |
| E06 | traçabilité exploitable | D2,D5 | store / graph |
| E07 | abstraction testable | D1-D3 | store contract |
| E08 | persistance locale viable | D1-D5 | backend initial |
| E09 | graph store utile | D5 | backend spécialisé |
| E10 | lexical suffisant MVP | D2,D6 | search |
| E11 | incremental fiable | D3,D5 | sync strategy |
| E12 | diagnostics robustes | D4 | error model |
| E13 | contexte compact utile | D2,D6 | query DTO |
| E14 | external refs découplées | D2 | cross-engine contract |

---

# 4. Mesures communes

Chaque benchmark doit enregistrer lorsque pertinent :

```text
wall clock time
CPU time
peak memory
storage bytes
entities read
entities written
links traversed
warnings
errors
result count
```

Les résultats doivent être reproductibles autant que possible.

---

# 5. Environnements

Au minimum :

- Windows — environnement prioritaire du projet ;
- un environnement Linux pour vérifier la portabilité ;
- filesystem local ;
- offline pour les capacités cœur.

macOS peut être différé si aucun composant spécifique ne l'exige pendant M0.

---

# 6. Livrable de sortie M0

M0 doit produire un rapport de décision contenant pour chaque hypothèse :

```text
Hypothesis
Evidence
Measurements
Observed limitations
Decision
Constraints
Follow-up ADR
```

Aucune ADR technologique ne doit passer à `Acceptée` sans référence à ces preuves lorsqu'elle dépend d'une expérimentation.