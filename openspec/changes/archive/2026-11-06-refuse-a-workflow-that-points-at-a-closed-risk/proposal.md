# Proposition : refuser qu'un workflow renvoie vers une entrée close du registre des risques

## Why

Les messages des workflows renvoient le mainteneur vers le registre des risques (`docs/architecture/risks/register.md`)
au moment où un gate échoue : `.github/workflows/security.yml` cite `RT-13` (`:96`, `:123`, `:186`, `:217`, `:361`,
`:369`, commentaire `:291`), `.github/workflows/nightly.yml` cite `RT-08` (`:8`, `:116`, `:189`, `:191`, `:192`). Le
26/10/2026, ces renvois pointaient vers une entrée close (RT-13 « clos le 22/09/2026 », commit `cba6dd68`) alors que le
risque bloquait encore les PR. Le constat `AUD-DEP-26` (Faible) de l'audit `docs/project/audits/2026-10-09/findings.md`,
revérifié le 2026-11-06 sur `30520bd3`, reste PARTIEL sur un seul point : le volet a été résorbé à la main (RT-13 est
« ouvert », `register.md:21` et `:30`), mais aucune règle ne refuse qu'un workflow renvoie vers une entrée close. Les
deux tests qui lisent déjà le registre ou ses renvois — `RepositoryDocumentationCoherenceTest` (ratchets recopiés,
`:339-345`) et `D2RepositoryHardeningArchitectureTest#theMacosLaneStaysAdvisoryAndClaimsNoSupport` (texte
`RT-08 open` dans `nightly.yml`) — ne croisent pas le statut d'une entrée et les renvois des workflows. La dérive s'est
déjà produite une fois ; sans gate, elle reviendra.

## What Changes

- Gate de build ajouté (suite d'architecture) : tout identifiant `RT-NN` ou `DT-NN` écrit dans un workflow
  `.github/workflows/*.yml` désigne une entrée du registre qui n'est pas close ; sinon le build échoue en nommant le
  fichier, la ligne, l'identifiant et l'état trouvé (close ou inconnue).
- L'état d'une entrée se lit dans la structure du registre, sans nouveau champ : ligne d'un tableau actif, en-tête de
  détail, en-tête de requalification en position.
- Le registre qui se contredit (ligne au tableau actif, en-tête de détail qui la dit close) fait échouer le build.
- Aucune surface publique, aucun format persisté ne change ; les workflows et le registre actuels satisfont la règle.

## Capabilities

### New Capabilities
- `risk-register-references` : ce qu'un renvoi d'un workflow vers le registre des risques doit désigner, et comment
  l'état d'une entrée du registre est lu.

### Modified Capabilities

Aucune.

## Périmètre et exclusions

**Inclus :** les identifiants `RT-NN` et `DT-NN` dans `.github/workflows/*.yml`, commentaires compris ; l'état de ces
entrées dans `docs/architecture/risks/register.md`.

**Exclus :**
- les autres préfixes du registre (`A-NN`, `F-NN`), qu'aucun workflow ne cite ;
- les renvois depuis d'autres fichiers (`config/*.xml`, `scripts/`, `docs/`) : question ouverte ;
- la rédaction du registre lui-même (dates, mitigations), hors de sa structure d'état.

## Acteurs et surfaces concernés

Mainteneur qui lit un échec de gate en CI ; auteur d'une PR qui clôt une entrée du registre ou modifie un workflow ; CI
(`ci.yml`, suite d'architecture).

## Nature des informations

| Affirmation | Nature | Source ou preuve |
|---|---|---|
| Les workflows citent `RT-13` et `RT-08`, et aucun autre identifiant du registre | existant vérifié | recherche de `\b(RT|DT)-[0-9]+\b` dans `.github/` à `30520bd3` |
| `RT-13` a été cité par les workflows alors qu'il était clos | existant vérifié | `git show cba6dd68:docs/architecture/risks/register.md`, ligne 27 ; preuve de `AUD-DEP-26` |
| Une entrée close quitte le tableau actif | existant vérifié | `register.md` : « résolues et retirées du tableau actif » (`DT-06`, `DT-09`), « ne sont plus actifs » (`RT-10`, `RT-11`), « `DT-13` en sort » |
| Une entrée au tableau actif n'est pas close, même un risque accepté sans correctif (`RT-14`) | décision | `design.md`, D2 |
| Une position requalifiée (`DT-04`, `DT-05`, `DT-12`, `DT-13`) n'est pas close | décision | `design.md`, D2 |
| Un identifiant inconnu du registre est refusé comme un identifiant clos | décision | `design.md`, D3 |

## Impact

- Tests : une classe ajoutée dans `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/`, marquée
  `@CoherenceCheck` ; `architectureTestsMinimum` ne baisse pas.
- Documentation : page durable nouvelle `docs/developer/specifications/risk-register-references.md` et son index ; une
  phrase en tête du registre qui dit comment l'état d'une entrée est lu.
- ADR : non concerné.

## Questions ouvertes

- Étendre la règle aux renvois écrits dans `config/` (les notes de `config/dependency-check-suppressions.xml` et
  `config/dependency-check-hints.xml` citent `RT-13`) et dans `scripts/` ? À trancher par le mainteneur ; la règle est
  écrite pour qu'ajouter un répertoire ne change que sa liste de fichiers lus.
