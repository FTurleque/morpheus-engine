# Statut et autorité de la documentation MORPHEUS

Statut : **ACTIF — MORPHEUS 1.2.0 PUBLIÉ — BASELINE DÉVELOPPEMENT 1.2.1**

Dernière mise à jour : 27 août 2026

## Hiérarchie d’autorité

Pour l’état courant de développement :

```text
root pom.xml + code + tests
        ↓
docs/governance/ROADMAP.md
        ↓
docs/developer/BUILD_AND_TEST.md
        ↓
GitHub exact-head CI / Security / CodeQL
```

Pour les preuves historiques D2 :

```text
docs/roadmap/D2_EXECUTION.md
        ↓
docs/validation/VALIDATION_D2.md
        ↓
PR #121 exact-head Windows + Linux/WSL evidence
```

Pour la release stable déjà publiée :

```text
docs/validation/VALIDATION_R3.md
        ↓
v1.2.0 + exact-tag assets publiés
```

Les plans et preuves des jalons terminés restent des archives factuelles. Ils ne sont jamais réécrits pour leur attribuer un résultat, une commande ou un SHA postérieur.

## Documentation active

```text
README.md
docs/README.md
docs/user/README.md
docs/user/INSTALLATION.md
docs/user/MCP_CLIENTS.md
docs/user/UPGRADE_1_2.md
docs/developer/README.md
docs/developer/BUILD_AND_TEST.md
docs/developer/MCP.md
distribution/README.md
docs/governance/ROADMAP.md
docs/governance/DOCUMENTATION_STATUS.md
docs/validation/README.md
docs/validation/VALIDATION_R3.md
docs/release/RELEASE_NOTES_1.2.0.md
integration/README.md
scripts/README.md
```

`docs/roadmap/D2_EXECUTION.md` et `docs/validation/VALIDATION_D2.md` restent référencés comme **archives historiques D2**, pas comme état actif du produit.

## Release stable publiée

```text
Version                    1.2.0
Tag                        v1.2.0
Tag target                 3ad9ebf030b58df97482e21e272c24feae6b9d86
Qualified executable SHA   d08542026817f0d743766656a0197790c6809eca
PR                         #118 MERGED
Issue                      #117 CLOSED / completed
GitHub Release             stable / latest
Assets                     8/8 uploaded
Published parity           8/8 PASS
Exact-tag Windows          PASS
Exact-tag Linux            PASS
```

## D2 — terminé et historique

D2 a été qualifié et intégré le 11 août 2026.

```text
Issue                      #120 CLOSED / completed
PR                         #121 MERGED
Qualified exact head       fa54b3d6a316357b2ef79afd2243619a64a05f3b
Develop merge commit       c12882d6e43daab600f6580f22f8eff2fbc6f4de
Windows local gate         PASS
Linux/WSL local gate       PASS
Same exact SHA             PASS
.github/workflows delta    NONE
CI used as D2 gate         false
```

Hardening D2 intégré :

```text
Jackson                    3.1.5 LTS
sqlite-jdbc                3.53.2.0
OWASP Dependency-Check     12.2.2
absolute coverage floors   40% lines / 35% branches
dependency hygiene         blocking
```

La règle « CI NOT USED » appartient exclusivement au protocole historique D2. Elle ne s’applique pas aux protections continues actuelles.

## Baseline développement 1.2.1

```text
Version                    1.2.1
Java                       21
Maven Wrapper              3.9.16
Maven modules              17
MCP SDK                    2.0.1
SQLite JDBC                3.53.2.0
Jackson                    3.2.2
```

Qualité continue :

```text
MORPHEUS CI                exact-head Windows + Ubuntu
MORPHEUS Security          OWASP Dependency-Check
MORPHEUS CodeQL            security-extended
Surefire ratchet           >= 1300
Architecture ratchet       >= 335
Global line ratchet        >= 54.5%
Global branch ratchet      >= 47.7%
PR changed line coverage   >= 80%
PR changed branch coverage >= 70%
Dependency hygiene         blocking
CycloneDX SBOM             required
Build provenance           required
```

Le gate durable est documenté dans [`../developer/BUILD_AND_TEST.md`](../developer/BUILD_AND_TEST.md).

## Autorité des commandes Windows

Le dispatcher actif est :

```powershell
.\scripts\validate.cmd <target> [arguments]
```

Pour la baseline courante :

```powershell
.\scripts\validate.cmd m21 -Version 1.2.1
```

Les anciens wrappers `validate-*.cmd` à la racine ne font plus partie de la documentation active.

## Suivis clôturés

```text
#154  vérifier SonarCloud et les réglages GitHub Security administrateur — clôturée
#184  relever progressivement la couverture historique sur les frontières prioritaires — clôturée
```

## Suivis encore ouverts

Les éléments suivants ne doivent pas être déclarés terminés sans preuve externe ou événement réel :

```text
#185  qualifier le workflow de release attestée lors d'une vraie v1.2.1+ release
```

## État fonctionnel

```text
C0 → M28       ✅ validés / intégrés
D0 + D1        ✅ validés / intégrés
R1             ✅ 1.0.0 publié
R2             ✅ 1.1.0 publié
R3             ✅ 1.2.0 publié
D2             ✅ qualifié / intégré
1.2.1          🔧 baseline de développement active
```
