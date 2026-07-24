# ADR-0058 — Vue compacte d'analyse de changement et JSON canonique

- Statut : **Proposée — M8, preuve Maven en attente**
- Date : 24 juillet 2026
- Dépend de : ADR-0047, ADR-0056, ADR-0057
- Portée : M8 — exposition stable de l'analyse de changement

## Contexte

M5 a stabilisé des vues compactes et un sérialiseur JSON canonique sans dépendance JSON métier générique. M8 doit exposer son résultat riche sans dupliquer une nouvelle stratégie de sérialisation et sans laisser fuiter des objets techniques non stables.

## Décision

Introduire :

```text
CompactChangeAnalysisView
CompactChangeAnalysisViewService
```

avec :

```text
schemaVersion = 1
operation = analyze_change
```

`CompactChangeAnalysisViewService` réutilise :

```text
CanonicalJsonSerializer
CompactQueryTypes.QueryMetadata
CompactQueryTypes.SnapshotMetadata
CompactQueryTypes.ProvenanceView
CompactQueryTypes.RequirementView
CompactQueryTypes.ChangeView
CompactQueryTypes.ConstraintView
CompactQueryTypes.DesignDecisionView
CompactQueryTypes.ImplementationTaskView
CompactQueryTypes.TraceNodeView
CompactQueryTypes.TraceLinkView
```

Aucune nouvelle bibliothèque JSON n'est introduite.

## Contenu exposé

La vue contient :

```text
baseline snapshot
change proposal
summary
requirement impacts
constraints
design decisions
implementation tasks
dependency impacts
acceptance coverage status
warnings
```

Pour chaque requirement impacté :

```text
delta id / kind / requirement id
specification key
key / title / statement proposés
provenance du delta
CURRENT requirement éventuel
scénarios CURRENT
scénarios proposés
changed fields
warnings
```

## Chemins

Chaque dépendance expose un chemin ordonné de `PathStepView` :

```text
from
into
persisted TraceLinkView
```

`from/into` représente le sens de traversée explicatif. Le `TraceLinkView` conserve le sens métier réellement persisté, ce qui est indispensable pour les traversées `INCOMING`.

## Warnings

Les warnings M8 possèdent leur propre DTO compact car leur sévérité peut être :

```text
INFO
WARNING
```

Ils conservent :

```text
code
severity
optional requirementId
message
details
```

Ils ne sont pas convertis en texte libre non structuré.

## Déterminisme

- identités converties en chaînes ;
- enums convertis par leur nom stable ;
- champs modifiés triés ;
- evidence IDs de trace triés ;
- chemins dans l'ordre de traversée ;
- maps sérialisées avec clés triées par `CanonicalJsonSerializer` ;
- aucun timestamp généré pendant la projection.

Le même `ChangeAnalysisResult` doit produire exactement les mêmes :

```text
CompactChangeAnalysisView
String JSON
byte[] UTF-8
```

Après reopen SQLite, la projection doit rester identique byte pour byte.

## Frontières

La vue compacte :

```text
n'est pas une table de persistence
n'est pas un contrat HTTP
n'est pas encore une commande CLI
n'est pas un outil MCP
```

Elle prépare les surfaces M9/M10/M11 sans les anticiper.

## Preuve attendue

L'ADR pourra passer à **Acceptée — M8** lorsque le gate démontre :

1. projection compacte de l'analyse complète ;
2. `operation=analyze_change` et `schemaVersion=1` ;
3. statut d'acceptance explicite ;
4. chemins contenant `from`, `into` et le lien persistant ;
5. JSON identique entre appels ;
6. `byte[]` identiques après reopen SQLite ;
7. parité Memory / SQLite de l'analyse source ;
8. `./mvnw clean test` vert.
