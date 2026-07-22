# ADR-0025 — Normaliser les deltas de requirements sans projeter l'état temporel

- Statut : **Acceptée — M2**
- Date : 22 juillet 2026
- Dépend de : ADR-0001, ADR-0006, ADR-0009, ADR-0022, ADR-0023, ADR-0024
- Portée : modèle M2, deltas OpenSpec, identité logique des requirements

## 1. Contexte

La fixture M0 `openspec-basic` contient un changement `add-remember-me` avec :

```text
MODIFIED auth-session/session-expiration
ADDED    auth-session/explicit-remember-me-opt-in
ADDED    auth-session/persistent-credential-revocation
```

Le requirement courant `auth-session/session-expiration` existe déjà dans `openspec/specs/auth-session/spec.md`.

M0 a démontré un invariant critique :

```text
CURRENT baseline
+
PROPOSED MODIFIED delta
```

doivent coexister sans que l'ingestion du delta remplace silencieusement la baseline.

## 2. Problème

M2 doit normaliser `ADDED / MODIFIED / REMOVED` sans :

1. injecter les requirements de changement dans la collection des requirements courants ;
2. créer prématurément `TemporalState` ou `SpecificationVersion` M3 ;
3. perdre la continuité d'identité d'un requirement modifié ;
4. confondre l'identité logique du requirement avec l'identité de l'occurrence de delta ;
5. créer dès maintenant les relations de traçabilité `AFFECTS` de M4.

## 3. Décision

Introduire :

```text
RequirementDeltaKind
- ADDED
- MODIFIED
- REMOVED

RequirementDeltaId
RequirementDelta
```

Un `RequirementDelta` contient :

```text
RequirementDeltaId    identité de l'occurrence de changement
ChangeId              changement propriétaire
RequirementDeltaKind  ADDED / MODIFIED / REMOVED
specificationKey      spécification concernée
RequirementId         identité logique du requirement
key                    clé métier provider-neutral
content                titre / statement / scenarios
Provenance             preuve source
```

## 4. Deux identités distinctes

Invariant :

```text
RequirementDeltaId != RequirementId
```

`RequirementDeltaId` identifie l'occurrence :

```text
change X modifies requirement Y
```

`RequirementId` identifie le requirement logique Y.

Pour un `MODIFIED` :

```text
baseline Requirement.id == delta RequirementDelta.requirementId
```

Le contenu reste néanmoins séparé.

Cette séparation prépare le versioning M3 sans l'implémenter en M2.

## 5. Clés externes provider-scoped

Le requirement logique utilise la même clé que le reader courant :

```text
requirement:<specificationKey>/<requirementSlug>
```

Ainsi ADR-0023 retrouve le même `DomainIdentity` pour un requirement modifié déjà connu.

L'occurrence de delta utilise une clé distincte :

```text
requirement-delta:<changeKey>:<kind>:<requirementKey>
```

Les deux mappings restent provider-scoped.

## 6. Scénarios de delta

Les scénarios appartenant au contenu d'un delta restent des `Scenario` MORPHEUS provider-neutral, mais sont embarqués dans `RequirementDelta.scenarios`.

Ils ne sont pas ajoutés à la collection `NormalizedProjectContent.scenarios`, réservée au contenu courant de ce slice.

Chaque scénario de delta doit référencer :

```text
Scenario.requirementId == RequirementDelta.requirementId
```

Un scénario OpenSpec ne devient toujours pas un `AcceptanceCriterion` sans règle explicite.

## 7. ADDED / MODIFIED / REMOVED

### ADDED

Le requirement logique reçoit un nouveau `RequirementId` stable via ADR-0023.

Il n'est pas ajouté à la baseline courante pendant M2.

### MODIFIED

Le requirement logique réutilise son `RequirementId` courant lorsque la même clé externe est connue.

La baseline et le delta restent deux contenus séparés dans l'enveloppe normalisée.

### REMOVED

Le delta peut exister sans statement complet si la source ne fournit que l'identité/titre de l'élément supprimé.

La suppression effective d'une projection courante appartient au traitement temporel/versionné M3.

