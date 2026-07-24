# Validation M13 — NEXUS optionnel et contexte technique sous budget

Statut : **✅ VALIDÉ**

Date : 24 juillet 2026

Issue : #63  
PR : #64  
Head validé : `a44e8938bfa03e8b8a1039c8271a8865b871ed7d`

## Question de sortie

> MORPHEUS peut-il déléguer à NEXUS la sélection, le ranking, la fusion et la compression du contexte technique sous budget, à partir d'une intention MORPHEUS explicite, sans recopier ces règles et tout en restant entièrement utilisable lorsque NEXUS est absent ou indisponible ?

**Réponse : OUI.**

## Architecture validée

```text
MORPHEUS Java 21
 -> TechnicalContextProvider
 -> morpheus-integration-nexus
 -> Java MCP client 2.0.0 / STDIO
 -> NEXUS MCP runner Java 21
 -> list_projects
 -> build_context | explain_context
```

MORPHEUS ne dépend d'aucun type `com.nexus.*`.

## Frontière de responsabilité

```text
MORPHEUS = requirement / change / constraints / decisions / tasks / intent
NEXUS    = technical context selection / ranking / fusion / compression / budget
```

MORPHEUS construit un seed d'intention déterministe depuis le snapshot ACTIVE puis transmet explicitement le projet NEXUS, le budget, les sources, les contraintes et `explain`.

Le `ContextBundle` retourné reste un fait NEXUS : MORPHEUS ne le reranke, ne le fusionne, ne le retronque et ne recalcule pas son budget.

## Mapping projet

```text
nexusProject = UUID ou nom unique NEXUS
```

Le mapping est explicite par appel. M13 n'effectue aucune heuristique et ne déclenche aucune mutation NEXUS (`project add`, index, rebuild, remove).

## Optionalité

Sans configuration NEXUS :

```text
MORPHEUS CLI  -> disponible
MORPHEUS MCP  -> disponible
MORPHEUS API  -> disponible
NEXUS status  -> DISABLED
technical ctx -> absent explicitement
```

Un runner absent, arrêté ou incompatible produit une observation `UNAVAILABLE` sans rendre MORPHEUS indisponible.

## Surfaces validées

CLI :

```text
nexus-status
augmented-context requirement
augmented-context change
```

MCP :

```text
get_augmented_requirement_context
get_augmented_change_context
```

Le serveur M13 expose **18 tools read-only** : 14 M10 + 2 M12 + 2 M13.

HTTP :

```text
GET  /api/v1/integrations/nexus/status
POST /api/v1/projects/{projectId}/requirements/{requirementId}/augmented-context
POST /api/v1/projects/{projectId}/changes/{changeId}/augmented-context
```

Les réponses M13 sont live et exposent `persisted=false` ; aucun `ContextBundle` NEXUS n'est écrit dans un `KnowledgeSnapshot`.

## Gate Maven autoritatif

Commande exécutée sur Windows :

```powershell
.\mvnw.cmd clean test
```

Head exact :

```text
a44e8938bfa03e8b8a1039c8271a8865b871ed7d
```

Résultats :

```text
Domain              21/21 PASS
Application         87/87 PASS
OpenSpec             26/26 PASS
Synthetic             7/7 PASS
SQLite                7/7 PASS
MINOS Integration     8/8 PASS
NEXUS Integration     7/7 PASS
MCP                    5/5 PASS
API                    7/7 PASS
CLI                  17/17 PASS
Architecture       154/154 PASS
--------------------------------
TOTAL              346/346 PASS
Failures                 0
Errors                   0
Skipped                  0
BUILD SUCCESS
```

## Preuves M13 spécifiques

```text
TechnicalContextOptionsTest                  3/3 PASS
NexusIntegrationSettingsTest                 3/3 PASS
NexusMcpTechnicalContextProviderTest          3/3 PASS
NexusMcpTransportIntegrationTest              1/1 PASS
MorpheusAugmentedContextApiContractTest       2/2 PASS
MorpheusNexusCliTest                          1/1 PASS
MorpheusM13McpStdioIntegrationTest            1/1 PASS
LayerDependencyTest                           5/5 PASS
```

Le transport est prouvé par un vrai subprocess MCP STDIO fixture et le serveur MORPHEUS MCP est également exercé comme vrai subprocess.

## Correction révélée par le premier gate

Le premier passage sur `a91af6288eef3937339a6fbcef7366d0022adff8` a révélé que `KnowledgeSnapshotMetadata` exposait indirectement un `java.util.UUID` au `CanonicalJsonSerializer` dans les réponses M13.

La correction introduit `AugmentedSnapshotView`, qui projette les identités et timestamps en scalaires JSON stables sans élargir le sérialiseur canonique global. Le second gate complet sur `a44e8938...` valide cette correction.

## Packaging Windows

Commande exécutée :

```powershell
.\distribution\build-portable.ps1
```

Résultats :

```text
Maven package: BUILD SUCCESS
MCP/API/MINOS/NEXUS adapter packaging proof: PASS
jpackage app-image: PASS
morpheus.exe --version: PASS
morpheus.exe --json version: PASS
minos-status without config: DISABLED
nexus-status without config: DISABLED
Packaged standalone optional-engines smoke: PASS
Packaged API health smoke: PASS
Portable archive creation: PASS
```

Archive :

```text
dist/morpheus-0.1.0-windows-x64.zip
33,654,379 bytes
```

Le shaded JAR contient les adapters MINOS/NEXUS mais aucune classe `com/minos/*` ni `com/nexus/*`.

## Smoke cross-repo réel NEXUS

`distribution/test-nexus-compatibility.ps1` est disponible pour vérifier un runner NEXUS réel. Ce smoke complémentaire n'a pas été exécuté dans la preuve fournie pour cette validation ; le gate M13 officiel défini par la roadmap est néanmoins entièrement satisfait par `clean test` + packaging standalone.

## ADR acceptées

```text
ADR-0073 — Intégration NEXUS par MCP STDIO inter-processus
ADR-0074 — Mapping projet NEXUS explicite sans ownership de lifecycle
ADR-0075 — Séparer l'intention MORPHEUS du contexte technique NEXUS
ADR-0076 — Runtime et surfaces NEXUS optionnels
```

## Conclusion

M13 satisfait sa question de sortie : MORPHEUS peut enrichir une intention Requirement/Change avec un contexte technique NEXUS budgété et attribuable, sans dupliquer l'intelligence de contexte NEXUS, sans mutation de snapshot et sans rendre NEXUS obligatoire au fonctionnement de MORPHEUS.
