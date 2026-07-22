# Contrat conceptuel — `SpecificationProvider`

Statut : **Proposition C0 — à valider en M0**

Date : 22 juillet 2026

Ce document décrit les responsabilités attendues d'un provider de spécifications sans imposer de langage d'implémentation ni de framework.

---

## 1. Rôle

Un `SpecificationProvider` sait découvrir et lire une source de spécification particulière puis produire une représentation d'ingestion que MORPHEUS pourra normaliser.

Le provider :

- connaît le format externe ;
- connaît ses versions et conventions ;
- sait produire des diagnostics adaptés ;
- expose explicitement ses capacités ;
- ne définit pas le modèle métier public de MORPHEUS.

---

## 2. Flux

```text
Source externe
    │
    ▼
SpecificationProvider
    │
    ▼
ProviderDocument / ProviderFacts
    │
    ▼
MORPHEUS Ingestion
    │
    ▼
Normalized Domain
```

Le type exact intermédiaire reste à concevoir. Il peut être spécifique au provider à condition de ne jamais franchir la frontière d'ingestion.

---

## 3. Identification

Chaque provider doit exposer au minimum :

```text
providerId
providerVersion
supportedSourceKinds
capabilities
```

Exemple conceptuel :

```text
providerId = "openspec"
providerVersion = "x.y.z"
supportedSourceKinds = [DIRECTORY]
```

Le `providerId` est un identifiant d'adaptateur, pas une identité métier des spécifications.

---

## 4. Détection

Opération candidate :

```text
probe(sourceCandidate) -> ProbeResult
```

`ProbeResult` doit pouvoir indiquer :

```text
SUPPORTED
UNSUPPORTED
AMBIGUOUS
INVALID
```

avec :

- score ou confiance de détection éventuelle ;
- version de format détectée ;
- diagnostics ;
- capacités réellement disponibles pour cette source.

MORPHEUS ne doit pas sélectionner un provider uniquement à partir d'une extension de fichier lorsqu'une détection plus fiable existe.

---

## 5. Capacités

Taxonomie candidate :

```text
DISCOVER_PROJECT
READ_CURRENT_SPECIFICATIONS
READ_CHANGES
READ_REQUIREMENTS
READ_CONSTRAINTS
READ_SCENARIOS
READ_DESIGN_DECISIONS
READ_ACCEPTANCE_CRITERIA
READ_IMPLEMENTATION_TASKS
READ_HISTORY
READ_ARCHIVES
INCREMENTAL_READ
WATCH_CHANGES
WRITE_CHANGE
WRITE_TASK_STATE
ARCHIVE_CHANGE
```

Les capacités d'écriture sont optionnelles et séparées des capacités de lecture.

### Invariant

Un provider parfaitement valide peut être entièrement read-only.

---

## 6. Découverte

Opération candidate :

```text
discover(sourceRoot, options) -> ProviderProjectDescriptor
```

Le descripteur peut contenir :

```text
sourceRoot
formatVersion
currentSpecLocations
changeLocations
archiveLocations
ignoredLocations
providerMetadata
```

MORPHEUS conserve le contrôle sur l'identité logique du projet et sur son registre.

---

## 7. Lecture d'un snapshot

Opération candidate :

```text
readSnapshot(projectDescriptor, readOptions) -> ProviderSnapshot
```

Un snapshot doit être cohérent au niveau défini par le provider.

Il contient potentiellement :

- spécifications courantes ;
- changements ;
- décisions ;
- tâches ;
- archives ;
- références ;
- diagnostics ;
- empreintes permettant l'incrémental.

Le provider doit indiquer clairement les catégories non supportées plutôt que retourner silencieusement une collection vide ambiguë.

---

## 8. Lecture ciblée

Les opérations ciblées sont à évaluer selon le coût du format :

```text
readSpecification(key)
readChange(key)
readArchive(key)
```

Deux stratégies doivent rester possibles :

1. provider optimisé pour accès ciblé ;
2. provider lisant un snapshot complet puis laissant MORPHEUS requêter son store.

L'API publique MORPHEUS ne doit pas dépendre de ce choix.

---

## 9. Synchronisation incrémentale

Un provider déclarant `INCREMENTAL_READ` doit pouvoir produire une forme de changement :

```text
ADDED
MODIFIED
REMOVED
MOVED
UNCHANGED
UNKNOWN
```

Les détails restent propres au provider.

