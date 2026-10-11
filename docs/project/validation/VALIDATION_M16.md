# Validation M16 — Constraint Semantics & Policy Enforcement

Statut : **✅ VALIDÉ TECHNIQUEMENT — PR #79 prête à intégrer**

Date : 26 juillet 2026

Issue : #78  
PR : #79  
Head de code validé : `f349c5f4701665e649d985426d35b5e6a6060e32`

## Question de sortie

> MORPHEUS peut-il déterminer de façon explicable quelles contraintes sont applicables et lesquelles bloquent réellement une action ou une transition, sans convertir une absence d'information en interdiction ?

**Réponse : OUI.**

M16 introduit une sémantique provider-neutral explicite des contraintes, persistante, requêtable et exploitable par les décisions lifecycle sans interpréter le texte ni la sévérité comme politique exécutable.

## Invariants validés

```text
applicable != blocking
warning != blocker
UNKNOWN != BLOCKED
constraint text != executable policy
severity != blocking policy
policy decision exposes reason + evidence
provider-specific policy types never leak into domain
base lifecycle rules remain MORPHEUS-owned
JARVIS still owns sequencing / action choice
```

## Modèle validé

```text
Constraint
├── ConstraintApplicability
├── ConstraintSeverity
├── ConstraintSatisfaction
├── ConstraintBlockingPolicy
├── blocking lifecycle targets
├── supportingEvidenceIds[]
└── Provenance

ConstraintPolicyEvaluationService
        ↓
ConstraintEvaluation
├── NOT_APPLICABLE
├── NON_BLOCKING
├── BLOCKING
└── UNKNOWN
```

Règle de blocage :

```text
APPLICABLE
+ BLOCK_WHEN_VIOLATED
+ target lifecycle explicitement ciblée
+ VIOLATED
+ supporting evidence explicite
= BLOCKING
```

Une contrainte legacy/OpenSpec sans sémantique explicite reste `UNKNOWN`; elle n'est jamais transformée en blocker ni en autorisation implicite.

## Normalisation / providers / persistance

Validé :

- extension rétrocompatible de `Constraint` ;
- supporting evidence validée dans `NormalizedProjectContent` et `SnapshotBusinessContent` ;
- OpenSpec conserve une sémantique `UNKNOWN` lorsqu'aucune policy structurée n'est fournie ;
- Synthetic provider expose une contrainte `CRITICAL` violée et réellement bloquante sur `VERIFYING`, ainsi qu'une contrainte `WARNING` violée mais explicitement `NON_BLOCKING` ;
- SQLite V010 persiste applicabilité, sévérité, satisfaction, mode de blocage, targets lifecycle et supporting evidence ;
- Memory et SQLite produisent la même projection ;
- SQLite close/reopen conserve exactement la sémantique M16.

## Lifecycle / orchestration

La machine structurelle M3 reste évaluée en premier. M16 ajoute ensuite la politique explicite des contraintes.

```text
base lifecycle ALLOWED
+ explicit BLOCKING constraint for target
= BLOCKED + BLOCKING_CONSTRAINT
```

Une policy ou satisfaction inconnue produit `UNKNOWN`, jamais `BLOCKED` ni `ALLOWED` par défaut.

La vue UC-16 expose désormais :

```text
applicableConstraints
blockingConstraints.status = AVAILABLE | PARTIALLY_AVAILABLE | UNKNOWN
constraintEvaluations[]
reason
supportingEvidenceIds[]
sourceEvidenceId
```

Le placeholder historique `UNAVAILABLE_BLOCKING_SEMANTICS_NOT_MODELED` n'est plus utilisé pour les contraintes M16 évaluables.

## Surfaces validées

CLI :

```text
constraints evaluate --project ID --change ID --target STATE
```

avec pagination et sortie JSON canonique.

MCP :

```text
get_change_orchestration_state
evaluate_change_transition
```

Le catalogue reste à **20 tools read-only**. Le transport MCP STDIO réel est couvert par `MorpheusM16McpStdioIntegrationTest`.

HTTP :

```text
GET  /api/v1/projects/{projectId}/changes/{changeId}/orchestration
POST /api/v1/projects/{projectId}/changes/{changeId}/transition-check
```

OpenAPI : **1.5.0**, avec `ConstraintEvaluation` et `ConstraintBlockingPolicy` typés.

La projection machine utilise une vue applicative JSON-safe ; les objets domaine ne sont pas sérialisés directement, conformément à ADR-0047.

## Gate Maven autoritatif

Commande exécutée par le validateur M16 :

```powershell
.\mvnw.cmd clean test
```

Head testé :

```text
f349c5f4701665e649d985426d35b5e6a6060e32
```

Résultats :

```text
Domain               37/37 PASS
Application        100/100 PASS
OpenSpec             26/26 PASS
Synthetic              7/7 PASS
SQLite                 7/7 PASS
MINOS Integration      8/8 PASS
NEXUS Integration      7/7 PASS
MCP                     5/5 PASS
API                   10/10 PASS
CLI                   25/25 PASS
Architecture        161/161 PASS
---------------------------------
TOTAL               393/393 PASS
Failures                  0
Errors                    0
Skipped                   0
BUILD SUCCESS
```

Le module Memory Store ne contient pas de tests propres et n'ajoute donc aucun cas au total.

## Packaging Windows

Le même validateur a ensuite exécuté le packaging portable sur le code M16 testé et ses smokes.

Preuves :

```text
MCP/API/MINOS/NEXUS/M14 orchestration packaging proof: PASS
Packaged standalone optional-engines + M14 orchestration smoke: PASS
Packaged API health smoke: PASS
Portable archive creation: PASS
```

Archive :

```text
dist/morpheus-0.1.0-windows-x64.zip
33,767,379 bytes
```

L'archive contient MORPHEUS et son runtime Java, CLI/MCP/API, les adapters optionnels MINOS/NEXUS et le contrat d'orchestration. MINOS, NEXUS et JARVIS ne sont ni embarqués ni requis.

## Validateur reproductible

Commande unique Windows :

```powershell
.\validate-m16.cmd
```

Le validateur :

1. vérifie/met à jour la branche M16 ;
2. contrôle Java et Maven Wrapper ;
3. exécute le reactor `clean test` complet ;
4. s'arrête sur un vrai code de sortie non nul ;
5. exécute le packaging Windows uniquement si Maven est vert ;
6. conserve les logs sous `.git/morpheus-validation/m16`.

Environnement du gate :

```text
Windows 10 amd64
OpenJDK 24.0.1
Maven Wrapper / Apache Maven 3.9.16
Java compilation target: release 21
```

## ADR

ADR-0082 — **Acceptée — M16** après preuve du présent gate.

## Conclusion

M16 est **VALIDÉ TECHNIQUEMENT** sur le head de code `f349c5f4701665e649d985426d35b5e6a6060e32`.

La PR #79 peut passer Ready. La fusion reste soumise à une autorisation explicite distincte.