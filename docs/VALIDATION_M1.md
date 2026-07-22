# Validation M1 — MORPHEUS

Statut : **M1 VALIDÉE — M2 autorisée**

Date : 22 juillet 2026

---

## 1. Décision

La phase **M1 — Découverte des projets et providers** est validée.

Question de sortie :

> **MORPHEUS peut-il découvrir de manière fiable un workspace et ses sources de spécification, enregistrer localement le projet, sélectionner un provider selon ses capacités effectives et produire des diagnostics déterministes sur une fondation Java durable et découplée ?**

Réponse :

```text
OUI — M1 VALIDÉE
M2 — INGESTION ET MODÈLE NORMALISÉ — AUTORISÉE
```

La validation repose sur le gate officiel du dépôt :

```text
.\mvnw.cmd clean test
```

exécuté avec succès le 22 juillet 2026 sur Windows 10 x64.

---

## 2. Incréments M1

M1 a été construit et validé par incréments :

```text
PR #4  Bootstrap Java 21 / Maven Wrapper
PR #5  Provider discovery et sélection par capabilities
PR #6  Workspace discovery robuste et SourceLocator
PR #7  Knowledge Store mémoire / SQLite et migrations
PR #8  Registre local des projets et clôture M1
```

Chaque incrément a été soumis au gate local Maven Wrapper avant intégration.

---

## 3. Porte fonctionnelle M1

Flux obtenu :

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

Critère de sortie de la roadmap :

> Une source supportée est détectée et un provider compatible est sélectionné de manière déterministe et explicable.

État : **SATISFAIT**.

---

## 4. Registre local des projets

M1 fournit :

```text
LocalProjectRegistry
SpecificationKnowledgeStore.findProjectByRoot(...)
SpecificationKnowledgeStore.listProjects()
```

Règles validées :

- normalisation lexicale par `toAbsolutePath().normalize()` ;
- racine représentée par un `SourceLocator` provider-neutral ;
- identité MORPHEUS indépendante du chemin ;
- réenregistrement d'une même racine idempotent ;
- une racine ne peut appartenir qu'à une seule identité ;
- liste déterministe ;
- même contrat sur Memory et SQLite ;
- unicité SQLite verrouillée par `V002__project_root_uniqueness.sql`.

Invariant :

```text
ProjectSpecificationId != SourceLocator
```

---

## 5. Discovery de workspace

ADR-0020 est **Acceptée — M1**.

Politique :

```text
1. chemin explicite en priorité
2. fallback vers un ancêtre .git uniquement si aucune source n'est reconnue
3. aucune dépendance au binaire git
4. workspace non-Git supporté
```

Propriétés validées :

- chemins normalisés ;
- `.git` fichier ou répertoire ;
- monorepo protégé par priorité au chemin explicite ;
- source reconnue mais invalide/non supportée jamais masquée ;
- provenance conservée via `SpecificationSource` / `SourceLocator` ;
- résultat déterministe.

La discovery M1 n'effectue pas de crawl récursif global. Un moteur d'exclusions récursives reste donc différé jusqu'à l'introduction d'un tel crawl.

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

Règles validées :

- provider unsupported refusé ;
- capabilities `required` obligatoires ;
- capabilities `preferred` utilisées comme préférence ;
- capability préférée absente = dégradation explicite ;
- local préféré à remote à capacités équivalentes ;
- remote soumis à opt-in ;
- provider explicite toujours vérifié ;
- départage stable par `providerId` ;
- matches multiples signalés explicitement.

---

## 7. Provider OpenSpec M1

Premier provider de référence :

```text
providerId = openspec
schema     = spec-driven
mode       = local / read-only
```

Signature :

```text
openspec/config.yaml
schema: spec-driven
```

Locator :

```text
file:openspec/config.yaml
```

Invariants validés :

```text
Scenario != AcceptanceCriterion
WRITE_CHANGE      = absent
WRITE_TASK_STATE  = absent
ARCHIVE_CHANGE    = absent
```

Aucune `formatVersion` n'est inventée lorsque la source n'en déclare pas :

```text
formatVersion = empty
```

Les schémas OpenSpec inconnus échouent explicitement avec `UNSUPPORTED_PROVIDER_SCHEMA`.

---

## 8. Diagnostics M1

Catalogue présent :

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

Contrat machine :

```text
code
severity
message
details
source?
```

Les consommateurs machine ne dépendent pas du texte du message humain.

---

## 9. Identité et Knowledge Store

### DomainIdentity

ADR-0015 est portée en production Java :

```text
RFC 9562 UUIDv7
opaque
provider-neutral
```

La composante temporelle du UUIDv7 n'est jamais utilisée comme donnée métier implicite.

