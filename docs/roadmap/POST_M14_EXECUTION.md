# MORPHEUS — Roadmap post-M14

Statut : **ACTIVE — D0 et M15→M17 validés/intégrés ; M18 prochain jalon**

Dernière mise à jour : 26 juillet 2026

Cette roadmap prolonge la baseline **C0 à M14 validée et intégrée**. Elle ne réécrit pas les preuves historiques : elle décrit l’état courant du cycle post-M14 et les prochaines étapes.

La roadmap globale reste [`../governance/ROADMAP.md`](../governance/ROADMAP.md). La politique documentaire est [`../governance/DOCUMENTATION_STATUS.md`](../governance/DOCUMENTATION_STATUS.md).

---

## 1. Baseline acquise

```text
C0 -> M14   ✅ validés et intégrés
D0          ✅ intégré

M15         ✅ validé / intégré
M15 merge   c37134439844cb088adff855c339a259bb908b6a
M15 tests   371/371 PASS
Architecture M15 157/157 PASS
Packaging M15 Windows + smokes PASS

M16         ✅ validé / intégré
M16 merge   97308005a63854c7cb08dc19cd3cdb02ac739404
M16 code    f349c5f4701665e649d985426d35b5e6a6060e32
M16 tests   393/393 PASS
Architecture M16 161/161 PASS
Packaging M16 Windows + smokes PASS

M17         ✅ validé / intégré
M17 merge   02bdb38669efc85af17343d15e689743362d2e12
M17 code    87d2c0238f90aeb17dab5fed04f1c83a1b548f15
M17 tests   410/410 PASS
Architecture M17 167/167 PASS
Packaging M17 Windows + smokes PASS
```

Capacités disponibles après M17 :

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
Controlled lifecycle mutation command
WRITE_CHANGE capability negotiation
expected revision / CAS
idempotency key + duplicate suppression
audit append-only + SQLite reopen
CLI / MCP / HTTP controlled-write surfaces
CLI / MCP / HTTP acceptance + constraint-policy surfaces
MINOS optional integration
NEXUS optional integration
JARVIS orchestration boundary preserved
Portable Windows/Linux packaging
```

Frontières à préserver :

```text
MORPHEUS = specification facts + intent + lifecycle rules + controlled state invariants
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
READ_CHANGES != WRITE_CHANGE
ALLOWED != applied
published snapshot != operational lifecycle state
stale revision != overwrite
idempotent retry != duplicate mutation/audit
optional engine absence != MORPHEUS failure
live external observation != published snapshot mutation
```

---

## 2. Progression post-M14

M15 ferme le gap acceptance :

```text
AcceptanceCriterion first-class
VerificationStatus first-class
verification evidence explicit
coverage calculable
CLI / MCP / HTTP cohérents
```

M16 ferme le gap de politique de contraintes :

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

M17 introduit les mutations contrôlées sans confondre décision et effet :

```text
read-only evaluation
      !=
explicit mutation command

WRITE_CHANGE required
confirmation required by policy
expected revision / CAS
idempotency
append-only audit
published snapshots remain immutable
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

Preuve : [`../validation/VALIDATION_D0.md`](../validation/VALIDATION_D0.md).  
Issue : **#74**. PR : **#75**. Merge : `ec75d3963422d6281f2904c5ebd547124db92ad6`.

---

# M15 — Acceptance Criteria, Verification & Evidence

Statut : **✅ VALIDÉ / INTÉGRÉ — PR #77**

## Question de sortie

> MORPHEUS peut-il représenter explicitement ce qui doit être vérifié, l'état réel de cette vérification et les preuves associées, sans confondre scénario, test, critère d'acceptation et preuve ?

**Réponse : OUI.**

```text
TOTAL 371/371 PASS
Architecture 157/157 PASS
Packaging Windows + smokes PASS
```

Head de code validé : `9e6450a099157cfdfcd11cc29dfb986ef7701247`.  
Preuve : [`../validation/VALIDATION_M15.md`](../validation/VALIDATION_M15.md).  
Plan : [`M15_EXECUTION.md`](M15_EXECUTION.md).  
ADR : ADR-0081 **Acceptée — M15**.  
Merge : `c37134439844cb088adff855c339a259bb908b6a`.

