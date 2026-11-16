---
id: ARCH-0169
title: "OpenSpec comme premier provider de référence, lu sous spec-driven ou sous un schéma qui déclare en dériver, sans verrouillage du domaine"
domain: architecture
rules: true
files: ["morpheus-provider-openspec/src/main/java/**/*.java"]
---

# OpenSpec comme premier provider de référence, lu sous spec-driven ou sous un schéma qui déclare en dériver, sans verrouillage du domaine

- Statut : **Acceptée**
- Date : 2026-11-16
- Remplace : ADR-0002

## Context

MORPHEUS a besoin d'une source de spécifications réelle pour valider son architecture de providers, son ingestion,
sa distinction état courant / changement proposé et sa traçabilité. OpenSpec s'y prête : spécifications conservées
dans le dépôt, organisation par capacités, changements séparés des spécifications courantes (proposition, design,
tâches, deltas), workflow compatible avec plusieurs agents, historique porté par Git. Ces propriétés ne justifient pas
que MORPHEUS dépende structurellement d'OpenSpec : le format évolue, d'autres sources doivent être intégrées, certaines
notions MORPHEUS n'existent pas dans OpenSpec, et le moteur fournit des contrats stables à NEXUS, JARVIS, la CLI et MCP.

Options considérées pour le provider de référence : un format MORPHEUS propriétaire (non retenu : un moteur et un
standard à créer en même temps, sans retour d'expérience), du Markdown libre seul (non retenu comme provider
principal : trop peu de structure pour la traçabilité), un tracker de tickets (différé : pas une source complète de
spécifications vivantes), OpenSpec sous contraintes (retenu).

### Schémas de projet

OpenSpec laisse un projet déclarer son propre schéma (`openspec/config.yaml`, clé `schema:`) et le définir sous
`openspec/schemas/<nom>/`. Les trois projets du mainteneur (`morpheus-engine`, `nexus-context-engine`,
`minos-code-intelligence`) déclarent `detailed-uml`, schéma dérivé du schéma officiel qui ajoute diagrammes et
matrices de traçabilité sans changer la structure lue : `## Why`, `## What Changes`, deltas
`## ADDED|MODIFIED|REMOVED|RENAMED Requirements`, `### Requirement:`, `#### Scenario:`, tâches `- [ ] X.Y`. Une copie
de `nexus-context-engine/openspec` dont la seule ligne `schema:` passe à `spec-driven` est publiée en entier, sans
diagnostic (constat AUD-ARC-22, `docs/project/audits/2026-10-09/findings.md`, revérification du 15/11/2026). La CLI
OpenSpec 1.14.3 tolère une clé `derives:` inconnue dans `schema.yaml` : `openspec schema validate detailed-uml` reste
valide.

Options considérées pour admettre un tel schéma :

- **`derives: spec-driven` dans `openspec/schemas/<schema>/schema.yaml`** — retenue : le schéma dit de qui il dérive,
  la déclaration voyage avec lui d'un dépôt à l'autre, et un projet ne peut pas se dire compatible sans porter la
  définition qu'il qualifie.
- **`derives:` dans `openspec/config.yaml`** — écartée : la déclaration se détache du schéma qu'elle qualifie.
- **Une liste de schémas admis dans MORPHEUS** — écartée : chaque schéma dérivé exigerait une version du produit, qui
  se prononcerait sur des schémas qu'il ne voit pas.
- **Déclarer `spec-driven` dans les projets et porter `detailed-uml` ailleurs** — écartée : le schéma pilote les
  gabarits et le workflow de rédaction amont.
- **Suivre une chaîne de dérivation** — écartée : boucles et profondeur à borner pour aucun besoin constaté.

### Capacités

Le provider déclare les capacités qu'il détecte dans l'arborescence (`OpenSpecSpecificationProvider#detectCapabilities`) :
découverte du projet, spécifications courantes, exigences, scénarios, changements, décisions de design, tâches,
historique et archives. Il ne déclare ni la surveillance (`WATCH_CHANGES`), ni l'écriture (`WRITE_CHANGE`,
`WRITE_TASK_STATE`), ni l'archivage (`ARCHIVE_CHANGE`), ni la lecture de critères d'acceptation
(`READ_ACCEPTANCE_CRITERIA`). La correspondance entre artefacts OpenSpec et concepts MORPHEUS est une donnée d'étude,
tenue dans l'[étude du provider OpenSpec](../../project/research/openspec-provider-study.md) (§5).

OpenSpec est réévalué comme provider de référence si apparaissent une instabilité de format incompatible avec une
intégration fiable, une impossibilité de reconstruire les informations nécessaires, des contraintes de licence
incompatibles, un coût d'adaptation disproportionné, ou une dépendance technique qui fuit dans le domaine. La
stratégie provider reste valide si le provider de référence change.

## Decision

OpenSpec est le **premier provider de référence** : *OpenSpec-first, not OpenSpec-locked*. Il est intégré par
`OpenSpecSpecificationProvider`, adaptateur du port `SpecificationProvider` ; sa connaissance du format s'arrête à la
frontière d'ingestion.

1. **Aucun type OpenSpec dans le domaine ni dans l'application** ; un second provider prouve cette indépendance
   (ADR-0135).
2. **Schéma supporté explicite.** Le provider lit `SUPPORTED_SCHEMA` (`spec-driven`) et un schéma de projet qui
   **déclare** en dériver : `derives: spec-driven` dans `openspec/schemas/<schema>/schema.yaml` du même workspace.
