# Proposition : publier un projet par le provider avec lequel il est inscrit (AUD-ARC-25)

## Why

La publication d'un projet est câblée sur OpenSpec dans les classes de transport, et un projet inscrit ne porte aucun
provider. Revérifié sur l'arbre courant (`1a539c22`) :

- `new ProjectContentIngestion(new OpenSpecSpecificationContentReader())` dans
  `morpheus-api/.../MorpheusProjectSyncApiService.java:70`, `morpheus-cli/.../command/MorpheusProjectCli.java:153` et
  `MorpheusChangeCli.java:209` ;
- les répertoires analysés par une synchronisation sont `OpenSpecSpecificationProvider.SOURCE_DIRECTORIES`, nommés par
  `MorpheusProjectSyncApiService.java:58` et `MorpheusProjectCli.java:121` ;
- les registres d'écriture ne contiennent qu'OpenSpec : `MorpheusControlledLifecycleCli.java:73-74` et
  `launch/CliProjectWriteCapabilityResolver.java:27-28` (`List.of(new OpenSpecSpecificationProvider())`) ;
- `MorpheusCompositionCli.java:86-103`, que l'audit ne cite pas, construit à son tour les deux providers et les deux
  lecteurs empaquetés ;
- l'entrée persistée d'un projet, `ProjectStoreEntry`, ne porte que `id` et `rootLocator`, et la table `projects`
  (`V001__foundation.sql`) que `id`, `root_scheme` et `root_value`.

La neutralité provider n'est donc prouvée qu'au port (`ProviderAntiLockInTest`) : un projet Structured Markdown ne peut
être ni synchronisé, ni analysé, ni autorisé en écriture par la CLI ou par HTTP, alors qu'ADR-0169 décide
« OpenSpec-first, not OpenSpec-locked » et qu'ARCH-0135 (invariant 6) veut qu'un provider s'ajoute sans modifier les
cas d'usage publics. `TransportsReadAProjectThroughTheProviderPortTest` accepte l'état actuel, puisque le type concret
implémente le port.

Preuve : constat `AUD-ARC-25` (Moyenne, sprint 10), `docs/project/audits/2026-11-17/findings.md`, mesuré par l'audit du
17/11/2026 sur `a04f32bd` et revérifié sur `1a539c22`.

## What Changes

- **Persistance** : l'entrée d'un projet porte l'identifiant du provider avec lequel il est inscrit ; une migration
  SQLite ajoute la colonne et renseigne les projets existants (design.md, D3). Version de schéma : lire
  `SqliteSchemaManager.SUPPORTED_SCHEMA_VERSION`, jamais un littéral.
- **Inscription** (`projects add`, `POST /api/v1/projects`) : le provider est déterminé par la sonde des providers
  empaquetés. Un workspace qu'aucun ne supporte est refusé (`NO_PROVIDER_FOUND`) ; parmi plusieurs, la priorité déclarée
  par le lanceur l'emporte et la réponse nomme le provider retenu et les autres candidats ; à priorité égale, refus
  `MULTIPLE_PROVIDER_MATCHES`. **BREAKING** : un répertoire qu'aucun provider empaqueté ne reconnaît n'est plus
  inscrit. La sélection explicite est une question ouverte (Q3) ; si elle est retenue, la ligne `project.register` de
  `contracts/public-surfaces.tsv` et `ProjectRegistrationRequest` de `docs/developer/reference/openapi/morpheus-v1.yaml`
  changent dans ce changement.
- **Publication et analyse** (`sync`, `POST /api/v1/projects/{projectId}/sync`, `analyze-change`) : le contenu est lu par
  le lecteur du provider inscrit, et les répertoires analysés sont ceux que ce provider déclare.
- **Autorisation d'écriture** (`lifecycle apply`, CLI et HTTP) : `WRITE_CHANGE` est résolu par la sonde du seul
  provider inscrit.