### SpecificationKnowledgeStore

Backends conformes au même contrat :

```text
MemorySpecificationKnowledgeStore
SqliteSpecificationKnowledgeStore
```

Invariants validés :

- rejeu idempotent ;
- collision d'identité explicite, jamais fusionnée silencieusement ;
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

Le blob JSON expérimental E08 reste rejeté comme schéma de production.

---

## 10. Invariants M0 portés ou différés

| Invariant | Décision M1 |
|---|---|
| `Scenario != AcceptanceCriterion` | **PORTÉ** |
| collision d'identité sans fusion silencieuse | **PORTÉ** |
| UUIDv7 opaque | **PORTÉ** |
| provider read-only valide | **PORTÉ** |
| capability obligatoire manquante = diagnostic | **PORTÉ** |
| snapshot non actif jamais visible | **PORTÉ** |
| `CURRENT / PROPOSED / HISTORICAL` | **DIFFÉRÉ M3** |
| `ExternalReference` sans cible | **DIFFÉRÉ M2** |

Aucun type métier M2/M3 n'a été créé uniquement pour satisfaire artificiellement une checklist M1.

---

## 11. Fixtures M0 réutilisées

Réutilisées directement :

```text
openspec-basic
openspec-partial
openspec-unsupported-schema
```

Différées avec leur sémantique :

```text
openspec-state-matrix -> M3
identity-scenarios    -> M2/M3
synthetic-basic       -> M2
```

Les fixtures M0 n'ont pas été modifiées pour arranger l'implémentation de production.

---

## 12. Frontières architecturales

ArchUnit impose notamment :

```text
com.morpheus.domain      -X-> provider/store/cli
com.morpheus.application -X-> provider/store/cli
```

SQLite/JDBC et OpenSpec restent des adapters.

Aucun type OpenSpec ne traverse `com.morpheus.domain` et aucun `SQLException` ne devient un contrat public MORPHEUS.

---

## 13. Fondation technique validée

```text
Language             : Java
Compatibility        : Java 21 source / bytecode
Compiler JDK         : Java 21+ avec --release 21
Build                : Maven 3.9.16 + Maven Wrapper
Persistent store     : SQLite JDBC 3.53.1.0 derrière SpecificationKnowledgeStore
Memory store         : référence des tests contractuels
DomainIdentity       : UUIDv7
Remote CI            : optionnelle, non gate
LLM                  : aucun obligatoire
```

Validation effective M1 :

```text
Windows 10 x64
Apache Maven 3.9.16
JDK 24.0.1
javac release 21
```

La portabilité Linux/macOS est recherchée par l'usage des abstractions Java `Path`, mais aucune exécution Linux distincte n'est revendiquée comme preuve M1.

---

## 14. Gate final M1 — preuve

Commande :

```text
.\mvnw.cmd clean test
```

Résultats observés le 22 juillet 2026 :

```text
DomainIdentityTest                         4/4 PASS
LocalProjectRegistryTest                  2/2 PASS
ProjectDiscoveryServiceTest               6/6 PASS
WorkspaceRootResolverTest                 3/3 PASS
ProviderSelectionPolicyTest               6/6 PASS
OpenSpecDiscoveryIntegrationTest          5/5 PASS
OpenSpecSpecificationProviderTest         5/5 PASS
SqliteDriverSmokeTest                     1/1 PASS
SqliteSchemaMigrationTest                 4/4 PASS
LayerDependencyTest                       2/2 PASS
SpecificationKnowledgeStoreContractTest   4/4 PASS

TOTAL                                    42/42 PASS
Failures                                  0
Errors                                    0
BUILD SUCCESS
```

Le warning JDK 24 relatif à `System::load` / `--enable-native-access=ALL-UNNAMED` du driver Xerial SQLite reste non bloquant pour M1 et devra être traité avant stabilisation du packaging/CLI.

---

## 15. Limites acceptées

- pas de canonicalisation physique systématique symlink/junction ;
- pas de discovery récursive globale ;
- pas de moteur d'exclusions récursives ;
- pas d'ingestion métier OpenSpec avant M2 ;
- pas de `ExternalReference` de production avant M2 ;
- pas de `TemporalState` de production avant M3 ;
- pas de CLI fonctionnelle stabilisée ;
- aucune CI distante obligatoire.

Ces limites sont explicites et n'empêchent pas la porte de sortie M1.

---

## 16. Décision finale

```text
M1 = VALIDÉE
M2 = AUTORISÉE
```

Le prochain jalon est :

> **M2 — Ingestion et modèle normalisé**

Contrainte principale de M2 :

> **aucun type OpenSpec ne traverse le domaine MORPHEUS ni ses services publics.**
