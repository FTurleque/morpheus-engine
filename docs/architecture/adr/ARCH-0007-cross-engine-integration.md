---
id: ARCH-0007
title: "Les intégrations cross-engine utilisent des contrats explicites et découplés"
domain: architecture
rules: false
---

# Les intégrations cross-engine utilisent des contrats explicites et découplés

- Statut : **Acceptée**
- Date : 2026-07-22

## Context

MORPHEUS fait partie d'un écosystème qui comprend notamment MINOS, NEXUS et JARVIS. Les interactions entre moteurs
sont riches :

- relier une exigence à un symbole MINOS ;
- relier un critère d'acceptation à un test ;
- fournir à NEXUS les exigences pertinentes pour une tâche ;
- permettre à JARVIS d'orchestrer la récupération d'intention puis l'analyse du code.

Une intégration directe par partage des classes de domaine créerait un couplage fort entre dépôts, versions et cycles
de livraison. MORPHEUS doit rester utilisable seul.

Options considérées :

- **Monorepo et modèles partagés** : non retenu comme contrainte architecturale. Un partage technique ponctuel de DTO
  reste envisageable, mais les domaines ne fusionnent pas.
- **MORPHEUS dépend directement de MINOS** : rejetée. Une spécification existe indépendamment du code ou de son
  indexation.
- **Toutes les interactions passent obligatoirement par JARVIS** : rejetée. Les moteurs restent appelables
  directement.
- **Contrats explicites et références externes** : retenue.

## Decision

Les moteurs restent autonomes et communiquent par des **contrats d'intégration explicites**. MORPHEUS ne référence
pas directement les objets internes de domaine d'un autre moteur.

Pour conserver un lien vers un système externe, MORPHEUS possède son propre concept de référence externe
(`com.morpheus.domain.reference.ExternalReference`), dont la cible (`ExternalReferenceTarget`) porte des coordonnées
neutres :

```text
ExternalReferenceTarget
├── system
├── project        (optionnel)
├── resourceType
├── externalId
└── revision       (optionnelle)
```

Une relation cross-engine peut être portée par un lien de traçabilité dont la cible est externe.

**MINOS.** Une exigence peut être reliée à un symbole MINOS par une référence externe (`system = MINOS`). MORPHEUS
conserve le lien même si MINOS n'est pas disponible au moment de la requête : la résolution live est une capacité
d'intégration, pas une condition d'existence du lien.

**NEXUS.** MORPHEUS expose une vue stable du contexte de spécification (objectif, exigences, contraintes, décisions,
critères d'acceptation, tâches, traçabilité, provenance), que NEXUS consomme par un contrat ou un adaptateur. MORPHEUS
ne connaît ni les budgets de tokens de NEXUS, ni ses stratégies de ranking, ni ses profils d'agents, ni ses
algorithmes de compression.

**JARVIS.** JARVIS appelle les opérations MORPHEUS, mais le cœur MORPHEUS ne contient aucun workflow JARVIS. Le
workflow (lire un changement et son contexte dans MORPHEUS, analyser l'impact dans MINOS, construire le contexte dans
NEXUS, exécuter l'agent) appartient à JARVIS.

**Références obsolètes.** Une référence externe peut devenir invalide si le système cible change. MORPHEUS distingue
les états de résolution `UNVALIDATED`, `UNRESOLVED`, `RESOLVED` et `STALE`
(`ExternalReferenceResolutionState`). La disparition d'une cible externe ne supprime pas l'historique de la relation.

Invariants :

1. MORPHEUS démarre et fonctionne sans autre moteur.
2. Une indisponibilité de MINOS n'empêche pas la lecture des spécifications.
3. Une indisponibilité de NEXUS n'empêche pas les requêtes MORPHEUS.
4. Une indisponibilité de JARVIS n'empêche pas l'usage direct de la CLI, de MCP ou de l'API.
5. Aucun package interne d'un autre moteur n'est une dépendance du domaine MORPHEUS.
6. Les versions des contrats externes peuvent évoluer explicitement.

## Do's and Don'ts

### Do

- Relier MORPHEUS à une ressource d'un autre moteur par une `ExternalReference` possédée par MORPHEUS.
- Conserver une référence externe et son historique quand le système cible est indisponible ou que la cible disparaît.
- Laisser les moteurs appelables directement, sans passage obligé par JARVIS.

### Don't

- Ne pas faire dépendre le domaine ni l'application MORPHEUS d'un package `com.minos..`, `com.nexus..` ou
  `com.jarvis..`.
- Ne pas faire de la résolution live d'une référence une condition d'existence du lien.
- Ne pas placer de workflow JARVIS, ni de logique de budget, de ranking ou de compression NEXUS, dans le cœur
  MORPHEUS.

## Consequences

### Positives

- Autonomie, déploiement séparé et évolution indépendante des moteurs.
- Tests isolés et tolérance aux indisponibilités.
- Possibilité de remplacer un consommateur ; responsabilités claires.

### Négatives

- Contrats supplémentaires, sérialisation et mapping.
- Gestion de versions inter-services.

### Risques

- Des références externes peuvent devenir obsolètes ; elles exigent des mécanismes de résolution ou de validation
  différée.

## Compliance and Enforcement

- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/LayerDependencyTest.java` :
  `domainMustNotDependOnAdapters`, `applicationMustNotDependOnAdapters`, `httpApiMustRemainSiblingOfCliAndMcp`,
  `minosIntegrationMustNotDependOnOuterMorpheusAdaptersOrMinosImplementation`,
  `nexusIntegrationMustNotDependOnOuterMorpheusAdaptersOrNexusImplementation`,
  `morpheusMustNotDependOnJarvisImplementation` interdisent les dépendances vers les autres moteurs.
- `morpheus-application/src/test/java/com/morpheus/application/reference/ExternalReferenceResolutionServiceTest.java`
  prouve qu'une référence existe sans système cible, qu'un résolveur absent ou un système indisponible produit un état
  explicite sans échec, et qu'une cible supprimée devient `STALE`.
- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/LiveExternalReferenceResolutionContractTest.java`
  tient le contrat de résolution live.
- Revue : la vue de contexte consommée par NEXUS et l'absence de workflow JARVIS dans le cœur.

## References

- [ADR-0142](DATA-0142-references-externes.md) — résolution optionnelle des références externes.
- [ADR-0142](DATA-0142-references-externes.md) — résolution live sans mutation du
  snapshot.
- [ADR-0139](ARCH-0139-frontiere-morpheus-jarvis-en-lecture-seule.md) — frontière d'orchestration JARVIS.
- [ADR-0128](ARCH-0128-pairs-minos-et-nexus-par-mcp-stdio.md) — pairs MINOS et NEXUS par MCP STDIO.
- [Validation M0](../../project/validation/VALIDATION_M0.md)