- **Composition** : `composition sync` prend ses providers et leurs priorités, inchangées, dans le même catalogue que
  les autres commandes.
- **Câblage** : un catalogue unique des providers publiables est construit par le câblage du processus et remis aux
  commandes et au serveur HTTP ; aucune classe de transport ne construit plus de provider ni de lecteur.
- **Gate de build** : un test d'architecture refuse la construction d'un type de provider dans `api`, `cli` et `mcp`
  hors du câblage nommé.

## Capabilities

### New Capabilities
- `project-provider-binding` : ce qu'un projet inscrit retient de son provider, comment il est déterminé à
  l'inscription, ce que deviennent les projets déjà inscrits, et la résolution de l'autorisation d'écriture par ce
  provider.

### Modified Capabilities
- `project-ingestion-port` : la publication et l'analyse lisent par le lecteur du provider inscrit, analysent les
  répertoires qu'il déclare, et aucune classe de transport ne construit de provider (exigences ajoutées ; les
  exigences existantes ne changent pas).

## Périmètre et exclusions

**Inclus :** `ProjectStoreEntry` et le port `SpecificationKnowledgeStore` (`morpheus-application`), les deux stores
(`morpheus-store-memory`, `morpheus-store-sqlite` et une migration), le catalogue des providers publiables
(`com.morpheus.application.provider`), les six classes de transport citées, `MorpheusMain`, le serveur HTTP local et
distant qui reçoit le catalogue, `RegisteredProjectWriteCapabilityResolver`, les tests et les pages durables.

**Exclus :**
- les plugins provider : leur code ne s'exécute que dans le worker du probe (SEC-0129) ; un plugin publiable exigerait
  de rouvrir cette décision ;
- `ProjectSpecification` (`morpheus-domain`) : il reste le projet tel qu'un lecteur le produit (design.md, D2) ;
- la valeur des priorités (100 pour OpenSpec, 50 pour Markdown) : elles passent de `MorpheusCompositionCli` au
  catalogue sans changer ;
- l'exposition de `WRITE_CHANGE` par un provider empaqueté : aucun ne la déclare (ADR-0169, `Capacités`), donc
  `lifecycle apply` reste refusé sur un projet réel, comme aujourd'hui ;
- le choix « OpenSpec seul pour la publication » : c'est l'option B de Q1 ; retenue, elle remplace ce changement.

## Acteurs et surfaces concernés

L'utilisateur de la CLI (`projects add`, `sync`, `analyze-change`, `lifecycle apply`, `composition sync`) ; le client
HTTP local et le client distant de rôle WRITE (`POST /api/v1/projects`, `POST /api/v1/projects/{projectId}/sync`,
transitions de cycle de vie) ; le mainteneur qui ajoute un provider empaqueté.

## Nature des informations

| Affirmation | Nature | Source ou preuve |
|---|---|---|
| Trois sites construisent le lecteur OpenSpec, deux le registre d'écriture OpenSpec seul, un les deux providers de composition | existant vérifié | `grep "new OpenSpecSpecification"` sur `morpheus-api`, `morpheus-cli` (sept sites, six classes) |
| Un projet inscrit ne porte aucun provider | existant vérifié | `ProjectStoreEntry`, `V001__foundation.sql` |
| La neutralité est promise | existant vérifié | ADR-0169 (Decision), ARCH-0135 (invariant 6) |
| Aucun provider empaqueté ne déclare `WRITE_CHANGE` | existant vérifié | `grep WRITE_CHANGE morpheus-provider-*/src/main` : seul le provider synthétique, de portée test |
| La version 1.2.2 est ouverte et non publiée | existant vérifié | `released-schema-levels.properties` (dernière ligne `1.2.1`), `ProductMetadata` |
| Neutralité plutôt qu'OpenSpec seul | proposition à valider | Q1 |
| Provider sur l'entrée persistée, pas sur le domaine | proposition à valider | Q2, design.md D2 |
| La fixture `openspec-basic` est supportée par les deux providers | existant vérifié | `experiments/m0/fixtures/openspec-basic/morpheus/specification.md` |
| Priorité déclarée plutôt que refus de l'ambiguïté | proposition à valider | Q3, design.md D6 |
| Projets existants renseignés `openspec` | proposition à valider | Q4, design.md D3 |

