---
id: ARCH-0084
title: "Composition multi-provider provider-neutral, déterministe et explicable"
domain: architecture
rules: true
files: ["**/src/main/java/**/*.java"]
---

# Composition multi-provider provider-neutral, déterministe et explicable

- Statut : **Acceptée**
- Date : 2026-07-26

## Context

MORPHEUS dispose d'un contrat provider unifié, d'un provider de référence (OpenSpec), d'un provider de preuve
(Synthetic) et d'un second provider réel (Markdown structuré). Un même projet peut être lu depuis plusieurs
providers ; l'ordre d'exécution ne doit pas devenir une politique métier implicite.

Les risques à tenir sont :

- last-write-wins silencieux ;
- perte de provenance lors d'une fusion ;
- confusion entre identifiant provider, chemin source et `DomainIdentity` ;
- rapprochement heuristique non explicable ;
- fuite de types OpenSpec ou Markdown dans le domaine ou l'application ;
- publication d'un snapshot incohérent lorsqu'une source optionnelle est absente.

Les identités sont scopées par provider (ADR-0168) : les deux versions d'une même exigence lues par deux providers
portent deux identités différentes.

Options écartées :

- **Fusionner dans l'ordre des providers** : last-write-wins implicite et non explicable.
- **Réutiliser le chemin source comme identité** : un déplacement de fichier ne doit pas changer l'identité métier.
- **Faire connaître OpenSpec au provider Markdown** : couplage direct entre adaptateurs, composition impossible à
  étendre proprement.
- **Déduire l'identité par similarité textuelle** : heuristique non démontrable et ambiguïtés silencieuses.
- **Appliquer réellement une sélection par précédence.** La clé logique existe (`logicalKey(...)`), la sélection
  serait donc faisable pour spécifications, exigences et changements. Mais l'identité de l'entité écartée est
  référencée, chez son propre provider, par ses scénarios, contraintes, critères d'acceptation et liens de
  traçabilité. La retirer sans remapper ces références les rend orphelines ; les remapper revient à fusionner les
  identités de deux providers, ce qu'ADR-0168 interdit. Une sélection exige une décision sur la corrélation
  inter-provider que cet ADR ne prend pas.

Chaîne de composition et invariants d'identité qu'elle tient :

```text
provider adapter
    -> normalized ProviderContribution
    -> MultiProviderCompositionService
    -> ComposedProjectContent + CompositionConflict*

provider identifier != DomainIdentity
source path != identity
similar text != same entity
```

La valeur candidate de `rootLocator` est une empreinte parce qu'un conflit est publié sur des surfaces distantes, où un
chemin d'hôte ne figure pas. Un ratio de politique devient `UNKNOWN` sur des clés dupliquées parce qu'un ratio ne se
calcule pas sur une population qu'on sait dupliquée.

`CompositionEntityType` énumère projet, spécification, exigence, scénario, changement, contrainte, décision de design,
tâche, critère d'acceptation et identité ; les conflits couvrent ainsi le contenu, l'ownership et le type / l'identité.
Le domaine porte les identités et les faits métier canoniques, les stores l'état de composition.

## Decision

1. La composition (priorité, conflit, optionalité) est de l'application, sur des contributions normalisées ; les
   adaptateurs ne font que formats et parsing, CLI, MCP et HTTP qu'une projection JSON-safe.
2. Une contribution porte au moins `ProviderId`, priorité, contenu normalisé, provenance, diagnostics, caractère
   requis ou optionnel.
3. **Union** : chaque observation est publiée comme entité distincte ; la priorité est enregistrée, jamais appliquée.
4. Rapprochement inter-provider sur une clé logique provider-neutral explicite seulement ; jamais par chemin source,
   ordre de lecture ou texte similaire. Sans `externalId`, une contrainte, décision ou critère n'est pas rapproché.
5. Une clé vue par plusieurs providers produit un `CompositionConflict`, même valeurs égales (type, clé, champ,
   candidats et provenance, priorité) ; vue par un seul, aucun. Sa `CompositionResolution` : `IDENTICAL` (accord de
   tous), `PRECEDENCE_RECORDED` (accord des plus prioritaires, primant enregistré, chaque observation publiée),
   `UNRESOLVED` (divergence, résolution explicite requise). Une valeur absente face à une présente reste une
   observation ; aucun last-write-wins silencieux.
