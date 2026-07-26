# MORPHEUS — Roadmap post-M14

Statut : **ACTIVE — D0/M15 intégrés ; M16 validé techniquement / PR #79 Ready ; M17 prochain après intégration M16**

Dernière mise à jour : 26 juillet 2026

Cette roadmap prolonge la baseline **C0 à M14 validée et intégrée**. Elle ne réécrit pas l'historique des jalons déjà livrés : elle définit les prochaines étapes nécessaires pour approfondir la sémantique métier, sécuriser les mutations, ouvrir réellement le multi-provider, durcir l'exploitation et transformer le packaging existant en distribution produit installable.

La roadmap globale reste [`../governance/ROADMAP.md`](../governance/ROADMAP.md). Pendant l'exécution d'un jalon post-M14, son plan détaillé devient la source de vérité opérationnelle. D0 est détaillé dans [`D0_EXECUTION.md`](D0_EXECUTION.md), M15 dans [`M15_EXECUTION.md`](M15_EXECUTION.md), M16 dans [`M16_EXECUTION.md`](M16_EXECUTION.md), et la politique documentaire dans [`../governance/DOCUMENTATION_STATUS.md`](../governance/DOCUMENTATION_STATUS.md).

---

## 1. Baseline acquise

```text
C0 -> M14   ✅ validés et intégrés
D0          ✅ intégré
M14         357/357 PASS
Architecture M14 160/160 PASS
Packaging   Windows PASS
JARVIS      536 tests BUILD SUCCESS

M15         ✅ validé / intégré
M15 merge   c37134439844cb088adff855c339a259bb908b6a
M15 tests   371/371 PASS
Architecture M15 157/157 PASS
Packaging M15 Windows + smokes PASS

M16         ✅ validé techniquement
M16 code    f349c5f4701665e649d985426d35b5e6a6060e32
M16 tests   393/393 PASS
Architecture M16 161/161 PASS
Packaging M16 Windows + smokes PASS
PR #79      Ready, non mergée
```

Capacités disponibles après M16 :

```text
Domain model
Persistence Memory / SQLite
TemporalState + lifecycle + snapshots + versions
Published history / comparison / logical rollback
Typed traceability
Business queries + compact context
Quality diagnostics
Incremental synchronization / freshness
Change analysis
AcceptanceCriterion + VerificationStatus + Evidence semantics
Acceptance verification coverage
Acceptance traceability
Explicit ConstraintApplicability / Severity / Satisfaction
Explicit ConstraintBlockingPolicy
Explainable ConstraintEvaluation
Lifecycle BLOCKING_CONSTRAINT decisions
CLI / MCP / HTTP acceptance + constraint-policy surfaces
CLI
MCP STDIO
HTTP API
MINOS optional integration
NEXUS optional integration
JARVIS read-only orchestration contract
Portable Windows/Linux packaging
```

Frontières à préserver :

```text
MORPHEUS = specification facts + intent + lifecycle rules + transition decisions
MINOS    = code intelligence
NEXUS    = context selection / ranking / fusion / compression
JARVIS   = sequencing / orchestration / action choice
```

Invariants structurants :

```text
PROPOSED never leaks into CURRENT
APPLY != PROMOTE != ACTIVATE
Scenario != AcceptanceCriterion
AcceptanceCriterion != Test
Test existence != VERIFIED
Evidence != assertion
UNKNOWN != FAILED
lifecycle unavailable != lifecycle inferred
UNKNOWN != BLOCKED
applicable != blocking
warning != blocker
severity != blocking policy
constraint text != executable policy
transition evaluation != lifecycle mutation
optional engine absence != MORPHEUS failure
live external observation != published snapshot mutation
```

---

## 2. Progression post-M14

Le constat M14 était :

```text
acceptanceCriteria.status = UNAVAILABLE_IN_NORMALIZED_MODEL
blockingConstraints.status = UNAVAILABLE_BLOCKING_SEMANTICS_NOT_MODELED
```

M15 ferme le premier gap :

```text
acceptanceCriteria.status = AVAILABLE
AcceptanceCriterion first-class
VerificationStatus first-class
verification evidence explicit
coverage calculable
CLI / MCP / HTTP cohérents
```

M16 ferme le second gap sans convertir l'absence d'information en interdiction :

```text
ConstraintApplicability explicit
ConstraintSeverity explicit
ConstraintSatisfaction explicit
ConstraintBlockingPolicy explicit
ConstraintEvaluation explainable
blockingConstraints.status = AVAILABLE | PARTIALLY_AVAILABLE | UNKNOWN
UNKNOWN != BLOCKED
warning != blocker
```

