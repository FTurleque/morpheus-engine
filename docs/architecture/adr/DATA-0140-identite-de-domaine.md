---
id: DATA-0140
title: "Identité de domaine : opaque, UUIDv7, distincte du locator, persistée par clé scopée par provider"
domain: data
rules: false
---

# Identité de domaine : opaque, UUIDv7, distincte du locator, persistée par clé scopée par provider

- Statut : **Acceptée**
- Date : 2026-10-24
- Remplace : ADR-0009, ADR-0015, ADR-0023

## Context

MORPHEUS ingère des sources externes dont la structure physique évolue indépendamment de l'identité métier des
éléments décrits. Une exigence peut être déplacée dans un autre fichier, changer de titre, être renommée, être réécrite
sans changer de sens, être archivée, être réintroduite, posséder un identifiant externe stable ou n'en posséder aucun,
et être décrite par plusieurs sources.

Si MORPHEUS confond identité et emplacement physique, l'historique devient instable : un simple déplacement de fichier
ressemble à une suppression suivie d'une création. À l'inverse, une stratégie de rapprochement trop agressive peut
fusionner deux exigences distinctes parce qu'elles se ressemblent. MORPHEUS répond donc séparément à quatre questions,
qu'une seule valeur ne peut pas porter :

```text
Qui est cet élément ?
Dans quelle version est-il observé ?
Où est-il décrit dans la source ?
Comment le système externe l'identifie-t-il ?
```

Forces en présence : stabilité des identités face aux mouvements physiques ; fidélité (deux éléments distincts ne sont
pas fusionnés) ; explicabilité de tout rapprochement ; indépendance vis-à-vis du provider ; historique reliant les
versions d'une même entité ; ingestion incrémentale ; simplicité (pas de moteur complexe de record linkage).

L'identité opaque doit en outre être générable localement et hors ligne, unique sans coordinateur central, sérialisable
de manière stable et transportable par la CLI, MCP, l'API, les stores et les intégrations sans bibliothèque MORPHEUS
spécifique, sur 128 bits compatibles avec les types UUID usuels, dotée d'une localité temporelle favorable aux index
ordonnés et au diagnostic, et standardisée plutôt que propriétaire.

Pour être stable entre deux exécutions, elle doit être mémorisée : la relation entre une identité externe observée et
son `DomainIdentity` est persistée. Le corpus de référence (`experiments/m0/fixtures/identity-scenarios.json`) impose :

```text
move_file               -> même identité si external id stable
rename_title            -> le titre n'est pas l'identité
modify_statement        -> même identité si external id stable
delete_restore          -> même identité si external id stable
provider_collision      -> namespaces providers indépendants
change_external_key     -> continuité uniquement si explicite
conflicting_identifiers -> collision explicite, jamais de fusion silencieuse
```

Options écartées pour le modèle d'identité :

- **Chemin de fichier + section** : trop instable et propre au provider.
- **Identifiant externe seul** : tous les providers n'offrent pas d'identifiant stable, et un identifiant externe peut
  changer de sémantique ou de format.
- **Hash du contenu** comme identité principale : la moindre modification de texte créerait une nouvelle identité. Le
  hash reste utile comme empreinte de version ou aide au rapprochement.
- **Identité sémantique encodée** (`project/spec/file/section/title`) : lisible, mais elle change dès qu'un segment est
  renommé, fait fuir des détails de source, encode mal les collisions, dépend du provider et rend coûteuse toute
  évolution de stratégie.
- **Recalculer un UUID à chaque ingestion**, ou **fusionner automatiquement deux éléments sur similarité
  heuristique** : les références deviendraient instables et l'identité dépendrait de conventions provider ou
  d'heuristiques non prouvées.

Options écartées pour le format :

- **UUIDv4** : simple et universellement supporté, mais sans localité temporelle alors que MORPHEUS crée des séries
  d'entités lors des ingestions. Il reste un repli techniquement valide si une contrainte de runtime rendait UUIDv7
  disproportionné.
- **ULID** : mêmes propriétés (128 bits, horodatage milliseconde, tri lexical), mais UUIDv7 les apporte à l'intérieur
  du standard UUID et de son écosystème de types.
