# Validation M1 — MORPHEUS

Statut : **CANDIDATE À VALIDATION — dernier gate Windows requis**

Date : 22 juillet 2026

---

## 1. Décision candidate

La phase **M1 — Découverte des projets et providers** est candidate à validation.

La décision finale sera prononcée uniquement après exécution réussie du gate officiel :

```text
.\mvnw.cmd clean test
```

sur la branche de clôture M1.

Question de sortie :

> **MORPHEUS peut-il découvrir de manière fiable un workspace et ses sources de spécification, enregistrer localement le projet, sélectionner un provider selon ses capacités effectives et produire des diagnostics déterministes sur une fondation Java durable et découplée ?**

Réponse candidate :

```text
OUI — sous réserve du dernier BUILD SUCCESS de clôture
```

---

## 2. Incréments M1

M1 a été construit par incréments validés séparément :

```text
PR #4  Bootstrap Java 21 / Maven Wrapper
PR #5  Provider discovery et sélection par capabilities
PR #6  Workspace discovery robuste et SourceLocator
PR #7  Knowledge Store mémoire / SQLite et migrations
PR #8  Registre local des projets et clôture M1
```

Les PR #4 à #7 ont été validées localement sous Windows avant merge.

---

## 3. Porte fonctionnelle M1

Flux de production obtenu :

```text
requested workspace path
        ↓
normalisation lexicale
        ↓
probe au chemin explicite
        ↓
[fallback .git structurel si aucune source reconnue]
        ↓
SpecificationSource / SourceLocator
        ↓
SpecificationProviderRegistry
        ↓
ProviderSelectionPolicy
        ↓
ProjectDiscoveryResult
        ↓
LocalProjectRegistry
        ↓
SpecificationKnowledgeStore
```

### Critère roadmap

> Une source supportée est détectée et un provider compatible est sélectionné de manière déterministe et explicable.

État candidat : **SATISFAIT**.

---

## 4. Registre local des projets

La roadmap M1 exige un registre local des projets.

M1 fournit :

```text
LocalProjectRegistry
SpecificationKnowledgeStore.findProjectByRoot(...)
SpecificationKnowledgeStore.listProjects()
```

Règles :

- la racine est convertie en `SourceLocator` provider-neutral ;
- la normalisation reste lexicale (`toAbsolutePath().normalize()`) conformément à ADR-0020 ;
- réenregistrer la même racine retourne la même identité MORPHEUS ;
- deux identités différentes ne peuvent pas posséder la même racine ;
- la liste des projets est déterministe ;
- le backend mémoire et SQLite appliquent le même contrat ;
- SQLite verrouille l'unicité avec la migration `V002__project_root_uniqueness.sql`.

Invariant :

```text
ProjectSpecificationId != SourceLocator
```

La racine locale n'est jamais utilisée comme `DomainIdentity`.

---

## 5. Discovery de workspace

ADR-0020 est **Acceptée — M1**.

Politique :

```text
1. chemin explicite
2. fallback ancêtre .git uniquement si aucune source n'est reconnue
3. aucune commande git
4. workspace non-Git valide
```

Propriétés démontrées :

- chemins normalisés ;
- `.git` fichier ou répertoire ;
- monorepo protégé par priorité au chemin explicite ;
- source reconnue mais invalide/non supportée jamais masquée ;
- résultat déterministe ;
- provenance via `SpecificationSource` / `SourceLocator`.

### Exclusions

La roadmap mentionne des exclusions de discovery.

M1 **ne réalise aucune exploration récursive globale du filesystem**. La recherche est bornée au chemin explicite puis, si nécessaire, à une racine Git ancêtre unique.

Conséquence : il n'existe pas encore de collection de sous-répertoires à exclure. Un moteur d'exclusions serait artificiel à ce stade.

Décision M1 :

```text
recursive discovery = deferred
exclusion engine     = not required until recursive discovery exists
```

Ce point n'affaiblit pas le critère de sortie M1 et reste explicite.

---

## 6. Provider registry et négociation

Implémenté :