6. `CompositionEntityType` couvre tout le publié ; candidat `rootLocator` : empreinte SHA-256 du localisateur.
7. Un fait `QUALITY_THRESHOLD` de ratio (`REQUIREMENT_COVERAGE_PERCENT`, `TASK_COVERAGE_PERCENT`) est `UNKNOWN` si la
   composition du snapshot actif a des clés dupliquées de ce type ; un comptage inclut les doublons.
8. Provider optionnel absent ou incompatible : diagnostic, sans échec s'il reste une contribution requise valide ;
   provider requis absent : échec.
9. L'état de composition (contributions, provenance, priorité, conflits) est scopé par snapshot, persisté à part en
   mémoire et en SQLite (`V012__multi_provider_composition.sql`, `V020__composition_precedence_recorded.sql`).

## Do's and Don'ts

### Do

- Publier chaque observation de chaque contribution comme une entité distincte.
- Émettre un conflit pour toute clé logique observée par plusieurs providers, `IDENTICAL` compris.
- Enregistrer la précédence (`PRECEDENCE_RECORDED`) sans retirer l'observation des autres providers.
- Publier l'empreinte SHA-256 du localisateur racine, jamais le chemin, comme valeur candidate du projet.
- Rendre `UNKNOWN` un fait de ratio de politique calculé sur une population que la composition sait dupliquée.
- Échouer explicitement quand un provider requis est absent.

### Don't

- Ne jamais appliquer un last-write-wins, ni par ordre de lecture ni par ordre d'écriture.
- Ne jamais créer une identité à partir d'un chemin source, d'un ordre de lecture ou d'une similarité textuelle.
- Ne pas fusionner les identités de deux providers pour rapprocher leurs entités.
- Ne laisser apparaître aucun type `openspec.*` ou `markdown.*` dans le domaine ou l'application.
- Ne pas supprimer un conflit parce que les valeurs observées s'accordent.

## Consequences

### Positives

- Ajout de providers futurs sans modifier le domaine.
- Conflits audités et requêtables ; comportement déterministe.
- Provenance complète préservée ; optionalité explicite.
- Même politique en mémoire et en SQLite.

### Négatives

- Modèle de composition et persistance supplémentaires, surfaces de diagnostic dédiées.
- Besoin de clés logiques explicites pour rapprocher les entités.
- Une composition multi-provider rapporte des conflits `IDENTICAL` sur des accords, et publie des doublons
  d'entités.

### Risques

- Le rapport de qualité (`QualityReportMetrics`) affiche ses ratios en `double` même sur une population dupliquée :
  seule la politique les rend `UNKNOWN`. Leur donner le tri-état exige de changer le type sur toutes les surfaces,
  choix distinct de cet ADR.
- Les comptages incluent les doublons d'entités publiées.

## Compliance and Enforcement

- Règle Archgate `ARCH-0084-provider-neutral-multi-provider-composition.rules.ts` : `no-implicit-precedence-selection`.
- `morpheus-application/src/test/java/com/morpheus/application/composition/MultiProviderCompositionDuplicationTest.java` :
  accord → `IDENTICAL`, divergence → `PRECEDENCE_RECORDED` avec deux entités publiées, types observés, racine jamais
  en clair, cas mono-provider sans conflit.
- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/DuplicatedCompositionPolicyContractTest.java` :
  ratio de politique `UNKNOWN` sous duplication.
- `morpheus-store-sqlite/src/test/java/com/morpheus/store/sqlite/adapter/SqliteCompositionResolutionMigrationTest.java` :
  migration des résolutions persistées.
- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/LayerDependencyTest.java` et
  `ProviderAntiLockInTest.java` : aucune dépendance du domaine ou de l'application vers un provider.

## References

- [ADR-0011](BE-0011-provider-capability-negotiation.md) — sélection des providers par capacités.
- [ADR-0168](DATA-0168-identite-de-domaine-generee-dans-l-ordre.md) — identité persistée scopée par provider.
- [ADR-0028](BE-0028-unified-provider-read-contract.md) — contrat de lecture provider unifié.
- [ADR-0135](ARCH-0135-domaine-propre-et-neutralite-multi-provider.md) — preuve anti-verrouillage.
- [ADR-0093](ARCH-0093-provider-neutral-policy-packs-governance-automation.md) — policy packs et faits `UNKNOWN`.
- [VALIDATION_M18](../../project/validation/VALIDATION_M18.md)
