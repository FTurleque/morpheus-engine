# Proposition : n'envoyer le jeton Sonar qu'à l'hôte configuré (AUD-DEP-37)

## Why

`scripts/classify-sonar-quality-gate.sh`, que le job `sonar` de `.github/workflows/ci.yml` lance quand le scanner
échoue, prend le **premier** fichier `*/target/sonar/report-task.txt` trouvé sous le workspace
(`find … -print -quit`), y lit `ceTaskUrl` et `serverUrl`, puis appelle ces URL par `curl` avec
`Authorization: Bearer ${SONAR_TOKEN}`. Aucun contrôle ne vérifie que ces URL désignent `https://sonarcloud.io/`, la
valeur passée au scanner par `-Dsonar.host.url`. Le script tourne dans le workspace où `./mvnw clean verify` vient de
s'exécuter : tout fichier `report-task.txt` déposé sous un `target/sonar/` pendant le build (un test, un plugin)
choisit la destination du jeton Sonar durable. Le job ne tourne que sur `main`, d'où la sévérité faible ; le contrôle
coûte une ligne.

Preuve : constat `AUD-DEP-37` (Faible), `docs/project/audits/2026-11-17/findings.md`, mesuré par l'audit du 17/11/2026
sur `a04f32bd`, revérifié le 17/11/2026 sur `1a539c22` (script et job relus ; aucun contrôle d'hôte).

## What Changes

- Le classificateur lit `report-task.txt` au chemin exact que produit le scanner pour le module racine
  (`$GITHUB_WORKSPACE/target/sonar/report-task.txt`), et non le premier trouvé.
- Il refuse, avant tout appel réseau, un `ceTaskUrl` ou un `serverUrl` dont le préfixe n'est pas l'hôte configuré
  (`https://sonarcloud.io/`), par une erreur nommée ; le jeton n'est alors envoyé nulle part.
- Le contrat du classificateur exige ce contrôle et ce chemin.

## Capabilities

### New Capabilities
- `sonar-quality-gate-classification` : le classificateur du verdict Sonar ne lit que l'analyse produite par le scanner
  du job et n'envoie le jeton Sonar qu'à l'hôte configuré.

### Modified Capabilities

## Périmètre et exclusions

**Inclus :** `scripts/classify-sonar-quality-gate.sh`, éventuellement `ci.yml` (passage de l'hôte attendu au script,
design.md D2), `SonarQualityGateClassificationContractTest` (`morpheus-architecture-tests`, paquet `m21`), la nouvelle
page durable.

**Exclus :** la classification elle-même (`OK`, `ERROR`, `NONE`), inchangée ; la version du scanner (`AUD-DEP-36`,
change `manage-every-maven-plugin-a-workflow-invokes-in-the-root-pom`) ; le modèle de branche du job, tenu par
`single-integration-branch`.

## Acteurs et surfaces concernés

Le job `sonar` de la CI sur `main`, porteur de `SONAR_TOKEN` ; SonarQube Cloud.

## Nature des informations

| Affirmation | Nature | Source ou preuve |
|---|---|---|
| Le script lit le premier `report-task.txt` trouvé | existant vérifié | `scripts/classify-sonar-quality-gate.sh`, `find … -print -quit` |
| Le jeton est envoyé à `ceTaskUrl` et à `serverUrl` lus dans ce fichier | existant vérifié | même script, fonction `sonar_get` et appel du gate |
| Aucun contrôle d'hôte | existant vérifié | même script ; `SonarQualityGateClassificationContractTest` n'en exige pas |
| Le scanner écrit `report-task.txt` sous `target/sonar/` du module racine | proposition à valider | comportement du scanner Maven, vérifié sur une exécution du job (tâche 2.3) |

## Impact

`scripts/classify-sonar-quality-gate.sh`, `ci.yml` si D2 le retient, `SonarQualityGateClassificationContractTest`
(fichier de gouvernance : justification de PR), nouvelle page durable et son index. Aucune surface publique, aucun code
de production, aucun ADR (design.md).

## Questions ouvertes

Aucune.