```text
SpecificationProvider
SpecificationProviderRegistry
ProviderCapability
ProviderCapabilitySet
ProviderProbeResult
ProviderSelectionRequest
ProviderSelectionPolicy
ProviderSelectionResult
```

Règles démontrées :

- unsupported refusé ;
- capabilities `required` obligatoires ;
- capabilities `preferred` utilisées comme préférence ;
- absence d'une capability préférée = diagnostic de dégradation ;
- provider local préféré à capacités équivalentes ;
- provider distant soumis à opt-in ;
- provider explicite toujours soumis à compatibilité ;
- égalité résolue de manière déterministe par `providerId` ;
- matches multiples signalés explicitement.

---

## 7. OpenSpec M1

Premier provider de référence :

```text
providerId = openspec
schema     = spec-driven
mode       = local / read-only
```

Signatures M1 :

```text
openspec/config.yaml
schema: spec-driven
```

Locator :

```text
file:openspec/config.yaml
```

Capabilities effectives selon les répertoires réellement présents.

Invariants :

```text
Scenario != AcceptanceCriterion
```

et aucune capability d'écriture n'est annoncée :

```text
WRITE_CHANGE      = absent
WRITE_TASK_STATE  = absent
ARCHIVE_CHANGE    = absent
```

### Version de format

Le contrat `ProviderProbeResult` transporte `formatVersion` lorsqu'un provider peut la déterminer.

Les fixtures OpenSpec M1 ne déclarent pas de version de format distincte du `schema`. MORPHEUS retourne donc :

```text
formatVersion = empty
```

plutôt que d'inventer une version.

---

## 8. Diagnostics

Catalogue M1 présent :

```text
NO_PROVIDER_FOUND
UNSUPPORTED_SOURCE
UNSUPPORTED_PROVIDER_SCHEMA
UNSUPPORTED_FORMAT_VERSION
MISSING_REQUIRED_CAPABILITY
OPTIONAL_CAPABILITY_UNAVAILABLE
MULTIPLE_PROVIDER_MATCHES
EXPLICIT_PROVIDER_INCOMPATIBLE
REMOTE_PROVIDER_REQUIRES_OPT_IN
INVALID_SOURCE
PARTIAL_INGESTION
```

Contrat :

```text
code
severity
message
details
source?
```

Les consommateurs automatiques utilisent `code`, `severity` et `details`, jamais le texte du message humain comme protocole.

---

## 9. Identité et store

### `DomainIdentity`

ADR-0015 est portée en Java :

```text
RFC 9562 UUIDv7
opaque
provider-neutral
```

La composante temporelle du UUIDv7 n'est jamais interprétée comme donnée métier.

### `SpecificationKnowledgeStore`

Deux backends partagent le même contrat :

```text
MemorySpecificationKnowledgeStore
SqliteSpecificationKnowledgeStore
```

Invariants M1 prouvés :

- rejeu idempotent ;
- collision d'identité explicite ;
- snapshot non `ACTIVE` invisible via `activeSnapshot` ;
- activation atomique observable ;
- predecessor obsolète rejeté ;
- ancien `ACTIVE` devient `RETIRED` ;
- persistance SQLite après réouverture.

### Migrations SQLite

ADR-0021 est **Acceptée — M1**.

```text
V001__foundation.sql
V002__project_root_uniqueness.sql
```

Le ledger `schema_migrations` conserve version, nom, checksum SHA-256 et date d'application.

Le blob JSON expérimental E08 reste explicitement rejeté comme schéma de production.

---

## 10. Invariants M0 portés ou différés explicitement

| Invariant M0 | État M1 | Justification |
|---|---|---|
| `Scenario != AcceptanceCriterion` | **PORTÉ** | test OpenSpec + capability absente |
| collision d'identité sans fusion silencieuse | **PORTÉ** | tests contractuels store |
| UUIDv7 opaque | **PORTÉ** | `DomainIdentityTest` |
| provider read-only valide | **PORTÉ** | capabilities d'écriture absentes |
| capability obligatoire manquante = diagnostic | **PORTÉ** | `ProviderSelectionPolicyTest` |
| snapshot non actif jamais visible | **PORTÉ** | contrat mémoire/SQLite |
| `CURRENT / PROPOSED / HISTORICAL` | **DIFFÉRÉ M3** | état temporel complet hors périmètre M1 |
| external reference sans cible possible | **DIFFÉRÉ M2** | `ExternalReference` entre dans le modèle normalisé M2 |

