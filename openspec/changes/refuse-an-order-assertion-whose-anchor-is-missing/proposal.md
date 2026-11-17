# Proposition : refuser une assertion d'ordre dont une ancre manque (AUD-TST-34)

## Why

Plusieurs contrats de la suite d'architecture vérifient l'ordre de deux textes en comparant deux `indexOf`. Quand
l'ancre placée à gauche d'un `<` (ou à droite d'un `>`) disparaît, `indexOf` rend `-1` et l'assertion devient vraie
quel que soit l'ordre. Revérifié le 17/11/2026 sur `1a539c22`, les trois sites de l'audit sont confirmés :

- `m26/RemoteAccessJournalArchitectureTest.java:69` : `serve.indexOf("identity.role().allows(route.role())") <
  accepted`, ancre vérifiée nulle part ailleurs ;
- `d2/DependencyCheckWorkflowContractTest.java:811` : l'étape « Run OWASP Dependency-Check scan over build and test
  dependencies » doit précéder la vérification d'identification, nom non vérifié par ailleurs (le décompte
  `scans >= 2` ne lit pas ce nom) ;
- `m21/AuditHardeningWorkflowContractTest.java:127` : `sonarJob.indexOf("clean verify") < …`, aucune assertion
  `contains("clean verify")`.

La revérification en trouve un quatrième, absent de l'audit : `d2/DependencyCheckWorkflowContractTest.java:499`,
`sentinelWrite > trustedUpdate.indexOf("org.owasp:dependency-check-maven")`, vrai si l'appel du rafraîchissement
disparaît de l'étape. Pour le journal d'accès, `RemoteAccessJournalHttpTest` couvre le comportement ; pour les
workflows, rien d'autre.

Constat `AUD-TST-34` (Faible), `docs/project/audits/2026-11-17/findings.md`.

## What Changes

- Une aide unique de la suite d'architecture compare l'ordre de deux ancres dans un texte et échoue, en nommant
  l'ancre, si l'une est absente.
- Les quatre sites, et les autres comparaisons d'ordre par `indexOf` dans une assertion de la suite, passent par elle.
- Gate de build : la suite refuse une assertion dont la condition compare un résultat d'`indexOf` hors de cette aide.

## Capabilities

### New Capabilities

### Modified Capabilities
- `architecture-rule-vacuity-guard` : une assertion d'ordre textuel de la suite échoue quand une de ses ancres manque,
  comme une règle ArchUnit qui ne retient aucune classe.

## Périmètre et exclusions

**Inclus :** `morpheus-architecture-tests` (aide nouvelle, sites migrés, règle) ; la page durable de la capacité.

**Exclus :** les `indexOf` qui servent à découper une section (`substring`, boucles de recherche) et non à comparer un
ordre dans une assertion ; les sources de test des modules produit (aucun site constaté) ; le comportement des
fichiers vérifiés, inchangé.

## Nature des informations

| Affirmation | Nature | Source ou preuve |
|---|---|---|
| Trois sites de l'audit passent à vide si leur ancre disparaît | existant vérifié | lignes citées ci-dessus |
| Un quatrième site, `DependencyCheckWorkflowContractTest.java:499` | existant vérifié (absent de l'audit) | ligne citée |
| Une aide qui refuse `-1` et une règle qui refuse la comparaison nue | décision | design.md, D1 et D2 |
| Aucun ADR | décision | `.claude/rules/adr.md` : outillage de test |

## Impact

`morpheus-architecture-tests` : `RemoteAccessJournalArchitectureTest`, `DependencyCheckWorkflowContractTest`,
`AuditHardeningWorkflowContractTest`, les autres classes qui portent une comparaison d'ordre, une classe d'aide et une
méthode de règle (fichiers de gouvernance : justification de PR requise). Le nombre de méthodes `@Test` ne baisse pas
(`architectureTestsMinimum`). Aucune dépendance envers un autre constat ; `give-every-merge-on-main-its-ci-verdict` et
`bound-every-workflow-job-in-time` ajoutent des méthodes aux deux mêmes classes de contrat de workflow, sans
recouvrement.

## Questions ouvertes

Aucune.
