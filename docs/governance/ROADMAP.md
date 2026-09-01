# Feuille de route — MORPHEUS

Statut : **MORPHEUS 1.2.0 PUBLIÉ — BASELINE DÉVELOPPEMENT 1.2.1 / HARDENING CONTINU**

Dernière mise à jour : 27 août 2026

MORPHEUS est piloté par des preuves : contrats stables, tests reproductibles, SHA exacts et réponse explicite à chaque question de sortie.

## Politique de branches

```text
feature / fix branch        -> develop via PR
promotion branch temporaire -> main après qualification
develop                     -> intégration protégée
main                        -> stabilisation / livraison protégée
```

Les protections repository imposent des checks exact-head et interdisent de considérer une simple documentation comme preuve d’exécution.

## Baseline stable et historique

```text
C0 → M20      ✅ validés et intégrés
D0 + D1       ✅ validés et intégrés
R1            ✅ MORPHEUS 1.0.0 publié
M21 → M27     ✅ validés et intégrés
R2            ✅ MORPHEUS 1.1.0 publié
M28           ✅ validé, intégré et livré dans 1.2.0
R3            ✅ MORPHEUS 1.2.0 publié
D2            ✅ Post-R3 Repository Hardening qualifié et intégré
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
D2 issue                    #120 CLOSED / completed
D2 PR                       #121 MERGED
D2 qualified exact head     fa54b3d6a316357b2ef79afd2243619a64a05f3b
D2 develop merge commit     c12882d6e43daab600f6580f22f8eff2fbc6f4de
```

## D2 — terminé

D2 répondait à la question de sortie suivante :

> Le HEAD post-R3 est-il sécurisé, documenté et localement qualifié sur Windows + Linux/WSL, sans dépendre d’aucune CI et sans modifier GitHub Actions ?

Résultat final : **oui**, sur le SHA exact `fa54b3d6a316357b2ef79afd2243619a64a05f3b`.

```text
Jackson 3.1.5 LTS             integrated
sqlite-jdbc 3.53.2.0          integrated
local Maven SCA               PASS
coverage floors 40% / 35%     enforced
blocking dependency hygiene   enforced
Windows exact-head local gate PASS
Linux/WSL exact-head local    PASS
same exact SHA                PASS
.github/workflows delta       NONE
CI used as D2 gate            false
```

Plan historique : [`../roadmap/D2_EXECUTION.md`](../roadmap/D2_EXECUTION.md).
Preuve historique : [`../validation/VALIDATION_D2.md`](../validation/VALIDATION_D2.md), complétée par les preuves exact-head publiées sur la PR #121.

La contrainte « aucune CI » était spécifique au protocole D2. Elle ne redéfinit pas la politique actuelle de `develop`.

## NOW — baseline corrective 1.2.1

La priorité courante est de faire progresser la qualité sans inventer une nouvelle release tant qu’un vrai tag n’a pas été produit.

```text
version développement           1.2.1
MORPHEUS CI                      Windows + Ubuntu exact-head
MORPHEUS Security                OWASP Dependency-Check
MORPHEUS CodeQL                  security-extended
diff coverage PR                 >= 80% lines / >= 70% branches
global coverage ratchet          >= 52.0% lines / >= 45.0% branches
Surefire / architecture ratchets >= 1000 / >= 300
SBOM / provenance                required
```

Suivis clôturés : #154 (SonarCloud + réglages GitHub Security administrateur), #184
(réduction progressive de la dette de couverture historique).

Suivi actif :

```text
#185  qualification réelle du workflow de release attestée sur v1.2.1+
```

Ce suivi a des critères de clôture explicites : aucune qualification de release n’est
simulée pour obtenir artificiellement un état vert.

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
historical validation evidence is never rewritten
current documentation must match repository facts
```

## Prochaine sortie

Aucun numéro de release supplémentaire n’est déclaré publié avant une vraie exécution du pipeline de release.

```text
NEXT
  maintenir les ratchets exact-head sans régression
  qualifier la prochaine vraie release attestée (#185)
```
