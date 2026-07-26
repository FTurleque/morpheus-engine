# M15 — Acceptance Criteria, Verification & Evidence

Statut : **✅ VALIDÉ TECHNIQUEMENT — PR #77 prête à intégrer**

Dernière mise à jour : 26 juillet 2026

Issue : **#76**  
Branche : `m15/acceptance-verification-evidence`  
PR : **#77**

Head de code validé : `9e6450a099157cfdfcd11cc29dfb986ef7701247`

Preuve : [`../validation/VALIDATION_M15.md`](../validation/VALIDATION_M15.md)

## 1. Question de sortie

> **MORPHEUS peut-il représenter explicitement ce qui doit être vérifié, l'état réel de cette vérification et les preuves associées, sans confondre scénario, test, critère d'acceptation et preuve ?**

**Réponse : OUI.**

## 2. Baseline d'entrée

```text
C0 → M14       ✅ validés et intégrés
D0             ✅ intégré — PR #75
main entrée    ec75d3963422d6281f2904c5ebd547124db92ad6
M14            357/357 PASS
Architecture   160/160 PASS
Packaging Win  PASS
```

## 3. Résultat M15

M15 ferme l'ancien gap :

```text
AcceptanceCoverageStatus.UNAVAILABLE_IN_NORMALIZED_MODEL
```

avec un modèle réellement exploitable de bout en bout :

