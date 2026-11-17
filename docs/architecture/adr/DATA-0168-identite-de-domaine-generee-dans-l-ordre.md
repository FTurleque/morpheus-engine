---
id: DATA-0168
title: "Identité de domaine : opaque, UUIDv7 générée dans l'ordre par processus, distincte du locator, persistée par clé scopée par provider"
domain: data
rules: false
---

# Identité de domaine : opaque, UUIDv7 générée dans l'ordre par processus, distincte du locator, persistée par clé scopée par provider

- Statut : **Acceptée**
- Date : 2026-11-14
- Remplace : ADR-0140

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

Ordre de génération. L'application trie par identité de nombreuses listes visibles (`ProposedChangeSet`, analyse
d'un changement, qualité des tâches, requêtes de contenu, pages SQLite `ORDER BY id`). Avec un `rand_a` tiré au hasard,
deux identités créées dans la même milliseconde se comparent dans un ordre tiré au sort, et deux ingestions d'une même
source rendent deux ordres différents : une transcription de la CLI (`analyze-change-json`) a ainsi échoué sur la CI
Linux, deux tâches d'un même changement sortant inversées. Options considérées :

- **Trier chaque liste visible par une clé métier** : `Constraint` et `DesignDecision` n'ont aucune clé, celle des
  tâches et des deltas est facultative, et la pagination par identité serait à refaire.
- **Compteur dans `rand_a`** (RFC 9562, section 6.2, méthode 1) : un tri par identité devient un tri par ordre de
  génération dans un processus, sans changer le format.
- **Précision d'horloge accrue** (méthode 3) : l'horloge murale de la JVM ne donne que la milliseconde.
- **`rand_b` incrémenté d'un aléa** (méthode 2) : rend `rand_b` en partie prévisible.

Un `providerId` fait partie de la clé et se range aussi en texte joint par `"\n"` (ensemble des fournisseurs d'une
adhésion de portefeuille dans SQLite) : un caractère de contrôle interne rendrait la relecture ambiguë et ferait
diverger les stores mémoire et SQLite. Options écartées sur ce point : refuser aux seuls points d'entrée du portefeuille
(un appel direct au store resterait ambigu, et la parité ne tiendrait que tant que chaque appelant passe par ces
points) ; encoder l'ensemble en tableau JSON (supprime l'ambiguïté sans rien refuser, mais coûte une migration de
colonne pour accepter des identifiants qu'aucun fournisseur livré ne produit).

Les quatre valeurs que la décision distingue :

- **`DomainIdentity`** : identité logique opaque possédée par MORPHEUS (conceptuellement `RequirementId`,
  `SpecificationId`, `ChangeId`, `ConstraintId`…), sans structure dépendante du provider dans les contrats publics.
- **`EntityVersion`** : un état de l'entité dans un snapshot ou une version de spécification.
- **`SourceLocator`** : où l'élément a été observé (fichier, section, ligne ou plage, JSON pointer, autre locator de
  provider).
- **`ExternalReference`** : l'identifiant ou la clé du système source lorsqu'il existe.

Invariants du modèle : `DomainIdentity != SourceLocator != externalId` ; une même identité logique peut apparaître dans
plusieurs versions et changer de locator ; l'identité finale est persistable indépendamment du provider. Titre, chemin,
énoncé, hash de contenu ou de locator, score de similarité et position dans le document peuvent servir d'indices ou de
preuves, sans jamais fusionner silencieusement deux entités.

Forme et structure d'un UUIDv7 :

```text
xxxxxxxx-xxxx-7xxx-yxxx-xxxxxxxxxxxx

48 bits  Unix timestamp en millisecondes
4 bits   version = 7
12 bits  rand_a
2 bits   variant UUID
62 bits  rand_b
```

