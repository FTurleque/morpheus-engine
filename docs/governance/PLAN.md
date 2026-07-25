# Plan de travail — MORPHEUS

Statut : **HISTORIQUE — plan de cadrage C0 / faisabilité M0 exécuté**

Date de cadrage : 22 juillet 2026  
Contextualisation D0 : 26 juillet 2026

Ce document conserve le plan de travail utilisé pour C0 et M0. Il n'est plus la roadmap opérationnelle courante : **C0 à M14 sont validés et intégrés**. Pour l'état actuel, consulter [`ROADMAP.md`](ROADMAP.md) et [`../roadmap/POST_M14_EXECUTION.md`](../roadmap/POST_M14_EXECUTION.md).

La baseline fonctionnelle C0 est [`CAHIER_DES_CHARGES.md`](../product/CAHIER_DES_CHARGES.md). La politique d'interprétation des documents historiques est [`DOCUMENTATION_STATUS.md`](DOCUMENTATION_STATUS.md).

> **Règle de travail historique C0 : documenter d'abord, décider ensuite, implémenter en dernier.**

---

## C0 — Cadrage fonctionnel et architectural

### Objectif

Définir précisément le produit, son domaine, ses frontières, son MVP et ses choix structurants avant toute implémentation fonctionnelle significative.

### Axe 1 — Vision et positionnement

Valider :

- définition de MORPHEUS ;
- problème résolu ;
- utilisateurs ;
- valeur spécifique ;
- non-objectifs ;
- frontière avec MINOS ;
- frontière avec NEXUS ;
- rôle de JARVIS ;
- autonomie du moteur.

### Axe 2 — Cas d'usage

Spécifier et prioriser :

- lire l'état courant d'une spécification ;
- rechercher une exigence ;
- lire un changement ;
- lister les changements ;
- obtenir les contraintes ;
- obtenir les décisions ;
- obtenir les critères d'acceptation ;
- obtenir les tâches ;
- suivre la traçabilité ;
- construire un contexte de changement.

Pour chaque cas d'usage :

- entrée ;
- sortie ;
- erreurs ;
- provenance ;
- priorité MVP ;
- contraintes de performance.

### Axe 3 — Modèle de domaine

Consolider :

```text
ProjectSpecification
Specification
Requirement
Scenario
ChangeProposal
Constraint
DesignDecision
AcceptanceCriterion
ImplementationTask
SpecificationVersion
Evidence
TraceabilityLink
ExternalReference
```

Points à trancher :

- identité stable ;
- granularité ;
- statuts ;
- versionnement ;
- relation état courant / changement proposé ;
- provenance ;
- confiance ;
- relations factuelles et dérivées.

### Axe 4 — Cycle de vie

Définir :

- états ;
- transitions ;
- conditions d'entrée/sortie ;
- archivage ;
- réouverture ;
- changements abandonnés ;
- gestion des états propres à un provider.

### Axe 5 — Providers

Définir :

```text
SpecificationProvider
SpecificationProviderRegistry
ProviderCapabilities
```

Étudier :

- OpenSpec ;
- Markdown structuré ;
- détection automatique ;
- version de format ;
- provider en lecture seule ;
- capacités d'écriture éventuelles.

### Axe 6 — OpenSpec

Évaluer explicitement :

- modèle de fichiers ;
- cycle de changement ;
- archivage ;
- stabilité ;
- versionnement ;
- adaptation au domaine MORPHEUS ;
- licence ;
- compatibilité multi-agent ;
- limites ;
- risques de couplage.

### Axe 7 — Stockage

Définir :

```text
SpecificationKnowledgeStore
```

Comparer les familles de solutions :

- relationnelle ;
- documentaire ;
- graphe ;
- hybride ;
- mémoire pour tests.

Le choix doit découler des requêtes prioritaires.

### Axe 8 — Traçabilité

Définir :

- types de liens ;
- direction ;
- cardinalité ;
- provenance ;
- résolution des liens cassés ;
- liens cross-engine ;
- chemins explicatifs.

### Axe 9 — Sécurité / local-first

Définir :

- données autorisées à sortir ;
- exclusions ;
- gestion des secrets ;
- journalisation ;
- fonctionnement hors ligne ;
- providers externes opt-in.

### Axe 10 — Critères de validation

Définir avant M0 :

- fidélité d'ingestion ;
- reconstruction de l'état courant ;
- couverture de la traçabilité ;
- temps d'ingestion ;
- latence ;
- taille de stockage ;
- mémoire ;
- robustesse face aux fichiers invalides ;
- compatibilité de versions.

### Livrables C0

- cahier des charges validé ;
- écosystème validé ;
- MVP validé ;
- modèle de domaine ;
- architecture haut niveau ;
- ADR structurantes ;
- étude OpenSpec ;
- stratégie provider ;
- stratégie stockage ;
- roadmap ;
- plan M0.

### Condition de sortie

> Aucun développement fonctionnel significatif ne commence tant que les principales décisions ne sont pas explicitement validées.

---

## M0 — Faisabilité technique

### Objectif

Valider par expérimentation que les choix structurants retenus en C0 sont viables.

### Spike 1 — Provider OpenSpec

Vérifier :

- découverte ;
- lecture des specs courantes ;
- lecture des changements ;
- proposal/design/tasks ;
- critères d'acceptation ;
- archivage ;
- versions du format ;
- erreurs.

### Spike 2 — Normalisation

Démontrer qu'aucun type OpenSpec n'est nécessaire dans le domaine public MORPHEUS.

### Spike 3 — Identité et versionnement

Tester :

- renommage ;
- changement de chemin ;
- changement de version ;
- collision ;
- suppression ;
- archivage.

### Spike 4 — SpecificationKnowledgeStore

Comparer au moins :

- backend mémoire ;
- un backend persistant simple ;
- option graphe si la traçabilité le justifie.

### Spike 5 — Requêtes verticales

Vertical slice :

```text
Projet réel
   ↓
Provider
   ↓
Ingestion
   ↓
Modèle MORPHEUS
   ↓
SpecificationKnowledgeStore
   ↓
find_requirements
get_change
trace_requirement
```

### Sortie M0

Décision documentée :

```text
ADOPTER
ADOPTER_AVEC_CONTRAINTES
REVOIR
REMPLACER
```

Les décisions structurantes sont mises à jour dans les ADR.

---

## Travaux explicitement différés pendant C0/M0

- serveur MCP complet ;
- API REST de production ;
- interface graphique ;
- génération de specs par LLM ;
- recherche vectorielle ;
- synchronisation Jira/GitHub Issues complète ;
- intégration runtime complète avec MINOS/NEXUS/JARVIS ;
- édition bidirectionnelle multi-provider ;
- plateforme collaborative.
