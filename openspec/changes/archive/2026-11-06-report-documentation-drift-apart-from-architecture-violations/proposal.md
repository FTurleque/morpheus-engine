# Proposition : rapporter une désynchronisation documentaire à part d'une violation d'architecture

## Why

Le constat `AUD-TRV-03` de l'audit du 09/10/2026 (`docs/project/audits/2026-10-09/findings.md`, revérifié le
2026-11-06 sur `30520bd3`, statut PARTIEL) demande, en troisième clause de son action, qu'un échec de cohérence
documentaire dise « documentation désynchronisée » et non « architecture violée ». Aujourd'hui, une classe marquée
`@CoherenceCheck` (étiquette JUnit `coherence`) échoue exactement comme une règle sur le bytecode : même exécution
Surefire, même ligne finale de Maven, aucun verdict qui les distingue. La page durable
`docs/developer/specifications/enforcement-mechanism-accounting.md` relève en outre qu'aucun build ne lance la sélection
`-Dgroups=coherence` (écart 5, « preuve manquante »). L'action proposait de sortir ces contrôles du module de gates ;
l'exigence en vigueur l'interdit (`enforcement-mechanism-accounting` › A test class that reads only non-compiled
artefacts SHALL be marked as a coherence check and SHALL stay in the gate module), et le mainteneur a décidé le 06/11
qu'ils **restent dans le module**.

## What Changes

- À chaque exécution de la suite d'architecture, un écouteur de la plateforme JUnit classe chaque test en échec selon
  l'étiquette `coherence` de sa classe et écrit deux verdicts distincts — désynchronisation documentaire, violation
  d'architecture — sur la sortie du build et dans un fichier de preuve du module, avec le nombre d'exécutions de chaque
  nature.
- Aucune seconde exécution Surefire, aucun second répertoire de rapports : le compte d'exécutions que lisent les
  validateurs de présence ne bouge pas, et une invocation par `-Dtest` garde son comportement.
- Le fichier de verdict rejoint l'artefact d'intégrité publié par la CI.

## Capabilities

### New Capabilities

Aucune.

### Modified Capabilities
- `enforcement-mechanism-accounting` : deux exigences ajoutées, sur le verdict distinct d'un échec de cohérence et sur
  l'invariance de la sélection et du compte des exécutions. L'exigence R4 (le marqueur, et la classe qui reste dans le
  module) n'est pas modifiée.

## Périmètre et exclusions

**Inclus :** la classification des échecs de `morpheus-architecture-tests` par l'étiquette `coherence`, son affichage,
son fichier de preuve et sa publication par la CI.

**Exclus :**
- sortir les contrôles de cohérence du module : écarté par le mainteneur, gardé en question ouverte ;
- la règle qui décide qu'une classe est un contrôle de cohérence (inchangée, `CoherenceCheckMarkerTest`) ;
- les autres modules du réacteur ;
- le gabarit de pull request et la population des interdits en attente de règle (changements
  `ask-for-the-intention-of-every-textual-assertion-in-the-pull-request` et
  `write-every-dependency-ban-as-a-bytecode-rule`).

## Acteurs et surfaces concernés

Mainteneur qui lit un build en échec, localement ou en CI. Aucune surface publique ne change.

## Nature des informations

| Affirmation | Nature | Source ou preuve |
|---|---|---|
| Un échec de cohérence et une violation d'architecture partagent la même exécution et le même message final | existant vérifié | `morpheus-architecture-tests/pom.xml` ne configure pas Surefire ; le POM racine ne déclare qu'une configuration (`pluginManagement`, `maven-surefire-plugin`) sans exécution ni groupe |
| Aucun build ne lance `-Dgroups=coherence` | existant vérifié | recherche de `groups=coherence` et `Dgroups` dans les workflows, scripts, POM et sources : seule la Javadoc de `CoherenceCheck` le cite ; page durable, écart 5 |
| Les validateurs de présence ne lisent que `target/surefire-reports/` | existant vérifié | `scripts/validate-m21.sh` (`rglob('target/surefire-reports/TEST-*.xml')`), `scripts/validate-m21.ps1` et `scripts/validate-d2.ps1` (filtre `[\\/]target[\\/]surefire-reports[\\/]`), `scripts/validate-d2.sh` |
| Les contrôles restent dans le module | décision | mainteneur, 06/11/2026 ; exigence R4 en vigueur |
| Un écouteur plutôt qu'une seconde exécution | décision | `design.md`, D1 |

## Impact

- Tests : un écouteur et son enregistrement de service dans les sources et ressources de test de
  `morpheus-architecture-tests` ; un test de la classification ; dépendance de test `junit-platform-launcher` (gérée par
  `junit-bom`, sans version dans le module).
- CI : `.github/workflows/ci.yml` publie le fichier de verdict avec `enforcement-composition-summary.txt`.
- Documentation durable : `docs/developer/specifications/enforcement-mechanism-accounting.md` ; `.claude/rules/architecture.md`
  (paragraphe `@CoherenceCheck`).
- ADR : aucun.

## Questions ouvertes

- Sortir un jour les contrôles de cohérence du module de gates ? Écarté par le mainteneur le 06/11 ; la question reste
  ouverte, sans travail associé.
