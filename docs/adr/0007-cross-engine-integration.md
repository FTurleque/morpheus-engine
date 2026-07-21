# ADR-0007 — Les intégrations cross-engine utilisent des contrats explicites et découplés

- Statut : **Proposée — à valider pendant C0**
- Date : 22 juillet 2026
- Portée : frontières MORPHEUS / MINOS / NEXUS / JARVIS

---

## 1. Contexte

MORPHEUS fait partie d'un écosystème comprenant notamment MINOS, NEXUS et JARVIS.

Les interactions futures sont riches :

- relier une exigence à un symbole MINOS ;
- relier un critère d'acceptation à un test ;
- fournir à NEXUS les exigences pertinentes pour une tâche ;
- permettre à JARVIS d'orchestrer la récupération d'intention puis l'analyse du code.

Une intégration directe par partage des classes de domaine créerait cependant un couplage fort entre dépôts, versions et cycles de livraison.

MORPHEUS doit rester utilisable seul.

---

## 2. Décision proposée

Les moteurs restent autonomes et communiquent via des **contrats d'intégration explicites**.

MORPHEUS ne référence pas directement les objets internes de domaine d'un autre moteur.

Pour conserver un lien vers un système externe, MORPHEUS utilise un concept de référence externe :

```text
ExternalReference
├── system
├── project
├── targetType
├── targetId
├── revision éventuelle
└── metadata contrôlée
```

Une relation cross-engine peut être portée par `TraceabilityLink` avec une cible externe.

---

## 3. Exemple avec MINOS

```text
Requirement
   │
   └── LINKS_TO_CODE
             │
             ▼
      ExternalReference
        system = MINOS
        targetType = SYMBOL
        targetId = ...
```

MORPHEUS conserve le lien même si MINOS n'est pas disponible au moment de la requête.

La résolution live est une capacité d'intégration, pas une condition d'existence du lien.

---

## 4. Exemple avec NEXUS

MORPHEUS expose une vue stable :

```text
SpecificationContext
├── objective
├── requirements
├── constraints
├── decisions
├── acceptanceCriteria
├── tasks
├── traceability
└── provenance
```

NEXUS consomme cette vue via un contrat ou adaptateur.

MORPHEUS ne connaît pas :

- les budgets de tokens NEXUS ;
- les stratégies de ranking ;
- les profils d'agents ;
- les algorithmes de compression.

---

## 5. Exemple avec JARVIS

JARVIS appelle les opérations MORPHEUS mais le cœur MORPHEUS ne contient aucun workflow JARVIS.

Exemple d'orchestration externe :

```text
JARVIS
  │
  ├── MORPHEUS.getChange(...)
  ├── MORPHEUS.getChangeContext(...)
  ├── MINOS.analyzeImpact(...)
  ├── NEXUS.buildContext(...)
  └── Agent.execute(...)
```

Le workflow appartient à JARVIS.

---

## 6. Invariants

1. MORPHEUS démarre et fonctionne sans autre moteur.
2. Une indisponibilité de MINOS ne doit pas empêcher la lecture des specs.
3. Une indisponibilité de NEXUS ne doit pas empêcher les requêtes MORPHEUS.
4. Une indisponibilité de JARVIS ne doit pas empêcher CLI/MCP/API directs.
5. Aucun package interne d'un autre moteur n'est une dépendance du domaine MORPHEUS.
6. Les versions de contrats externes doivent pouvoir évoluer explicitement.

---

## 7. Conséquences positives

- autonomie ;
- déploiement séparé ;
- évolution indépendante ;
- tests isolés ;
- tolérance aux indisponibilités ;
- possibilité de remplacer un consommateur ;
- clarté des responsabilités.

---

## 8. Conséquences négatives

- contrats supplémentaires ;
- sérialisation/mapping ;
- gestion de versions inter-services ;
- références externes potentiellement obsolètes ;
- besoin de mécanismes de résolution ou validation différée.

---

## 9. Alternatives étudiées

### A. Monorepo et modèles partagés

**Non retenu comme contrainte architecturale.**

Un partage technique ponctuel de DTO peut être étudié ultérieurement, mais les domaines ne doivent pas fusionner.

### B. MORPHEUS dépend directement de MINOS

**Rejetée.**

Une spécification existe indépendamment du code ou de son indexation.

### C. Toutes les interactions passent obligatoirement par JARVIS

**Rejetée.**

Les moteurs doivent rester appelables directement.

### D. Contrats explicites et références externes

**Retenue.**

---

## 10. Gestion des références obsolètes

Une `ExternalReference` peut devenir invalide si le système cible change.

MORPHEUS doit distinguer :

```text
UNVALIDATED
RESOLVED
STALE
UNRESOLVED
```

La terminologie finale sera alignée avec le modèle général de résolution.

La disparition d'une cible externe ne doit pas supprimer l'historique de la relation.

---

## 11. Validation

Avant acceptation complète, démontrer qu'un test peut :

1. créer une exigence avec une référence MINOS fictive ;
2. stocker et relire cette exigence sans MINOS ;
3. brancher un résolveur simulé ;
4. résoudre la cible ;
5. simuler une cible supprimée ;
6. conserver la provenance et l'historique du lien.

---

## 12. Condition d'acceptation

Cette ADR passe à **Acceptée** lorsque les contrats d'intégration initiaux sont définis sans dépendance de domaine directe et qu'au moins une preuve de concept démontre une résolution externe optionnelle.

Les intégrations concrètes seront livrées plus tard dans la roadmap et pourront avoir leurs propres ADR de protocole.