`DomainIdentity` vit dans `com.morpheus.domain.identity`. `generate()` applique RFC 9562, section 6.2, méthode 1 :
`MonotonicTimestampSequence` réensemence le compteur à chaque nouvelle milliseconde avec son bit de gauche à 0,
l'incrémente dans la même milliseconde ou quand l'horloge recule, et avance l'horodatage d'une unité quand il déborde.
Deux identités d'un même processus se comparent donc dans l'ordre de leur génération. Opacité : UUIDv7 ne remplace pas
`createdAt` ; l'ordre des UUID ne remplace ni `EntityVersion` ni `SpecificationVersion` et départage seulement les
éléments d'une liste ; le timestamp encodé ne détermine aucun état métier ; un backend peut garder une représentation
native ou binaire tant que le round-trip rend le même UUID canonique ; un identifiant n'est jamais un mécanisme
d'autorisation. Ce format ne fixe pas celui des identifiants externes des providers.

Niveaux de résolution : A, une source fournit un identifiant stable déjà associé (`externalId -> DomainIdentity`) ; B,
sans identifiant stable, une règle documentée fait correspondre sans ambiguïté (clé de spécification + clé d'exigence,
namespace logique + key, identifiant composite normalisé) ; C, titre, contenu ou voisinage suggèrent une continuité
sans la prouver, et aucune fusion de deux identités permanentes n'est automatique ; D, aucune correspondance fiable.

La clé est `EntityIdentityKey = (providerId, entityType, externalId)` et le binding persisté
`EntityIdentityKey -> DomainIdentity (UUIDv7)`. `EntityIdentityStore` est implémenté par le store mémoire et par SQLite.
Un external ID n'est jamais supposé global :

```text
openspec  + requirement + R-1 -> UUID A
synthetic + requirement + R-1 -> UUID B
```

`resolve(providerId, entityType, externalId)` génère et persiste un nouveau `DomainIdentity` faute de binding ; deux
résolutions concurrentes peuvent générer deux candidats, seul le premier binding inséré gagne, le perdant relit et rend
l'identité gagnante. `continueIdentity(providerId, entityType, previousExternalId, newExternalId)` crée un alias vers le
même `DomainIdentity` ; plusieurs external IDs peuvent pointer vers la même identité. `IDENTITY_COLLISION` est un
`DiagnosticCode` ; une collision d'alias vers une autre identité reste une erreur. La table SQLite
`entity_identity_bindings` (`V003__entity_identity_bindings.sql`, migration selon ADR-0021) porte `provider_id`,
`entity_type`, `external_id`, `domain_identity`, avec `PRIMARY KEY(provider_id, entity_type, external_id)` et un index
non unique sur `domain_identity` qui admet plusieurs alias ; ce n'est pas une table d'entités métier. Hors périmètre du
resolver : résolution par similarité, auto-détection de renommage ou de déplacement, fusion automatique de doublons.

Le refus de `ProviderId` porte sur U+0000–U+001F et U+007F–U+009F, par une `IllegalArgumentException` :

```text
provider id must not contain control characters: "openspec[U+000A]markdown"
```

La notation évite la barre oblique inverse parce que les frontières HTTP la lisent comme un chemin Windows
(`ServerLocationDisclosure`) et remplaceraient tout le message par le nom de l'exception. Placée dans le constructeur
compact, la règle vaut pour HTTP, MCP, CLI, `provider.id` des métadonnées de plugin et du codec de probe, et la
relecture des stores, où une ligne fautive est refusée par un message nommé, jamais recomposée.

Mouvements : un déplacement de fichier d'un élément à identité explicite stable garde la `DomainIdentity` avec un
nouveau `SourceLocator` et une nouvelle `EntityVersion` ; le titre est une propriété versionnée ; un renommage de clé
externe ambigu n'entraîne aucune fusion. L'élément supprimé peut garder son état historique et ses liens historiques
explicables, la version courante ne le présente plus comme actif, et sa réapparition n'est rapprochée de l'ancienne
identité que par une règle explicite. Deux providers qui décrivent la même réalité (une exigence OpenSpec et une issue
de tracker) se rapprochent par un `TraceabilityLink`, une `ExternalReference` ou une relation d'équivalence explicite ;
la fusion d'identité reste une opération contrôlée et explicable.

## Decision

