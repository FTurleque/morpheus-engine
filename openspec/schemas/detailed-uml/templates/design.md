# Conception : <!-- titre court du changement -->

<!-- Proportionner : conception complète pour une évolution fonctionnelle, transverse, de
     sécurité, de persistance, de surface publique ou d'architecture ; conception allégée
     (Contexte, Exigences couvertes, Décisions, Stratégie de vérification) pour un changement
     documentaire ou local. Toute autre rubrique sans objet devient une ligne
     « Sans objet : <raison> ». Ne pas répéter proposal.md ni les specs : y renvoyer.
     Étiquettes de nature : [EXISTANT VÉRIFIÉ], [DÉCISION], [PROPOSITION], [QUESTION]. -->

## Contexte

<!-- État actuel et contraintes qui façonnent l'approche, chacun avec sa preuve (chemin,
     test, commande). Motivation : voir proposal.md, section Why. -->

## Exigences couvertes

| Capacité | Exigence (titre exact) | Rubriques de conception qui y répondent |
|---|---|---|
| `<capability-path>` | <!-- titre --> | <!-- §Décisions D1, §Traitements --> |

## Composants et frontières

<!-- Modules, paquets et classes réels touchés ; responsabilité de chacun ; règle de couche
     ou test d'architecture qui borne la frontière (LayerDependencyTest, *ArchitectureTest).
     Vue de composants ou C4 si une frontière bouge. -->

## Modèle de domaine et invariants

<!-- Types du domaine, relations, cardinalités ; invariants et où ils sont tenus
     (constructeur compact, port, contrainte SQLite). classDiagram si le modèle change. -->

## Traitements et interactions

<!-- Déroulé nominal et alternatives d'erreur. sequenceDiagram avec `alt` pour les refus. -->

## États et transitions

<!-- Cycle de vie : états, événements, gardes, transitions interdites. stateDiagram-v2. -->

## Contrats d'interface

<!-- Renvoyer au contrat exécutable (docs/developer/reference/openapi/*.yaml, ligne de
     contracts/public-surfaces.tsv, aide CLI, schéma d'outil MCP) sans le recopier.
     Pour une route HTTP : opération et chemin, authentification et rôle, champs requis ou
     facultatifs avec types et bornes, exemple de requête et de réponse, erreurs et codes,
     pagination, tri, filtrage, compatibilité, idempotence. Pour la CLI et le MCP : commande
     ou outil, arguments, sortie, codes de refus. -->

## Persistance et intégrité

<!-- Tables, colonnes, index, migrations, contraintes ; parité store-memory / store-sqlite.
     erDiagram si les relations persistées changent (modèle entité-relation, pas UML). -->

## Transactions, concurrence et idempotence

<!-- Portée transactionnelle, verrous, CAS (révision attendue), rejeu, écrivains concurrents. -->

## Sécurité et isolation

<!-- Frontière de confiance, loopback ou remote, rôle requis, secrets, entrées non fiables,
     divulgation dans les réponses et les journaux. -->

## Gestion des erreurs et reprise

<!-- Erreurs nommées, état laissé après échec, rollback, reprise, aucun repli silencieux. -->

## Observabilité

<!-- Journaux, compteurs, diagnostics exposés ; ce qui ne doit jamais y figurer. -->

## Performance et stratégie de mesure

<!-- Budget cité depuis sa source (M19, ADR) ou stratégie de mesure ; sinon [QUESTION]. -->

## Compatibilité, migration et retour arrière

<!-- Données existantes, versions de schéma publiées, clients existants ; étapes de
     déploiement ; comment revenir en arrière et ce qui est perdu. -->

## Décisions

### D1 : <!-- intitulé -->
- **Nature** : [DÉCISION] ou [PROPOSITION]
- **Exigences servies** : <!-- capability-path › titre -->
- **Choix** : <!-- ce qui est retenu -->
- **Alternatives écartées** : <!-- option et raison du rejet -->
- **Compromis** : <!-- ce que le choix coûte -->
- **ADR** : <!-- ADR existant qui la couvre, « ADR à proposer », ou « non concerné » (filtre de .claude/rules/adr.md) -->

## Écarts constatés

<!-- Divergences entre code, tests, specs et documentation, sans les résoudre en silence.
     Écrire « Aucun écart constaté » après vérification. -->

| Constat | Source A | Source B | Traitement proposé |
|---|---|---|---|

## Stratégie de vérification

| Exigence | Scénario | Preuve | État |
|---|---|---|---|
| <!-- titre --> | <!-- titre du scénario --> | <!-- test (classe#méthode), gate, commande --> | existant · à écrire · manuel |

## Documentation durable et diagrammes à mettre à jour

<!-- Pages de docs/developer/specifications/, de docs/architecture/arc42/, ADR ou guides
     touchés par ce changement ; la synchronisation des specs ne les met pas à jour. -->

## Questions ouvertes

<!-- Seulement ce qui peut attendre sans changer les specs, l'approche ni les tâches.
     Écrire « Aucune » sinon. -->