La boucle cible reste :

```text
INTENTION
   ↓
REQUIREMENT
   ↓
CHANGE
   ↓
IMPLEMENTATION
   ↓
VERIFICATION
   ↓
EVIDENCE
   ↓
SATISFACTION EXPLICABLE
```

---

# D0 — Réconciliation documentaire post-M14

Statut : **✅ VALIDÉ / INTÉGRÉ — PR #75**

Objectif : aligner la documentation active sur l'état réel du dépôt avant le cycle post-M14.

Gate :

```text
aucun document actif ne présente M3..M14 comme non intégrés          PASS
cahier des charges aligné avec la baseline livrée                    PASS
roadmap post-M14 référencée depuis la gouvernance                    PASS
aucun lien documentaire cassé sur les parcours principaux           PASS
preuves historiques de gates conservées sans réécriture              PASS
```

Preuve : [`../validation/VALIDATION_D0.md`](../validation/VALIDATION_D0.md).  
Issue : **#74**. PR : **#75**. Merge : `ec75d3963422d6281f2904c5ebd547124db92ad6`.

---

# M15 — Acceptance Criteria, Verification & Evidence

Statut : **✅ VALIDÉ / INTÉGRÉ — PR #77**

## Question de sortie

> MORPHEUS peut-il représenter explicitement ce qui doit être vérifié, l'état réel de cette vérification et les preuves associées, sans confondre scénario, test, critère d'acceptation et preuve ?

**Réponse : OUI.**

## Résultat

```text
AcceptanceCriterion first-class
VerificationStatus first-class
Evidence reused as verification material
Requirement -> AcceptanceCriterion
ChangeProposal -> AcceptanceCriterion
AcceptanceCriterion -> Evidence
verification provenance separated from verification evidence
coverage / uncovered criteria
unverified / partially verified / verified / failed / unknown
CLI / MCP / HTTP surfaces
change-orchestration acceptance availability
```

Les références test externes ne sont jamais inventées : `AcceptanceCriterion -> ExternalReference(test)` ne sera créé que lorsqu'une source/provider fournit explicitement cette relation.

## Invariants

```text
Scenario != AcceptanceCriterion
AcceptanceCriterion != Test
Test existence != VERIFIED
Evidence != assertion
missing evidence != FAILED
UNKNOWN != FAILED
verification state must be explicit or demonstrably derived by policy
```

## Gate M15

```text
acceptanceCriteria.status != UNAVAILABLE_IN_NORMALIZED_MODEL     PASS
critères persistés et requêtables                                PASS
preuves traçables et explicables                                 PASS
aucune conversion Scenario -> AcceptanceCriterion implicite      PASS
verification UNKNOWN conservé lorsque les faits manquent         PASS
Memory == SQLite                                                 PASS
SQLite close/reopen                                              PASS
CLI / MCP / HTTP                                                 PASS
TOTAL 371/371                                                    PASS
Architecture 157/157                                             PASS
Packaging Windows + smokes                                      PASS
```

Head de code validé : `9e6450a099157cfdfcd11cc29dfb986ef7701247`.

Preuve : [`../validation/VALIDATION_M15.md`](../validation/VALIDATION_M15.md).  
Plan : [`M15_EXECUTION.md`](M15_EXECUTION.md).  
ADR : ADR-0081 **Acceptée — M15**.  
Merge : PR #77 -> `c37134439844cb088adff855c339a259bb908b6a`.

---

# M16 — Constraint Semantics & Policy Enforcement

Statut : **✅ VALIDÉ TECHNIQUEMENT — PR #79 Ready**

## Question de sortie

> MORPHEUS peut-il déterminer de façon explicable quelles contraintes sont applicables et lesquelles bloquent réellement une action ou une transition, sans convertir une absence d'information en interdiction ?

**Réponse : OUI.**

## Résultat

```text
ConstraintApplicability = APPLICABLE | NOT_APPLICABLE | UNKNOWN
ConstraintSeverity = INFO | WARNING | ERROR | CRITICAL | UNKNOWN
ConstraintSatisfaction = SATISFIED | VIOLATED | UNKNOWN
ConstraintBlockingPolicy = NON_BLOCKING | BLOCK_WHEN_VIOLATED | UNKNOWN
ConstraintEvaluation = NOT_APPLICABLE | NON_BLOCKING | BLOCKING | UNKNOWN
supporting evidence explicit
SQLite V010 persistence
Memory == SQLite
SQLite reopen identical
BLOCKING_CONSTRAINT lifecycle reason
blockingConstraints AVAILABLE | PARTIALLY_AVAILABLE | UNKNOWN
CLI / MCP / HTTP / OpenAPI 1.5.0
```

