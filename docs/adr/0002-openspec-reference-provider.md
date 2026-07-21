# ADR-0002 — OpenSpec comme premier provider de référence, sans verrouillage du domaine

- Statut : **Proposée — à valider pendant C0 et M0**
- Date : 22 juillet 2026
- Dépend de : ADR-0001
- Portée : stratégie d'intégration initiale

---

## 1. Contexte

MORPHEUS a besoin d'une source de spécifications réelle pour valider son architecture de providers, son ingestion, sa distinction état courant/changement proposé et sa traçabilité.

OpenSpec présente plusieurs caractéristiques adaptées :

- spécifications conservées dans le dépôt ;
- organisation par capacités ;
- changements séparés des spécifications courantes ;
- proposition, design, tâches et deltas de specs ;
- workflow compatible avec plusieurs agents ;
- historique géré naturellement par Git ;
- approche adaptée aux codebases existantes.

Ces propriétés en font un bon terrain d'expérimentation.

Elles ne suffisent cependant pas à justifier que MORPHEUS dépende structurellement d'OpenSpec.

---

## 2. Décision proposée

Adopter OpenSpec comme **premier provider de référence** pour C0/M0 et le premier vertical slice fonctionnel.

Formulation de la stratégie :

> **OpenSpec-first, not OpenSpec-locked.**

OpenSpec sera intégré via :

```text
OpenSpecSpecificationProvider
        │
        ▼
SpecificationProvider
        │
        ▼
Ingestion MORPHEUS
```

Le provider pourra être riche et connaître précisément OpenSpec, mais cette connaissance s'arrête à la frontière d'ingestion.

---

## 3. Capacités initiales attendues

Le provider de référence doit viser :

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

Les capacités suivantes sont différées sauf nécessité démontrée :

```text
WATCH
WRITE
ARCHIVE
```

---

## 4. Mapping conceptuel candidat

| Source OpenSpec | MORPHEUS |
|---|---|
| spec par capacité | `Specification` |
| requirement | `Requirement` |
| scenario | `Scenario` |
| répertoire de changement | `ChangeProposal` |
| proposal | objectif, motivation, périmètre du changement |
| design | `DesignDecision` ou information de design |
| tasks | `ImplementationTask` |
| spec delta | relations entre `ChangeProposal` et éléments modifiés |
| archive | état/historique de changement |

Ce tableau est un mapping d'étude. Les règles précises doivent être documentées et testées.

---

## 5. Pourquoi OpenSpec n'est pas le domaine MORPHEUS

Plusieurs raisons :

1. le format peut évoluer ;
2. MORPHEUS doit intégrer d'autres sources ;
3. certaines notions MORPHEUS peuvent ne pas exister directement dans OpenSpec ;
4. certains artefacts OpenSpec peuvent contenir plusieurs concepts métier ;
5. le moteur doit fournir des contrats stables à NEXUS/JARVIS/CLI/MCP ;
6. MORPHEUS doit pouvoir fonctionner sur un projet sans OpenSpec si un autre provider est disponible.

---

## 6. Conséquences positives

- terrain de validation concret ;
- workflow de changement déjà structuré ;
- séparation état courant/proposé directement testable ;
- forte compatibilité avec une approche agentique ;
- persistance Git native ;
- possibilité d'obtenir rapidement un jeu de données représentatif.

---

## 7. Conséquences négatives

- dépendance d'intégration au format OpenSpec dans l'adaptateur ;
- nécessité de suivre ses évolutions ;
- risque de biaiser le domaine ;
- nécessité de versionner le support du provider ;
- besoin de diagnostics lorsque certaines constructions ne sont pas comprises.

---

## 8. Alternatives étudiées

### A. Concevoir d'abord un format MORPHEUS propriétaire

**Non retenue pour le premier vertical slice.**

Cela obligerait à créer simultanément un moteur et un nouveau standard de spécification, sans retour d'expérience réel.

### B. Markdown libre uniquement

**Non retenue comme provider principal initial.**

Le manque de structure rendrait plus difficile la validation de la traçabilité et des changements.

### C. Intégrer directement un tracker de tickets

**Différée.**

Les trackers apportent des workflows et métadonnées utiles, mais ne constituent pas forcément une source complète de spécifications vivantes.

### D. OpenSpec comme provider de référence

**Retenue sous contraintes.**

---

## 9. Risques spécifiques

### Évolution incompatible du format

**Mitigation :** version supportée explicitement, tests fixtures, diagnostic `UNSUPPORTED_PROVIDER_VERSION`.

### Confusion delta / état courant

**Mitigation :** tests d'acceptation obligatoires et modèle séparé des deux dimensions.

### Écriture trop tôt

**Mitigation :** lecture d'abord. Les mutations sont une capacité distincte et feront l'objet d'une décision ultérieure.

### Verrouillage organisationnel

**Mitigation :** démontrer un deuxième provider minimal ou synthétique avant acceptation finale de C0/M0.

---

## 10. Plan de validation M0

Construire un corpus de référence comportant :

- plusieurs specs courantes ;
- au moins deux changements actifs ;
- un changement archivé ;
- requirements ;
- scenarios ;
- proposal ;
- design ;
- tasks ;
- delta modifiant une spec existante ;
- cas invalide ;
- référence manquante.

Mesurer :

- taux d'éléments correctement ingérés ;
- diagnostics ;
- temps d'ingestion ;
- capacité à reconstruire les liens ;
- stabilité des identités.

---

## 11. Critères d'acceptation

Cette ADR peut passer à **Acceptée** lorsque :

1. le provider lit correctement le corpus de référence ;
2. la distinction courant/proposé est fiable ;
3. aucun type OpenSpec n'est exposé dans le domaine public ;
4. la provenance permet de revenir au fichier source ;
5. une version non supportée est détectée explicitement ;
6. un second provider de test démontre que les services MORPHEUS restent indépendants.

---

## 12. Critère de remplacement

OpenSpec devra être réévalué comme provider initial si M0 révèle :

- une instabilité de format incompatible avec une intégration fiable ;
- une impossibilité de reconstruire les informations nécessaires ;
- des contraintes de licence incompatibles ;
- un coût d'adaptation disproportionné ;
- une dépendance technique qui fuit inévitablement dans le domaine.

Dans ce cas, la stratégie provider reste valide même si le provider de référence change.