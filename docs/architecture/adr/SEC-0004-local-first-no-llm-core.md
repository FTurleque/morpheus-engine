---
id: SEC-0004
title: "Le cœur MORPHEUS est local-first et ne dépend d'aucun LLM"
domain: security
rules: true
files: ["**/pom.xml"]
---

# Le cœur MORPHEUS est local-first et ne dépend d'aucun LLM

- Statut : **Acceptée**
- Date : 2026-07-22

## Context

Les spécifications d'un projet peuvent contenir des informations sensibles : architecture future, exigences métier,
contraintes internes, décisions stratégiques, vulnérabilités connues, noms de clients ou plans non publiés.

MORPHEUS est aussi utilisé par des agents IA. Il serait tentant de confier au LLM des opérations fondamentales comme
l'extraction d'exigences, la classification ou la recherche. Cette dépendance rendrait toutefois le moteur non
déterministe, dépendant d'un fournisseur, plus coûteux, moins utilisable hors ligne et potentiellement incompatible
avec des dépôts confidentiels.

Options considérées :

- **LLM obligatoire pour toute ingestion** : rejetée, incompatible avec les exigences de confidentialité, de
  reproductibilité et d'autonomie.
- **Service SaaS central comme backend** : rejeté comme prérequis ; il peut devenir un mode de déploiement optionnel.
- **Cœur déterministe et extensions IA optionnelles** : retenu.

## Decision

Le fonctionnement de base de MORPHEUS est :

- **local-first** ;
- **sans LLM obligatoire** ;
- **sans service cloud obligatoire** ;
- **indépendant du fournisseur IA**.

Les capacités essentielles suivantes fonctionnent sans IA générative :

```text
discovery
ingestion
normalization
storage
versioning
traceability
current/proposed separation
structured querying
compact context output
```

### Ce que « local-first » signifie

Par défaut :

1. les fichiers de spécification restent sur la machine ;
2. aucune donnée n'est envoyée vers une API externe ;
3. l'index et les métadonnées peuvent être reconstruits localement ;
4. les providers réseau sont opt-in ;
5. la télémétrie externe n'est pas requise ;
6. le fonctionnement hors ligne est possible après installation des dépendances locales nécessaires.

Local-first n'interdit pas toute intégration réseau : elle est explicite et optionnelle.

### Place des LLM

Des capacités IA peuvent être ajoutées sous forme d'extensions ou de providers spécialisés, par exemple : proposition
de liens de traçabilité, résumé de changements, détection heuristique de contradictions, génération assistée de
brouillons, recherche sémantique. Leurs résultats sont clairement identifiés comme dérivés ou heuristiques.

Un LLM ne devient jamais la seule source de vérité pour un fait déterministe disponible dans les spécifications.

Aucun contrat du domaine n'exige un type ou un service LLM. Toute fonctionnalité qui nécessiterait obligatoirement un
service distant fait l'objet d'une décision explicite et ne modifie pas le comportement par défaut sans nouvel ADR.

### Sécurité

Les implémentations considèrent notamment :

- le masquage des secrets dans les logs ;
- des exclusions configurables ;
- un stockage local protégé selon les capacités de l'OS ;
- l'absence de contenu complet dans la télémétrie ;
- le consentement explicite avant appel à un provider externe ;
- le signalement clair des données envoyées lorsqu'une intégration cloud est activée.

## Do's and Don'ts

### Do

- Faire fonctionner discovery, ingestion, normalisation, stockage, versionnement, traçabilité, séparation
  current/proposed, requête structurée et sortie de contexte compact sans réseau et sans LLM.
- Rendre toute intégration réseau explicite et optionnelle (opt-in).
- Identifier comme dérivé ou heuristique tout résultat produit par une capacité IA.
- Exiger un consentement explicite avant d'appeler un provider externe, et signaler les données envoyées.
- Écrire un ADR pour toute fonctionnalité qui exigerait un service distant.

### Don't

- Ne pas rendre une clé API, un service cloud ou un LLM nécessaire au fonctionnement par défaut.
- Ne pas envoyer de donnée de spécification vers une API externe par défaut.
- Ne pas introduire de type ou de service LLM dans un contrat du domaine.
- Ne pas laisser un LLM remplacer un fait déterministe disponible dans les spécifications.
- Ne pas placer de contenu complet de spécification dans une télémétrie.

## Consequences

### Positives

- Confidentialité accrue et fonctionnement sur dépôts privés.
- Réduction des coûts IA et indépendance vis-à-vis des fournisseurs.
- Comportement reproductible et meilleure testabilité.
- Utilisation possible en CI hors réseau.
- Séparation nette entre faits et inférences.

### Négatives

- Certaines fonctions avancées sont plus difficiles sans IA.
- L'extraction depuis du texte libre est moins fiable.
- Les providers structurés sont à privilégier.
- Les fonctions sémantiques peuvent exiger une duplication locale de modèles ou d'index.

### Risques

- Aucun contrôle global ne prouve l'absence d'appel réseau par défaut : une dépendance réseau ajoutée à un chemin
  par défaut ne serait vue qu'en revue.

## Compliance and Enforcement

- Règle Archgate `SEC-0004-local-first-no-llm-core.rules.ts` : `no-llm-client-dependency`.
- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/m27/ReasoningPlatformArchitectureTest.java`
  (`reasoningCoreContainsNoTransportPersistenceOrProcessExecution`) interdit `java.net.http` dans le noyau de
  raisonnement assisté.
- Revue : absence de clé API requise, absence d'appel externe par défaut, exécution des tests principaux sans LLM et
  documentation de toute dépendance qui nécessite un téléchargement initial.

## References

- [ADR-0095](ARCH-0095-evidence-backed-assisted-reasoning.md) — raisonnement assisté, optionnel et sans LLM requis.
- [VALIDATION_M0](../../project/validation/VALIDATION_M0.md)