## Invariants

```text
applicable != blocking
warning != blocker
severity != blocking policy
UNKNOWN != BLOCKED
constraint text != executable policy
policy decision must expose provenance and reason
```

## Gate M16

```text
blockingConstraints.status != UNAVAILABLE_BLOCKING_SEMANTICS_NOT_MODELED     PASS
transition decisions explain every blocking constraint                      PASS
UNAVAILABLE remains distinct from false / allowed                           PASS
no provider-specific policy type leaks into domain                          PASS
Memory == SQLite                                                            PASS
SQLite close/reopen identical                                               PASS
CLI/MCP/HTTP coherent                                                       PASS
TOTAL 393/393                                                               PASS
Architecture 161/161                                                        PASS
Packaging Windows + smokes                                                  PASS
```

Head de code validé : `f349c5f4701665e649d985426d35b5e6a6060e32`.

Preuve : [`../validation/VALIDATION_M16.md`](../validation/VALIDATION_M16.md).  
Plan : [`M16_EXECUTION.md`](M16_EXECUTION.md).  
ADR : ADR-0082 **Acceptée — M16**.

La PR #79 doit être intégrée avant d'ouvrir M17.

---

# M17 — Controlled Lifecycle & Write Operations

Statut : **PROCHAIN APRÈS MERGE M16**

## Question de sortie

> MORPHEUS peut-il appliquer une mutation explicitement autorisée avec contrôle de concurrence, permission, confirmation et audit, tout en restant distinct de JARVIS qui choisit et séquence les actions ?

## Objectif

Passer du contrat M14 d'évaluation read-only à des mutations contrôlées, opt-in et auditables.

## Flux cible

```text
JARVIS / caller chooses action
          ↓
MORPHEUS evaluates transition
          ↓
authorization / capability
          ↓
confirmation / required input
          ↓
expected version / CAS
          ↓
apply mutation
          ↓
audit trail + evidence
          ↓
new observable state
```

## Capacités envisagées

```text
WRITE_CHANGE
WRITE_TASK_STATE
ARCHIVE_CHANGE
controlled lifecycle transition
provider write capability discovery
optimistic concurrency / expected version
conflict reporting
idempotency key
structured audit record
explicit confirmation policy
```

## Invariants

```text
read capability != write capability
ALLOWED != applied
JARVIS owns sequencing
MORPHEUS owns state invariants
no implicit overwrite
no mutation without explicit provider capability
no mutation without conflict policy
```

## Gate M17

```text
read-only mode remains fully supported
write paths are opt-in
concurrent stale mutation rejected deterministically
mutation audit survives restart
MCP/API mutation surface separated from evaluation surface
```

---

# M18 — Real Providers & Multi-Provider Composition

Statut : **PLANIFIÉ**

## Question de sortie

> MORPHEUS peut-il construire une vue cohérente à partir de plusieurs providers réels en conservant identité, provenance, priorité et conflits sans devenir dépendant d'un format particulier ?

## Objectif

Passer de la preuve anti-lock-in `OpenSpec + Synthetic` à une vraie composition multi-provider utilisable.

## Étape 1 — deuxième provider réel

Priorité recommandée : **Markdown structuré générique**.

Candidats suivants :

```text
GitHub Issues
GitLab Issues
Jira
ADR repositories
Git sources / metadata
other structured specification formats
```

## Étape 2 — composition

```text
OpenSpec
   +
Structured Markdown / ADR
   +
Issue provider
   +
future providers
        ↓
identity reconciliation
        ↓
precedence + provenance + conflicts
        ↓
coherent KnowledgeSnapshot
```

## Sémantique nécessaire

```text
provider ownership
source precedence
identity continuity
cross-provider ExternalReference
conflict detection
conflict explanation
confidence / resolution
composition diagnostics
```

## Invariants

```text
provider identifier != DomainIdentity
source path != identity
ambiguous continuity must be surfaced
conflict != silent last-write-wins
provider absence != project failure when optional
```

## Gate M18

```text
at least two real providers validated
same project can consume multiple providers
conflicts are explicit and queryable
reopen SQLite preserves provider provenance
no provider-specific types leak into domain/application contracts
```

