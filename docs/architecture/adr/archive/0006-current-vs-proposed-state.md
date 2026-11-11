# ADR-0006 — Distinguer structurellement l'état courant des changements proposés

> **Archivée.** Cette décision est consolidée dans [ADR-0136](../ARCH-0136-etat-temporel-et-occurrences-versionnees.md). Ce texte n'est plus en vigueur et n'est pas tenu à jour.

- Statut : **Remplacée par ADR-0136**
- Date : 2026-07-22

## Context

Un moteur d'intelligence des spécifications doit éviter une ambiguïté critique : confondre ce que le système **doit
déjà faire aujourd'hui** avec ce qu'un changement **propose qu'il fasse demain**.

Cette confusion peut conduire un agent à :

- traiter une fonctionnalité non encore validée comme existante ;
- ignorer qu'une exigence est en cours de remplacement ;
- produire du code contre une version future non approuvée ;
- mélanger plusieurs changements concurrents ;
- construire un contexte incohérent pour NEXUS ou JARVIS.

OpenSpec offre une séparation utile entre specs courantes et changements/deltas, mais MORPHEUS doit posséder cette
distinction indépendamment d'OpenSpec.

Options considérées :

- **Fusionner immédiatement les changements dans une vue unique** : rejetée, cela détruit la distinction temporelle
  essentielle.
- **Déduire l'état uniquement du chemin du provider** : rejetée comme modèle public. Le provider peut utiliser ses
  chemins pour déterminer l'état, mais le domaine stocke la sémantique normalisée.
- **Modèle courant/proposé explicite** : retenue.

## Decision

Le modèle MORPHEUS représente séparément :

1. **l'état courant de la spécification** ;
2. **les changements proposés ou en cours** ;
3. **l'historique archivé ou supersédé**.

Cette distinction n'est pas déduite uniquement du chemin de fichier lors des requêtes : elle est normalisée dans le
modèle.

### Dimensions à ne pas confondre

MORPHEUS n'utilise pas une seule enum mélangeant plusieurs notions. Il existe au moins trois dimensions, qui restent
orthogonales autant que possible :

| Dimension | Valeurs |
|---|---|
| Position par rapport à l'état courant | `CURRENT`, `PROPOSED`, `HISTORICAL` |
| Cycle de vie d'un changement | `DRAFT`, `PROPOSED`, `SPECIFIED`, `DESIGNED`, `PLANNED`, `IMPLEMENTING`, `VERIFYING`, `COMPLETED`, `ARCHIVED`, `ABANDONED` |
| Qualité / résolution de l'information | `RESOLVED`, `PARTIALLY_RESOLVED`, `UNRESOLVED`, `HEURISTIC` |

Dans le code, ces dimensions sont respectivement `com.morpheus.domain.temporal.TemporalState`,
`com.morpheus.domain.change.lifecycle.ChangeLifecycleState` (dont les transitions relèvent d'ADR-0119) et
`com.morpheus.domain.traceability.TraceabilityResolutionState`.

### Reconstruction de l'état courant

MORPHEUS répond explicitement à `get_current_specification` sans incorporer silencieusement les deltas de changements
actifs. Une vue future de type `preview_specification_after(changeId)` est permise, mais elle est identifiée comme
simulation ou projection.

### Changements multiples

Plusieurs changements peuvent être actifs simultanément. MORPHEUS ne suppose pas qu'ils sont automatiquement
compatibles et ne les fusionne pas silencieusement. Il conserve pour chacun :

- son identité ;
- sa base de référence ;
- les spécifications affectées ;
- les dépendances entre changements ;
- les conflits explicites détectables.

### Archivage et supersession

Lorsqu'un changement est terminé :

- son historique reste disponible ;
- sa relation à la nouvelle spécification courante est traçable ;
- MORPHEUS peut expliquer quel changement a introduit une exigence lorsque la source le permet.

Un élément supersédé ne disparaît pas de l'historique comme s'il n'avait jamais existé.

## Do's and Don'ts

### Do

- Porter la position temporelle (courant, proposé, historique) comme une donnée normalisée du modèle.
- Garder séparées la position temporelle, l'état de cycle de vie et l'état de résolution.
- Marquer explicitement comme simulation ou projection toute vue qui applique un changement à l'état courant.
- Conserver l'identité, la base de référence, les spécifications affectées et les dépendances de chaque changement
  actif.
- Garder interrogeables les changements archivés et les éléments supersédés.
- Conserver le statut externe du provider dans la provenance et exposer `UNKNOWN` ou un diagnostic quand aucune
  équivalence MORPHEUS n'est sûre.

### Don't

- Ne pas inclure un delta de changement actif dans la vue de l'état courant.
- Ne pas déduire la position temporelle du seul chemin de fichier au moment de la requête.
- Ne pas fusionner silencieusement plusieurs changements actifs ni les supposer compatibles.
- Ne pas mélanger plusieurs dimensions dans une seule enum.
- Ne pas inventer une équivalence entre un statut provider et un état MORPHEUS.

## Consequences

### Positives

- Contexte fiable pour les agents.
- Historique intelligible.
- Meilleure analyse de changement et comparaison de versions.
- Détection de conflits possible.
- Projections contrôlées possibles.
- Distinction claire entre vérité courante et intention future.

### Négatives

- Modèle plus complexe.
- Nécessité de gérer des versions et des règles d'archivage.
- Gestion des changements concurrents.
- Duplication logique possible de certaines exigences entre état courant et deltas.

### Risques

- **Divergence entre source et index** : mitigée par les snapshots, les empreintes et la synchronisation explicite.
- **Changement archivé mais état courant non synchronisé** : mitigé par la validation de cohérence lors de l'ingestion
  et par les diagnostics.
- **Mapping imparfait des statuts provider** : mitigé en conservant le statut externe et en exposant `UNKNOWN` ou un
  diagnostic plutôt qu'une équivalence inventée.

## Compliance and Enforcement

- `morpheus-application/src/test/java/com/morpheus/application/temporal/TemporalProjectionTest.java`
  (`currentViewNeverIncludesProposedOrHistoricalContent`) : la vue courante n'inclut ni contenu proposé ni contenu
  historique.
- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/VersionedRequirementPersistenceTest.java` :
  la requête courante ne lit que le snapshot actif et l'état temporel `CURRENT`, en mémoire et en SQLite, et la
  séparation courant/proposé survit à la réouverture SQLite.
- Revue : orthogonalité des dimensions, absence de fusion silencieuse de changements concurrents, conservation du
  statut provider dans la provenance.

## References

- [ADR-0031](0031-explicit-temporal-projection-and-entity-version.md) — projection temporelle explicite et
  versions d'entité.
- [ADR-0035](../BE-0035-explicit-requirement-delta-application-and-promotion.md) — application et promotion explicites
  des deltas.
- [ADR-0056](0056-deterministic-current-proposed-change-analysis.md) — analyse déterministe courant/proposé.
- [ADR-0119](../BE-0119-cycle-de-vie-des-changements-et-transition-tri-etat.md) — cycle de vie des changements.
- [ADR-0121](../ARCH-0121-modele-normalise-neutre-vis-a-vis-des-providers.md) — modèle normalisé neutre vis-à-vis des
  providers.
- [VALIDATION_M0](../../../project/validation/VALIDATION_M0.md) — preuve du jalon M0.
