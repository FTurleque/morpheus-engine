# ADR-0088 — Product release, installation and persistent-data separation

Statut : **Proposée — M20**

Date : 2026-07-27

## Contexte

M19 qualifie MORPHEUS comme application exploitable, mais les distributions restent principalement des archives portables. M20 doit transformer cette capacité technique en contrat produit stable pour MORPHEUS 1.0.

Le risque principal est de coupler le cycle de vie du programme au knowledge store : une mise à jour ou une désinstallation ne doit jamais effacer implicitement les données persistantes.

## Décision

### 1. Programme et état persistant sont séparés

Sous Windows :

```text
programme  %LOCALAPPDATA%\Programs\MORPHEUS
state root %LOCALAPPDATA%\MORPHEUS
  data\
  config\
  logs\
  backups\
```

Sous Linux :

```text
data    ${XDG_DATA_HOME:-$HOME/.local/share}/morpheus
config  ${XDG_CONFIG_HOME:-$HOME/.config}/morpheus
logs    ${XDG_STATE_HOME:-$HOME/.local/state}/morpheus/logs
backups ${XDG_STATE_HOME:-$HOME/.local/state}/morpheus/backups
```

Les overrides CLI/env existants restent prioritaires.

### 2. Le setup Windows est per-user

Le setup ne demande pas d’élévation et installe par défaut dans `%LOCALAPPDATA%\Programs\MORPHEUS`.

Une tâche explicite et décochée par défaut permet d’ajouter le répertoire programme au `PATH` utilisateur. L’uninstall retire cette entrée lorsqu’elle a été ajoutée.

### 3. L’uninstall ne possède pas le knowledge store

Le désinstalleur retire uniquement les fichiers programme qu’il a installés. Il ne supprime pas `%LOCALAPPDATA%\MORPHEUS`.

La suppression des données est une opération distincte, explicite et hors du contrat d’uninstall par défaut.

### 4. Upgrade in-place

Un `AppId` stable identifie MORPHEUS 1.x. Une nouvelle installation remplace les fichiers programme mais ne touche pas aux données/configuration persistantes.

### 5. Archives portables conservées

Les artefacts supportés sont :

```text
MORPHEUS-<version>-windows-x64-setup.exe
MORPHEUS-<version>-windows-x64-setup.exe.sha256
morpheus-<version>-windows-x64.zip
morpheus-<version>-windows-x64.zip.sha256
morpheus-<version>-linux-x64.tar.gz
morpheus-<version>-linux-x64.tar.gz.sha256
```

Le runtime Java est embarqué dans tous les artefacts d’exécution.

### 6. Checksums obligatoires

Chaque asset de release a un SHA-256 compagnon. La chaîne de build recalcule et vérifie le checksum après écriture.

### 7. Release depuis un tag

La procédure de publication stable part d’un tag Git `v<version>` pointant sur un SHA qualifié. Les artefacts doivent être reconstruisibles depuis ce tag avec les scripts versionnés.

GitHub Actions n’est pas la preuve autoritative de M20 ; les validateurs locaux restent la source de vérité du gate.

## Conséquences

- aucune dépendance Git/Maven/JDK n’est requise chez l’utilisateur final ;
- l’installation peut être remplacée ou désinstallée sans effacer le knowledge store ;
- le ZIP/tar portable reste utilisable en automation et side-by-side ;
- les intégrations MINOS/NEXUS restent externes, opt-in et réversibles ;
- un outil d’assemblage d’installeur Windows est une dépendance de build uniquement.

## Validation avant acceptation

L’ADR ne passe à **Acceptée** qu’après preuve réelle :

```text
setup install PASS
PATH option PASS
no-JDK runtime PASS
upgrade preservation PASS
uninstall preservation PASS
Windows/Linux portable PASS
checksums verified PASS
full reactor PASS
```
