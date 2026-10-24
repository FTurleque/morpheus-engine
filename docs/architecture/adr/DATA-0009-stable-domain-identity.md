---
id: DATA-0009
title: "Séparer l'identité logique, la version, l'emplacement et l'identifiant externe"
domain: data
rules: false
---

# Séparer l'identité logique, la version, l'emplacement et l'identifiant externe

- Statut : **Acceptée**
- Date : 2026-07-22

## Context

MORPHEUS ingère des sources externes dont la structure physique peut évoluer indépendamment de l'identité métier des
éléments décrits. Une exigence peut être déplacée dans un autre fichier, changer de titre, être renommée, être réécrite
sans changer de sens, être archivée, être réintroduite, posséder un identifiant externe stable ou n'en posséder aucun,
et être décrite par plusieurs sources.

Si MORPHEUS confond identité et emplacement physique, l'historique devient instable : un simple déplacement de fichier
ressemble à une suppression suivie d'une création. À l'inverse, une stratégie de rapprochement trop agressive peut
fusionner deux exigences distinctes simplement parce qu'elles se ressemblent.

MORPHEUS doit donc répondre séparément à quatre questions, qui ne peuvent pas être portées par une seule valeur :

```text
Qui est cet élément ?
Dans quelle version est-il observé ?
Où est-il décrit dans la source ?
Comment le système externe l'identifie-t-il ?
```

Forces en présence : stabilité des identités face aux mouvements physiques ; fidélité (deux éléments distincts ne sont
pas fusionnés) ; explicabilité de tout rapprochement ; indépendance vis-à-vis du provider ; historique reliant les
versions d'une même entité ; ingestion incrémentale ; simplicité (pas de moteur complexe de record linkage).

Options considérées :

- **Chemin de fichier + section** : rejetée, trop instable et propre au provider.
- **Identifiant externe seul** : rejetée comme modèle général. Tous les providers n'offrent pas d'identifiant stable, et
  un identifiant externe peut changer de sémantique ou de format.
- **Hash du contenu** : rejetée comme identité principale. La moindre modification de texte créerait une nouvelle
  identité alors qu'il peut s'agir de la même exigence. Le hash reste utile comme empreinte de version ou aide au
  rapprochement.
- **Identité sémantique encodée** (`project/spec/file/section/title`) : lisible, mais elle change dès qu'un segment est
  renommé, fait fuir des détails de source, encode mal les collisions, dépend du provider et rend coûteuse toute
  évolution de stratégie.
- **Identité MORPHEUS opaque + clés externes séparées** : retenue, seule option qui sépare proprement domaine, source et
  version.

## Decision

MORPHEUS distingue explicitement :

```text
DomainIdentity
EntityVersion
SourceLocator
ExternalReference
```

- **`DomainIdentity`** : identité logique opaque possédée par MORPHEUS (conceptuellement `RequirementId`,
  `SpecificationId`, `ChangeId`, `ConstraintId`…). Elle n'expose aucune structure dépendante du provider dans les
  contrats publics. Son format concret est UUIDv7 (ADR-0015).
- **`EntityVersion`** : identifie un état de l'entité dans un snapshot ou une version de spécification.
- **`SourceLocator`** : décrit où l'élément a été observé (fichier, section, ligne ou plage, JSON pointer, autre
  locator de provider).
- **`ExternalReference`** : conserve l'identifiant ou la clé du système source lorsqu'il existe.

Une clé lisible peut exister comme `key`, mais elle reste distincte de `id`.

### Invariants

1. un chemin de fichier seul ne constitue jamais une identité logique suffisante ;
2. un numéro de ligne ne constitue jamais une identité logique ;
3. un identifiant externe peut aider à résoudre l'identité mais ne devient pas automatiquement l'identité MORPHEUS ;
4. une même identité logique peut apparaître dans plusieurs versions ;
5. une même identité logique peut changer de locator ;
6. deux entités ambiguës ne sont jamais fusionnées silencieusement ;
7. une collision produit un diagnostic ;
8. l'identité finale est persistable indépendamment du provider.

### Résolution par niveaux de confiance

- **Niveau A — correspondance explicite.** Une source fournit un identifiant stable et non ambigu déjà associé à une
  identité MORPHEUS (`externalId -> DomainIdentity`). Résolution : `RESOLVED`.
- **Niveau B — correspondance structurelle déterministe.** Sans identifiant stable, un ensemble de propriétés permet
  une correspondance non ambiguë selon une règle documentée (clé de spécification + clé d'exigence, namespace logique
  + key, identifiant composite normalisé). Résolution : `RESOLVED` ou `PARTIALLY_RESOLVED` selon la règle.
