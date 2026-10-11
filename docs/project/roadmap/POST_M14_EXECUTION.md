# MORPHEUS — Roadmap post-M14

Statut : **ACTIVE — D0 et M15→M19 validés/intégrés ; M20 validé techniquement, PR #93 non mergée**

Dernière mise à jour : 27 juillet 2026

Cette roadmap prolonge la baseline **C0 à M14 validée et intégrée**. Elle ne réécrit pas les preuves historiques : elle décrit l’état courant du cycle post-M14 et les prochaines étapes d’intégration/publication.

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

M16         ✅ validé / intégré
M16 merge   97308005a63854c7cb08dc19cd3cdb02ac739404
M16 code    f349c5f4701665e649d985426d35b5e6a6060e32
M16 tests   393/393 PASS
Architecture M16 161/161 PASS

M17         ✅ validé / intégré
M17 merge   02bdb38669efc85af17343d15e689743362d2e12
M17 code    87d2c0238f90aeb17dab5fed04f1c83a1b548f15
M17 tests   410/410 PASS
Architecture M17 167/167 PASS

M18         ✅ validé / intégré
M18 issue   #85 CLOSED / completed
M18 PR      #86 MERGED
M18 code    7e8caacff567f51354fcb88bd7505a6d135071c0
M18 merge   30f11ac3ffc522bcc0c71e31216a3fb70f0631d7
M18 tests   418/418 PASS
Architecture M18 170/170 PASS

M19         ✅ validé / intégré
M19 issue   #88 CLOSED / completed
M19 PR      #89 MERGED
M19 merge   762b6dedd0760f8e08722ef5ee5dcf5057309574
M19 tests   449/449 PASS Windows + Linux
Architecture M19 178/178 PASS

M20         ✅ validé techniquement
M20 issue   #92 OPEN jusqu'au merge
M20 PR      #93 non mergée
M20 code    9199ed43c4bd8596a97db055eeff17ae31399eb8
M20 tests   454/454 PASS Windows + Linux
Architecture M20 182/182 PASS
```

Capacités disponibles après qualification M20 :

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
MINOS optional integration
NEXUS optional integration
JARVIS orchestration boundary preserved
OpenSpec real provider
Structured Markdown real provider
ProviderContribution provider-neutral
MultiProviderCompositionService
explicit precedence + preserved provenance + explicit conflicts
Memory / SQLite V012 composition persistence
CLI / MCP / HTTP composition surfaces
production hardening / scale budgets / recovery
local-first operability and diagnostics
portable Windows/Linux packaging
Windows per-user installer
program/persistent-data separation
upgrade/uninstall persistence guarantees
release manifests + SHA-256
embedded runtime / no user JDK
```

Frontières à préserver :

```text
MORPHEUS = specification facts
           + intent
           + lifecycle rules
           + controlled state invariants
           + provider composition facts

MINOS    = code intelligence

NEXUS    = context selection
           + ranking
           + fusion
           + compression

JARVIS   = sequencing
           + orchestration
           + action choice
```

Invariants structurants :

```text
DomainIdentity != EntityVersionId != SourceLocator != ExternalReference
SpecificationVersion != KnowledgeSnapshot

PROPOSED never leaks into CURRENT
published history = RETIRED* -> ACTIVE
APPLY != PROMOTE != ACTIVATE

Scenario != AcceptanceCriterion
AcceptanceCriterion != Test
Test existence != VERIFIED
Evidence != assertion

UNKNOWN != FAILED
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

provider identifier != DomainIdentity
source path != identity
precedence != provenance erasure
conflict != silent last-write-wins
ambiguous continuity must be surfaced
optional provider absence != project failure when optional

optional engine absence != MORPHEUS failure
live external observation != published snapshot mutation
MORPHEUS rules != JARVIS action sequencing

programme != données persistantes
update programme != reset knowledge store
uninstall programme != delete knowledge store
runtime utilisateur != JDK utilisateur
```

---

## 2. Progression post-M14

### D0 — Réconciliation documentaire post-M14

Statut : **✅ VALIDÉ / INTÉGRÉ — PR #75**  
Preuve : [`../validation/VALIDATION_D0.md`](../validation/VALIDATION_D0.md).  
Issue : **#74**. PR : **#75**. Merge : `ec75d3963422d6281f2904c5ebd547124db92ad6`.

### M15 — Acceptance Criteria, Verification & Evidence

Statut : **✅ VALIDÉ / INTÉGRÉ — PR #77**

