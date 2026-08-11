# Feuille de route — MORPHEUS

Statut : **MORPHEUS 1.2.0 PUBLIÉ — D2 POST-R3 HARDENING EN COURS**

Dernière mise à jour : 5 août 2026

MORPHEUS est piloté par des preuves : contrats stables, tests reproductibles, SHA exacts et réponse explicite à chaque question de sortie.

## Politique de branches

```text
feature / milestone branch -> develop
release branch             -> main après qualification
develop                    -> intégration
main                       -> stabilisation / livraison
```

## Baseline stable

```text
C0 → M20      ✅ validés et intégrés
D0 + D1       ✅ validés et intégrés
R1            ✅ MORPHEUS 1.0.0 publié
M21 → M27     ✅ validés et intégrés
R2            ✅ MORPHEUS 1.1.0 publié
M28           ✅ validé, intégré et livré dans 1.2.0
R3            ✅ MORPHEUS 1.2.0 publié
D2            🚧 Post-R3 Repository Hardening
```

```text
stable version              1.2.0
stable tag                  v1.2.0
stable release commit       3ad9ebf030b58df97482e21e272c24feae6b9d86
qualified executable SHA    d08542026817f0d743766656a0197790c6809eca
R3 PR                       #118 MERGED
R3 issue                    #117 CLOSED / completed
published assets            8/8
published parity            8/8 PASS
D2 issue                    #120 OPEN
D2 branch                   d2-post-r3-hardening
```

## NOW — D2 Post-R3 Repository Hardening

Question de sortie :

> Le HEAD post-R3 est-il sécurisé, documenté et localement qualifié sur Windows + Linux/WSL, sans dépendre d’aucune CI et sans modifier GitHub Actions ?

Périmètre :

```text
Jackson 3.1 LTS patched baseline
sqlite-jdbc patched baseline
local Maven SCA
JSON nesting regression
coverage ratchet 40% / 35%
blocking dependency hygiene
active documentation reconciliation
Windows exact-head local gate
Linux/WSL exact-head local gate
same exact SHA
```

Plan : [`../roadmap/D2_EXECUTION.md`](../roadmap/D2_EXECUTION.md).
Preuve : [`../validation/VALIDATION_D2.md`](../validation/VALIDATION_D2.md).

## Contrainte D2 — aucune CI

D2 n’utilise aucune GitHub Actions / CI comme source de vérité.

```text
workflow inspection         NOT USED
workflow rerun/dispatch     NOT USED
.github/workflows mutation  FORBIDDEN
CI status as gate           FORBIDDEN
Windows local gate          REQUIRED
Linux/WSL local gate        REQUIRED
```

Les validateurs D2 refusent tout delta sous `.github/workflows/`.

Cette contrainte est spécifique au jalon D2 demandé ; elle ne redéfinit pas à elle seule la politique future de CI après D2.

## R3 — MORPHEUS 1.2.0

R3 reste la preuve de publication stable :

```text
qualified executable      d08542026817f0d743766656a0197790c6809eca
main release commit       3ad9ebf030b58df97482e21e272c24feae6b9d86
tag                       v1.2.0
Windows exact-head        PASS
Linux/WSL exact-head      PASS
exact-tag builds          PASS
GitHub Release            stable / latest
published assets          8/8
published parity          8/8 PASS
```

Référence : [`../validation/VALIDATION_R3.md`](../validation/VALIDATION_R3.md).

## M28 — MCP Client Integration & Installer Wiring

M28 est livré dans 1.2.0 :

```text
native MCP command       morpheus mcp --stdio
Copilot JetBrains        supported
Copilot CLI              supported
Claude Code              supported
Claude Desktop           supported
OpenAI Codex             supported
configuration            explicit opt-in
foreign overwrite        prohibited
uninstall                state-driven
Docker required          false
```

## Invariants

```text
DomainIdentity != source path
SpecificationVersion != KnowledgeSnapshot
PROPOSED never leaks into CURRENT
READ != WRITE
ALLOWED != applied
facts != inference
reasoning != mutation
MCP local remains native-first
Docker is not required
third-party client modification is opt-in
foreign `morpheus` entry is never overwritten
manual client changes are preserved
uninstall is state-driven
stable tag is immutable
release tag target == exact main release commit
published assets == exact-tag assets
D2 CI workflow delta == NONE
```

## Après D2

Aucun nouveau grand jalon produit n’est défini par ce document tant que D2 n’est pas qualifié et intégré.

```text
NEXT
  terminer D2
  réconcilier develop après merge
  sélectionner le prochain milestone produit
```