- **Identifiant numérique local** : allocation liée au store, créations hors ligne ou multi-store compliquées.
- **Hash du contenu ou du locator** : confond identité, version et emplacement.
- **Format MORPHEUS propriétaire** : aucun besoin métier ne justifie un nouveau standard d'identifiant.

Un `providerId` fait partie de la clé et se range aussi en texte joint par `"\n"` (ensemble des fournisseurs d'une
adhésion de portefeuille dans SQLite) : un caractère de contrôle interne rendrait la relecture ambiguë et ferait
diverger les stores mémoire et SQLite. Options écartées sur ce point : refuser aux seuls points d'entrée du portefeuille
(un appel direct au store resterait ambigu, et la parité ne tiendrait que tant que chaque appelant passe par ces
points) ; encoder l'ensemble en tableau JSON (supprime l'ambiguïté sans rien refuser, mais coûte une migration de
colonne pour accepter des identifiants qu'aucun fournisseur livré ne produit).

## Decision

### 1. Quatre valeurs distinctes

MORPHEUS distingue explicitement :

```text
DomainIdentity
EntityVersion
SourceLocator
ExternalReference
```

- **`DomainIdentity`** : identité logique opaque possédée par MORPHEUS (conceptuellement `RequirementId`,
  `SpecificationId`, `ChangeId`, `ConstraintId`…). Elle n'expose aucune structure dépendante du provider dans les
  contrats publics.
- **`EntityVersion`** : identifie un état de l'entité dans un snapshot ou une version de spécification.
- **`SourceLocator`** : décrit où l'élément a été observé (fichier, section, ligne ou plage, JSON pointer, autre
  locator de provider).
- **`ExternalReference`** : conserve l'identifiant ou la clé du système source lorsqu'il existe.

Une clé lisible peut exister comme `key`, mais elle reste distincte de `id`. Invariant :
`DomainIdentity != SourceLocator != externalId`.

Invariants du modèle :

1. un chemin de fichier seul ne constitue jamais une identité logique suffisante ;
2. un numéro de ligne ne constitue jamais une identité logique ;
3. un identifiant externe peut aider à résoudre l'identité mais ne devient pas automatiquement l'identité MORPHEUS ;
4. une même identité logique peut apparaître dans plusieurs versions ;
5. une même identité logique peut changer de locator ;
6. deux entités ambiguës ne sont jamais fusionnées silencieusement ;
7. une collision produit un diagnostic ;
8. l'identité finale est persistable indépendamment du provider.

Ne participent jamais implicitement à l'identité :

```text
title    file path / SourceLocator    statement text    content hash    similarity score    position in document
```

Ces informations peuvent servir d'indices ou de preuves, mais ne fusionnent jamais silencieusement deux entités.

### 2. Format : UUIDv7 opaque

**UUIDv7** (RFC 9562) est le format canonique de `DomainIdentity`. Représentation publique : la forme UUID canonique,
en minuscules :

```text
xxxxxxxx-xxxx-7xxx-yxxx-xxxxxxxxxxxx
```

Structure :

```text
48 bits  Unix timestamp en millisecondes
4 bits   version = 7
12 bits  rand_a
2 bits   variant UUID
62 bits  rand_b
```

`DomainIdentity` (`morpheus-domain`, `com.morpheus.domain.identity`) refuse dans son constructeur compact toute valeur
dont la version n'est pas 7 ou la variante n'est pas celle de RFC 9562. `generate()` produit l'identifiant localement :
horodatage courant, `rand_a` et `rand_b` tirés d'un `SecureRandom`. Aucun ordre n'est garanti entre deux identifiants
créés dans la même milliseconde. `parse()` relit la forme canonique. Aucune bibliothèque tierce n'est utilisée.

Le domaine traite la valeur comme **opaque** :

1. `DomainIdentity` est créé une fois puis conservé tant que la continuité logique est démontrée.
2. UUIDv7 ne remplace pas `createdAt`.
3. L'ordre des UUID ne remplace ni `EntityVersion` ni `SpecificationVersion`.
4. Le timestamp encodé ne détermine aucun état métier.
5. Aucun provider, chemin, titre ou contenu n'est encodé dans l'identifiant.
6. Un backend peut utiliser une représentation native ou binaire différente tant que le round-trip produit le même
   UUID canonique.