La fermeture M1 ne crée donc aucun type métier uniquement pour cocher une case d'un jalon ultérieur.

---

## 11. Fixtures M0 et non-régression

Réutilisées directement en M1 :

```text
openspec-basic
openspec-partial
openspec-unsupported-schema
```

Les autres fixtures restent associées au jalon dont elles portent la sémantique :

```text
openspec-state-matrix -> M3
identity-scenarios    -> résolution d'identité M2/M3 selon les cas
synthetic-basic       -> normalisation multi-provider M2
```

Règle maintenue : les fixtures M0 ne sont pas modifiées pour arranger l'implémentation de production.

---

## 12. Frontières architecturales

ArchUnit impose notamment :

```text
com.morpheus.domain      -X-> provider/store/cli
com.morpheus.application -X-> provider/store/cli
```

SQLite/JDBC et OpenSpec restent des adapters.

Aucun type OpenSpec ne traverse `com.morpheus.domain`.

Aucun `SQLException` ne devient un contrat public MORPHEUS.

---

## 13. Fondation technique M1

```text
Language             : Java
Compatibility        : Java 21 source / bytecode
Compiler JDK         : Java 21+ avec --release 21
Build                : Maven 3.9.16 + Maven Wrapper
Persistent store     : SQLite 3.53.1.0 derrière SpecificationKnowledgeStore
Memory store         : référence des tests contractuels
DomainIdentity       : UUIDv7
Remote CI            : optionnelle, non gate
LLM                   : aucun obligatoire
```

Validation principale réalisée sous Windows 10 x64 avec JDK 24.0.1 compilant en `release 21`.

Le code de discovery utilise les abstractions Java `Path` et n'appelle aucun binaire Git. La portabilité Linux/macOS est une propriété recherchée de l'implémentation, mais aucune exécution Linux distincte n'est revendiquée comme preuve M1 depuis la suppression volontaire du gate CI distante.

---

## 14. Limites connues acceptées

- pas de canonicalisation physique systématique symlink/junction (`toRealPath`) ;
- pas de discovery récursive globale ;
- pas de moteur d'exclusions tant qu'il n'existe pas de crawl récursif ;
- pas d'ingestion métier OpenSpec M2 ;
- pas de `TemporalState` de production avant M3 ;
- pas d'`ExternalReference` de production avant M2 ;
- pas de CLI fonctionnelle stabilisée ;
- warning JDK 24 sur l'accès natif Xerial SQLite à traiter avant stabilisation packaging/CLI ;
- aucune CI distante obligatoire.

Aucune de ces limites n'empêche le critère de sortie M1.

---

## 15. Gate final attendu

Après les ajouts de clôture, la suite attendue contient **42 tests** :

```text
DomainIdentityTest                         4
LocalProjectRegistryTest                  2
ProjectDiscoveryServiceTest               6
WorkspaceRootResolverTest                 3
ProviderSelectionPolicyTest               6
OpenSpecDiscoveryIntegrationTest          5
OpenSpecSpecificationProviderTest         5
SqliteDriverSmokeTest                     1
SqliteSchemaMigrationTest                 4
LayerDependencyTest                       2
SpecificationKnowledgeStoreContractTest   4

TOTAL                                    42
```

Critère :

```text
Failures = 0
Errors   = 0
BUILD SUCCESS
```

---

## 16. Décision finale à appliquer après le gate

Si le gate final est vert :

```text
M1 = VALIDÉE
M2 = AUTORISÉE
```

Le prochain jalon sera :

> **M2 — Ingestion et modèle normalisé**

avec comme contrainte principale :

> **aucun type OpenSpec ne traverse le domaine MORPHEUS ni ses services publics.**
