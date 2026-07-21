# Étude — OpenSpec comme provider candidat de MORPHEUS

Statut : **Étude C0 — conclusions à valider**

Date : 22 juillet 2026

---

## 1. Objet

Cette étude évalue OpenSpec comme **premier provider de spécifications** pour MORPHEUS.

Elle ne vise pas à faire d'OpenSpec le domaine de MORPHEUS.

La question est :

> **OpenSpec constitue-t-il une source suffisamment structurée et utile pour valider le modèle de provider de MORPHEUS, tout en restant remplaçable ?**

---

## 2. Caractéristiques observées

La documentation officielle actuelle présente OpenSpec comme un framework léger de développement piloté par les spécifications.

Les spécifications vivent dans le dépôt aux côtés du code et sont organisées par capacité.

Structure typique :

```text
openspec/
├── specs/
│   └── <capability>/
│       └── spec.md
└── changes/
    └── <change-id>/
        ├── proposal.md
        ├── design.md
        ├── tasks.md
        └── specs/
            └── <capability>/
                └── spec.md
```

Le principe central est de distinguer :

- les spécifications courantes ;
- les changements proposés ;
- les deltas de spécification associés à un changement.

Lorsqu'un changement est archivé, son delta est destiné à être intégré aux spécifications courantes.

OpenSpec vise également une utilisation multi-agent et conserve les spécifications dans Git plutôt que dans l'historique d'une conversation.

---

## 3. Intérêt pour MORPHEUS

### 3.1 Séparation état courant / changement

Cette séparation correspond directement à une exigence fondamentale de MORPHEUS.

MORPHEUS doit pouvoir répondre distinctement à :

```text
Que dit actuellement la spécification ?
```

et :

```text
Que propose le changement en cours ?
```

### 3.2 Artefacts structurés

Les fichiers `proposal.md`, `design.md`, `tasks.md` et les deltas de specs fournissent plusieurs types d'informations utiles :

- intention ;
- justification ;
- exigences modifiées ;
- décisions techniques ;
- plan d'implémentation.

### 3.3 Persistance dans Git

Les artefacts étant stockés dans le dépôt :

- ils sont versionnables ;
- reviewables ;
- diffables ;
- partageables entre agents ;
- indépendants d'une session de chat.

### 3.4 Brownfield

Le modèle est intéressant pour des projets existants : les specs peuvent être construites progressivement, capacité par capacité, sans exiger une spécification exhaustive initiale.

---

## 4. Ce que MORPHEUS ne doit pas copier comme domaine

Même si OpenSpec est le premier provider, MORPHEUS ne doit pas considérer automatiquement :

```text
proposal.md = ChangeProposal métier complet
design.md = DesignDecision unique
tasks.md = modèle universel de tâches
spec.md = modèle universel de Specification
```

Ces fichiers sont des **représentations externes**.

Le provider doit les interpréter puis produire des concepts MORPHEUS normalisés.

---

## 5. Mapping candidat

| OpenSpec | Concept MORPHEUS candidat |
|---|---|
| `openspec/specs/<capability>/spec.md` | `Specification` + `Requirement` + `Scenario` |
| `changes/<id>/` | `ChangeProposal` |
| `proposal.md` | intention, justification et métadonnées du `ChangeProposal` |
| `design.md` | une ou plusieurs `DesignDecision` ou éléments de design |
| `tasks.md` | `ImplementationTask` |
| `changes/<id>/specs/...` | deltas liés au `ChangeProposal` |
| archived change | changement terminé/archivé + provenance historique |

Ce mapping doit être prouvé par un spike et ne constitue pas encore un contrat définitif.

---

## 6. Risques de couplage

### Risque 1 — Le domaine reflète la structure de fichiers

Si les entités MORPHEUS deviennent des copies de `proposal.md`, `design.md` et `tasks.md`, un changement de format OpenSpec forcerait une modification du domaine.

**Mitigation :** adaptateur d'ingestion strict et tests d'architecture.

### Risque 2 — Cycle de vie imposé

OpenSpec possède son propre workflow de changements.

MORPHEUS doit pouvoir représenter ce workflow sans supposer qu'il est universel.

**Mitigation :** cycle MORPHEUS conceptuel + mapping de statuts provider.

### Risque 3 — Format évolutif

Le format peut évoluer.

**Mitigation :** détection de version/capacités et erreurs explicites.

### Risque 4 — Confusion entre outil de développement et moteur de connaissance

OpenSpec aide à créer et gérer des changements.

MORPHEUS doit surtout comprendre, normaliser, interroger et relier l'intention.

**Mitigation :** conserver l'écriture comme capacité séparée et non comme prérequis du cœur.

---

## 7. Capacités attendues du provider OpenSpec

### MVP

```text
DISCOVER
CURRENT_SPECIFICATIONS
CHANGES
REQUIREMENTS
SCENARIOS
DESIGN
TASKS
ACCEPTANCE_CRITERIA
HISTORY
```

### Différées

```text
WATCH
WRITE
ARCHIVE
```

Les capacités différées pourront être avancées si les expérimentations démontrent qu'elles sont nécessaires à la cohérence du modèle.

---

## 8. Critères d'acceptation pendant M0

Le provider OpenSpec est considéré viable si :

1. un projet réel est découvert automatiquement ;
2. les specs courantes sont lues ;
3. les changements actifs sont lus séparément ;
4. les deltas sont reliés aux changements ;
5. proposal/design/tasks sont normalisés sans exposer leurs structures propres ;
6. les emplacements sources sont conservés comme provenance ;
7. un changement archivé peut être représenté ;
8. une structure invalide produit un diagnostic explicite ;
9. une version non supportée est détectée ;
10. aucun type OpenSpec n'apparaît dans les interfaces publiques MORPHEUS.

---

## 9. Stratégie candidate

La stratégie recommandée pendant C0 est :

> **OpenSpec-first, not OpenSpec-locked.**

Cela signifie :

- OpenSpec sert de premier provider réel ;
- son workflow fournit un excellent terrain de validation ;
- son format ne devient pas le domaine MORPHEUS ;
- l'architecture doit permettre un second provider sans réécriture du cœur.

---

## 10. Conclusion provisoire

Décision proposée : **ADOPTER AVEC CONTRAINTES comme premier provider de référence**.

Les contraintes sont :

- isolation derrière `SpecificationProvider` ;
- mapping explicite ;
- provenance complète ;
- gestion des versions ;
- aucune fuite de type OpenSpec ;
- écriture différée ;
- validation par un second provider ou un provider de test indépendant.

La décision définitive dépend des expérimentations M0.