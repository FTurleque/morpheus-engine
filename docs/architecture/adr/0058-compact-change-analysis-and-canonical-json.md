# ADR-0058 — Vue compacte d'analyse de changement et JSON canonique

- Statut : **Acceptée — M8**
- Date : 24 juillet 2026
- Dépend de : ADR-0047, ADR-0056, ADR-0057
- Portée : M8 — exposition stable de l'analyse de changement

## Contexte

M5 a stabilisé des vues compactes et un sérialiseur JSON canonique sans dépendance JSON métier générique. M8 expose son résultat riche sans dupliquer une nouvelle stratégie de sérialisation et sans laisser fuiter des objets techniques non stables.

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

`CompactChangeAnalysisViewService` réutilise `CanonicalJsonSerializer` et les DTOs compacts M5 existants. Aucune nouvelle bibliothèque JSON n'est introduite.

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

Pour chaque requirement impacté : delta id/kind/requirement id, specification key, contenu proposé, provenance, requirement CURRENT éventuel, scénarios CURRENT, scénarios proposés, changed fields et warnings.

## Chemins

Chaque dépendance expose un chemin ordonné de `PathStepView` :

```text
from
into
persisted TraceLinkView
```

`from/into` représente le sens de traversée explicatif. Le `TraceLinkView` conserve le sens métier réellement persisté, indispensable pour les traversées `INCOMING`.

## Warnings

Les warnings M8 possèdent leur propre DTO compact car leur sévérité peut être `INFO` ou `WARNING`. Ils conservent code, sévérité, optional requirementId, message et details ; ils ne sont pas convertis en texte libre non structuré.

## Déterminisme

- identités converties en chaînes ;
- enums convertis par leur nom stable ;
- champs modifiés triés ;
- evidence IDs de trace triés ;
- chemins dans l'ordre de traversée ;
- maps sérialisées avec clés triées par `CanonicalJsonSerializer` ;
- aucun timestamp généré pendant la projection.

Le même `ChangeAnalysisResult` produit exactement les mêmes :

```text
CompactChangeAnalysisView
String JSON
byte[] UTF-8
```

Après reopen SQLite, la projection reste identique byte pour byte.

## Frontières

La vue compacte n'est ni une table de persistance, ni un contrat HTTP, ni encore une commande CLI/MCP. Elle prépare M9/M10/M11 sans les anticiper.

## Preuve d'acceptation — 24 juillet 2026

`ChangeAnalysisContractTest` : **7/7 PASS**.

Les preuves couvrent : projection compacte complète, `operation=analyze_change`, `schemaVersion=1`, statut d'acceptance explicite, chemins `from/into` + lien persisté, JSON déterministe, UTF-8 byte-déterministe après reopen SQLite et parité Memory/SQLite.

Gate complet :

```text
TOTAL              289/289 PASS
Architecture       146/146 PASS
Failures             0
Errors               0
Skipped              0
BUILD SUCCESS
Total time         26.406 s
Finished 2026-07-24T09:44:51+02:00
```

Warnings connus non bloquants uniquement : Xerial SQLite native-access et SLF4J NOP.

**Décision : Acceptée — M8.**