```text
AcceptanceCriterion production type
AcceptanceCriterionId
VerificationStatus
acceptance criteria in NormalizedProjectContent
acceptance criteria in SnapshotBusinessContent
snapshot-scoped SQLite persistence V009
traceability kinds ACCEPTANCE_CRITERION + EVIDENCE
real acceptance verification coverage
business query + CLI + MCP + HTTP surfaces
change-orchestration availability projection
OpenAPI contract 1.4.0
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

## 5. Modèle validé

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

`Provenance.evidenceId` prouve l'origine du critère. `verificationEvidenceIds` prouve séparément l'état de vérification lorsqu'un état positif/négatif est affirmé.

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

## 6. Slices livrées

### M15-S1 — Domaine canonique ✅

- ✅ `AcceptanceCriterionId` MORPHEUS-owned ;
- ✅ `VerificationStatus` ;
- ✅ `AcceptanceCriterion` provider-neutral ;
- ✅ rattachement requirement/change explicite ;
- ✅ séparation provenance du critère / preuves de vérification ;
- ✅ `TraceabilityEntityKind.ACCEPTANCE_CRITERION` et `EVIDENCE` ;
- ✅ invariants domaine testés ;
- ✅ ADR-0081 acceptée après preuve.

### M15-S2 — Normalisation et providers ✅

- ✅ critères ajoutés à `NormalizedProjectContent` ;
- ✅ IDs requirement/change/evidence validés ;
- ✅ Synthetic provider étendu avec `acceptance_criteria` explicite ;
- ✅ fixture avec `VERIFIED` + preuve et `NOT_VERIFIED` sans preuve ;
- ✅ aucune conversion `Scenario -> AcceptanceCriterion` ;
- ✅ OpenSpec reste honnêtement `UNSUPPORTED` tant que son format implémenté n'expose pas de structure acceptance explicite ;
- ✅ `UNKNOWN`/`NOT_VERIFIED` ne fabriquent aucune preuve.

### M15-S3 — Snapshot et persistance ✅

- ✅ critères ajoutés à `SnapshotBusinessContent` ;
- ✅ Memory store compatible ;
- ✅ migration SQLite `V009__snapshot_acceptance_criteria.sql` ;
- ✅ tables critères + relation vers preuves ;
- ✅ lecture/écriture déterministe SQLite ;
- ✅ Memory == SQLite ;
- ✅ SQLite close/reopen préserve critères, statuts et preuves.

### M15-S4 — Traçabilité ✅

- ✅ `Requirement -> AcceptanceCriterion` via `VERIFIED_BY` lorsque le rattachement est explicite ;
- ✅ `ChangeProposal -> AcceptanceCriterion` via `VERIFIED_BY` lorsque le rattachement est explicite ;
- ✅ `AcceptanceCriterion -> Evidence` uniquement pour les preuves explicites ;
- ✅ aucun lien fabriqué depuis le texte, un scénario ou la provenance du critère ;
- ✅ traversal existant compatible avec les nouveaux kinds.

`AcceptanceCriterion -> ExternalReference(test)` reste volontairement absent tant qu'aucun provider/source ne fournit une référence test explicite. L'existence d'un test n'est jamais assimilée à `VERIFIED`.

### M15-S5 — Quality / coverage ✅

- ✅ `EVALUATED` / `NO_CRITERIA` distingués de l'ancien `UNAVAILABLE_IN_NORMALIZED_MODEL` ;
- ✅ total criteria ;
- ✅ verified / partially verified / failed / not verified / unknown ;
- ✅ ratio `VERIFIED / total` ;
- ✅ findings dédiés ;
- ✅ `UNKNOWN != FAILED` ;
- ✅ zéro critère = modèle disponible mais vide (`NO_CRITERIA`) ;
- ✅ `ChangeCompletenessService.acceptanceCriteriaDefined` = TRUE/FALSE observable ;
- ✅ contrats M6 historiques actifs migrés.

### M15-S6 — Requêtes et surfaces ✅

- ✅ `BusinessContentQueryService` : global / par change / par requirement ;
- ✅ CLI `acceptance-criteria list --project ID [--change ID | --requirement ID] [--offset N] [--limit N]` ;
- ✅ JSON canonique CLI ;
- ✅ MCP `get_acceptance_criteria` sur le modèle réel, tool count inchangé ;
- ✅ HTTP `/acceptance-criteria` sur le modèle réel ;
- ✅ OpenAPI **1.4.0** avec `AcceptanceCriterion` et `VerificationStatus` ;
- ✅ mapping expose `verificationStatus`, `verificationEvidenceIds`, `sourceEvidenceId`.

### M15-S7 — Orchestration et gate final ✅

- ✅ `change-orchestration.acceptanceCriteria.status = AVAILABLE` ;
- ✅ `observedCount` réel par change ;
- ✅ absence de critère = fait observable FALSE / artefact manquant, pas `UNAVAILABLE` ;
- ✅ aucune sémantique de blocker inventée : `blockingAcceptanceCriterion*` reste indisponible jusqu'à M16 ;
- ✅ Maven reactor complet ;
- ✅ packaging Windows + smokes ;
- ✅ `VALIDATION_M15.md` ;
- ✅ ADR-0081 acceptée.

## 7. Gate M15

```text
acceptanceCriteria.status != UNAVAILABLE_IN_NORMALIZED_MODEL     PASS
critères persistés et requêtables                                PASS
preuves de vérification traçables et explicables                 PASS
aucune conversion Scenario -> AcceptanceCriterion implicite      PASS
Test existence != VERIFIED                                       PASS
UNKNOWN conservé lorsque les faits manquent                      PASS
Memory == SQLite                                                 PASS
SQLite close/reopen identique                                    PASS
CLI/MCP/HTTP cohérents                                           PASS
change-orchestration utilise le modèle réel                      PASS
full Maven gate                                                  PASS
packaging Windows                                                PASS
```

## 8. Résultat de validation

```text
Domain               29/29 PASS
Application          94/94 PASS
OpenSpec              26/26 PASS
Synthetic              7/7 PASS
SQLite                 7/7 PASS
MINOS Integration      8/8 PASS
NEXUS Integration      7/7 PASS
MCP                     5/5 PASS
API                     9/9 PASS
CLI                   22/22 PASS
Architecture        157/157 PASS
---------------------------------
TOTAL               371/371 PASS
Failures                  0
Errors                    0
Skipped                   0
BUILD SUCCESS
Packaging Windows       PASS
```

Archive : `dist/morpheus-0.1.0-windows-x64.zip` — `33,729,071` bytes.

## 9. Décision de clôture

```text
M15       VALIDÉ TECHNIQUEMENT
ADR-0081  ACCEPTÉE — M15
PR #77    peut passer Ready
Issue #76 reste ouverte jusqu'à intégration
main      inchangé tant que PR #77 n'est pas mergée
```

La fusion de la PR #77 reste interdite sans autorisation explicite distincte.