7. Un identifiant n'est jamais un secret ni un mécanisme d'autorisation.

Ce format ne fixe pas celui des identifiants externes fournis par les providers.

### 3. Résolution par niveaux de confiance

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

### 4. Clé persistée scopée par provider

La clé est provider-scoped :

```text
EntityIdentityKey = (providerId, entityType, externalId)
```

et le binding persisté est `EntityIdentityKey -> DomainIdentity (UUIDv7)`. Le resolver de production est
`PersistentEntityIdentityResolver`, appuyé sur le port `EntityIdentityStore`, implémenté par le store mémoire et par
SQLite. Un external ID n'est jamais supposé global à MORPHEUS :

```text
openspec  + requirement + R-1 -> UUID A
synthetic + requirement + R-1 -> UUID B
```

**Résolution normale.** `resolve(providerId, entityType, externalId)` retourne le binding existant ; sinon il génère un
nouveau `DomainIdentity` UUIDv7 et le persiste. La contrainte persistante est l'arbitre final : deux résolutions
concurrentes sur la même clé peuvent générer deux UUID candidats, seul le premier binding inséré gagne, et le perdant
relit le binding persistant et retourne l'identité gagnante.

**Continuité explicite.** Un changement d'external ID n'est jamais rapproché automatiquement. La continuité se déclare
par `continueIdentity(providerId, entityType, previousExternalId, newExternalId)`, qui crée un nouvel alias vers le même
`DomainIdentity`, uniquement si l'ancien external ID possède déjà un binding. Plusieurs external IDs peuvent donc
pointer vers la même identité.

**Collision.** Le triplet `(providerId, entityType, externalId)` est unique. S'il est déjà lié à une autre identité,
MORPHEUS lève `IdentityCollisionException` ; le code machine associé est `IDENTITY_COLLISION` (`DiagnosticCode`). Une
collision d'alias vers une identité différente reste une erreur et n'est jamais transformée en succès.

**Persistance SQLite.** La migration suit ADR-0021. La table `entity_identity_bindings`
(`V003__entity_identity_bindings.sql`) porte `provider_id`, `entity_type`, `external_id`, `domain_identity`, avec
`PRIMARY KEY(provider_id, entity_type, external_id)` et un index non unique sur `domain_identity` qui admet plusieurs
alias vers la même identité. Elle n'est pas une table d'entités métier.

Hors périmètre du resolver : résolution par similarité, auto-détection de renommage ou de déplacement, fusion
automatique de doublons.

### 5. Identifiant de provider

`ProviderId` refuse tout caractère de contrôle ISO (`Character.isISOControl` : U+0000–U+001F et U+007F–U+009F) qui
reste après le retrait des blancs d'extrémité, par une `IllegalArgumentException` dont le message nomme l'identifiant,
caractères de contrôle écrits `[U+XXXX]` :

```text
provider id must not contain control characters: "openspec[U+000A]markdown"
```

La notation ne porte pas de barre oblique inverse : les frontières HTTP lisent une barre oblique inverse comme un
chemin Windows (`ServerLocationDisclosure`) et remplaceraient tout le message par le nom de l'exception. La règle vit
dans le constructeur compact : elle vaut pour chaque chemin qui fabrique un identifiant — HTTP, MCP, CLI,
`provider.id` des métadonnées de plugin et du codec de probe, relecture des stores. Une ligne stockée qui porterait un
identifiant à caractère de contrôle, ou un segment vide, est refusée à la relecture par un message nommé, jamais
recomposée.

### 6. Renommage, déplacement, suppression, plusieurs providers

- Déplacement de fichier d'un élément à identité explicite stable : même `DomainIdentity`, nouveau `SourceLocator`,
  nouvelle `EntityVersion`.
- Renommage de titre : le titre est une propriété versionnée, pas l'identité.
- Renommage de clé externe : si la source fournit une relation de renommage, l'identité est conservée ; sinon une
  règle de résolution s'applique ; en cas d'ambiguïté, aucune fusion automatique.
