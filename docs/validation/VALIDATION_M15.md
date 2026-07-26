# Validation M15 — Acceptance Criteria, Verification & Evidence

Statut : **✅ VALIDÉ TECHNIQUEMENT — PR #77 prête à intégrer**

Date : 26 juillet 2026

Issue : #76  
PR : #77  
Head de code validé : `9e6450a099157cfdfcd11cc29dfb986ef7701247`

## Question de sortie

> MORPHEUS peut-il représenter explicitement ce qui doit être vérifié, l'état réel de cette vérification et les preuves associées, sans confondre scénario, test, critère d'acceptation et preuve ?

**Réponse : OUI.**

M15 introduit un modèle provider-neutral d'acceptance et de vérification, persistant et requêtable, avec preuve séparée de la provenance du critère.

## Invariants validés

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

## Modèle validé

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

`VerificationStatus` :

```text
NOT_VERIFIED
PARTIALLY_VERIFIED
VERIFIED
FAILED
UNKNOWN
```

Les états `PARTIALLY_VERIFIED`, `VERIFIED` et `FAILED` exigent au moins une preuve de vérification explicite. `UNKNOWN` et `NOT_VERIFIED` n'en fabriquent pas.

## Provider / normalisation / persistance

Validé :

- `NormalizedProjectContent` et `SnapshotBusinessContent` transportent les critères d'acceptation ;
- le provider Synthetic expose des critères explicites, dont un `VERIFIED` avec preuve et un `NOT_VERIFIED` sans preuve ;
- OpenSpec reste explicitement `UNSUPPORTED` pour l'acceptance tant que son format implémenté n'expose pas de structure dédiée ;
- aucune conversion `Scenario -> AcceptanceCriterion` n'est effectuée ;
- SQLite V009 persiste les critères et leurs relations vers les preuves ;
- Memory et SQLite produisent des projections cohérentes ;
- SQLite close/reopen conserve critères, statuts et preuves.

## Traçabilité

Relations dérivées uniquement depuis des identités structurelles explicites :

```text
Requirement          VERIFIED_BY AcceptanceCriterion
ChangeProposal       VERIFIED_BY AcceptanceCriterion
AcceptanceCriterion  VERIFIED_BY Evidence
```

Le lien `AcceptanceCriterion -> Evidence` ne concerne que `verificationEvidenceIds`, jamais la simple provenance source du critère.

## Qualité et lifecycle

Validé :

```text
AcceptanceCoverageStatus = EVALUATED | NO_CRITERIA | ...
```

La couverture expose les compteurs `verified`, `partiallyVerified`, `failed`, `notVerified`, `unknown` et le ratio `VERIFIED / total`.

Zéro critère signifie modèle disponible mais vide (`NO_CRITERIA`), pas modèle indisponible.

`ChangeCompletenessService.acceptanceCriteriaDefined` est désormais observable `TRUE/FALSE`. Les sémantiques de blocking acceptance restent volontairement indisponibles et appartiennent à M16.

## Surfaces validées

CLI :

```text
acceptance-criteria list --project ID
acceptance-criteria list --project ID --change ID
acceptance-criteria list --project ID --requirement ID
```

avec pagination `--offset` / `--limit` et `--json`.

MCP :

```text
get_acceptance_criteria
```

Le catalogue reste à **20 tools read-only**.

HTTP :

```text
GET /api/v1/projects/{projectId}/changes/{changeId}/acceptance-criteria
```

OpenAPI : **1.4.0**.

Orchestration JARVIS :

```text
acceptanceCriteria.status = AVAILABLE
observedCount = nombre réel de critères du change
```

Aucun blocker acceptance n'est inventé ; `blockingAcceptanceCriterion*` reste indisponible jusqu'à M16.

## Gate Maven autoritatif

Commande exécutée par le validateur M15 :

```powershell
.\mvnw.cmd clean test
```

Head testé :

```text
9e6450a099157cfdfcd11cc29dfb986ef7701247
```

Résultats :

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
```

Le module Memory Store ne contient pas de tests propres et n'ajoute donc aucun cas au total.

## Packaging Windows

Le même validateur a ensuite exécuté le packaging portable et ses smokes.

Preuves :

```text
MCP/API/MINOS/NEXUS/M14 orchestration packaging proof: PASS
Packaged standalone optional-engines + M14 orchestration smoke: PASS
Packaged API health smoke: PASS
Portable archive creation: PASS
```

Archive :

```text
dist/morpheus-0.1.0-windows-x64.zip
33,729,071 bytes
```

L'archive contient MORPHEUS, son runtime Java, CLI/MCP/API et les adapters optionnels MINOS/NEXUS ainsi que le contrat read-only M14. MINOS, NEXUS et JARVIS ne sont pas embarqués ni requis.

## Validateur reproductible

Commande unique Windows :

```powershell
.\validate-m15.cmd
```

Le validateur :

1. vérifie/met à jour la branche M15 ;
2. contrôle Java et Maven Wrapper ;
3. exécute le reactor `clean test` complet ;
4. s'arrête sur un vrai code de sortie non nul ;
5. exécute le packaging Windows uniquement si Maven est vert ;
6. conserve les logs sous `.git/morpheus-validation/m15`.

## ADR

ADR-0081 — **Acceptée — M15** après preuve du présent gate.

## Conclusion

M15 est **VALIDÉ TECHNIQUEMENT** sur le head de code `9e6450a099157cfdfcd11cc29dfb986ef7701247`.

La PR #77 peut passer Ready. La fusion reste soumise à une autorisation explicite distincte.