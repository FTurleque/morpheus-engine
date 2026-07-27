# MORPHEUS — Roadmap post-M14

Statut : **ACTIVE — D0 et M15→M18 validés/intégrés ; M19 qualifié techniquement, non mergé**

Dernière mise à jour : 27 juillet 2026

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

M18         ✅ validé / intégré
M18 issue   #85 CLOSED / completed
M18 PR      #86 MERGED
M18 code    7e8caacff567f51354fcb88bd7505a6d135071c0
M18 merge   30f11ac3ffc522bcc0c71e31216a3fb70f0631d7
M18 tests   418/418 PASS
Architecture M18 170/170 PASS
Packaging M18 Windows + smokes PASS
```

Capacités disponibles après M18 :

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
OpenAPI 1.7.0
Portable Windows/Linux packaging
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

Head validé : `9e6450a099157cfdfcd11cc29dfb986ef7701247`.  
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

Head validé : `f349c5f4701665e649d985426d35b5e6a6060e32`.  
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

```text
TOTAL 410/410 PASS
Architecture 167/167 PASS
Failures 0 / Errors 0 / Skipped 0
Packaging Windows + smokes PASS
Portable ZIP 33,839,272 bytes
```

Head validé : `87d2c0238f90aeb17dab5fed04f1c83a1b548f15`.  
Preuve : [`../validation/VALIDATION_M17.md`](../validation/VALIDATION_M17.md).  
Plan : [`M17_EXECUTION.md`](M17_EXECUTION.md).  
ADR-0083 : **Acceptée — M17**.  
Merge : `02bdb38669efc85af17343d15e689743362d2e12`.

### M18 — Real Providers & Multi-Provider Composition

Statut : **✅ VALIDÉ / INTÉGRÉ — PR #86**

Question de sortie :

> MORPHEUS peut-il construire une vue cohérente à partir de plusieurs providers réels en conservant identité, provenance, priorité et conflits sans devenir dépendant d'un format particulier ?

**Réponse : OUI.**

Architecture livrée :

```text
OpenSpec réel
+
Structured Markdown réel
        ↓
ProviderContribution
        ↓
MultiProviderCompositionService
        ↓
precedence explicite
provenance conservée
conflits explicites
        ↓
Memory / SQLite V012
        ↓
CLI / MCP / HTTP
```

Surfaces :

```text
CLI:
composition sync
composition status
composition conflicts

MCP:
get_composition_status
list_composition_conflicts

HTTP:
GET /api/v1/projects/{projectId}/composition
GET /api/v1/projects/{projectId}/composition/conflicts

OpenAPI 1.7.0
SQLite V012
```

Gate réel :

```text
code validé     7e8caacff567f51354fcb88bd7505a6d135071c0
TOTAL           418/418 PASS
Architecture    170/170 PASS
Failures        0
Errors          0
Skipped         0
Reactor         14/14 modules SUCCESS
Packaging Win   PASS
Packaged smokes PASS
API health      PASS
Portable ZIP    33,919,431 bytes
```

Preuve : [`../validation/VALIDATION_M18.md`](../validation/VALIDATION_M18.md).  
Plan : [`M18_EXECUTION.md`](M18_EXECUTION.md).  
ADR-0084 : **Acceptée — M18**.  
Issue #85 : **CLOSED / completed**.  
PR #86 : **MERGED**.  
Merge : `30f11ac3ffc522bcc0c71e31216a3fb70f0631d7`.

Le SHA testé et le merge commit sont volontairement distincts : les commits post-gate de M18 étaient documentaires uniquement.

---

# M19 — Production Hardening, Scale & Operability

Statut : **✅ VALIDÉ TECHNIQUEMENT — PR #89 NON MERGÉE**

Issue : **#88**.

PR : **#89**.

## Question de sortie

> **MORPHEUS reste-t-il déterministe, observable et exploitable sur des dépôts réalistes de grande taille, avec des limites et performances mesurées plutôt que supposées ?**

**Réponse : OUI**, sur le profil gelé `M19-LARGE-GATE-1`, avec preuves Windows et Linux au SHA de code exact `dca27db969b426ad43941ccb8cee7e926efb931b`.

## Objectif

Transformer les garanties fonctionnelles en garanties d'exploitation, sans déplacer les responsabilités de MORPHEUS vers MINOS, NEXUS ou JARVIS.

## M19-S0 — Budgets et protocole avant optimisation

Avant toute optimisation, fixer et versionner :

```text
fixture sizes
requirement counts
traceability graph sizes
incremental sync latency budgets
query latency budgets
startup time budget
memory budget
SQLite size / growth budget
history retention cost budget
benchmark warmup / repetitions
machine/environment metadata
PASS / FAIL interpretation
```

**Les seuils ne doivent pas être choisis après observation des performances.**

## M19-S1/S2 — Performance et capacité

Prouver sur des fixtures reproductibles :

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

Les résultats Windows et Linux doivent être distingués. Un environnement non exécuté n’est jamais déclaré PASS.

## M19-S3/S4/S5 — Robustesse

Contrats et tests réels :

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

Invariant critique :

```text
failure during BUILDING/VALIDATING
        !=
