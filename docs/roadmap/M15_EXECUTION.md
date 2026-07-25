# M15 — Acceptance Criteria, Verification & Evidence

Statut : **🚧 EN COURS — S1→S5 codées, S6/S7 en consolidation ; gate Maven non exécuté**

Dernière mise à jour : 26 juillet 2026

Issue : **#76**  
Branche : `m15/acceptance-verification-evidence`  
PR : **#77 — Draft**

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

La baseline possédait déjà :

```text
ReadCategory.ACCEPTANCE_CRITERIA
ProviderCapability.READ_ACCEPTANCE_CRITERIA
Evidence / EvidenceId
Provenance
SnapshotBusinessContent
AcceptanceQualityService
AcceptanceCoverageStatus.UNAVAILABLE_IN_NORMALIZED_MODEL
```

M15 introduit maintenant sur la branche :

```text
AcceptanceCriterion production type
AcceptanceCriterionId
VerificationStatus
acceptance criteria in NormalizedProjectContent
acceptance criteria in SnapshotBusinessContent
snapshot-scoped SQLite persistence V009
traceability kinds ACCEPTANCE_CRITERION + EVIDENCE
real acceptance verification coverage
business query + MCP + HTTP surfaces
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

## 5. Modèle implémenté

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

## 6. Slices

Légende :

```text
✅ codé sur la branche
🟡 codé mais preuve Maven/gate encore requise
⏳ restant
```

### M15-S1 — Domaine canonique 🟡

- ✅ `AcceptanceCriterionId` MORPHEUS-owned ;
- ✅ `VerificationStatus` ;
- ✅ `AcceptanceCriterion` provider-neutral ;
- ✅ rattachement requirement/change explicite ;
- ✅ séparation provenance du critère / preuves de vérification ;
- ✅ `TraceabilityEntityKind.ACCEPTANCE_CRITERION` et `EVIDENCE` ;
- ✅ tests domaine des invariants écrits ;
- ⏳ ADR-0081 reste **Proposée** jusqu'au gate réel.

### M15-S2 — Normalisation et providers 🟡

- ✅ critères ajoutés à `NormalizedProjectContent` ;
- ✅ IDs requirement/change/evidence vérifiés ;
- ✅ Synthetic provider étendu avec `acceptance_criteria` explicite ;
- ✅ fixture avec `VERIFIED` + preuve et `NOT_VERIFIED` sans preuve ;
- ✅ aucune conversion `Scenario -> AcceptanceCriterion` ;
- ✅ OpenSpec reste honnêtement `UNSUPPORTED` tant que son format implémenté n'expose pas de structure acceptance explicite ;
- ✅ `UNKNOWN`/`NOT_VERIFIED` ne fabriquent aucune preuve.

### M15-S3 — Snapshot et persistance 🟡

- ✅ critères ajoutés à `SnapshotBusinessContent` ;
- ✅ Memory store compatible via projection immuable ;
- ✅ migration SQLite `V009__snapshot_acceptance_criteria.sql` ;
- ✅ tables critères + relation vers preuves de vérification ;
- ✅ lecture/écriture déterministe SQLite ;
- ✅ tests Memory==SQLite et close/reopen écrits ;
- ⏳ exécution réelle des tests requise.

### M15-S4 — Traçabilité 🟡

- ✅ `Requirement -> AcceptanceCriterion` via `VERIFIED_BY` lorsque le rattachement est explicite ;
- ✅ `ChangeProposal -> AcceptanceCriterion` via `VERIFIED_BY` lorsque le rattachement est explicite ;
- ✅ `AcceptanceCriterion -> Evidence` uniquement pour `verificationEvidenceIds` explicites ;
- ✅ aucun lien de preuve fabriqué depuis le texte ou la provenance du critère ;
- ✅ traversal existant reste générique pour les nouveaux kinds ;
- ⏳ `AcceptanceCriterion -> ExternalReference(test)` reste à traiter lorsqu'un provider/source fournit une référence test explicite.

### M15-S5 — Quality / coverage 🟡

- ✅ `EVALUATED` / `NO_CRITERIA` distingués de l'ancien `UNAVAILABLE_IN_NORMALIZED_MODEL` ;
- ✅ total criteria ;
- ✅ verified / partially verified / failed / not verified / unknown ;
- ✅ ratio `VERIFIED / total` ;
- ✅ findings dédiés par état non pleinement vérifié ;
- ✅ `UNKNOWN != FAILED` ;
- ✅ zéro critère = modèle disponible mais vide (`NO_CRITERIA`) ;
- ✅ `ChangeCompletenessService.acceptanceCriteriaDefined` devient TRUE/FALSE observable ;
- ⏳ tests d'agrégat M6 historiques actifs à finir d'aligner sur les nouveaux compteurs.

### M15-S6 — Requêtes et surfaces 🚧

- ✅ `BusinessContentQueryService` : global / par change / par requirement ;
- ✅ MCP `get_acceptance_criteria` utilise le modèle réel, tool count inchangé ;
- ✅ HTTP route existante `/acceptance-criteria` utilise le modèle réel ;
- ✅ OpenAPI **1.4.0** avec `AcceptanceCriterion` et `VerificationStatus` ;
- ✅ tests MCP/API ancien `UNAVAILABLE` migrés vers page vide disponible ;
- ✅ mapping JSON expose `verificationStatus`, `verificationEvidenceIds`, `sourceEvidenceId` ;
- ⏳ CLI dédiée acceptance à ajouter sans réécriture risquée du gros routeur CLI ;
- ⏳ docs utilisateur/développeur finales à aligner après stabilisation du gate ;
- ⏳ confirmer JSON canonique par tests exécutés.

### M15-S7 — Orchestration et gate final 🚧

- ✅ `change-orchestration.acceptanceCriteria.status = AVAILABLE` ;
- ✅ `observedCount` réel par change ;
- ✅ absence de critère = fait observable FALSE / artefact manquant, pas `UNAVAILABLE` ;
- ✅ aucune sémantique de blocker inventée : `blockingAcceptanceCriterion*` reste indisponible jusqu'à M16 ;
- ✅ test JARVIS actif réécrit pour ne plus attendre l'ancien gap M14 ;
- 🟡 Memory == SQLite : test écrit, non exécuté ;
- 🟡 SQLite reopen : test écrit, non exécuté ;
- ⏳ full Maven gate ;
- ⏳ packaging Windows ;
- ⏳ `VALIDATION_M15.md` ;
- ⏳ mise à jour roadmap globale / ADR index après preuve ;
- ⏳ ADR-0081 à accepter seulement après preuve reproductible.

## 7. Gate M15

```text
acceptanceCriteria.status != UNAVAILABLE_IN_NORMALIZED_MODEL     CODED
critères persistés et requêtables                                CODED
preuves de vérification traçables et explicables                 CODED
aucune conversion Scenario -> AcceptanceCriterion implicite      CODED
Test existence != VERIFIED                                       CODED
UNKNOWN conservé lorsque les faits manquent                      CODED
Memory == SQLite                                                 TEST WRITTEN / NOT RUN
SQLite close/reopen identique                                    TEST WRITTEN / NOT RUN
CLI/MCP/HTTP cohérents                                           MCP+HTTP CODED / CLI PENDING
change-orchestration utilise le modèle réel                      CODED
full Maven gate                                                  NOT RUN
packaging Windows PASS                                           NOT RUN
```

## 8. État de validation

Aucun workflow GitHub Actions n'est attaché au head de la PR #77. MORPHEUS conserve donc sa règle de preuve : **aucun PASS n'est revendiqué sans exécution réelle du Maven Wrapper / packaging**.

En conséquence :

```text
PR #77    reste Draft
ADR-0081  reste Proposée
M15       reste EN COURS
main      inchangé
```

## 9. Ordre de clôture restant

```text
1. finir compatibilité tests d'agrégat + CLI
2. aligner docs actives / contrat 1.4.0
3. exécuter .\mvnw.cmd clean test
4. corriger tout échec réel
5. exécuter packaging Windows
6. créer VALIDATION_M15.md avec SHA/compteurs exacts
7. accepter ADR-0081
8. passer PR #77 Ready
9. merger uniquement après autorisation explicite
```
