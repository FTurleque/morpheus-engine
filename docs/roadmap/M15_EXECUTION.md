# M15 — Acceptance Criteria, Verification & Evidence

Statut : **🚧 EN COURS — M15-S1**

Dernière mise à jour : 26 juillet 2026

Issue : **#76**

## 1. Question de sortie

> **MORPHEUS peut-il représenter explicitement ce qui doit être vérifié, l'état réel de cette vérification et les preuves associées, sans confondre scénario, test, critère d'acceptation et preuve ?**

## 2. Baseline d'entrée

```text
C0 → M14       ✅ validés et intégrés
D0             ✅ intégré — PR #75
main           ec75d3963422d6281f2904c5ebd547124db92ad6
M14            357/357 PASS
Architecture   160/160 PASS
Packaging Win  PASS
```

## 3. Constat de départ

Le dépôt possède déjà plusieurs briques nécessaires :

```text
ReadCategory.ACCEPTANCE_CRITERIA
ProviderCapability.READ_ACCEPTANCE_CRITERIA
Evidence / EvidenceId
Provenance
SnapshotBusinessContent
AcceptanceQualityService
AcceptanceCoverageStatus.UNAVAILABLE_IN_NORMALIZED_MODEL
```

Mais il manque encore :

```text
AcceptanceCriterion production type
AcceptanceCriterionId
VerificationStatus production type
acceptance criteria in NormalizedProjectContent
acceptance criteria in SnapshotBusinessContent
snapshot-scoped persistence
traceability kind ACCEPTANCE_CRITERION
real acceptance coverage
query / CLI / MCP / HTTP surfaces
change-orchestration projection
```

## 4. Invariants M15

```text
Scenario != AcceptanceCriterion
AcceptanceCriterion != Test
Test existence != VERIFIED
Evidence != assertion
missing verification evidence != FAILED
UNKNOWN != FAILED
criterion source provenance != verification evidence
verification status is explicit, never guessed from test presence
provider-specific acceptance types never leak into domain
```

Un `AcceptanceCriterion` peut être rattaché à un requirement, à un change, ou aux deux lorsque la source fournit explicitement ces deux relations. Au moins un rattachement métier est obligatoire.

## 5. Modèle cible

```text
AcceptanceCriterion
├── AcceptanceCriterionId
├── optional RequirementId
├── optional ChangeId
├── title
├── condition
├── VerificationStatus
├── verificationEvidenceIds[]
└── Provenance
```

`Provenance.evidenceId` prouve l'origine du critère lui-même. `verificationEvidenceIds` prouve séparément l'état de vérification lorsqu'un état positif/négatif est affirmé.

### VerificationStatus

```text
NOT_VERIFIED
PARTIALLY_VERIFIED
VERIFIED
FAILED
UNKNOWN
```

Règles :

```text
UNKNOWN / NOT_VERIFIED -> verificationEvidenceIds may be empty
PARTIALLY_VERIFIED / VERIFIED / FAILED -> at least one verification evidence required
```

Cette règle empêche qu'un simple booléen provider ou la présence d'un test soit transformé en preuve silencieuse.

## 6. Slices

### M15-S1 — Domaine canonique

- [ ] `AcceptanceCriterionId` MORPHEUS-owned ;
- [ ] `VerificationStatus` ;
- [ ] `AcceptanceCriterion` provider-neutral ;
- [ ] rattachement requirement/change explicite ;
- [ ] séparation provenance du critère / preuves de vérification ;
- [ ] `TraceabilityEntityKind.ACCEPTANCE_CRITERION` ;
- [ ] tests domaine des invariants ;
- [ ] ADR-0081 proposée puis acceptée uniquement après gate vert.

### M15-S2 — Normalisation et providers

- [ ] ajouter les critères à `NormalizedProjectContent` ;
- [ ] vérifier IDs requirement/change/evidence ;
- [ ] étendre Synthetic provider pour preuve anti-lock-in ;
- [ ] projeter OpenSpec uniquement lorsque la source expose explicitement un critère ;
- [ ] ne jamais convertir un `Scenario` en `AcceptanceCriterion` ;
- [ ] conserver `UNKNOWN` lorsqu'aucun état de vérification explicite n'existe.

### M15-S3 — Snapshot et persistance

- [ ] ajouter les critères à `SnapshotBusinessContent` ;
- [ ] Memory store ;
- [ ] migration SQLite ;
- [ ] persistance déterministe ;
- [ ] close/reopen identique ;
- [ ] snapshot ACTIVE/RETIRED uniquement côté requêtes publiées.

### M15-S4 — Traçabilité

- [ ] Requirement -> AcceptanceCriterion ;
- [ ] ChangeProposal -> AcceptanceCriterion ;
- [ ] AcceptanceCriterion -> ExternalReference(test) lorsque explicite ;
- [ ] AcceptanceCriterion -> Evidence via représentation explicable ;
- [ ] traversal/path compatibles ;
- [ ] aucun lien synthétique à partir d'un texte ressemblant à un test.

### M15-S5 — Quality / coverage

- [ ] remplacer `UNAVAILABLE_IN_NORMALIZED_MODEL` lorsque les critères sont réellement présents ;
- [ ] total criteria ;
- [ ] verified / partially verified / failed / not verified / unknown ;
- [ ] coverage ratio documenté ;
- [ ] findings pour critères non vérifiés / échoués / sans preuve ;
- [ ] zéro critère distinct d'un modèle indisponible.

### M15-S6 — Requêtes et surfaces

- [ ] query service acceptance ;
- [ ] compact views ;
- [ ] CLI ;
- [ ] MCP read-only ;
- [ ] HTTP `/api/v1` ;
- [ ] OpenAPI ;
- [ ] JSON canonique stable.

### M15-S7 — Orchestration et gate final

- [ ] alimenter `change-orchestration.acceptanceCriteria` depuis le modèle réel ;
- [ ] ne pas convertir absence de critère en échec ;
- [ ] exposer les états de vérification sans inventer de blocker M16 ;
- [ ] Memory == SQLite ;
- [ ] SQLite reopen ;
- [ ] full Maven gate ;
- [ ] packaging Windows ;
- [ ] `VALIDATION_M15.md` ;
- [ ] roadmap / ADR index.

## 7. Gate M15

```text
acceptanceCriteria.status != UNAVAILABLE_IN_NORMALIZED_MODEL
critères persistés et requêtables
preuves de vérification traçables et explicables
aucune conversion Scenario -> AcceptanceCriterion implicite
Test existence != VERIFIED
UNKNOWN conservé lorsque les faits manquent
Memory == SQLite
SQLite close/reopen identique
CLI/MCP/HTTP cohérents
change-orchestration utilise le modèle réel
packaging Windows PASS
```

## 8. Ordre d'exécution

```text
S1 domain
 ↓
S2 normalization/providers
 ↓
S3 snapshot/persistence
 ↓
S4 traceability
 ↓
S5 quality/coverage
 ↓
S6 CLI/MCP/HTTP
 ↓
S7 orchestration/final gate
```

Chaque slice doit garder le dépôt compilable. Une ADR n'est acceptée qu'après preuve reproductible de la slice correspondante.