Question de sortie :

> MORPHEUS peut-il représenter explicitement ce qui doit être vérifié, l'état réel de cette vérification et les preuves associées, sans confondre scénario, test, critère d'acceptation et preuve ?

**Réponse : OUI.**

```text
TOTAL 371/371 PASS
Architecture 157/157 PASS
Packaging Windows + smokes PASS
```

Preuve : [`../validation/VALIDATION_M15.md`](../validation/VALIDATION_M15.md).  
Plan : [`M15_EXECUTION.md`](M15_EXECUTION.md).  
ADR-0081 : **Acceptée — M15**.  
Merge : `c37134439844cb088adff855c339a259bb908b6a`.

### M16 — Constraint Semantics & Policy Enforcement

Statut : **✅ VALIDÉ / INTÉGRÉ — PR #79**

Question de sortie :

> MORPHEUS peut-il déterminer de façon explicable quelles contraintes sont applicables et lesquelles bloquent réellement une action ou une transition, sans convertir une absence d'information en interdiction ?

**Réponse : OUI.**

```text
TOTAL 393/393 PASS
Architecture 161/161 PASS
Packaging Windows + smokes PASS
```

Preuve : [`../validation/VALIDATION_M16.md`](../validation/VALIDATION_M16.md).  
Plan : [`M16_EXECUTION.md`](M16_EXECUTION.md).  
ADR-0082 : **Acceptée — M16**.  
Merge : `97308005a63854c7cb08dc19cd3cdb02ac739404`.

### M17 — Controlled Lifecycle & Write Operations

Statut : **✅ VALIDÉ / INTÉGRÉ — PR #81**

Question de sortie :

> MORPHEUS peut-il appliquer une mutation explicitement autorisée avec contrôle de concurrence, permission, confirmation et audit, tout en restant distinct de JARVIS qui choisit et séquence les actions ?

**Réponse : OUI.**

```text
TOTAL 410/410 PASS
Architecture 167/167 PASS
Failures 0 / Errors 0 / Skipped 0
Packaging Windows + smokes PASS
```

Preuve : [`../validation/VALIDATION_M17.md`](../validation/VALIDATION_M17.md).  
Plan : [`M17_EXECUTION.md`](M17_EXECUTION.md).  
ADR-0083 : **Acceptée — M17**.  
Merge : `02bdb38669efc85af17343d15e689743362d2e12`.

### M18 — Real Providers & Multi-Provider Composition

Statut : **✅ VALIDÉ / INTÉGRÉ — PR #86**

Question de sortie :

> MORPHEUS peut-il construire une vue cohérente à partir de plusieurs providers réels en conservant identité, provenance, priorité et conflits sans devenir dépendant d'un format particulier ?

**Réponse : OUI.**

```text
code validé     7e8caacff567f51354fcb88bd7505a6d135071c0
TOTAL           418/418 PASS
Architecture    170/170 PASS
Reactor         14/14 modules SUCCESS
Packaging Win   PASS
Packaged smokes PASS
API health      PASS
```

Preuve : [`../validation/VALIDATION_M18.md`](../validation/VALIDATION_M18.md).  
Plan : [`M18_EXECUTION.md`](M18_EXECUTION.md).  
ADR-0084 : **Acceptée — M18**.  
Issue #85 : **CLOSED / completed**.  
PR #86 : **MERGED**.  
Merge : `30f11ac3ffc522bcc0c71e31216a3fb70f0631d7`.

Le SHA testé et le merge commit sont volontairement distincts : les commits post-gate de M18 étaient documentaires uniquement.

---

### M19 — Production Hardening, Scale & Operability

Statut : **✅ VALIDÉ / INTÉGRÉ — PR #89**

Question de sortie :

> MORPHEUS reste-t-il déterministe, observable et exploitable sur des dépôts réalistes de grande taille, avec des limites et performances mesurées plutôt que supposées ?

**Réponse : OUI**, sur le profil gelé `M19-LARGE-GATE-1`.

Preuve finale acquise :

```text
Windows exact-head  0209a473d58cadb4d69ff4c1b3a00ffe57f8436b PASS
Linux code SHA      dca27db969b426ad43941ccb8cee7e926efb931b PASS ext4/WSL2
Tests               449/449 PASS
Architecture        178/178 PASS
Reactor             14/14 SUCCESS
Budgets             PASS
Packaging           PASS Windows + Linux
```

