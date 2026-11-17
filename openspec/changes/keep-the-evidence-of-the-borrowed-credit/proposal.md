# Proposition : conserver la preuve du crédit emprunté (AUD-TST-33)

## Why

Les deux échelles de couverture sont qualifiées sur des preuves conservées sous `config/coverage-qualification/`, à une
seule tête, et `CoverageQualificationEvidenceTest` compare chaque plafond à ces preuves (exigences
`coverage-qualification-evidence`). La troisième grandeur, le crédit emprunté, ne l'est pas : sa mesure qualifiée
(`QUALIFIED_BORROWED_LINES` / `QUALIFIED_BORROWED_BRANCHES` de `BorrowedCoverageGateTest`) cite des artefacts CI du
30/10/2026 à `d3580dc9`, expirés, et `CoverageQualificationEvidenceTest` ne lit aucune clé `borrowed`
(`config/coverage-qualification/evidence.tsv` ne porte que `per-module` et `aggregate`, à `dbd65062`). Personne ne
peut vérifier la constante.

Revérifié le 17/11/2026 sur `1a539c22` : la différence `lineCovered` agrégée moins par module des preuves conservées
à `dbd65062` vaut 802 lignes sur les quatre runs et 381 à 383 branches, au-dessus de la mesure qualifiée codée (801 /
380). Le gate tient « maximum ≥ mesure qualifiée » sur une mesure qui n'est plus la plus haute.

Pourquoi maintenant : `ci.yml` garde l'artefact `m21-integrity-<OS>` quatorze jours (`retention-days: 14`). Celui des
runs de la qualification en vigueur (`42306627340`, `42311778667`, 07/11/2026) porte
`m21-borrowed-coverage-summary.txt` et expire vers le 21/11/2026 ; passé cette date, conserver la preuve à la tête des
deux échelles exige une requalification complète des trois grandeurs.

Constat `AUD-TST-33` (Faible), `docs/project/audits/2026-11-17/findings.md`.

## What Changes

- Les résumés `m21-borrowed-coverage-summary.txt` des runs de qualification sont conservés sous
  `config/coverage-qualification/borrowed/`, déclarés dans `evidence.tsv` avec l'échelle `borrowed`.
- La suite d'architecture exige que la mesure qualifiée du crédit emprunté égale, en lignes et en branches, la plus
  haute mesure des résumés conservés, et que ces résumés soient à la même tête que les deux échelles.
- `QUALIFIED_BORROWED_LINES`, `QUALIFIED_BORROWED_BRANCHES` et `QUALIFIED_BY_MODULE` de `BorrowedCoverageGateTest` sont
  requalifiés sur ces preuves, commentaire compris.
- Le maximum (`borrowedLineCreditMaximum`, `borrowedBranchCreditMaximum`) ne monte pas ; sa baisse éventuelle est une
  décision du mainteneur (design.md, [QUESTION] Q1).

## Capabilities

### New Capabilities

### Modified Capabilities
- `coverage-attribution-gap` : la mesure qualifiée du crédit emprunté est adossée à des preuves conservées, à la tête
  des deux échelles, et égale à leur plus haute mesure.

## Périmètre et exclusions

**Inclus :** `config/coverage-qualification/` (dossier `borrowed/`, `evidence.tsv`), `CoverageQualificationEvidenceTest`
et `CoverageQualificationVerifier`, les constantes qualifiées de `BorrowedCoverageGateTest`, la page durable de la
capacité, `.claude/rules/testing.md` si son paragraphe sur le crédit emprunté le demande.

**Exclus :** la liste des classes entièrement empruntées, inchangée ; les plafonds et ratchets des deux échelles,
inchangés ; la règle de fenêtre du maximum (au plus trois fois la dispersion de référence au-dessus de la mesure),
fixée par GEN-0167.

## Acteurs et surfaces concernés

Le mainteneur qui qualifie la couverture ; la CI, dont le build lit les preuves.

## Nature des informations

| Affirmation | Nature | Source ou preuve |
|---|---|---|
| Aucune preuve conservée pour le crédit emprunté | existant vérifié | `config/coverage-qualification/evidence.tsv`, recherche `borrowed` dans `CoverageQualificationEvidenceTest` |
| Les preuves du 07/11 impliquent 802 lignes et jusqu'à 383 branches | existant vérifié (mesure datée, calcul sur les résumés conservés) | `config/coverage-qualification/{aggregate,per-module}/*.txt` |
| La mesure qualifiée codée est 801 / 380 au 30/10 | existant vérifié | `BorrowedCoverageGateTest.java:57-73` |
| L'artefact de la qualification en vigueur expire vers le 21/11/2026 | existant vérifié (déduit) | `ci.yml:101`, date des runs |
| Abaisser le maximum, et jusqu'où | question ouverte | design.md, Q1 |

## Impact

`config/coverage-qualification/` et `config/m21-quality-ratchets.properties` seulement si Q1 retient une baisse
(fichiers de gouvernance : justification de PR requise) ; `morpheus-architecture-tests` (`m21/`), `morpheus-coverage-report`
(`BorrowedCoverageGateTest`) ; `docs/developer/specifications/coverage-attribution-gap.md`. GEN-0167 reste l'ADR de la
grandeur ; aucun ADR nouveau. Aucune dépendance envers un autre constat.

## Questions ouvertes

- **Q1 (mainteneur)** : abaisser ou non `borrowedLineCreditMaximum` / `borrowedBranchCreditMaximum` après la
  requalification, et à quelle marge au-dessus de la mesure qualifiée.
- **Q2 (mainteneur, si l'artefact a expiré)** : requalifier les trois grandeurs à une tête nouvelle, ou conserver la
  preuve du crédit emprunté à sa propre tête en assouplissant l'exigence de tête commune pour cette seule grandeur.