- **Niveau C — correspondance heuristique.** Titre, contenu ou voisinage suggèrent une continuité sans la prouver.
  Résolution : `HEURISTIC`. Elle ne fusionne jamais automatiquement deux identités permanentes sans politique
  explicite.
- **Niveau D — nouvelle identité.** Aucune correspondance suffisamment fiable n'existe : une nouvelle identité MORPHEUS
  est créée.

Les règles de résolution persistées (clé externe scopée par provider, continuité explicite, collision) sont fixées par
ADR-0023.

### Renommage et déplacement

- Déplacement de fichier d'un élément à identité explicite stable : même `DomainIdentity`, nouveau `SourceLocator`,
  nouvelle `EntityVersion`.
- Renommage de titre : le titre est une propriété versionnée, pas l'identité.
- Renommage de clé externe : si la source fournit une relation de renommage, l'identité est conservée ; sinon une
  règle de résolution s'applique ; en cas d'ambiguïté, aucune fusion automatique.

### Suppression

Lorsqu'un élément disparaît de la source, son identité n'est pas réutilisée, son état historique peut être conservé,
ses liens historiques restent explicables, et la version courante ne le présente plus comme actif. Une réapparition
n'est rapprochée de l'ancienne identité que selon une règle de résolution explicite.

### Plusieurs providers

Deux providers peuvent décrire la même réalité logique (une exigence OpenSpec et une issue de tracker) ; ils ne
deviennent pas automatiquement une seule entité. Le rapprochement passe par un `TraceabilityLink`, une
`ExternalReference` ou une relation d'équivalence explicite. La fusion d'identité reste une opération contrôlée et
explicable.

## Do's and Don'ts

### Do

- Porter identité, version, emplacement et identifiant externe par quatre valeurs distinctes.
- Conserver la même `DomainIdentity` quand la continuité est démontrable, avec un nouveau locator et une nouvelle
  version.
- Signaler toute collision par un diagnostic.
- Exposer une `key` lisible à côté de l'`id` opaque quand c'est utile à l'humain.
- Rapprocher deux providers par un lien ou une référence explicite.

### Don't

- Ne pas utiliser un chemin de fichier, un numéro de ligne, un titre ou un hash de contenu comme identité.
- Ne pas promouvoir automatiquement un identifiant externe en identité MORPHEUS.
- Ne pas fusionner deux identités sur une correspondance heuristique sans politique explicite.
- Ne pas réutiliser l'identité d'un élément disparu.
- Ne pas exposer de type ou de structure propre à un provider dans l'identité publique.

## Consequences

### Positives

- Historique stable, et moins de faux ajouts/suppressions lors des déplacements.
- Indépendance réelle vis-à-vis du provider, et ingestion incrémentale facilitée.
- Un locator peut être corrigé sans casser les références ; la traçabilité inter-versions est meilleure.
- Les collisions sont visibles, et plusieurs sources peuvent coexister.

### Négatives

- Un mécanisme de résolution d'identité et un mapping `externalId -> DomainIdentity` sont à stocker et à maintenir.
- Les cas ambigus sont difficiles, l'historique doit être conservé et les tests sont plus complexes.
- L'identité est moins lisible pour l'humain qu'une clé naturelle.

### Risques

- Faux rapprochement : atténué par les niveaux de résolution, l'ambiguïté explicite et l'absence de fusion heuristique
  silencieuse.
- Explosion d'identités après renommage : atténuée par l'usage des identifiants externes stables et des règles
  déterministes avant toute création.
- Complexité pour l'utilisateur : atténuée par l'exposition de `key` lisibles.
- Évolution de la stratégie : la stratégie d'identité n'est pas encodée dans les contrats publics.

## Compliance and Enforcement

- `morpheus-domain/src/test/java/com/morpheus/domain/identity/DomainIdentityTest.java` : identité opaque, distincte,
  au format canonique.
- `morpheus-application/src/test/java/com/morpheus/application/identity/PersistentEntityIdentityResolverTest.java` :
  identité stable pour une même clé externe, namespaces de providers indépendants, continuité explicite et ses refus.
- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/EntityIdentityStoreContractTest.java` : une clé
  externe ne pointe jamais vers deux identités, sur mémoire et SQLite.
- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/ProviderAntiLockInTest.java` :
  `identicalExternalKeysRemainProviderScoped`.
- Revue : l'absence de fusion heuristique automatique et de réutilisation d'une identité disparue.

## References

- [ADR-0015](DATA-0015-domain-identity-uuidv7.md) — format UUIDv7 de `DomainIdentity`.
- [ADR-0023](DATA-0023-persistent-provider-scoped-entity-identity.md) — identités persistées par clé externe scopée
  par provider.
- [VALIDATION_M0](../../project/validation/VALIDATION_M0.md)
- Fixture `experiments/m0/fixtures/identity-scenarios.json`
