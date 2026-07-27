# Validation M20 — Release Engineering, Installation PROD & MORPHEUS 1.0

Statut : **PASS — preuves Windows et Linux réelles acquises**

Issue : #92  
PR : #93  
Branche : `m20-release-engineering-prod-installation-1.0`

## Verdict

Question de sortie :

> MORPHEUS peut-il être installé, mis à jour, diagnostiqué et désinstallé comme un produit Windows/Linux sans Git, Maven ou JDK utilisateur, tout en préservant les données et en conservant les archives portables pour l’automatisation ?

**Réponse : OUI**, pour MORPHEUS **1.0.0**, avec preuves Windows et Linux exécutées sur le même SHA de code exact :

```text
9199ed43c4bd8596a97db055eeff17ae31399eb8
```

Les éventuels commits post-gate de finalisation M20 sont documentaires uniquement et doivent être vérifiés par diff avant passage de la PR en Ready.

## Règle de preuve

La preuve M20 provient exclusivement des validateurs versionnés exécutés sur des workspaces Git propres :

```text
Windows : validate-m20.cmd -> scripts/validate-m20.ps1
Linux   : scripts/validate-m20.sh
```

GitHub Actions n’est pas la source de vérité du jalon.

## Preuve Windows réelle

Exécution : **27 juillet 2026**  
SHA exact : `9199ed43c4bd8596a97db055eeff17ae31399eb8`  
Version : `1.0.0`

```text
M20 VALIDATION SUMMARY
SHA:       9199ed43c4bd8596a97db055eeff17ae31399eb8
Version:   1.0.0
Result:    PASS
Linux proof: NOT EXECUTED BY THIS WINDOWS VALIDATOR
Full reactor tests: 454; failures: 0; errors: 0; skipped: 0; suites: 128
Architecture tests: 182; failures: 0; errors: 0; skipped: 0

Workspace / SHA / version              PASS
Full Maven reactor                     PASS
Installer contract                     PASS
Validation tag preparation             PASS
Tagged Windows release build           PASS
SHA-256 + release manifest             PASS
Install + PATH + no-JDK + API          PASS
Upgrade preserves data/config          PASS
Uninstall preserves persistent state   PASS
Reinstall reuses persistent state      PASS
Exact-head stability                   PASS
```

Toolchain observée :

```text
Windows validation host  Windows
MORPHEUS                  1.0.0
Inno Setup                7.0.2, bootstrap Authenticode vérifié
Inno signer               Pyrsys B.V.
```

Assets Windows qualifiés :

```text
MORPHEUS-1.0.0-windows-x64-setup.exe
bytes   32353416
sha256  61d216ec8c706ccfe6bb24bd9da791e9d6dc8463a39fa4ee9135db91cca20223

morpheus-1.0.0-windows-x64.zip
bytes   36630977
sha256  4b3a63d50850fc2ba78ca2c5bc4de0e61e3749bce8cb132b6d0417a0d8c54152
```

Contrats réellement prouvés sous Windows :

```text
setup per-user/non-admin             PASS
programme != données persistantes    PASS
option PATH utilisateur              PASS
runtime sans JDK utilisateur         PASS
version packagée 1.0.0              PASS
MINOS/NEXUS désactivés par défaut    PASS
API health/readiness/metrics         PASS
SQLite persistent state              PASS
upgrade conserve DB/config           PASS
uninstall conserve DB/config         PASS
uninstall retire l'entrée PATH       PASS
réinstallation retrouve l'état      PASS
release depuis tag exact == HEAD     PASS
checksums et manifest                PASS
exact-head stability                 PASS
```

## Preuve Linux réelle

Exécution : **27 juillet 2026**  
Environnement : **WSL2, clone sous `$HOME` sur filesystem Linux/ext4**  
SHA exact : `9199ed43c4bd8596a97db055eeff17ae31399eb8`  
Version : `1.0.0`

Toolchain observée :

```text
OpenJDK Runtime  21.0.11
javac            21.0.11
jpackage         21.0.11
```

Résumé réel :

```text
M20 LINUX VALIDATION SUMMARY
SHA:       9199ed43c4bd8596a97db055eeff17ae31399eb8
Version:   1.0.0
Result:    PASS
Full Maven reactor: PASS
Tagged Linux release build: PASS
SHA-256 verification: PASS
Embedded runtime / no user JDK: PASS
XDG data/config/state layout: PASS
MINOS/NEXUS opt-in defaults: PASS
Exact-head stability: PASS
```

Le reactor Linux complet a terminé :

```text
14/14 modules SUCCESS
BUILD SUCCESS
Architecture 182/182 PASS
Failures 0
Errors 0
Skipped 0
```

Le jeu de tests correspond au reactor M20 qualifié : **454/454 PASS**.

Asset Linux qualifié :

```text
morpheus-1.0.0-linux-x64.tar.gz
bytes   39449807
sha256  f0c28959f492e246810293db74f26e6929a27bb2b6d75bad1f6f48ca309c1bf8
```

Contrats réellement prouvés sous Linux :

```text
portable tar.gz                         PASS
release depuis tag exact == HEAD       PASS
SHA-256                                 PASS
manifest version/tag/SHA                PASS
runtime Java embarqué                   PASS
aucun JDK utilisateur requis            PASS
version packagée 1.0.0                 PASS
API health/readiness/metrics            PASS
XDG data/config/state                   PASS
SQLite créé sous XDG_DATA_HOME          PASS
MINOS/NEXUS désactivés par défaut       PASS
exact-head stability                    PASS
```

## Résultats consolidés

```text
Code SHA qualifié             9199ed43c4bd8596a97db055eeff17ae31399eb8
Version                       1.0.0
Windows                       PASS
Linux ext4 / WSL2             PASS
Tests                         454/454 PASS
Architecture                  182/182 PASS
Failures/errors/skipped       0/0/0
Reactor                       14/14 SUCCESS
Windows setup                 PASS
Windows portable              PASS
Linux portable                PASS
SHA-256                       PASS Windows + Linux
No-user-JDK                   PASS Windows + Linux
PATH option                   PASS Windows
Program/data separation       PASS
Upgrade preservation          PASS
Uninstall preservation        PASS
Integrations opt-in           PASS Windows + Linux
Release from exact tag        PASS Windows + Linux
Exact-head stability          PASS Windows + Linux
```

## Conclusion

M20 répond positivement à sa question de sortie. MORPHEUS 1.0.0 possède désormais une chaîne de distribution produit prouvée sur Windows et Linux : runtime embarqué, installation Windows per-user, archives portables, checksums, séparation programme/données, upgrade/uninstall conservateurs et releases liées à un tag/SHA exact.

ADR-0088 peut être **Acceptée — M20**.  
M20-S9 peut être déclaré terminé après réconciliation documentaire et contrôle explicite que le delta post-gate est documentaire uniquement.  
La PR #93 peut devenir **Ready for review** après ce contrôle.

**Le merge reste interdit sans autorisation explicite du propriétaire.**
