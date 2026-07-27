# R1 — Validation publication officielle MORPHEUS 1.0.0

Statut : **PASS — release publiée et vérifiée**

Date : 27 juillet 2026

## Identité de release

```text
Version      1.0.0
Tag stable   v1.0.0
Git SHA      51f6a120f3461c8d8c24323f3db8211d28d6cb42
Issue        #96
```

GitHub compare confirme :

```text
51f6a120f3461c8d8c24323f3db8211d28d6cb42 == v1.0.0
status     identical
ahead_by   0
behind_by  0
files      0
```

Le code M20 qualifié était `9199ed43c4bd8596a97db055eeff17ae31399eb8`. Le delta entre ce SHA et le SHA de release final est strictement documentaire ; aucun delta exécutable n’a été introduit après la qualification M20.

## Build Windows exact-tag

Résultat : **PASS**.

```text
MORPHEUS-1.0.0-windows-x64-setup.exe
bytes   32351809
sha256  b5152ebe6be599905db9cfa907ab001b87edf2f06172812e1b9639d3c3a78923

morpheus-1.0.0-windows-x64.zip
bytes   36631038
sha256  d59b592bc2c9bfa7de5dad8d756bf155de6dc40e64815f8b685774933633b9d6
```

Preuves observées : packaging uber-JAR, smokes CLI/API, MINOS/NEXUS désactivés par défaut, runtime embarqué, `jdk.httpserver` + `java.sql`, bootstrap Inno Setup signé, setup compilé, SHA-256 et manifest exact-tag.

## Build Linux exact-tag

Exécuté sous WSL sur filesystem Linux réel :

```text
/home/fturleque/morpheus-r1-release
```

Résultat : **PASS**.

```text
morpheus-1.0.0-linux-x64.tar.gz
bytes   39449845
sha256  b9527ab661909e9a9f3a38dd68fcbc6bb874541009334f122e6362d9234aa6cd
```

Preuves observées : Maven packaging, shaded-JAR proof, smokes CLI/API, MINOS/NEXUS désactivés par défaut, runtime embarqué, checksum et manifest exact-tag.

## Contrôle croisé final

Les deux manifests staged ont été relus avant publication :

```text
version = 1.0.0
tag     = v1.0.0
gitSha  = 51f6a120f3461c8d8c24323f3db8211d28d6cb42
```

Les trois payloads binaires ont été rehachés après staging et correspondent à leurs fichiers `.sha256` : **PASS**.

## GitHub Release

Release : **MORPHEUS 1.0.0**

```text
tagName       v1.0.0
isDraft       false
isPrerelease  false
assets        8/8 uploaded
```

Assets publiés :

```text
MORPHEUS-1.0.0-windows-x64-setup.exe
MORPHEUS-1.0.0-windows-x64-setup.exe.sha256
morpheus-1.0.0-windows-x64.zip
morpheus-1.0.0-windows-x64.zip.sha256
morpheus-1.0.0-linux-x64.tar.gz
morpheus-1.0.0-linux-x64.tar.gz.sha256
morpheus-1.0.0-windows-x64-release-manifest.json
morpheus-1.0.0-linux-x64-release-manifest.json
```

Les digests GitHub des trois payloads principaux correspondent aux SHA-256 qualifiés avant upload.

## Chronologie des incidents Linux

Deux incidents de lancement ont précédé le build Linux final :

1. quoting PowerShell/WSL invalide — build non exécuté ;
2. `JAVA_HOME` absent — toolchain bloquée avant build.

Le build final avec `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64` est PASS. Aucun de ces deux incidents n’est classé comme échec MORPHEUS.

## Résultat R1

```text
release SHA exact                 PASS
Windows exact-tag build           PASS
Linux exact-tag build             PASS
SHA-256 staged                    PASS
manifests version/tag/gitSha      PASS
stable tag GitHub exact SHA       PASS
GitHub Release stable             PASS
8 expected assets uploaded        PASS
Result                            R1 PASS
```

MORPHEUS 1.0.0 est officiellement publié. La prochaine trajectoire active commence avec **M21 — Production Integrity & Surface Convergence**.