3. **Dériver engage les catégories de `spec-driven`, et rien de plus** : mêmes capacités, schéma rapporté sous son nom
   déclaré ; les artefacts propres au schéma dérivé ne créent aucune catégorie.
4. **Un seul niveau, un seul parent.** Tout autre schéma est refusé à la sonde : `UNSUPPORTED`,
   `UNSUPPORTED_PROVIDER_SCHEMA`, aucune capacité, motif nommé dans `reason`. Une définition illisible, symbolique ou
   non régulière est `INVALID`, `INVALID_SOURCE`, source relative au workspace.
5. **Lecture d'abord** ; une mutation relève d'une capacité distincte (ADR-0083).
6. **Courant et proposé restent deux dimensions séparées** (ADR-0136).
7. **Provenance** : tout élément ingéré renvoie à son fichier source.

Sont exclus : un parent autre que `spec-driven`, une chaîne de dérivation, la lecture des artefacts propres à un
schéma dérivé, et toute déclaration de dérivation hors de la définition du schéma.

## Do's and Don'ts

### Do

- Garder toute connaissance du format OpenSpec dans `morpheus-provider-openspec`.
- Refuser à la sonde un schéma qui n'est ni `SUPPORTED_SCHEMA` ni déclaré `derives: spec-driven` dans sa définition,
  avec `UNSUPPORTED_PROVIDER_SCHEMA` et le motif (`invalid-schema-name`, `schema-definition-missing`,
  `no-declared-parent`, `unsupported-parent`).
- Valider le nom de schéma comme un segment de chemin unique avant de construire `openspec/schemas/<schema>/`.
- Lire la définition du schéma par `SafeWorkspaceFileResolver` ou la session du budget d'ingestion, et n'en extraire que
  la valeur scalaire de `derives`.
- Ne déclarer une capacité que si le provider la détecte dans la source.
- Conserver la provenance de chaque élément ingéré jusqu'au fichier source.

### Don't

- N'exposer aucun type OpenSpec dans `morpheus-domain` ni dans `morpheus-application`.
- Ne pas tenir dans MORPHEUS une liste de schémas dérivés admis.
- Ne pas suivre un parent déclaré au-delà d'un niveau, ni introduire un parseur YAML général dans le provider.
- Ne pas confondre un delta de changement et l'état courant d'une spécification.
- Ne pas faire écrire le provider de lecture.

## Consequences

### Positives

- Les projets qui écrivent sous un schéma dérivé de `spec-driven` sont ingérés sans réécrire leur configuration.
- Le produit ne se prononce que sur ce qu'il lit : la déclaration est portée par le schéma, pas par une version de
  MORPHEUS.
- Terrain de validation concret, séparation état courant / proposé directement testable, fixtures
  `experiments/m0/fixtures/openspec-*`.

### Négatives

- La sonde lit un fichier de plus pour un schéma autre que `spec-driven`.
- La déclaration est une affirmation du projet : un contenu qui ne suit pas la structure n'est refusé qu'à la lecture
  (catégorie `FAILED`, diagnostic attribué), pas à la sonde.
- MORPHEUS ne tire rien des diagrammes ni des matrices propres à `detailed-uml`.

### Risques

- Biais du domaine vers OpenSpec : tenu par l'interdit de dépendance et la preuve du second provider.
- Évolution incompatible du format : mitigée par le schéma supporté explicite, les fixtures et le refus nommé.
- Un schéma dérivé qui diverge de `spec-driven` sans retirer sa déclaration : visible aux diagnostics de lecture.

## Compliance and Enforcement

- Règle Archgate `ARCH-0169-openspec-provider-de-reference-et-schemas-qui-derivent-de-spec-driven.rules.ts` :
  `openspec-provider-declares-no-write-capability`.
- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/LayerDependencyTest.java`
  (`domainMustNotDependOnAdapters`, `applicationMustNotDependOnAdapters`) et `ProviderAntiLockInTest` : ni le domaine
  ni l'application ne dépendent d'un provider, et un consommateur neutre lit les trois formats.
- `morpheus-provider-openspec/src/test/java/com/morpheus/provider/openspec/OpenSpecSpecificationProviderTest.java` :
  refus d'un schéma inconnu sur la fixture `openspec-unsupported-schema`, schéma dérivé lu comme `spec-driven`
  (`aSchemaThatDeclaresItDerivesFromSpecDrivenIsReadAsSpecDriven`), un refus nommé par motif, une définition
  illisible ou symbolique `INVALID`.
- Revue : la provenance jusqu'au fichier source, l'absence d'écriture dans le provider de lecture, l'absence de
  parseur YAML général.

## References

- [ADR-0135](ARCH-0135-domaine-propre-et-neutralite-multi-provider.md) — le domaine appartient à MORPHEUS ; preuve
  anti-verrouillage par un second provider.
- [ADR-0136](ARCH-0136-etat-temporel-et-occurrences-versionnees.md) — état courant et état proposé.
- [ADR-0028](BE-0028-unified-provider-read-contract.md) — contrat de lecture provider unifié.
- [ADR-0083](BE-0083-controlled-lifecycle-write-operations.md) — écritures lifecycle contrôlées.
- Changement OpenSpec archivé `openspec/changes/archive/2026-11-16-accept-a-declared-derived-openspec-schema/` ; spécification
  [`openspec-provider-reading`](../../developer/specifications/openspec-provider-reading.md).
- [VALIDATION_M0](../../project/validation/VALIDATION_M0.md) ;
  [étude du provider OpenSpec](../../project/research/openspec-provider-study.md).