---

# M16 — Constraint Semantics & Policy Enforcement

Statut : **✅ VALIDÉ / INTÉGRÉ — PR #79**

## Question de sortie

> MORPHEUS peut-il déterminer de façon explicable quelles contraintes sont applicables et lesquelles bloquent réellement une action ou une transition, sans convertir une absence d'information en interdiction ?

**Réponse : OUI.**

```text
TOTAL 393/393 PASS
Architecture 161/161 PASS
Packaging Windows + smokes PASS
```

Head de code validé : `f349c5f4701665e649d985426d35b5e6a6060e32`.  
Preuve : [`../validation/VALIDATION_M16.md`](../validation/VALIDATION_M16.md).  
Plan : [`M16_EXECUTION.md`](M16_EXECUTION.md).  
ADR : ADR-0082 **Acceptée — M16**.  
Merge : `97308005a63854c7cb08dc19cd3cdb02ac739404`.

---

# M17 — Controlled Lifecycle & Write Operations

Statut : **✅ VALIDÉ / INTÉGRÉ — PR #81**

## Question de sortie

> MORPHEUS peut-il appliquer une mutation explicitement autorisée avec contrôle de concurrence, permission, confirmation et audit, tout en restant distinct de JARVIS qui choisit et séquence les actions ?

**Réponse : OUI.**

Résultat :

```text
ChangeLifecycleMutationCommand
ChangeLifecycleOperationalState
ChangeLifecycleMutationStore
Memory + SQLite V011
WRITE_CHANGE capability
confirmation policy
expected revision / CAS
idempotency
append-only audit
CLI lifecycle apply
MCP apply_change_lifecycle_transition
HTTP POST .../lifecycle-transitions
OpenAPI 1.6.0
```

Gate :

```text
TOTAL 410/410 PASS
Architecture 167/167 PASS
Failures 0 / Errors 0 / Skipped 0
Packaging Windows + smokes PASS
Portable ZIP 33,839,272 bytes
```

Head de code validé : `87d2c0238f90aeb17dab5fed04f1c83a1b548f15`.  
Preuve : [`../validation/VALIDATION_M17.md`](../validation/VALIDATION_M17.md).  
Plan : [`M17_EXECUTION.md`](M17_EXECUTION.md).  
ADR : ADR-0083 **Acceptée — M17**.  
Merge : `02bdb38669efc85af17343d15e689743362d2e12`.

---

# M18 — Real Providers & Multi-Provider Composition

Statut : **⏭ PROCHAIN JALON**

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
M16  Constraint semantics / blocking policy              ✅ intégré
 ↓
M17  Controlled write / lifecycle mutations              ✅ intégré
 ↓
M18  Real providers / multi-provider composition         ⏭ prochain
 ↓
M19  Production hardening / scale / operability
 ↓
M20  Release engineering / PROD installation / 1.0
```

La séquence est volontaire :

- M15/M16 approfondissent la vérité métier avant d'autoriser des écritures ;
- M17 sécurise les mutations avant la composition de providers ;
- M18 étend les sources après stabilisation des contrats métier et write ;
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
MORPHEUS must not apply ALLOWED decisions implicitly
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
12. réconcilier ROADMAP.md / POST_M14_EXECUTION.md / index après merge
13. supprimer les branches de jalon devenues obsolètes
```

---

# 6. Position cible

```text
C0-M14  = plateforme MVP / intégrations fondamentales          ✅ acquis
D0      = documentation réconciliée                            ✅ intégré
M15     = intention vérifiable et prouvable                    ✅ intégré
M16     = contraintes exécutables/explicables                  ✅ intégré
M17     = mutations contrôlées                                 ✅ intégré
M18     = multi-provider réel                                  ⏭ prochain
M19     = exploitation à l'échelle                             ⏳
M20     = distribution produit / installation PROD / 1.0      ⏳
```

La priorité immédiate est désormais **M18 — valider un deuxième provider réel et une composition multi-provider explicable sans verrouillage de format**.