## 8. Enveloppe normalisée

`NormalizedProjectContent` ajoute :

```text
requirementDeltas
```

et vérifie :

- chaque delta référence un `ChangeId` connu ;
- chaque `RequirementDeltaId` est unique ;
- chaque provenance de delta référence une evidence existante ;
- chaque scénario embarqué possède une evidence existante.

L'enveloppe n'interdit pas qu'un requirement courant et un delta `MODIFIED` partagent le même `RequirementId` : c'est le comportement attendu.

## 9. OpenSpec reader

Le provider ajoute :

```text
OpenSpecRequirementDeltaReader
```

qui lit uniquement :

```text
openspec/changes/<change>/specs/**/spec.md
```

et reconnaît :

```text
## ADDED Requirements
## MODIFIED Requirements
## REMOVED Requirements
```

Le reader projet agrégé devient :

```text
OpenSpecCurrentSpecificationReader
OpenSpecChangeMetadataReader
OpenSpecRequirementDeltaReader
              ↓
OpenSpecProjectContentReader
              ↓
NormalizedProjectContent
```

## 10. Frontière M2 / M3 / M4

Ce slice ne crée pas :

```text
TemporalState
SpecificationVersion complet
promotion CURRENT
application de delta
relation générique AFFECTS
```

Règles :

```text
RequirementDeltaKind != TemporalState
normalized delta != applied delta
structural ownership by ChangeId != traceability graph
```

## 11. Hors périmètre

- historique archivé ;
- projection `CURRENT / PROPOSED / HISTORICAL` ;
- application/promotion des deltas ;
- comparaison de versions ;
- AcceptanceCriterion ;
- ExternalReference ;
- sources partielles ;
- second provider ;
- persistance métier complète ;
- traçabilité M4.

## 12. Critère d'acceptation

ADR-0025 passe à **Acceptée — M2** lorsque le build complet démontre :

1. `openspec-basic` produit exactement 3 `RequirementDelta` ;
2. la répartition est 1 `MODIFIED`, 2 `ADDED`, 0 `REMOVED` ;
3. les deltas contiennent 5 scénarios normalisés ;
4. les deltas/scénarios possèdent provenance + evidence ;
5. le `MODIFIED session-expiration` partage le même `RequirementId` que la baseline courante ;
6. les statements baseline et delta restent simultanément accessibles et différents ;
7. `RequirementDeltaId` reste distinct du `RequirementId` logique ;
8. un delta vers un `ChangeId` absent est rejeté ;
9. le reader agrégé conserve 2 requirements courants + 3 deltas séparés ;
10. aucun `TemporalState`, application de delta ou relation `AFFECTS` n'est introduit ;
11. `.\mvnw.cmd clean test` est vert.

## 13. Preuve d'acceptation — 22 juillet 2026

Gate exécuté sous Windows :

```text
.\mvnw.cmd clean test
Windows 10 x64
Apache Maven 3.9.16
JDK 24.0.1
javac release 21
```

Résultats :

```text
NormalizedProjectContentTest          7/7 PASS
OpenSpecRequirementDeltaReaderTest    4/4 PASS
OpenSpecProjectContentReaderTest      1/1 PASS

TOTAL                                70/70 PASS
Failures                                 0
Errors                                   0
BUILD SUCCESS
```

La fixture `openspec-basic` produit exactement 3 deltas : `1 MODIFIED`, `2 ADDED`, `0 REMOVED`, avec 5 scénarios de delta et 8 evidences. Un test dédié couvre `REMOVED` sans statement et confirme qu'aucun contenu absent n'est inventé.

Le `MODIFIED auth-session/session-expiration` partage le même `RequirementId` que la baseline courante, tandis que `RequirementDeltaId` reste distinct et que les deux statements restent simultanément accessibles. Le graphe agrégé conserve 2 requirements courants + 3 deltas séparés et n'introduit ni `TemporalState`, ni application de delta, ni relation `AFFECTS`.

Le warning JDK 24 `--enable-native-access=ALL-UNNAMED` du driver SQLite reste non bloquant et relève de la stratégie runtime/packaging.
