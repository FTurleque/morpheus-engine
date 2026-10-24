---
id: ARCH-0002
title: "OpenSpec comme premier provider de référence, sans verrouillage du domaine"
domain: architecture
rules: true
files: ["morpheus-provider-openspec/src/main/java/**/*.java"]
---

# OpenSpec comme premier provider de référence, sans verrouillage du domaine

- Statut : **Acceptée avec contraintes**
- Date : 2026-07-22

## Context

MORPHEUS a besoin d'une source de spécifications réelle pour valider son architecture de providers, son ingestion,
sa distinction état courant / changement proposé et sa traçabilité.

OpenSpec présente plusieurs caractéristiques adaptées :

- spécifications conservées dans le dépôt ;
- organisation par capacités ;
- changements séparés des spécifications courantes ;
- proposition, design, tâches et deltas de specs ;
- workflow compatible avec plusieurs agents ;
- historique géré naturellement par Git ;
- approche adaptée aux codebases existantes.

Ces propriétés en font un bon terrain de validation. Elles ne suffisent pas à justifier que MORPHEUS dépende
structurellement d'OpenSpec :

1. le format peut évoluer ;
2. MORPHEUS doit intégrer d'autres sources ;
3. certaines notions MORPHEUS peuvent ne pas exister directement dans OpenSpec ;
4. certains artefacts OpenSpec peuvent contenir plusieurs concepts métier ;
5. le moteur doit fournir des contrats stables à NEXUS, JARVIS, la CLI et MCP ;
6. MORPHEUS doit pouvoir fonctionner sur un projet sans OpenSpec si un autre provider est disponible.

Options considérées :

- **Concevoir d'abord un format MORPHEUS propriétaire** — non retenue : elle obligerait à créer simultanément un
  moteur et un nouveau standard de spécification, sans retour d'expérience réel.
- **Markdown libre uniquement** — non retenue comme provider principal : le manque de structure rend plus difficile
  la validation de la traçabilité et des changements.
- **Intégrer directement un tracker de tickets** — différée : les trackers apportent des workflows et des
  métadonnées utiles, mais ne constituent pas forcément une source complète de spécifications vivantes.
- **OpenSpec comme provider de référence** — retenue sous contraintes.

## Decision

OpenSpec est le **premier provider de référence** de MORPHEUS. La stratégie se formule ainsi :

> **OpenSpec-first, not OpenSpec-locked.**

OpenSpec est intégré par un adaptateur qui implémente le contrat provider de l'application :

```text
OpenSpecSpecificationProvider
        │
        ▼
SpecificationProvider
        │
        ▼
Ingestion MORPHEUS
```

Le provider peut être riche et connaître précisément OpenSpec, mais cette connaissance s'arrête à la frontière
d'ingestion.

Le choix est assorti de contraintes, toutes en vigueur :

1. **Aucun type OpenSpec dans le domaine ni dans l'application.** Les services MORPHEUS restent indépendants du
   provider ; un second provider démontre cette indépendance (ADR-0135).
2. **Version supportée explicite.** Le provider déclare le schéma OpenSpec qu'il supporte
   (`OpenSpecSpecificationProvider.SUPPORTED_SCHEMA`). Un autre schéma est refusé à la sonde : statut `UNSUPPORTED`,
   diagnostic `UNSUPPORTED_PROVIDER_SCHEMA`, aucune capacité déclarée.
3. **Lecture d'abord.** Les mutations sont une capacité distincte, décidée ailleurs (ADR-0083).
4. **Courant et proposé restent deux dimensions séparées** du modèle (ADR-0136).
5. **Provenance.** Tout élément ingéré permet de revenir à son fichier source.

Capacités : le provider déclare les capacités qu'il détecte dans l'arborescence OpenSpec
(`OpenSpecSpecificationProvider#detectCapabilities`) : découverte du projet, spécifications courantes, exigences,
scénarios, changements, décisions de design, tâches, historique et archives. Il ne déclare ni la surveillance
(`WATCH_CHANGES`), ni l'écriture (`WRITE_CHANGE`, `WRITE_TASK_STATE`), ni l'archivage (`ARCHIVE_CHANGE`), ni la
lecture de critères d'acceptation (`READ_ACCEPTANCE_CRITERIA`).