Le delta exécutable entre la preuve Linux et le head final Windows a été vérifié nul ; les commits intermédiaires étaient documentaires.  
Preuve : [`../validation/VALIDATION_M19.md`](../validation/VALIDATION_M19.md).  
Plan : [`M19_EXECUTION.md`](M19_EXECUTION.md).  
ADR-0085/0086/0087 : **Acceptées — M19**.  
Issue #88 : **CLOSED / completed**.  
PR #89 : **MERGED**.  
Merge : `762b6dedd0760f8e08722ef5ee5dcf5057309574`.

---

### M20 — Release Engineering, Installation PROD & MORPHEUS 1.0

Statut : **✅ VALIDÉ TECHNIQUEMENT — PR #93 NON MERGÉE**

Issue : **#92**.  
PR : **#93**.  
Plan : [`M20_EXECUTION.md`](M20_EXECUTION.md).  
Preuve : [`../validation/VALIDATION_M20.md`](../validation/VALIDATION_M20.md).

Question de sortie :

> MORPHEUS peut-il être installé, mis à jour, diagnostiqué et désinstallé comme un produit Windows/Linux sans Git, Maven ou JDK utilisateur, tout en préservant les données et en conservant les archives portables pour l’automatisation ?

**Réponse : OUI.**

Code qualifié sur les deux plateformes :

```text
9199ed43c4bd8596a97db055eeff17ae31399eb8
```

Gate consolidé :

```text
Version                         1.0.0
Windows                         PASS
Linux ext4 / WSL2               PASS
Tests                           454/454 PASS
Architecture                    182/182 PASS
Failures/errors/skipped         0/0/0
Reactor                         14/14 SUCCESS
Windows setup                   PASS
Windows portable ZIP            PASS
Linux portable tar.gz           PASS
SHA-256                         PASS Windows + Linux
No-user-JDK                     PASS Windows + Linux
PATH                            PASS
Program/data separation         PASS
Upgrade preservation            PASS
Uninstall preservation          PASS
MINOS/NEXUS opt-in              PASS Windows + Linux
API health/readiness/metrics    PASS Windows + Linux
Release from exact tag/SHA      PASS Windows + Linux
Exact-head stability            PASS Windows + Linux
```

Layout Windows :

```text
programme  %LOCALAPPDATA%\Programs\MORPHEUS
state      %LOCALAPPDATA%\MORPHEUS\{data,config,logs,backups}
```

Layout Linux :

```text
data    ${XDG_DATA_HOME:-$HOME/.local/share}/morpheus
config  ${XDG_CONFIG_HOME:-$HOME/.config}/morpheus
logs    ${XDG_STATE_HOME:-$HOME/.local/state}/morpheus/logs
backups ${XDG_STATE_HOME:-$HOME/.local/state}/morpheus/backups
```

Assets qualifiés :

```text
MORPHEUS-1.0.0-windows-x64-setup.exe
morpheus-1.0.0-windows-x64.zip
morpheus-1.0.0-linux-x64.tar.gz
+ SHA-256 compagnons et manifests de release
```

ADR-0088 : **Acceptée — M20** après preuve réelle Windows + Linux.

Les commits post-gate ne doivent modifier que la documentation de preuve/gouvernance. La PR #93 peut devenir Ready uniquement après comparaison explicite avec `9199ed43c4bd8596a97db055eeff17ae31399eb8` et confirmation d’un delta exécutable nul.

**Aucun merge n’est autorisé sans décision explicite du propriétaire.**

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
M18  Real providers / multi-provider composition         ✅ intégré
 ↓
M19  Production hardening / scale / operability          ✅ intégré
 ↓
M20  Release engineering / PROD installation / 1.0       ✅ qualifié, PR #93 à intégrer
```

La séquence est volontaire :

- M15/M16 approfondissent la vérité métier avant d'autoriser des écritures ;
- M17 sécurise les mutations avant la composition de providers ;
- M18 étend les sources après stabilisation des contrats métier et write ;
- M19 mesure et durcit le système avant la release stable ;
- M20 transforme l’application techniquement packagée en produit installable et distribuable.

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
M18     = multi-provider réel                                  ✅ intégré
M19     = exploitation à l'échelle                             ✅ intégré
M20     = distribution produit / installation PROD / 1.0      ✅ qualifié techniquement
```

La priorité immédiate est la **revue puis l’intégration explicitement autorisée de M20**. Le tag stable `v1.0.0` et la GitHub Release ne doivent être produits qu’après cette intégration autorisée.
