# Statut et autorité de la documentation MORPHEUS

Statut : **ACTIF — MORPHEUS 1.1.0 PUBLIÉ — M28 TERMINÉ ET INTÉGRÉ**

Dernière mise à jour : 30 juillet 2026

## Hiérarchie d’autorité

```text
docs/governance/ROADMAP.md
        ↓
docs/roadmap/M28_EXECUTION.md
        ↓
docs/validation/VALIDATION_M28.md
        ↓
code + tests + logs exact-head
```

R2 est terminé et 1.1.0 reste la release stable publiée. M28 est validé et intégré dans `develop`.

## Documentation active

```text
README.md
docs/README.md
docs/user/README.md
docs/user/MCP_CLIENTS.md
docs/developer/README.md
docs/developer/MCP.md
docs/governance/ROADMAP.md
docs/governance/DOCUMENTATION_STATUS.md
docs/roadmap/M28_EXECUTION.md
docs/validation/VALIDATION_M28.md
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

## État de preuve

```text
implementation          COMPLETE
documentation           COMPLETE
Windows exact-head      PASS
Linux/WSL exact-head    PASS
same executable SHA     PASS
post-gate executable    NONE
ADR-0096                ACCEPTED
PR #116                 MERGED
issue #115              CLOSED / completed
```

## Baseline fonctionnelle

```text
C0 → M20       ✅ validés et intégrés
D0 + D1        ✅ validés et intégrés
R1             ✅ MORPHEUS 1.0.0 publié
M21 → M27      ✅ validés et intégrés
R2             ✅ MORPHEUS 1.1.0 publié
M28            ✅ validé et intégré
```

## Suite

La prochaine phase porte sur la consolidation et la publication de MORPHEUS 1.2.0. Le tag `v1.1.0` reste immuable.

## Politique CI — juillet 2026

Aucune GitHub Actions / CI ne sert de gate. Les preuves autoritatives sont les sorties locales Windows et Linux/WSL exact-head sur le même SHA.