---

# M19 — Production Hardening, Scale & Operability

Statut : **PLANIFIÉ**

## Question de sortie

> MORPHEUS reste-t-il déterministe, observable et exploitable sur des dépôts réalistes de grande taille, avec des limites et performances mesurées plutôt que supposées ?

## Objectif

Transformer les garanties fonctionnelles en garanties d'exploitation.

### Performance et capacité

```text
large repository fixtures
large requirement sets
large traceability graphs
incremental sync benchmarks
query latency budgets
startup time
memory budget
SQLite size / growth
history retention cost
```

### Robustesse

```text
corrupt / partial sources
interrupted sync
failed candidate recovery
concurrent readers
concurrent commands
locked database behavior
migration compatibility
rebuild from sources
```

### Observabilité

```text
structured logs
stable diagnostics
health / readiness semantics
operational counters
sync timing
provider timing
external integration timing
```

### Sécurité locale

```text
secret/path redaction
safe logging defaults
ignored path policy
external link non-following by default
write permission hardening
```

## Gate M19

Les seuils chiffrés seront fixés avant implémentation puis prouvés par benchmark reproductible Windows/Linux.

```text
performance budgets documented
large-fixture gates reproducible
no partial ACTIVE exposure under failure
migration/recovery scenarios validated
operational diagnostics documented
```

---

# M20 — Release Engineering, Installation PROD & MORPHEUS 1.0

Statut : **PLANIFIÉ**

## Question de sortie

> MORPHEUS peut-il être installé, mis à jour, diagnostiqué et désinstallé comme un produit Windows/Linux sans Git, Maven ou JDK utilisateur, tout en préservant les données et en conservant le ZIP portable pour l'automatisation ?

## Objectif

Faire du packaging existant une véritable distribution produit et aligner l'expérience Windows sur le standard retenu pour MINOS.

## 20.1 Standard Windows recommandé

Le mode utilisateur normal ne doit plus être documenté comme une extraction manuelle dans `C:\Tools\Morpheus`.

Cible :

```text
GitHub Release MORPHEUS
        ↓
MORPHEUS-<version>-windows-x64-setup.exe
        +
MORPHEUS-<version>-windows-x64-setup.exe.sha256
        ↓
verify SHA-256
        ↓
setup.exe
        ↓
%LOCALAPPDATA%\Programs\MORPHEUS
        ↓
CLI + MCP + API + PATH utilisateur optionnel
```

Installation programme :

```text
%LOCALAPPDATA%\Programs\MORPHEUS\
├── app\
├── lib\
├── integration\
├── morpheus.cmd
├── morpheus-mcp.cmd
├── VERSION
└── uninstaller
```

Données persistantes séparées :

```text
%LOCALAPPDATA%\MORPHEUS\
├── data\
│   └── morpheus.db
├── config\
├── logs\
└── backups\
```

Principe :

```text
programme != data
update/uninstall program != delete knowledge store
```

L'installation utilisateur doit normalement fonctionner sans élévation administrateur.

## 20.2 PATH et launchers

Le setup doit proposer explicitement :

```text
☐ Ajouter MORPHEUS au PATH de l'utilisateur
```

Cible :

```powershell
morpheus.cmd --version
morpheus.cmd paths
morpheus.cmd projects list
morpheus.cmd doctor
```

## 20.3 MCP utilisateur

Une intégration MCP native peut être proposée de manière **opt-in**, jamais silencieuse :

```text
☐ GitHub Copilot — JetBrains / IntelliJ
☐ GitHub Copilot CLI
☐ Claude Code
☐ Claude Desktop
☐ OpenAI Codex
```

Règles obligatoires :

```text
no overwrite of unmanaged existing MCP entry
backup before modification
preserve unrelated client configuration
managed integration registry
selective uninstall
```

## 20.4 Portable toujours supporté

Le ZIP reste une distribution de premier ordre pour :

```text
automation
CI
diagnostics
portable usage
multiple versions side-by-side
advanced users
```

Artefacts Windows :

```text
MORPHEUS-<version>-windows-x64-setup.exe
MORPHEUS-<version>-windows-x64-setup.exe.sha256
morpheus-<version>-windows-x64.zip
morpheus-<version>-windows-x64.zip.sha256
```

Artefacts Linux :

```text
morpheus-<version>-linux-x64.tar.gz
morpheus-<version>-linux-x64.tar.gz.sha256
```

