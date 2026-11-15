# Proposition : qualifier le budget d'évidence sur des corpus réels

## Why

`ProviderIngestionBudget.DEFAULT` fixe `maxEvidenceBytes = 512 * 1024`, compté sur toute la session d'ingestion, sans
option ni clé de configuration : les quatre lecteurs de production ouvrent tous `DEFAULT`. Mesuré le 15/11/2026 sur les
trois projets du mainteneur, le `sync` publie `nexus-context-engine` (164 Kio de `openspec/specs/`) et refuse
`morpheus-engine` (407 Kio, refus à `524777 > 524288`) et `minos-code-intelligence` (503 Kio, refus à
`557385 > 524288`). L'échec est total : huit catégories `FAILED`, aucune publication, puis `REBUILD_REQUIRED` /
`BASELINE_INCONSISTENT`. Nexus est à 40 % du plafond et le franchira en grandissant.

Le code montre en outre pourquoi le budget mord si tôt : `OpenSpecCurrentSpecificationReader` cite comme évidence le
fichier de spécification **entier**, puis chaque exigence avec ses scénarios, puis chaque scénario. Une ligne de scénario
est donc comptée jusqu'à trois fois, alors que l'évidence ne conserve que l'empreinte SHA-256 de son extrait
(`Evidence.excerptHash`). Les deux refus observés sont survenus en cours de lecture : leur valeur est un minimum, pas le
total du corpus.

Preuve : constat `AUD-ARC-23` (Élevée), `docs/project/audits/2026-10-09/findings.md`, revérification du 15/11/2026 sur
`1b5fe93d` ; `OpenSpecCurrentSpecificationReader.evidence` (appels pour la spécification, l'exigence et le scénario).

## What Changes

- Une mesure, livrée dans la PR : octets d'évidence comptés **par exigence** et par corpus, rapportés au texte des
  exigences, sur les trois projets ; elle départage les deux lectures que l'audit laisse ouvertes (plafond trop bas, ou
  évidence comptée en excès).
- Le budget d'évidence par défaut admet les corpus de qualification avec une marge déclarée, et le refuse toujours au-delà,
  de façon explicite. Le moyen (compter chaque ligne citée une fois, relever la borne, ou les deux) est une décision du
  mainteneur, prise sur la mesure (design.md, D1).
- Un test qualifie la borne sur un corpus déterministe dont le volume dépasse celui du plus gros projet mesuré, et sur le
  corpus `openspec/` de ce dépôt.
- Le guide utilisateur dit qu'une synchronisation est bornée en volume et comment se lit le refus.
- Hors décision contraire du mainteneur, aucune surface publique ne change (design.md, D2 et D3).

## Capabilities

### New Capabilities

### Modified Capabilities
- `provider-ingestion-budget` : exigence ajoutée, qui qualifie la borne d'évidence par défaut sur des corpus mesurés.
  La capacité est créée par `name-a-cumulative-budget-refusal-as-a-session-total`, à archiver avant celui-ci.

## Périmètre et exclusions

**Inclus :** la mesure, le budget d'évidence par défaut et la façon dont les lecteurs OpenSpec le consomment, le test de
qualification, la page durable `provider-ingestion-budget.md`, le guide utilisateur.

**Exclus, sauf décision du mainteneur (design.md, Questions ouvertes) :**
- un budget réglable par l'opérateur (option CLI ou clé de configuration), qui serait une surface publique ;
- une publication partielle avec raison de troncature, qui changerait la doctrine « un candidat qui échoue ne remplace
  pas l'`ACTIVE` » ;
- les autres bornes de `DEFAULT`, qui tiennent sur les trois corpus (fichiers, agrégat, taille d'un document) ;
- le texte du refus (`AUD-QUA-24`, changement `name-a-cumulative-budget-refusal-as-a-session-total`) et le schéma
  dérivé (`AUD-ARC-22`, changement `accept-a-declared-derived-openspec-schema`).

## Acteurs et surfaces concernés

L'utilisateur de la CLI qui synchronise un projet OpenSpec ; le mainteneur, qui choisit le moyen sur la mesure.

## Nature des informations

| Affirmation | Nature | Source ou preuve |
|---|---|---|
| Le budget d'évidence vaut 512 Kio, sans levier opérateur | existant vérifié | `ProviderIngestionBudget.DEFAULT`, `OpenSpecIngestionBudgets.open` |
| Deux des trois projets sont refusés, nexus passe | existant vérifié | `AUD-ARC-23`, trois `sync` relevés le 15/11/2026 |
| Une ligne de scénario est citée jusqu'à trois fois | existant vérifié | `OpenSpecCurrentSpecificationReader` : `evidence(…, 1, lines.size(), …)` puis exigence puis scénario |
| Seule l'empreinte de l'extrait est conservée | existant vérifié | `Evidence(…, Optional<String> excerptHash)` |
| Les valeurs des refus sont des minimums | existant vérifié | le refus interrompt la lecture (`exceeded` lève) |
| Le défaut doit admettre les corpus de qualification | proposition à valider | spec delta, design.md D1 |
| Faut-il un ADR | question ouverte | dépend de D1 : une borne qualifiée n'en exige pas, un budget réglable ou une publication partielle oui |

## Impact

`morpheus-application` (`ProviderIngestionBudget`) et/ou `morpheus-provider-openspec` (les trois appels
`addEvidenceFragment`) selon D1 ; un test de qualification ; `docs/developer/specifications/provider-ingestion-budget.md` ;
le guide utilisateur. Aucun test d'architecture modifié, aucun ratchet touché.

## Questions ouvertes

- D1, au mainteneur, sur la mesure de la tâche 1 : compter chaque ligne citée une fois, relever la borne, ou les deux.
- D2 et D3, au mainteneur : budget réglable par l'opérateur, publication partielle déclarée. Chacun ouvrirait son
  propre changement et un ADR.