Mapping conceptuel d'étude ; les règles précises sont documentées et testées :

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
| archive | état / historique de changement |

OpenSpec est réévalué comme provider de référence si apparaissent : une instabilité de format incompatible avec une
intégration fiable, une impossibilité de reconstruire les informations nécessaires, des contraintes de licence
incompatibles, un coût d'adaptation disproportionné, ou une dépendance technique qui fuit inévitablement dans le
domaine. Dans ce cas, la stratégie provider reste valide même si le provider de référence change.

## Do's and Don'ts

### Do

- Garder toute connaissance du format OpenSpec dans `morpheus-provider-openspec`.
- Refuser explicitement, à la sonde, un schéma OpenSpec autre que `SUPPORTED_SCHEMA`, avec un diagnostic nommé.
- Ne déclarer une capacité que si le provider la détecte dans la source.
- Conserver la provenance de chaque élément ingéré jusqu'au fichier source.
- Vérifier que tout consommateur du contrat provider fonctionne aussi avec un autre provider.

### Don't

- N'exposer aucun type OpenSpec dans `morpheus-domain` ni dans `morpheus-application`.
- Ne pas confondre un delta de changement et l'état courant d'une spécification.
- Ne pas faire écrire le provider de lecture : une mutation relève d'une capacité et d'une décision distinctes.

## Consequences

### Positives

- Terrain de validation concret, avec un workflow de changement déjà structuré.
- Séparation état courant / proposé directement testable.
- Forte compatibilité avec une approche agentique et persistance Git native.
- Jeu de données représentatif disponible rapidement (fixtures `experiments/m0/fixtures/openspec-*`).

### Négatives

- Dépendance d'intégration au format OpenSpec dans l'adaptateur.
- Nécessité de suivre les évolutions du format et de versionner le support du provider.
- Besoin de diagnostics lorsque certaines constructions ne sont pas comprises.

### Risques

- Biais du domaine vers OpenSpec : tenu par l'interdit de dépendance et par la preuve du second provider.
- Évolution incompatible du format : mitigée par le schéma supporté explicite, les fixtures et le diagnostic de
  schéma non supporté.
- Confusion delta / état courant : mitigée par le modèle séparant les deux dimensions et ses tests.

## Compliance and Enforcement

- Règle Archgate `ARCH-0002-openspec-reference-provider.rules.ts` : `openspec-provider-declares-no-write-capability`.
- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/LayerDependencyTest.java`
  (`domainMustNotDependOnAdapters`, `applicationMustNotDependOnAdapters`) : ni le domaine ni l'application ne
  dépendent d'un provider.
- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/ProviderAntiLockInTest.java` : OpenSpec,
  Synthetic et Markdown structuré implémentent le même port de lecture, un consommateur neutre lit les trois formats,
  et les identités restent scopées par provider.
- `morpheus-provider-openspec/src/test/java/com/morpheus/provider/openspec/OpenSpecSpecificationProviderTest.java` et
  `OpenSpecDiscoveryIntegrationTest.java` : refus d'un schéma non supporté (`UNSUPPORTED_PROVIDER_SCHEMA`), sur la
  fixture `experiments/m0/fixtures/openspec-unsupported-schema`.
- Revue : la provenance jusqu'au fichier source et l'absence d'écriture dans le provider de lecture.

## References

- [ADR-0135](ARCH-0135-domaine-propre-et-neutralite-multi-provider.md) — le domaine appartient à MORPHEUS.
- [ADR-0136](ARCH-0136-etat-temporel-et-occurrences-versionnees.md) — état courant et état proposé.
- [ADR-0028](BE-0028-unified-provider-read-contract.md) — contrat de lecture provider unifié.
- [ADR-0135](ARCH-0135-domaine-propre-et-neutralite-multi-provider.md) — preuve anti-verrouillage par un second provider.
- [ADR-0083](BE-0083-controlled-lifecycle-write-operations.md) — écritures lifecycle contrôlées.
- [VALIDATION_M0](../../project/validation/VALIDATION_M0.md)
- [Étude du provider OpenSpec](../../project/research/openspec-provider-study.md)