partial ACTIVE exposure
```

Un ancien `ACTIVE` valide reste publié tant qu’un nouveau candidat n’a pas atteint une activation atomique valide.

## M19-S6/S7 — Observabilité

Évaluer puis compléter :

```text
structured logs
stable diagnostics
health / readiness semantics
operational counters
sync timing
provider timing
external integration timing
```

Observabilité **locale-first** : aucune télémétrie externe obligatoire n’est introduite.

## M19-S8 — Sécurité locale

Règles vérifiables :

```text
secret/path redaction
safe logging defaults
ignored path policy
external link non-following by default
write permission hardening
```

Pas de durcissement cosmétique : chaque règle importante doit avoir un contrat et un test.

## M19-S9 — Validation finale

Créer :

```text
scripts/validate-m19.ps1
validate-m19.cmd
docs/validation/VALIDATION_M19.md
```

Le validateur mono-commande doit couvrir :

```text
workspace / SHA
toolchain
clean test reactor complet
benchmarks/gates M19 reproductibles
tests de robustesse
packaging Windows
smokes
résumé PASS/FAIL
failure-summary automatique
```

Gate M19 :

```text
performance budgets documented before optimization
large-fixture gates reproducible
no partial ACTIVE exposure under failure
migration/recovery scenarios validated
structured operational diagnostics validated
security-local contracts validated
Windows evidence explicit
Linux evidence explicit or explicitly missing
full Maven reactor PASS
packaging + smokes PASS
```

La PR M19 reste Draft jusqu’au gate final vert sur le SHA final de code. Les éventuels commits post-gate doivent être documentaires uniquement et leur diff explicitement vérifié.

Preuve finale acquise :

```text
code SHA        dca27db969b426ad43941ccb8cee7e926efb931b
Windows         PASS
Linux ext4/WSL2 PASS
tests           449/449 PASS sur chaque plateforme
architecture    178/178 PASS sur chaque plateforme
reactor         14/14 SUCCESS
budgets         PASS, seuils gelés inchangés
packaging       PASS Windows + Linux
```

La preuve détaillée est `docs/validation/VALIDATION_M19.md`. La PR #89 peut devenir Ready après contrôle final du diff ; aucun merge n'est autorisé sans décision explicite du propriétaire.

---

# M20 — Release Engineering, Installation PROD & MORPHEUS 1.0

Statut : **PLANIFIÉ**

## Question de sortie

> MORPHEUS peut-il être installé, mis à jour, diagnostiqué et désinstallé comme un produit Windows/Linux sans Git, Maven ou JDK utilisateur, tout en préservant les données et en conservant le ZIP portable pour l'automatisation ?

## Cible Windows

```text
GitHub Release MORPHEUS
        ↓
MORPHEUS-<version>-windows-x64-setup.exe
        +
MORPHEUS-<version>-windows-x64-setup.exe.sha256
        ↓
%LOCALAPPDATA%\Programs\MORPHEUS
```

Données persistantes séparées :

```text
%LOCALAPPDATA%\MORPHEUS\data
%LOCALAPPDATA%\MORPHEUS\config
%LOCALAPPDATA%\MORPHEUS\logs
%LOCALAPPDATA%\MORPHEUS\backups
```

Principe :

```text
programme != data
update/uninstall program != delete knowledge store
```

Le ZIP portable reste supporté pour automation, CI, diagnostic, portable usage et versions side-by-side.

Artefacts cibles :

```text
MORPHEUS-<version>-windows-x64-setup.exe
MORPHEUS-<version>-windows-x64-setup.exe.sha256
morpheus-<version>-windows-x64.zip
morpheus-<version>-windows-x64.zip.sha256
morpheus-<version>-linux-x64.tar.gz
morpheus-<version>-linux-x64.tar.gz.sha256
```

Le runtime Java reste embarqué ; aucun JDK n’est requis pour l’utilisateur final.

Gate M20 :

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

Cible : **MORPHEUS 1.0**.

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
M19  Production hardening / scale / operability          ✅ qualifié, non mergé
 ↓
M20  Release engineering / PROD installation / 1.0       ⏳ planifié
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
M18     = multi-provider réel                                  ✅ intégré
M19     = exploitation à l'échelle                             ✅ qualifié, PR #89 non mergée
M20     = distribution produit / installation PROD / 1.0      ⏳ après merge M19
```

La priorité immédiate est la **revue et l'intégration autorisée de M19**. M20 ne démarre pas avant ce merge.