MORPHEUS doit pouvoir retomber sur une lecture complète lorsque :

- le provider ne supporte pas l'incrémental ;
- la version du format change ;
- l'état local est incohérent ;
- la source ne permet pas de déterminer les deltas de manière fiable.

---

## 10. Diagnostics

Un provider doit distinguer au minimum :

```text
INFO
WARNING
ERROR
FATAL
```

avec un code stable et une localisation éventuelle.

Exemples :

```text
UNSUPPORTED_FORMAT_VERSION
MISSING_REQUIRED_FILE
INVALID_FRONT_MATTER
DUPLICATE_EXTERNAL_ID
BROKEN_REFERENCE
PARTIAL_READ
```

MORPHEUS ne doit pas transformer automatiquement un diagnostic provider en erreur métier équivalente sans mapping explicite.

---

## 11. Provenance

Pour chaque entité ou fait importé, le provider doit fournir suffisamment d'information pour construire :

```text
Provenance
Evidence
ExternalReference
```

Informations minimales souhaitées :

- provider ;
- version provider ;
- source ;
- locator ;
- identifiant externe éventuel ;
- révision source éventuelle.

---

## 12. Écriture

L'écriture est une capacité séparée.

Interface conceptuelle possible :

```text
SpecificationWriter
```

ou extension capability-based du provider.

Opérations candidates :

```text
createChange
updateChange
updateTaskState
archiveChange
```

### Règles

- aucune écriture implicite lors d'une lecture ;
- aucune mutation automatique pour « corriger » une source invalide ;
- contrôle explicite des droits ;
- stratégie de conflit requise ;
- idéalement dry-run ou preview avant écriture destructrice ;
- journalisation de la provenance de la mutation.

Le MVP peut rester entièrement read-only.

---

## 13. Version du format

Le provider doit séparer :

```text
provider implementation version
source format version
```

Une nouvelle version du format ne doit pas nécessiter une modification du domaine public si les concepts restent représentables.

Le provider peut déclarer :

```text
SUPPORTED
SUPPORTED_WITH_WARNINGS
UNSUPPORTED
```

pour une version donnée.

---

## 14. Sécurité

Par défaut, un provider local :

- ne doit pas envoyer les sources sur le réseau ;
- ne doit pas exécuter de code arbitraire présent dans les spécifications ;
- ne doit pas suivre des liens externes sans opt-in ;
- doit traiter les fichiers comme des données ;
- doit respecter les exclusions configurées.

Un provider distant doit être explicitement identifiable comme tel.

---

## 15. `SpecificationProviderRegistry`

Responsabilités candidates :

- enregistrer les providers disponibles ;
- exposer leurs capacités ;
- exécuter la détection ;
- arbitrer les collisions de détection ;
- sélectionner le provider adapté à une source ;
- permettre une sélection explicite par configuration.

Le registre ne doit contenir aucune logique métier de normalisation.

---

## 16. Négociation de capacités

Exemple :

```text
Need: READ_CHANGES + READ_ACCEPTANCE_CRITERIA

Provider A:
  READ_CHANGES = yes
  READ_ACCEPTANCE_CRITERIA = yes

Provider B:
  READ_CHANGES = yes
  READ_ACCEPTANCE_CRITERIA = no
```

MORPHEUS peut :

- préférer A ;
- accepter B avec dégradation explicite ;
- refuser B si la capacité est obligatoire pour le cas d'usage.

La politique de négociation appartient à MORPHEUS, pas au provider.

---

## 17. Critères M0

Le provider OpenSpec de référence devra démontrer :

1. détection fiable d'un projet ;
2. lecture des specs courantes ;
3. lecture des changements ;
4. lecture des tâches et critères disponibles ;
5. lecture des archives ;
6. production de provenance précise ;
7. diagnostics exploitables sur fichiers invalides ;
8. absence de types OpenSpec dans le domaine public ;
9. comportement explicite face à une version non supportée ;
10. possibilité de réindexation complète ;
11. temps de lecture mesurable sur plusieurs tailles de dépôt.

---

## 18. Questions ouvertes

- représentation exacte du `ProviderSnapshot` ;
- mécanisme d'extension des capacités ;
- détection de plusieurs formats dans un même workspace ;
- fusion multi-provider ;
- interface d'écriture ;
- granularité de l'incrémental ;
- protocole de watch ;
- compatibilité ascendante des providers.