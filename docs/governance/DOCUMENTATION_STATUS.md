# Statut et autorité de la documentation MORPHEUS

Statut : **ACTIF — MORPHEUS 1.1.0 PUBLIÉ — R3 / 1.2.0 EN PRÉPARATION**

Dernière mise à jour : 30 juillet 2026

## Hiérarchie d’autorité

```text
docs/governance/ROADMAP.md
        ↓
docs/roadmap/R3_EXECUTION.md
        ↓
docs/validation/VALIDATION_R3.md
        ↓
code + tests + logs exact-head
```

R2 est terminé et MORPHEUS 1.1.0 reste la release stable publiée. M28 est validé et intégré dans `develop`. R3 consolide ce contenu dans un candidat MORPHEUS 1.2.0 sur la branche `r3-release-1.2.0`.

La version 1.2.0 n’est pas publiée tant que les gates Windows et Linux/WSL, le merge dans `main`, le tag exact `v1.2.0`, les builds exact-tag et la parité des huit assets ne sont pas démontrés.

## Documentation active

```text
README.md
docs/README.md
docs/user/README.md
docs/user/MCP_CLIENTS.md
docs/user/UPGRADE_1_2.md
docs/developer/README.md
docs/developer/MCP.md
docs/governance/ROADMAP.md
docs/governance/DOCUMENTATION_STATUS.md
docs/roadmap/M28_EXECUTION.md
docs/roadmap/R3_EXECUTION.md
docs/validation/VALIDATION_M28.md
docs/validation/VALIDATION_R3.md
docs/release/RELEASE_NOTES_1.2.0.md
integration/README.md
```

## Release stable publiée

```text
Version                1.1.0
Tag                    v1.1.0
Tag target             31506029ded1101f0571edeb0d79c59bbf3f68c6
PR                     #114 MERGED
Issue                  #113 CLOSED / completed
GitHub Release         stable
Assets                 8/8 uploaded
Published parity       8/8 PASS
Published at           2026-07-30T14:13:17Z
```

## M28 intégré

```text
Issue                  #115 CLOSED / completed
PR                     #116 MERGED
Merge commit           1e606c63b9f74e45a2c0b3d2162d3db4721f4af4
Develop post-merge     2080c99895115464dafefb6515541666c5d972d8
Qualified exact head   58adfeb13b79808da12830f2d0b0b24ec46f67e6
Target release         1.2.0
Transport              MCP STDIO natif
Docker required        false
```

Clients :

```text
Copilot JetBrains / IntelliJ
Copilot CLI
Claude Code
Claude Desktop
OpenAI Codex
```

Garanties :

```text
client configuration is explicit opt-in
backup before JSON write
unrelated JSON content is preserved
foreign `morpheus` entry is not overwritten
compatible preexisting entry is never removed
managed modified entry is preserved
uninstall is state-driven
native CLI command timeout is bounded
stdout remains MCP JSON-RPC only
Docker is not required
```

## R3 actif

```text
Issue                  #117 OPEN
Branch                 r3-release-1.2.0
Main baseline          8dfbe807cb1a57a7750d9b9ac69def0da6c79ff3
Develop baseline       2080c99895115464dafefb6515541666c5d972d8
Target version         1.2.0
Target tag             v1.2.0
Windows exact-head     NOT RUN
Linux/WSL exact-head   NOT RUN
Main merge             NOT AUTHORIZED
GitHub Release         NOT PUBLISHED
```

R3 porte uniquement la stabilisation produit et release : version 1.2.0 cohérente sur les 17 POM, builders, validateurs, notes de version, guide d’upgrade, qualification dual-platform, tag exact et publication vérifiée.

## Baseline fonctionnelle

```text
C0 → M20       ✅ validés et intégrés
D0 + D1        ✅ validés et intégrés
R1             ✅ MORPHEUS 1.0.0 publié
M21 → M27      ✅ validés et intégrés
R2             ✅ MORPHEUS 1.1.0 publié
M28            ✅ validé et intégré dans develop
R3             🚧 MORPHEUS 1.2.0 en préparation
```

## Politique CI — juillet 2026

Aucune GitHub Actions / CI ne sert de gate. Aucun workflow ne doit être inspecté, relancé, déclenché ou modifié pour R3. Les preuves autoritatives sont les sorties locales Windows et Linux/WSL exact-head sur le même SHA.
