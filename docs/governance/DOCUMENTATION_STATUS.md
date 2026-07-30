# Statut et autorité de la documentation MORPHEUS

Statut : **ACTIF — MORPHEUS 1.2.0 PUBLIÉ — R3 TERMINÉ**

Dernière mise à jour : 30 juillet 2026

## Hiérarchie d’autorité

```text
docs/governance/ROADMAP.md
        ↓
docs/roadmap/R3_EXECUTION.md
        ↓
docs/validation/VALIDATION_R3.md
        ↓
code + tests + logs exact-head + artefacts exact-tag publiés
```

R3 est terminé. MORPHEUS 1.2.0 est la release stable publiée et remplace 1.1.0 comme baseline produit.

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
Version                    1.2.0
Tag                        v1.2.0
Tag target                 3ad9ebf030b58df97482e21e272c24feae6b9d86
Qualified executable SHA   d08542026817f0d743766656a0197790c6809eca
Final PR head              a2023d96dd0c4ad6d1f7a658bf3e7b4f8390e1bb
PR                         #118 MERGED
Issue                      #117 CLOSED / completed
GitHub Release             stable / latest
Assets                     8/8 uploaded
Published parity           8/8 PASS
Exact-tag Windows          PASS
Exact-tag Linux            PASS
```

Release précédente :

```text
Version                    1.1.0
Tag                        v1.1.0
Release commit             31506029ded1101f0571edeb0d79c59bbf3f68c6
PR                         #114 MERGED
Issue                      #113 CLOSED / completed
Published parity           8/8 PASS
```

## M28 livré dans 1.2.0

```text
Issue                      #115 CLOSED / completed
PR                         #116 MERGED
Merge commit               1e606c63b9f74e45a2c0b3d2162d3db4721f4af4
Qualified exact head       58adfeb13b79808da12830f2d0b0b24ec46f67e6
Release                    MORPHEUS 1.2.0
Transport                  MCP STDIO natif
Docker required            false
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

## Preuve R3 finale

```text
Windows exact-head          PASS
Linux/WSL exact-head        PASS
same executable SHA         PASS
reactor                     17/17 SUCCESS
tests                       608 PASS sur les deux plateformes
architecture                243 PASS sur les deux plateformes
post-gate executable delta  NONE
main merge                  PASS
immutable tag               PASS
exact-tag builds            PASS Windows + Linux
published assets            8/8
published parity            8/8 PASS
```

## Baseline fonctionnelle

```text
C0 → M20       ✅ validés et intégrés
D0 + D1        ✅ validés et intégrés
R1             ✅ MORPHEUS 1.0.0 publié
M21 → M27      ✅ validés et intégrés
R2             ✅ MORPHEUS 1.1.0 publié
M28            ✅ validé, intégré et livré dans 1.2.0
R3             ✅ MORPHEUS 1.2.0 publié
```

## Politique CI — juillet 2026

Aucune GitHub Actions / CI n’a servi de gate. Aucun workflow n’a été inspecté, relancé, déclenché ou modifié pour R3. Les preuves autoritatives sont les sorties locales Windows et Linux/WSL exact-head, les builds exact-tag et la parité publiée des huit assets.