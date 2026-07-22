# ADR — MORPHEUS

Ce répertoire contient les **Architecture Decision Records** de MORPHEUS.

Les ADR décrivent les décisions structurantes, leur contexte, les alternatives, les conséquences, les risques et les preuves attendues avant acceptation.

## Statuts

- **Proposée** : décision candidate en cours de cadrage ou d'expérimentation ;
- **Acceptée** : décision validée et applicable ;
- **Acceptée avec contraintes** : décision validée sous conditions explicites ;
- **Remplacée** : décision supersédée par une ADR plus récente ;
- **Rejetée** : option étudiée puis explicitement non retenue.

Une ADR proposée ne devient pas automatiquement une décision définitive parce qu'elle est documentée.

---

## Index

| ADR | Décision | Statut |
|---|---|---|
| [ADR-0001](0001-morpheus-owned-domain.md) | Domaine MORPHEUS indépendant des formats et providers | Proposée |
| [ADR-0002](0002-openspec-reference-provider.md) | OpenSpec comme premier provider de référence sans verrouillage | Proposée |
| [ADR-0003](0003-specification-knowledge-store.md) | Persistance derrière `SpecificationKnowledgeStore` | Proposée |
| [ADR-0004](0004-local-first-no-llm-core.md) | Cœur local-first et sans LLM obligatoire | Proposée |
| [ADR-0005](0005-traceability-first-class.md) | Traçabilité comme concept de premier ordre | Proposée |
| [ADR-0006](0006-current-vs-proposed-state.md) | Distinction structurelle état courant / proposé / historique | Proposée |
| [ADR-0007](0007-cross-engine-integration.md) | Intégrations cross-engine découplées | Proposée |
| [ADR-0008](0008-read-first-write-capability.md) | Providers read-first, écriture séparée et optionnelle | Proposée |
| [ADR-0009](0009-stable-domain-identity.md) | Séparer identité logique, version, emplacement et identifiant externe | Proposée |
| [ADR-0010](0010-traceability-relation-taxonomy.md) | Taxonomie contrôlée des relations de traçabilité | Proposée |
| [ADR-0011](0011-provider-capability-negotiation.md) | Sélection des providers par capacités effectives | Proposée |
| [ADR-0012](0012-snapshot-versioning-strategy.md) | Publication par snapshots versionnés et activation atomique | Proposée |
| [ADR-0013](0013-change-lifecycle-state-machine.md) | Cycle de vie des changements sous forme de machine d'état | Proposée |

---

## Règles de rédaction

Chaque ADR structurante doit préciser autant que possible :

1. contexte ;
2. problème ;
3. forces en présence ;
4. décision proposée ou adoptée ;
5. invariants ;
6. conséquences positives ;
7. conséquences négatives ;
8. alternatives étudiées ;
9. risques et mitigations ;
10. plan de validation ;
11. critères ou condition d'acceptation ;
12. impact sur les autres décisions.

Les décisions reposant sur une technologie doivent comporter des critères permettant de **revoir ou remplacer** cette technologie si les expérimentations ne confirment pas les hypothèses.

---

## Principe de validation

Une ADR dépendante d'une hypothèse technique doit référencer une preuve C0 ou M0 avant de passer à `Acceptée`.

Les résultats de benchmark ou d'expérimentation doivent préciser au minimum :

```text
hypothèse
dataset
environnement
mesures
limites observées
décision
contraintes
```

Le registre ADR doit donc être lu avec la matrice [`../research/M0_EXPERIMENT_MATRIX.md`](../research/M0_EXPERIMENT_MATRIX.md) pour les décisions soumises à expérimentation.