# Statut et autorité de la documentation MORPHEUS

Statut : **ACTIF — MORPHEUS 1.1.0 PUBLIÉ — M28 QUALIFIÉ**

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

R2 est terminé et 1.1.0 reste la release stable publiée. M28 a passé les gates Windows et Linux/WSL sur le même SHA exécutable.

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

## M28

```text
Issue                  #115
PR                     #116
Branch                 m28-mcp-client-integration
Baseline develop       8dfbe807cb1a57a7750d9b9ac69def0da6c79ff3
Qualified exact head   58adfeb13b79808da12830f2d0b0b24ec46f67e6
Target release         1.2.0
Transport              MCP STDIO natif
Docker required        false
```

Implémentation documentée :

```text
integration/configure-mcp-clients.ps1
integration/configure-mcp-clients-setup.ps1
scripts/verify-m28-mcp-client-integration.ps1
scripts/validate-m28.ps1
scripts/validate-m28.sh
validate-m28.cmd
distribution/windows/MORPHEUS.iss
docs/user/MCP_CLIENTS.md
docs/developer/MCP.md
```

Clients :

```text
Copilot JetBrains / IntelliJ
Copilot CLI
Claude Code
Claude Desktop
OpenAI Codex
```

## Garanties

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
review threads          0
blocking reviews        0
PR                      READY / MERGEABLE
merge                   AUTHORIZED
```

## Baseline fonctionnelle

```text
C0 → M20       ✅ validés et intégrés
D0 + D1        ✅ validés et intégrés
R1             ✅ MORPHEUS 1.0.0 publié
M21 → M27      ✅ validés et intégrés
R2             ✅ MORPHEUS 1.1.0 publié
M28            ✅ qualifié, merge vers develop autorisé
```

## Politique CI — juillet 2026

Aucune GitHub Actions / CI ne sert de gate. Les preuves autoritatives sont les sorties locales Windows et Linux/WSL exact-head sur le même SHA.

Le tag `v1.1.0` reste immuable. `v1.2.0` ne sera annoncé comme publié qu’après une phase de consolidation release dédiée.