1. **Valeurs** : `DomainIdentity`, `EntityVersion`, `SourceLocator`, `ExternalReference` distinctes, `key` distincte
   de `id`. Ni chemin, ligne, titre, énoncé, hash, score de similarité, position, ni d'office un identifiant externe
   ne font l'identité ; aucune fusion silencieuse, une collision produit un diagnostic.
2. **UUIDv7** (RFC 9562) en minuscules ; le constructeur compact de `DomainIdentity` refuse version ou variante autre.
   `generate()` : compteur 12 bits dans `rand_a` (`MonotonicTimestampSequence`, un par processus), `rand_b` par
   `SecureRandom` ; ordre garanti dans un processus seul. `parse()` relit ; sans bibliothèque tierce.
3. **Opacité** : stable tant que la continuité est démontrée ; on n'en lit ni `createdAt`, ni version, ni état
   métier, rien n'y est encodé ; un backend rend le même UUID ; ni secret ni droit.
4. **Résolution** : `RESOLVED` si explicite (A) ou structurelle documentée (B, ou `PARTIALLY_RESOLVED`) ;
   `HEURISTIC` (C), sans fusion hors politique explicite ; sinon nouvelle identité (D).
5. **Clé scopée** : `(providerId, entityType, externalId)` -> `DomainIdentity` (`PersistentEntityIdentityResolver`,
   `EntityIdentityStore`). `resolve` rend ou persiste le binding ; la contrainte persistante arbitre, le perdant
   relit. Seul `continueIdentity` relie deux external IDs (alias d'un ID lié). Un triplet lié ailleurs lève
   `IdentityCollisionException` (`IDENTITY_COLLISION`).
6. **`ProviderId`** refuse en constructeur compact tout `Character.isISOControl` après retrait des blancs, en nommant
   l'identifiant (`[U+XXXX]`, sans barre oblique inverse), et à la relecture une ligne fautive ou à segment vide.
7. **Mouvements** : déplacement et titre gardent l'identité, une clé externe renommée seulement par relation de la
   source ou règle ; une identité disparue n'est jamais réutilisée ; deux providers ne fusionnent jamais d'office.

## Do's and Don'ts

### Do

- Porter identité, version, emplacement et identifiant externe par quatre valeurs distinctes.
- Créer une identité par `DomainIdentity.generate()`, la relire par `DomainIdentity.parse()`, la sérialiser en UUID
  canonique minuscule et restituer le même UUID après un aller-retour par un store.
- Dater et versionner par `createdAt`, `EntityVersion`, `SpecificationVersion`.
- Inclure le `providerId` dans toute clé d'identité externe ; construire tout identifiant de provider par
  `ProviderId`, qui refuse un caractère de contrôle, relecture d'un store comprise.
- Garder la même `DomainIdentity` quand la continuité est démontrable (nouveaux locator et version), et déclarer par
  `continueIdentity` toute continuité entre deux external IDs.
- Laisser la contrainte persistante arbitrer une course d'insertion, puis relire le binding gagnant.
- Signaler toute collision par un diagnostic, jamais par un succès.
- Exposer une `key` lisible à côté de l'`id` opaque ; rapprocher deux providers par un lien explicite.

### Don't

- N'accepter dans `DomainIdentity` qu'un UUID de version 7 et de variante RFC 9562.
- Ne pas faire l'identité d'un chemin, d'une ligne, d'un titre, d'un texte, d'un hash, d'un score ou d'une position.
- N'encoder ni provider, ni chemin, ni titre, ni contenu dans une identité, ni l'exposer avec un type de provider.
- Ne pas lire le timestamp d'une identité comme fait métier ni `createdAt`.
- Ne pas remplir `rand_a` au hasard, ni construire un UUIDv7 de production hors de `generate()`.
- Ne pas supposer d'ordre entre identités de deux processus.
- Ne pas recalculer un UUID à chaque ingestion, ni promouvoir d'office un identifiant externe en identité, ni
  fusionner sur une correspondance heuristique sans politique explicite.
- Ne pas réutiliser l'identité d'un élément disparu.
- Ne pas utiliser une identité comme secret ou preuve d'autorisation.

## Consequences

### Positives

- Historique stable entre exécutions, et moins de faux ajouts/suppressions lors des déplacements.
- Indépendance réelle vis-à-vis du provider ; external IDs découplés des UUID métier ; ingestion incrémentale
  facilitée.
- Un locator peut être corrigé sans casser les références ; la traçabilité inter-versions est meilleure.
- Les collisions sont visibles, et plusieurs sources peuvent coexister.
- Standard UUID sur 128 bits, génération locale sans coordination centrale, sérialisation standard et compatibilité
  avec les types UUID des bases et des pilotes, localité temporelle.
- Une liste triée par identité rend le même ordre à chaque exécution d'un même processus, celui de la génération.
- Comportement identique mémoire/SQLite ; un ensemble de fournisseurs relu depuis SQLite est exactement celui
  enregistré.
- Renommages d'external ID gérables explicitement, sans verrouillage OpenSpec.

### Négatives

- Un mapping `externalId -> DomainIdentity` est à stocker, à maintenir et à sauvegarder avec la base MORPHEUS.
- Un external ID mal attribué exige une procédure explicite de réparation ; les heuristiques de continuité restent
  volontairement absentes.
- Les cas ambigus sont difficiles, l'historique doit être conservé et les tests sont plus complexes.
- L'identité est moins lisible pour l'humain qu'une clé naturelle, et sa représentation texte plus longue qu'un ULID.
- Le temps approximatif de génération est observable dans l'identifiant, et l'ordre de deux identités d'un même
  processus aussi.
- Une rafale de plus de 2048 identités dans une milliseconde fait devancer l'horloge à l'horodatage encodé, d'une
  milliseconde par débordement ; un recul d'horloge fait de même jusqu'à ce que l'horloge rattrape.
- L'ordre de deux identités créées par deux processus dans la même milliseconde reste non garanti.

### Risques

- Faux rapprochement : atténué par les niveaux de résolution, l'ambiguïté explicite et l'absence de fusion heuristique
  silencieuse.
- Fusion heuristique : le modèle d'identité admet une fusion sur correspondance heuristique sous « politique
  explicite » (`Decision`, point 4, niveau C), alors que le resolver persistant n'en fait aucune et exclut la fusion
  automatique de doublons de son périmètre (`Decision`, point 5). Aucune telle politique n'existe ; les deux formulations sont conservées telles
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
  RFC 9562, round-trip de la chaîne canonique, refus d'une valeur non UUIDv7, identifiants opaques et distincts,
  identités d'une même milliseconde comparées dans l'ordre de génération, par `compareTo` et par leur texte.
- `morpheus-domain/src/test/java/com/morpheus/domain/identity/MonotonicTimestampSequenceTest.java` : graine au bit de
  gauche nul, incrément dans la milliseconde, recul d'horloge, débordement vers la milliseconde suivante.
- Le constructeur compact de `DomainIdentity` refuse toute valeur hors version 7 / variante RFC 9562.
- `morpheus-application/src/test/java/com/morpheus/application/identity/PersistentEntityIdentityResolverTest.java` :
  identité stable pour une même clé externe, namespaces de providers indépendants, alias explicite et ses refus,
  collision.
- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/EntityIdentityStoreContractTest.java` : même
  contrat de binding sur mémoire et SQLite ; une clé externe ne pointe jamais vers deux identités.
- `morpheus-store-sqlite/src/test/java/com/morpheus/store/sqlite/adapter/SqliteEntityIdentityStoreTest.java` : binding qui
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
- [ADR-0140](archive/0140-identite-de-domaine.md) — décision précédente, archivée.
- RFC 9562, section 6.2 (*Monotonicity and Counters*).
- [Étude du format d'identité](../../project/research/domain-identity-format.md)
- [VALIDATION_M0](../../project/validation/VALIDATION_M0.md), [VALIDATION_M2](../../project/validation/VALIDATION_M2.md)
- Fixture `experiments/m0/fixtures/identity-scenarios.json`