## Impact

- Modules : `morpheus-application`, `morpheus-store-memory`, `morpheus-store-sqlite`, `morpheus-api`, `morpheus-cli`,
  `morpheus-architecture-tests`. `morpheus-api` n'a plus besoin de `morpheus-provider-openspec` en portée de
  compilation si le catalogue lui est remis (vérifier par `./mvnw dependency:analyze`).
- Schéma SQLite : une migration. Règle « Une version de schéma est une version de produit »
  (`.claude/rules/governance.md`) : 1.2.2 étant ouverte et non publiée, la migration y entre tant que 1.2.2 n'est pas
  publiée ; sinon elle ouvre la version suivante (Q5). `docs/user/UPGRADE_1_2_2.md` (ou le guide de la version
  ouverte) suit, ainsi que `ReleasedSchemaUpgradeTest`.
- Racines de composition (ADR-0162) : `MorpheusProjectSyncApiService` sort de
  `AdapterCompositionRootArchitectureTest.COMPOSITION_ROOTS` s'il ne construit plus de provider ; la liste rétrécit et
  la clé de `config/debt-counter-ratchets.properties` baisse dans le même changement (justification de PR).
- Tests d'architecture : `TransportsReadAProjectThroughTheProviderPortTest` (nouvelle règle),
  `AdapterCompositionRootArchitectureTest`, `ProviderAntiLockInTest` (preuve au transport) — justification de PR.
- Contrats : `contracts/public-surfaces.tsv` (notes de `project.register` et `project.sync`) et
  `docs/developer/reference/openapi/morpheus-v1.yaml` si Q3 retient le champ explicite ou si un code de refus est ajouté.
- ADR : ADR à proposer (design.md, D1) ; ADR-0169, ARCH-0135 et ADR-0162 sont les décisions concernées.
- Documentation durable : `docs/developer/specifications/project-ingestion-port.md`, la page à créer
  `docs/developer/specifications/project-provider-binding.md`, `docs/user/QUICKSTART.md` si l'inscription change.
- Dépendances entre constats : aucune (`depends_on` et `bloque` vides) ; le change
  `decide-each-persistence-invariant-once-outside-the-stores` du même sprint touche les mêmes stores mais pas les mêmes
  méthodes : l'ordre est libre, à ne pas mener en parallèle sur `SqliteSpecificationKnowledgeStore`.

## Questions ouvertes

- **Q1 (mainteneur)** : neutralité de la publication (option A, proposée) ou « OpenSpec seul pour la publication »
  écrit dans un ADR qui remplace ADR-0169 avec sa règle (option B) ?
- **Q2 (mainteneur)** : le provider vit sur l'entrée persistée `ProjectStoreEntry` (proposé) ou sur
  `ProjectSpecification` du domaine ?
- **Q3 (mainteneur)** : devant un workspace que plusieurs providers supportent, l'inscription retient-elle la priorité
  déclarée et rapporte son choix (proposé), refuse-t-elle et exige-t-elle une sélection explicite, ou accepte-t-elle
  une sélection explicite facultative en plus de la priorité (surface publique) ?
- **Q4 (mainteneur)** : que deviennent les projets déjà inscrits — `openspec` (proposé), sonde à la migration, ou valeur
  absente résolue au premier usage ?
- **Q5 (mainteneur)** : la migration entre-t-elle dans 1.2.2, ou attend-elle la version suivante ?
- **Q6 (mainteneur)** : la décision s'écrit-elle dans un ADR consolidé qui remplace ARCH-0135 (proposé), dans un ADR qui
  remplace ADR-0169, ou dans un ADR nouveau ?
