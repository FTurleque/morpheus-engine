# Proposition : dire qu'un refus de budget cumulatif porte sur toute la lecture

## Why

Un refus du budget d'ingestion s'écrit `provider ingestion <grandeur> exceeds budget for <source>: <valeur> > <maximum>`
pour toutes les grandeurs. Pour une grandeur cumulative, la valeur est le total de la session et `<source>` le fichier
qu'on lisait quand le compteur a basculé : la phrase accuse ce fichier. Deux instances relevées le 15/11/2026 :
`openspec/specs/operational-runtime-injection/spec.md` (2 005 octets) et `openspec/specs/frontieres-de-modules/spec.md`
(74 122 octets, le plus gros fichier de son dépôt) annoncés au-delà de 524 288 octets. Le second paraît confirmé, et le
découper ne change rien au total.

Le dépôt a déjà écrit la règle contraire : le Javadoc d'`OpenSpecSourceAttribution` refuse d'attribuer à un fichier ce
qui n'est pas de son fait, parce que nommer un fichier « would accuse an innocent one ».

Preuve : constat `AUD-QUA-24` (Moyenne), `docs/project/audits/2026-10-09/findings.md`, revérification du 15/11/2026 sur
`1b5fe93d` ; mécanisme dans `ProviderIngestionBudget.Session.addEvidenceFragment(String)`, qui attribue à
`lastDocument`, et dans `ProviderIngestionBudget.exceeded`.

## What Changes

- Le texte d'un refus distingue deux familles de grandeurs. **Par élément** (`document bytes`, et le nombre de lignes
  d'un seul document) : forme actuelle, le fichier nommé est la cause. **Cumulatives** (`file count`,
  `aggregate bytes`, `line count` de la session, `block count`, `entity count`, `evidence bytes`) : le refus dit que le
  total de toute la lecture dépasse le budget et nomme le fichier comme l'endroit où la lecture s'est arrêtée.
- Quand la lecture bornée s'arrête avant de connaître le total exact, le refus dit « plus de » au lieu d'imprimer
  `maximum + 1` comme une mesure.
- Aucune surface publique, aucun code de diagnostic, aucun format persisté ne change : seul le texte du message bouge.

## Capabilities

### New Capabilities
- `provider-ingestion-budget` : bornes de travail d'une ingestion par un provider, et texte de leurs refus.

### Modified Capabilities

## Périmètre et exclusions

**Inclus :** `ProviderIngestionBudget` (`morpheus-application`), les tests qui lisent le texte d'un refus dans
`morpheus-application`, `morpheus-provider-openspec` et `morpheus-provider-markdown`, une page durable
`docs/developer/specifications/provider-ingestion-budget.md`.

**Exclus :** la valeur des bornes et la possibilité de les régler (`AUD-ARC-23`, changement
`qualify-the-evidence-budget-on-real-corpora`) ; le mode d'échec en bloc, qui reste tel quel ; les notes de version
publiées, qui citent l'ancien texte et sont des relevés datés.

## Acteurs et surfaces concernés

L'utilisateur de la CLI qui lit le refus d'un `sync` (sortie texte et `--json`) ; les clients MCP et HTTP qui reçoivent
le même diagnostic.

## Nature des informations

| Affirmation | Nature | Source ou preuve |
|---|---|---|
| Le refus accuse le fichier en cours de lecture pour un total de session | existant vérifié | `AUD-QUA-24`, messages relevés mot pour mot |
| Six grandeurs sont comptées sur la session, une seule par élément | existant vérifié | `ProviderIngestionBudget.Session.read`, `addBlocks`, `addEntities`, `addEvidenceFragment` ; l'audit en citait quatre (design.md, Écarts) |
| La valeur imprimée vaut `maximum + 1` quand la lecture bornée s'arrête | existant vérifié | branche `WorkspaceFileTooLargeException` de `Session.read` |
| Aucun ADR n'est requis | décision | `.claude/rules/adr.md` : texte de diagnostic, retour arrière sans coût |

## Impact

`morpheus-application` (`ProviderIngestionBudget`) ; tests `ProviderIngestionBudgetTest`,
`ProviderIngestionBudgetPrecisionTest`, `OpenSpecReadChargesEachFileOnceTest`, `OpenSpecRefusedFileNameTest`,
`StructuredMarkdownSpecificationContentReaderTest` ; une page durable nouvelle et son index. Aucun test d'architecture
modifié.

## Questions ouvertes

Aucune.