- Suppression : l'identité d'un élément disparu n'est pas réutilisée, son état historique peut être conservé, ses liens
  historiques restent explicables, et la version courante ne le présente plus comme actif. Une réapparition n'est
  rapprochée de l'ancienne identité que selon une règle de résolution explicite.
- Deux providers qui décrivent la même réalité logique (une exigence OpenSpec et une issue de tracker) ne deviennent pas
  automatiquement une seule entité. Le rapprochement passe par un `TraceabilityLink`, une `ExternalReference` ou une
  relation d'équivalence explicite ; la fusion d'identité reste une opération contrôlée et explicable.

## Do's and Don'ts

### Do

- Porter identité, version, emplacement et identifiant externe par quatre valeurs distinctes.
- Créer une identité par `DomainIdentity.generate()` et relire sa forme texte par `DomainIdentity.parse()`.
- Sérialiser une identité sous sa forme UUID canonique en minuscules, et restituer exactement le même UUID après un
  aller-retour par un store.
- Trier et dater les entités par des champs explicites (`createdAt`, `EntityVersion`, `SpecificationVersion`).
- Inclure le `providerId` dans toute clé d'identité externe.
- Conserver la même `DomainIdentity` quand la continuité est démontrable, avec un nouveau locator et une nouvelle
  version.
- Déclarer explicitement par `continueIdentity` toute continuité entre deux external IDs.
- Laisser la contrainte persistante arbitrer une course d'insertion, et relire le binding gagnant.
- Signaler toute collision par un diagnostic.
- Construire tout identifiant de provider par `ProviderId`, y compris à la relecture d'un store.
- Exposer une `key` lisible à côté de l'`id` opaque quand c'est utile à l'humain.
- Rapprocher deux providers par un lien ou une référence explicite.

### Don't

- N'accepter dans `DomainIdentity` aucun UUID dont la version n'est pas 7 ou la variante n'est pas RFC 9562.
- Ne pas utiliser un chemin de fichier, un numéro de ligne, un titre, un texte, un hash de contenu ou de locator, un
  score de similarité ou une position comme identité.
- N'encoder ni provider, ni chemin, ni titre, ni contenu dans une identité.
- Ne pas lire le timestamp d'une identité comme un fait métier ni comme `createdAt`.
- Ne pas recalculer un UUID à chaque ingestion.
- Ne pas promouvoir automatiquement un identifiant externe en identité MORPHEUS.
- Ne pas fusionner deux identités sur une correspondance heuristique sans politique explicite.
- Ne pas transformer une collision d'identité en succès.
- Ne pas réutiliser l'identité d'un élément disparu.
- Ne pas exposer de type ou de structure propre à un provider dans l'identité publique.
- Ne pas accepter un caractère de contrôle dans un identifiant de provider.
- Ne pas utiliser une identité comme secret ou comme preuve d'autorisation.

## Consequences

### Positives

- Historique stable entre exécutions, et moins de faux ajouts/suppressions lors des déplacements.
- Indépendance réelle vis-à-vis du provider ; external IDs découplés des UUID métier ; ingestion incrémentale
  facilitée.
- Un locator peut être corrigé sans casser les références ; la traçabilité inter-versions est meilleure.
- Les collisions sont visibles, et plusieurs sources peuvent coexister.
- Standard UUID sur 128 bits, génération locale sans coordination centrale, sérialisation standard et compatibilité
  avec les types UUID des bases et des pilotes, localité temporelle.
- Comportement identique mémoire/SQLite ; un ensemble de fournisseurs relu depuis SQLite est exactement celui
  enregistré.
- Renommages d'external ID gérables explicitement, sans verrouillage OpenSpec.

### Négatives

- Un mapping `externalId -> DomainIdentity` est à stocker, à maintenir et à sauvegarder avec la base MORPHEUS.
- Un external ID mal attribué exige une procédure explicite de réparation ; les heuristiques de continuité restent
  volontairement absentes.
- Les cas ambigus sont difficiles, l'historique doit être conservé et les tests sont plus complexes.
- L'identité est moins lisible pour l'humain qu'une clé naturelle, et sa représentation texte plus longue qu'un ULID.
- Le temps approximatif de génération est observable dans l'identifiant, et l'ordre à l'intérieur d'une milliseconde
  n'est pas garanti.

