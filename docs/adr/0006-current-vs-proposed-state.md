# ADR-0006 — Distinguer structurellement l'état courant des changements proposés

- Statut : **Proposée — à valider pendant C0 et M0**
- Date : 22 juillet 2026
- Portée : sémantique fondamentale du modèle

---

## 1. Contexte

Un moteur d'intelligence des spécifications doit éviter une ambiguïté critique : confondre ce que le système **doit déjà faire aujourd'hui** avec ce qu'un changement **propose qu'il fasse demain**.

Cette confusion peut conduire un agent à :

- traiter une fonctionnalité non encore validée comme existante ;
- ignorer qu'une exigence est en cours de remplacement ;
- produire du code contre une version future non approuvée ;
- mélanger plusieurs changements concurrents ;
- construire un contexte incohérent pour NEXUS ou JARVIS.

OpenSpec offre une séparation utile entre specs courantes et changements/deltas, mais MORPHEUS doit posséder cette distinction indépendamment d'OpenSpec.

---

## 2. Décision proposée

Le modèle MORPHEUS doit représenter séparément :

1. **l'état courant de la spécification** ;
2. **les changements proposés ou en cours** ;
3. **l'historique archivé ou supersédé**.

Cette distinction ne doit pas être déduite uniquement du chemin de fichier lors des requêtes. Elle doit être normalisée dans le modèle.

---

## 3. Dimensions à ne pas confondre

MORPHEUS doit éviter une seule enum mélangeant plusieurs notions.

Il existe au moins trois dimensions différentes :

### 3.1 Position par rapport à l'état courant

```text
CURRENT
PROPOSED
HISTORICAL
```

### 3.2 Cycle de vie d'un changement

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

### 3.3 Qualité/résolution de l'information

```text
RESOLVED
PARTIALLY_RESOLVED
UNRESOLVED
HEURISTIC
```

Ces dimensions doivent rester orthogonales autant que possible.

---

## 4. Reconstruction de l'état courant

MORPHEUS doit pouvoir répondre explicitement à :

```text
get_current_specification
```

sans incorporer silencieusement les deltas de changements actifs.

Une vue future peut permettre :

```text
preview_specification_after(changeId)
```

mais elle doit être identifiée comme simulation ou projection.

---

## 5. Gestion des changements multiples

Plusieurs changements peuvent être actifs simultanément.

MORPHEUS ne doit pas supposer qu'ils sont automatiquement compatibles ni les fusionner silencieusement.

Il doit pouvoir conserver :

- leur identité ;
- leur base de référence ;
- les spécifications affectées ;
- les dépendances entre changements ;
- les conflits explicites détectables.

---

## 6. Archivage et supersession

Lorsqu'un changement est terminé :

- son historique doit rester disponible ;
- sa relation à la nouvelle spécification courante doit être traçable ;
- MORPHEUS doit pouvoir expliquer quel changement a introduit une exigence lorsque la source le permet.

Un élément supersédé ne doit pas disparaître de l'historique comme s'il n'avait jamais existé.

---

## 7. Conséquences positives

- contexte fiable pour les agents ;
- historique intelligible ;
- meilleure analyse de changement ;
- comparaison de versions ;
- détection future de conflits ;
- possibilité de projections contrôlées ;
- distinction claire entre vérité courante et intention future.

---

## 8. Conséquences négatives

- modèle plus complexe ;
- nécessité de gérer des versions ;
- besoin de règles d'archivage ;
- gestion des changements concurrents ;
- possible duplication logique de certaines exigences entre état courant et deltas.

---

## 9. Alternatives étudiées

### A. Fusionner immédiatement les changements dans une vue unique

**Rejetée.**

Cela détruit la distinction temporelle essentielle.

### B. Déduire l'état uniquement du chemin du provider

**Rejetée comme modèle public.**

Le provider peut utiliser ses chemins pour déterminer l'état, mais le domaine doit stocker la sémantique normalisée.

### C. Modèle courant/proposé explicite

**Retenu.**

---

## 10. Risques

### Divergence entre source et index

**Mitigation :** snapshots, empreintes et synchronisation explicite.

### Changement archivé mais état courant non synchronisé

**Mitigation :** validation de cohérence lors de l'ingestion et diagnostics.

### Mapping imparfait des statuts provider

**Mitigation :** conserver le statut externe et exposer un état MORPHEUS `UNKNOWN` ou diagnostic si nécessaire plutôt que d'inventer une équivalence.

---

## 11. Validation M0

Le corpus doit comporter :

1. une spécification courante ;
2. un changement actif modifiant cette spécification ;
3. un second changement actif distinct ;
4. un changement archivé ;
5. un changement abandonné ou fixture équivalente si le provider le permet.

Tests obligatoires :

- `get_current_specification` n'inclut pas un delta actif ;
- `get_change` expose le delta ;
- l'archive reste interrogeable ;
- une projection future, si implémentée, est explicitement marquée ;
- les états provider sont conservés dans la provenance.

---

## 12. Condition d'acceptation

Cette ADR passe à **Acceptée** lorsque le vertical slice M0 prouve que MORPHEUS peut distinguer de manière fiable courant, proposé et historique sur un corpus réel sans dépendre des concepts publics d'un provider particulier.