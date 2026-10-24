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

**1. Programme et état persistant sont séparés.** Sans réglage explicite, les emplacements sont :

```text
Windows
  programme   %LOCALAPPDATA%\Programs\MORPHEUS
  état        %LOCALAPPDATA%\MORPHEUS\{data,config,logs,backups}

Linux
  data        ${XDG_DATA_HOME:-$HOME/.local/share}/morpheus
  config      ${XDG_CONFIG_HOME:-$HOME/.config}/morpheus
  logs        ${XDG_STATE_HOME:-$HOME/.local/state}/morpheus/logs
  backups     ${XDG_STATE_HOME:-$HOME/.local/state}/morpheus/backups
```

Les options de la CLI et les variables d'environnement (`MORPHEUS_DATA_DIR`, `MORPHEUS_CONFIG_DIR`, `MORPHEUS_DB`,
`MORPHEUS_LOGS_DIR`, `MORPHEUS_BACKUPS_DIR`) restent prioritaires (`morpheus-cli/.../CliLayout.java`).

**2. Le setup Windows est par utilisateur.** Il ne demande pas d'élévation et installe par défaut dans
`%LOCALAPPDATA%\Programs\MORPHEUS`. Une tâche explicite, décochée par défaut, ajoute le répertoire programme au `PATH`
utilisateur ; la désinstallation retire cette entrée lorsqu'elle a été ajoutée.

**3. La désinstallation ne possède pas le knowledge store.** Le désinstalleur retire les fichiers programme et ne
supprime pas `%LOCALAPPDATA%\MORPHEUS`. La suppression des données est une opération distincte, explicite, hors du
contrat de désinstallation par défaut. Le retrait des entrées de clients MCP que MORPHEUS gère relève d'ADR-0096.

**4. Mise à jour en place.** Un `AppId` stable identifie MORPHEUS 1.x. Une nouvelle installation remplace les fichiers
programme sans toucher aux données ni à la configuration persistantes.

**5. Artefacts.** Une release publie un setup Windows, une archive portable Windows (ZIP) et une archive portable
Linux (`tar.gz`), chacun avec son SHA-256 compagnon (`.sha256`), ainsi que l'inventaire des composants du commit
étiqueté avec son SHA-256 compagnon. La liste exacte des assets est celle qu'attend `.github/workflows/release.yml`.
Le runtime Java est embarqué dans tous les artefacts d'exécution. Les archives portables restent supportées.

**6. Checksums vérifiés.** La chaîne de build calcule le SHA-256 de chaque artefact qu'elle accompagne d'un `.sha256`
et le vérifie après écriture.

**7. Release depuis un tag.** La publication stable part d'un tag Git `v<version>` qui pointe sur un SHA qualifié,
dans un espace de travail Git propre. Les artefacts sont reconstructibles depuis ce tag avec les scripts versionnés.
Le gate de release est conclu par les validateurs versionnés (`scripts/validate-m20.ps1`, `scripts/validate-m20.sh`) ;
GitHub Actions n'en est pas la preuve autoritative.

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