### Risques

- Faux rapprochement : atténué par les niveaux de résolution, l'ambiguïté explicite et l'absence de fusion heuristique
  silencieuse.
- Fusion heuristique : le modèle d'identité admet une fusion sur correspondance heuristique sous « politique
  explicite » (§3, niveau C), alors que le resolver persistant n'en fait aucune et exclut la fusion automatique de
  doublons de son périmètre (§4). Aucune telle politique n'existe ; les deux formulations sont conservées telles
  qu'écrites par les décisions sources.
- Explosion d'identités après renommage : atténuée par l'usage des identifiants externes stables et des règles
  déterministes avant toute création.
- Complexité pour l'utilisateur : atténuée par l'exposition de `key` lisibles.
- Utilisation du timestamp comme `createdAt` ou ordre trompeur : atténués par un `createdAt` explicite, l'opacité de
  l'identité et des tris métier sur champs explicites.
- Fuite temporelle : acceptée pour des identifiants techniques non secrets ; à réévaluer seulement si une surface
  publique l'interdit.
- Une base contenant un identifiant de provider à caractère de contrôle — seul un plugin tiers aurait pu l'y écrire —
  cesserait d'être lisible sur ces lignes, par un refus nommé et non par un repli silencieux. Aucun fournisseur livré
  n'en produit.
- La stratégie d'identité n'est pas encodée dans les contrats publics : elle peut évoluer sans les casser.

## Compliance and Enforcement

- `morpheus-domain/src/test/java/com/morpheus/domain/identity/DomainIdentityTest.java` : génération conforme UUIDv7
  RFC 9562, round-trip de la chaîne canonique, refus d'une valeur non UUIDv7, identifiants opaques et distincts.
- Le constructeur compact de `DomainIdentity` refuse toute valeur hors version 7 / variante RFC 9562.
- `morpheus-application/src/test/java/com/morpheus/application/identity/PersistentEntityIdentityResolverTest.java` :
  identité stable pour une même clé externe, namespaces de providers indépendants, alias explicite et ses refus,
  collision.
- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/EntityIdentityStoreContractTest.java` : même
  contrat de binding sur mémoire et SQLite ; une clé externe ne pointe jamais vers deux identités.
- `morpheus-store-sqlite/src/test/java/com/morpheus/store/sqlite/SqliteEntityIdentityStoreTest.java` : binding qui
  survit à la réouverture de la base.
- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/PersistentIdentityOpenSpecIntegrationTest.java`
  : le reader OpenSpec retrouve les mêmes identités après réouverture.
- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/ProviderAntiLockInTest.java`
  (`identicalExternalKeysRemainProviderScoped`) : clés externes identiques, identités distinctes par provider.
- `morpheus-domain/src/test/java/com/morpheus/domain/provider/ProviderIdTest.java` : refus des caractères de contrôle
  et forme du message.
- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/m23/PortfolioPersistenceParityTest.java` :
  résultats identiques des deux stores, refus compris, et ensemble de fournisseurs relu égal à celui enregistré.
- Revue : opacité de l'identité, absence d'usage métier de son timestamp, absence de fusion heuristique automatique et
  de réutilisation d'une identité disparue.

## References

- [ADR-0135](ARCH-0135-domaine-propre-et-neutralite-multi-provider.md) — domaine indépendant des providers.
- [ADR-0133](GEN-0133-toolchain-java-21-et-maven-wrapper.md) — toolchain Java 21 de production.
- [ADR-0021](DATA-0021-sqlite-schema-migrations-foundation.md) — migrations SQLite.
- [ADR-0121](ARCH-0121-modele-normalise-neutre-vis-a-vis-des-providers.md) — modèle normalisé provider-neutral.
- [Étude du format d'identité](../../project/research/domain-identity-format.md)
- [VALIDATION_M0](../../project/validation/VALIDATION_M0.md), [VALIDATION_M2](../../project/validation/VALIDATION_M2.md)
- Fixture `experiments/m0/fixtures/identity-scenarios.json`
