---
id: ARCH-0165
title: "Une technologie sortie du cœur entre dans un module de transport, qui tient sa propre table des classes qui l'atteignent"
domain: architecture
rules: false
---

# Une technologie sortie du cœur entre dans un module de transport, qui tient sa propre table des classes qui l'atteignent

- Statut : **Acceptée**
- Date : 2026-11-07

## Context

ADR-0150 fixe ce que `com.morpheus.application` peut toucher : SQL, bibliothèque JSON, exécution de processus, réseau
sortant et fils d'exécution y sont refusés ; l'accès fichier, l'environnement, les flux standard, l'horloge murale et la
réflexion n'y sont atteints que par des classes nommées, dans des compteurs de dette qui ne font que rétrécir. Faire
rétrécir ces listes, c'est sortir une technologie du cœur : la découverte de mise à jour lit son manifeste par un port
de `application.product`, implémenté dans `morpheus-cli`, qui apporte d'un coup le réseau, les fils d'exécution et
l'accès fichier.

La discipline d'ADR-0150 s'arrête à la frontière de l'application. Sans règle de l'autre côté, une technologie retirée
du cœur entre dans un adaptateur sans qu'aucune décision ne la voie, et `morpheus-cli`, qui câble le processus, est le
module où une dépendance technique s'ajoute le plus facilement et se remarque le moins.

Options considérées :

1. **Rien de l'autre côté** : la technologie sortie du cœur n'est plus observée.
2. **Un compteur de dette par module de transport** : faux par construction, puisque porter ces technologies est le rôle
   d'un module de transport ; un compteur qui ne fait que rétrécir pousserait à les déplacer ailleurs sans gain.
3. **Une table de faits par module de transport** : chaque famille y a un régime, et une famille confinée nomme
   exactement les classes qui l'atteignent, chacune avec sa raison.

Le mainteneur a retenu l'option 3 le 2026-11-06 pour la CLI et l'adaptateur HTTP.

## Decision

1. **Une technologie retirée du cœur entre dans un module de transport** — `morpheus-cli` ou `morpheus-api` —
   derrière un port de l'application. Elle n'entre pas dans le domaine, et dans l'application seulement par les classes
   que nomme ADR-0150. `morpheus-mcp` n'a pas de gate de familles et n'est pas un module d'accueil au sens de cet ADR.
2. **Chaque module de transport a son gate de familles** : `CliTechnologyBoundaryArchitectureTest` pour
   `com.morpheus.cli`, `ApiTechnologyBoundaryArchitectureTest` pour `com.morpheus.api`. Chaque famille du vocabulaire
   partagé `TechnologyFamily` y a un régime, avec sa raison : refusée, confinée aux classes nommées, libre, ou tenue par
   un autre gate nommé. Une famille ajoutée au vocabulaire exige un régime dans chaque gate de familles.
3. **Les listes confinées d'un module de transport sont des tables de faits.** Chacune égale l'ensemble des classes qui
   atteignent la famille, mesuré sur le bytecode, dans les deux sens. Une classe ajoutée s'y écrit avec sa raison, dans
   le changement qui l'introduit, sans ADR ; la description de la pull request la justifie comme toute modification d'un
   test d'architecture.
4. **Un régime qui change est une décision** : refuser une famille confinée se fait dans le gate ; confiner ou libérer
   une famille refusée remplace cet ADR.

## Do's and Don'ts

### Do

- Implémenter dans `morpheus-cli` ou `morpheus-api` le port par lequel une technologie quitte l'application.
- Nommer dans le gate de familles du module, avec sa raison, toute classe qui atteint une famille confinée.
- Donner un régime dans chaque gate de familles à une famille ajoutée à `TechnologyFamily`.

### Don't

- Ne pas ajouter à l'application une classe qui atteint une famille refusée, ni à ses listes un nom de plus.
- Ne pas faire atteindre à une classe d'un module de transport une famille que son gate refuse.
- Ne pas transformer une liste confinée d'un module de transport en compteur de dette.

## Consequences

### Positives

- Une technologie qui quitte le cœur reste visible : la classe qui la reçoit est nommée avec sa raison.
- Le vocabulaire des familles est unique ; les trois gates ne peuvent pas diverger sur ce qu'est une famille.

### Négatives

- Une table de faits ne promet pas de rétrécir : la croissance d'une famille confinée n'est bornée que par la revue.

### Risques

- Un module de transport sans gate de familles, `morpheus-mcp` compris, échappe à cette décision : une technologie
  qu'il reçoit n'est nommée nulle part.

## Compliance and Enforcement

- `CliTechnologyBoundaryArchitectureTest` et `ApiTechnologyBoundaryArchitectureTest` : un régime par famille, refus
  d'une famille refusée, égalité stricte entre classes nommées et classes qui atteignent une famille confinée.
- `ApplicationTechnologyBoundaryArchitectureTest` : le côté application, tenu par ADR-0150.
- `ProductionClassListsArchitectureTest` : les listes confinées des deux modules de transport sont déclarées tables de
  faits.
- Revue : une classe ajoutée à une liste confinée porte sa raison, et la pull request la justifie.

## References

- ADR-0150 (ce que le cœur applicatif peut toucher).
- ADR-0155 (un rôle est un paquet et une liste de classes déclare sa sorte).
- `.claude/rules/architecture.md`, section « Ce que l'application touche ».
