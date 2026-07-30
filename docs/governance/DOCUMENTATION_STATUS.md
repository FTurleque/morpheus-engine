# Statut et autorité de la documentation MORPHEUS

Statut : **ACTIF — MORPHEUS 1.1.0 PUBLIÉ — M28 EN QUALIFICATION**

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

R2 est terminé et 1.1.0 reste la release stable publiée. M28 est le jalon actif suivi par l’issue #115.

## Contrats exposés

Ordre d’autorité :

```text
code + tests
contracts/public-surfaces.tsv
docs/openapi/
docs/reference/
docs/developer/
docs/user/
```

Pour une release, le tag exact, les manifestes et les preuves de validation font autorité. Pour M28, aucun document ne peut déclarer PASS avant les logs locaux Windows et Linux/WSL sur le même SHA.

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
Qualified exact head   31212087ee5fab3c88b269d56f7f21402f31b683
PR                     #114 MERGED
Issue                  #113 CLOSED / completed
GitHub Release         stable
Assets                 8/8 uploaded
Published parity       8/8 PASS
Published at           2026-07-30T14:13:17Z
```

## Chantier actif M28

```text
Issue                  #115
Branch                 m28-mcp-client-integration
Baseline main          8dfbe807cb1a57a7750d9b9ac69def0da6c79ff3
Baseline develop       8dfbe807cb1a57a7750d9b9ac69def0da6c79ff3
Object                 MCP Client Integration & Installer Wiring
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

## Garanties documentées

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
Windows exact-head      NOT RUN
Linux/WSL exact-head    NOT RUN
same SHA                NOT PROVEN
post-gate delta         NOT PROVEN
PR                      NOT OPEN
merge                   NOT AUTHORIZED
```

## Baseline fonctionnelle

```text
C0 → M20       ✅ validés et intégrés
D0 + D1        ✅ validés et intégrés
R1             ✅ MORPHEUS 1.0.0 publié
M21 → M27      ✅ validés et intégrés
R2             ✅ MORPHEUS 1.1.0 publié
M28            🚧 implémenté, qualification en attente
```

## Politique CI — juillet 2026

Aucune GitHub Actions / CI ne sert de gate. Les preuves autoritatives sont les sorties locales Windows et Linux/WSL exact-head sur le même SHA.

Aucun document ne doit déplacer le tag `v1.1.0`, déclarer prématurément M28 PASS ou annoncer `v1.2.0` comme publiée avant une phase de release dédiée.