Le runtime Java reste embarqué : aucun JDK n'est requis pour l'utilisateur final.

## 20.5 Release GitHub

Une release stable doit être reproductible depuis un tag et publier automatiquement :

```text
binaries
checksums
release notes
CHANGELOG
version metadata
SBOM if retained by release policy
```

Le dépôt ne doit plus rester indéfiniment en `0.1.0-SNAPSHOT` lorsque la release stable est déclarée.

## 20.6 Diagnostic produit

Ajouter :

```powershell
morpheus.cmd doctor
```

Elle doit distinguer :

```text
MORPHEUS embedded runtime
program installation
write access to data/config/log paths
SQLite store health
optional MINOS state
optional NEXUS state
MCP/API readiness
external dependencies actually required by configured providers
```

## 20.7 Cohérence écosystème

Convention cible Windows :

```text
%LOCALAPPDATA%\Programs\
├── MINOS\
├── MORPHEUS\
├── NEXUS\
└── JARVIS\
```

Données :

```text
%LOCALAPPDATA%\
├── MINOS\
├── MORPHEUS\
├── NEXUS\
└── JARVIS\
```

Cette convention n'impose aucune dépendance runtime entre moteurs ; elle uniformise uniquement l'expérience d'installation et d'exploitation.

## Gate M20

```text
Windows setup installation PASS
Windows portable ZIP PASS
Linux portable archive PASS
SHA-256 assets generated and verified
no JDK required at runtime
per-user install path PASS
PATH option PASS
program/data separation PASS
uninstall preserves data by default
upgrade preserves data/config PASS
MCP integrations opt-in + reversible
GitHub release from tag reproducible
release documentation complete
```

Cible de version : **MORPHEUS 1.0** après validation de l'ensemble des gates post-M14 retenues pour la release.

---

# 3. Ordre d'exécution

```text
D0   Documentation reconciliation                        ✅ intégré
 ↓
M15  Acceptance / Verification / Evidence                ✅ intégré
 ↓
M16  Constraint semantics / blocking policy              ✅ validé techniquement / PR #79 Ready
 ↓ merge #79
M17  Controlled write / lifecycle mutations              ⏳ prochain
 ↓
M18  Real providers / multi-provider composition
 ↓
M19  Production hardening / scale / operability
 ↓
M20  Release engineering / PROD installation / 1.0
```

La séquence est volontaire :

- M15/M16 approfondissent la vérité métier avant d'autoriser des écritures ;
- M17 n'introduit les mutations qu'une fois les règles de décision suffisamment riches ;
- M18 étend les sources après stabilisation des contrats métier ;
- M19 mesure et durcit le système avant la release stable ;
- M20 transforme une application techniquement packagée en produit installable et distribuable.

---

# 4. Ce qui ne doit pas être fait implicitement

```text
MORPHEUS must not become a code intelligence engine
MORPHEUS must not become a general context ranking engine
MORPHEUS must not become JARVIS orchestration
MORPHEUS must not infer unavailable lifecycle facts
MORPHEUS must not silently merge provider conflicts
MORPHEUS must not turn read capability into write capability
MORPHEUS must not delete persistent knowledge on program uninstall
```

---

# 5. Gouvernance post-M14

Pour chaque jalon :

```text
1. créer l'issue de milestone
2. écrire le plan d'exécution détaillé du jalon
3. expliciter la question de sortie et les invariants
4. décider les ADR nécessaires
5. implémenter par vertical slices
6. tester Memory + SQLite + adapters réels pertinents
7. exécuter le gate complet
8. enregistrer SHA / commandes / résultats dans VALIDATION_Mxx.md
9. accepter les ADR seulement après preuve
10. passer la PR Ready seulement après gate vert
11. merger uniquement après autorisation explicite
12. mettre à jour ROADMAP.md et les index
```

---

# 6. Position cible

```text
C0-M14  = plateforme MVP / intégrations fondamentales          ✅ acquis
D0      = documentation réconciliée                            ✅ intégré
M15     = intention vérifiable et prouvable                    ✅ intégré
M16     = contraintes exécutables/explicables                  ✅ validé techniquement / PR #79 Ready
M17     = mutations contrôlées                                 ⏳ prochain après merge M16
M18     = multi-provider réel                                  ⏳
M19     = exploitation à l'échelle                             ⏳
M20     = distribution produit / installation PROD / 1.0      ⏳
```

La priorité post-M14 reste : **approfondir la sémantique de l'intention avant d'élargir davantage les surfaces techniques**.