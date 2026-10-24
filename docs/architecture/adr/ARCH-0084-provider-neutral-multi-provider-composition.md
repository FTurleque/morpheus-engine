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

Les identités sont scopées par provider (ADR-0140) : les deux versions d'une même exigence lues par deux providers
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
  identités de deux providers, ce qu'ADR-0140 interdit. Une sélection exige une décision sur la corrélation
  inter-provider que cet ADR ne prend pas.

## Decision

La composition est une responsabilité de l'application, exécutée sur des contributions déjà normalisées :

```text
provider adapter
    -> normalized ProviderContribution
    -> MultiProviderCompositionService
    -> ComposedProjectContent + CompositionConflict*
```

**Contribution.** Chaque contribution expose au minimum : `ProviderId`, priorité explicite, contenu normalisé,
provenance de lecture, diagnostics provider, caractère requis ou optionnel.

**La composition est une union.** Chaque observation de chaque contribution est publiée comme une entité distincte.
Aucune valeur n'est sélectionnée au détriment d'une autre ; la priorité est **enregistrée**, jamais appliquée.

**Identité.** Le rapprochement entre providers se fait uniquement sur une clé logique provider-neutral explicitement
disponible dans le contenu normalisé. Le chemin source, l'ordre de lecture ou un texte similaire ne créent jamais une
identité.

```text
provider identifier != DomainIdentity
source path != identity
similar text != same entity
```

Une contrainte, une décision ou un critère sans `externalId` n'a pour clé logique que son identité scopée par
provider : il n'est pas rapproché d'un provider à l'autre.

**Conflits.** Une clé logique observée par plusieurs providers produit un `CompositionConflict` explicite, même quand
les valeurs s'accordent. Une clé observée par un seul provider n'en produit aucun. Le conflit contient : type
d'entité, clé logique, champ, candidats et provenance, priorité, et une résolution `CompositionResolution` :

| Résolution | Sens |
|---|---|
| `IDENTICAL` | tous les providers s'accordent |
| `PRECEDENCE_RECORDED` | les providers de plus haute priorité s'accordent ; le provider qui prime est enregistré, chaque observation reste publiée |
| `UNRESOLVED` | les providers de plus haute priorité divergent ; une résolution explicite est requise |

`CompositionEntityType` couvre tout ce qui est publié : projet, spécification, exigence, scénario, changement,
contrainte, décision de design, tâche, critère d'acceptation, identité. Pour le projet, la valeur candidate de
`rootLocator` est l'empreinte SHA-256 du localisateur : un conflit est publié sur des surfaces distantes, et un
chemin d'hôte n'y figure pas.

Les conflits de contenu, d'ownership et de type / identité sont représentés explicitement. Une valeur absente face à
une valeur présente reste une observation valide et requêtable. Il n'existe aucun last-write-wins silencieux.

**Population dupliquée.** Un fait de politique `QUALITY_THRESHOLD` portant sur un ratio
(`REQUIREMENT_COVERAGE_PERCENT`, `TASK_COVERAGE_PERCENT`) est `UNKNOWN` quand l'état de composition du snapshot actif
porte des clés dupliquées de ce type : un ratio ne se calcule pas sur une population qu'on sait dupliquée. Les
comptages restent des comptages d'entités publiées, doublons compris.

**Optionalité.** Un provider optionnel absent ou non compatible produit un diagnostic de composition mais ne fait pas
échouer le projet si au moins une contribution requise et valide reste disponible. Un provider requis absent échoue
explicitement.

**Persistance.** L'état de composition est scopé par snapshot et persisté séparément du contenu provider. Les stores
mémoire et SQLite conservent contributions observées, provenance, priorité et conflits (migrations
`V012__multi_provider_composition.sql` et `V020__composition_precedence_recorded.sql` de `morpheus-store-sqlite`).

**Frontières.**

- adaptateurs : formats et parsing ;
- application : composition, priorité, conflit, optionalité ;
- domaine : identités et faits métier canoniques ;
- stores : persistance de l'état de composition ;
- CLI, MCP, HTTP : projection JSON-safe uniquement.

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
- `morpheus-store-sqlite/src/test/java/com/morpheus/store/sqlite/SqliteCompositionResolutionMigrationTest.java` :
  migration des résolutions persistées.
- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/LayerDependencyTest.java` et
  `ProviderAntiLockInTest.java` : aucune dépendance du domaine ou de l'application vers un provider.

## References

- [ADR-0011](BE-0011-provider-capability-negotiation.md) — sélection des providers par capacités.
- [ADR-0140](DATA-0140-identite-de-domaine.md) — identité persistée scopée par provider.
- [ADR-0028](BE-0028-unified-provider-read-contract.md) — contrat de lecture provider unifié.
- [ADR-0135](ARCH-0135-domaine-propre-et-neutralite-multi-provider.md) — preuve anti-verrouillage.
- [ADR-0093](ARCH-0093-provider-neutral-policy-packs-governance-automation.md) — policy packs et faits `UNKNOWN`.
- [VALIDATION_M18](../../project/validation/VALIDATION_M18.md)
