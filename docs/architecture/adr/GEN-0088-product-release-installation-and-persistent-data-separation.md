---
id: GEN-0088
title: "Product release, installation and persistent-data separation"
domain: general
rules: true
files: ["distribution/windows/MORPHEUS.iss"]
---

# Product release, installation and persistent-data separation

- Statut : **Acceptée**
- Date : 2026-07-27

## Context

MORPHEUS est distribué comme un produit installable, et pas seulement comme des archives portables. Le risque
principal est de coupler le cycle de vie du programme à celui du knowledge store : une mise à jour ou une
désinstallation ne doit jamais effacer implicitement les données persistantes.

## Decision

1. **Programme et état séparés** : sans réglage explicite, le programme Windows est dans
   `%LOCALAPPDATA%\Programs\MORPHEUS` et l'état dans `%LOCALAPPDATA%\MORPHEUS\{data,config,logs,backups}` ; sous
   Linux, `morpheus` sous `$XDG_DATA_HOME` (data), `$XDG_CONFIG_HOME` (config) et `$XDG_STATE_HOME` (`logs`,
   `backups`), replis XDG `~/.local/share`, `~/.config`, `~/.local/state`. Les options de la CLI et
   `MORPHEUS_DATA_DIR`, `MORPHEUS_CONFIG_DIR`, `MORPHEUS_DB`, `MORPHEUS_LOGS_DIR`, `MORPHEUS_BACKUPS_DIR` restent
   prioritaires (`CliLayout`).
2. **Setup Windows par utilisateur**, sans élévation ; l'ajout au `PATH` utilisateur est une tâche explicite
   décochée par défaut, retirée à la désinstallation si elle a été ajoutée.
3. **La désinstallation ne possède pas le knowledge store** : elle retire les fichiers programme, jamais
   `%LOCALAPPDATA%\MORPHEUS` ; supprimer les données est une opération distincte et explicite. Le retrait des entrées
   de clients MCP relève d'ADR-0096.
4. **Mise à jour en place** : un `AppId` stable identifie MORPHEUS 1.x ; une installation remplace les fichiers
   programme sans toucher aux données ni à la configuration.
5. **Artefacts** : setup Windows, archives portables Windows (ZIP) et Linux (`tar.gz`), inventaire des composants du
   commit étiqueté, chacun avec son `.sha256`, calculé et vérifié après écriture par la chaîne de build ; la liste
   exacte est celle qu'attend `.github/workflows/release.yml`. Tout artefact d'exécution embarque le runtime Java ;
   les archives portables restent supportées.
6. **Release depuis un tag** `v<version>` sur un SHA qualifié, dans un espace de travail Git propre ; les artefacts
   en sont reconstructibles par les scripts versionnés. Le gate est conclu par `scripts/validate-m20.ps1` et
   `scripts/validate-m20.sh`, jamais par GitHub Actions.

## Do's and Don'ts

### Do

- Résoudre les emplacements persistants hors du répertoire programme, en laissant les options et variables
  d'environnement prioritaires.
- Installer par utilisateur, sans élévation, avec l'ajout au `PATH` décoché par défaut.
- Garder l'`AppId` stable pour toute la ligne 1.x.
- Accompagner chaque artefact d'exécution et l'inventaire d'un `.sha256` vérifié après écriture.
- Construire une release depuis un tag `v<version>` dont le SHA est celui de `HEAD`, sur un espace de travail propre.

### Don't

- Ne pas supprimer `%LOCALAPPDATA%\MORPHEUS` ni les répertoires XDG de MORPHEUS à la désinstallation ou à la mise à
  jour.
- Ne pas exiger Git, Maven ni un JDK chez l'utilisateur final.
- Ne pas retirer les archives portables des artefacts supportés.

## Consequences

### Positives

- Aucune dépendance Git, Maven ou JDK n'est requise chez l'utilisateur final.
- L'installation peut être remplacée ou désinstallée sans effacer le knowledge store.
- Les archives portables restent utilisables en automatisation et côte à côte.
- Les intégrations MINOS et NEXUS restent externes, opt-in et réversibles.

### Négatives

- Un outil d'assemblage d'installeur Windows est une dépendance de build.

### Risques

- Les données persistantes survivent à la désinstallation : leur suppression reste une action de l'utilisateur.

## Compliance and Enforcement

- Règle Archgate `GEN-0088-product-release-installation-and-persistent-data-separation.rules.ts` : `path-task-is-opt-in`.
- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/m20/ProductReleaseContractTest.java` :
  `windowsProductionLayoutSeparatesProgramFromPersistentState`, `linuxProductionLayoutUsesXdgDataConfigAndStateRoots`,
  `releaseScriptsEnforcePerUserInstallExactTagChecksumsAndVerifiedToolBootstrap` (installation par utilisateur,
  tâche `addtopath`, désinstallation limitée à `{app}`, tag exact, workspace propre, checksums).
- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/m20/ReleaseQualificationContractTest.java` :
  même jeu d'assets attendu par `.github/workflows/release.yml` et les scripts `scripts/verify-release-provenance.*`.
- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/m20/InnoSetupToolchainTrustArchitectureTest.java`
  tient la résolution épinglée du compilateur d'installeur.
- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/m28/McpClientIntegrationArchitectureTest.java`
  exige que l'`AppId` de production de `distribution/windows/MORPHEUS.iss` reste inchangé.
- `scripts/validate-m20.ps1` exécute installation, mise à jour et désinstallation sous Windows ;
  `scripts/validate-m20.sh` vérifie l'archive portable Linux, son checksum et la disposition XDG sans JDK.
- Revue : tâche `addtopath` décochée par défaut (`Flags: unchecked`).

## References

- [ADR-0134](GEN-0134-distribution-native-autonome.md) — distribution native d'abord.
- [ADR-0134](GEN-0134-distribution-native-autonome.md) — distribution portable `jpackage`.
- [ADR-0096](GEN-0096-conservative-native-mcp-client-integration.md) — intégration conservatrice des clients MCP.
- [ADR-0110](DATA-0110-a-copy-is-kept-before-the-schema-advances.md) — copie avant migration du schéma.
- [ADR-0146](BE-0146-journal-operationnel-slf4j-et-fichier-borne.md) — journal opérationnel du répertoire de
  logs.
- [Validation M20](../../project/validation/VALIDATION_M20.md)
