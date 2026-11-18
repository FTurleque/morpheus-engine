# Proposition : vérifier les sommes de contrôle de chaque artefact Maven résolu (AUD-DEP-38)

## Why

Le dépôt vérifie la distribution Maven elle-même (`.mvn/wrapper/maven-wrapper.properties`, `distributionSha256Sum`,
contrôlé par `mvnw`), mais rien ne renforce l'intégrité des dépendances et des plugins résolus ensuite : `.mvn/` ne
contient que `wrapper/`, aucun `.mvn/maven.config` ne pose `--strict-checksums`, aucune source de sommes de confiance
n'est configurée. L'intégrité repose sur HTTPS et sur la politique de sommes de contrôle par défaut du résolveur, qui
avertit sans échouer. Les deux jobs de construction de `.github/workflows/release.yml` restaurent `~/.m2` depuis le
cache (`actions/setup-java`, `cache: maven`) : un artefact altéré dans ce cache ou sur un miroir ne serait pas refusé
par le build qui produit la release attestée.

Preuve : constat `AUD-DEP-38` (Faible), `docs/project/audits/2026-11-17/findings.md`, mesuré par l'audit du 17/11/2026
sur `a04f32bd`, revérifié le 17/11/2026 sur `1a539c22` (`.mvn/` ne porte que `wrapper/maven-wrapper.properties` ;
aucune occurrence de `strict-checksums` ni de `trustedChecksums` dans les workflows, scripts et POM ; `cache: maven`
dans les deux jobs de release).

## What Changes

- `.mvn/maven.config` porte `--strict-checksums` : toute exécution du wrapper, en local et en CI, échoue sur une somme
  de contrôle absente ou fausse au lieu d'avertir.
- Le build refuse l'absence de ce réglage.
- Selon la décision du mainteneur (design.md, D2) : les jobs de release résolvent sans restaurer le cache Maven partagé
  (**proposé**), ou vérifient chaque artefact contre des sommes de confiance versionnées dans le dépôt.

## Capabilities

### New Capabilities
- `maven-artifact-integrity` : tout artefact que Maven résout pour le build est vérifié par sa somme de contrôle, et le
  build qui produit une release ne réutilise pas un cache que d'autres exécutions ont rempli.

### Modified Capabilities

## Périmètre et exclusions

**Inclus :** `.mvn/maven.config` (à créer), `.github/workflows/release.yml` (option retenue en D2), un test de
`morpheus-architecture-tests` (famille `d2`), la nouvelle page durable, `docs/developer/BUILD_AND_TEST.md`.

**Exclus :** la séparation des permissions d'attestation (`AUD-DEP-33`, change
`attest-release-artifacts-in-a-job-that-runs-no-repository-code`, qui touche aussi `release.yml`) ; le cache Maven des
workflows de CI hors release, dont le risque est borné par `--strict-checksums` ; la vérification de signatures PGP
des artefacts.

## Acteurs et surfaces concernés

Tout contributeur qui lance `./mvnw` ; la CI ; le build de release ; Maven Central et tout miroir.

## Nature des informations

| Affirmation | Nature | Source ou preuve |
|---|---|---|
| La distribution Maven est vérifiée par SHA-256 | existant vérifié | `.mvn/wrapper/maven-wrapper.properties`, `distributionSha256Sum` |
| Aucun réglage n'impose la vérification stricte des sommes des artefacts | existant vérifié | `.mvn/` ; recherche de `strict-checksums` et `trustedChecksums` |
| Les jobs de release restaurent `~/.m2` | existant vérifié | `release.yml`, `cache: maven` dans `linux-release` et `windows-release` |
| Tous les dépôts utilisés publient des sommes pour chaque artefact | proposition à valider | un `./mvnw clean verify` à cache vide sous `--strict-checksums` (tâche 2.2) |
| Release sans cache ou sommes de confiance | question ouverte | design.md, D2 |

## Impact

`.mvn/maven.config`, `release.yml`, `morpheus-architecture-tests` (nouveau test de cohérence, fichier de gouvernance :
justification de PR), `docs/developer/BUILD_AND_TEST.md`, nouvelle page durable et son index. Temps de build de release
allongé si le cache est retiré (mesure en tâche 3.2). Aucune surface publique, aucun code de production.

## Questions ouvertes

- **Mainteneur** : pour le build de release, retirer `cache: maven` (proposé) ou adopter des sommes de confiance
  versionnées (`aether.trustedChecksumsSource.*`), plus fortes mais à régénérer à chaque montée de dépendance ? Voir
  design.md, D2.
