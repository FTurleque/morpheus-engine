# ADR-0084 — Composition multi-provider provider-neutral, déterministe et explicable

Statut : **Acceptée — M18**

Date : 26 juillet 2026

## Contexte

MORPHEUS dispose d'un contrat provider unifié et de deux adapters de preuve, OpenSpec et Synthetic. Synthetic démontre l'absence de verrouillage mais n'est pas une source réelle de production. M18 doit introduire un deuxième provider réel et permettre qu'un même projet soit lu depuis plusieurs providers sans transformer l'ordre d'exécution en politique métier implicite.

Les risques principaux sont :

- last-write-wins silencieux ;
- perte de provenance lors d'une fusion ;
- confusion entre identifiant provider, chemin source et `DomainIdentity` ;
- rapprochement heuristique non explicable ;
- fuite de types OpenSpec/Markdown dans le domaine/application ;
- publication d'un snapshot incohérent lorsqu'une source optionnelle est absente.

## Décision

La composition est une responsabilité application, exécutée sur des contributions déjà normalisées.

```text
provider adapter
    -> normalized ProviderContribution
    -> MultiProviderCompositionService
    -> ComposedProjectContent + CompositionConflict*
```

### Contribution

Chaque contribution expose au minimum :

- `ProviderId` ;
- priorité explicite ;
- contenu normalisé ;
- provenance de lecture ;
- diagnostics provider ;
- caractère requis ou optionnel.

### Identité

Le rapprochement se fait uniquement sur une clé logique provider-neutral explicitement disponible dans le contenu normalisé. Le chemin source, l'ordre de lecture ou un texte similaire ne créent jamais une identité.

```text
provider identifier != DomainIdentity
source path != identity
similar text != same entity
```

### Priorité et conflits

La priorité choisit un candidat principal lorsque plusieurs observations portent la même clé logique. Elle ne supprime jamais les autres observations.

Toute divergence sur un champ canonique produit un `CompositionConflict` explicite contenant :

- type d'entité ;
- clé logique ;
- champ ;
- candidats et provenance ;
- priorité ;
- résolution (`SELECTED_BY_PRECEDENCE`, `UNRESOLVED`, `IDENTICAL`).

Les conflits de contenu, d'ownership et de type/identité sont représentés explicitement. Une valeur absente face à une valeur présente reste une observation valide et requêtable.

Il n'existe aucun last-write-wins silencieux.

### Optionalité

Un provider optionnel absent ou non compatible produit un diagnostic de composition mais ne fait pas échouer le projet si au moins une contribution requise et valide reste disponible. Un provider requis absent échoue explicitement.

### Persistance

L'état de composition est snapshot-scoped et persisté séparément du contenu provider. Memory et SQLite conservent contributions observées, provenance, priorité et conflits. SQLite V012 porte cette persistance.

### Frontières

- adapters : formats et parsing ;
- application : composition, priorité, conflit, optionalité ;
- domain : identités et faits métier canoniques ;
- stores : persistance de l'état de composition ;
- CLI/MCP/HTTP : projection JSON-safe uniquement.

Aucun type `openspec.*` ou `markdown.*` ne doit apparaître dans le domaine/application.

## Conséquences

### Positives

- ajout de providers futurs sans modifier le domaine ;
- conflits audités et requêtables ;
- comportement déterministe ;
- provenance complète préservée ;
- optionalité explicite ;
- même politique Memory/SQLite.

### Coûts

- modèle de composition supplémentaire ;
- persistance V012 ;
- surfaces de diagnostic dédiées ;
- besoin de clés logiques explicites pour rapprocher les entités.

## Alternatives rejetées

### Fusionner dans l'ordre des providers

Rejeté : last-write-wins implicite et non explicable.

### Réutiliser le chemin source comme identité

Rejeté : un déplacement de fichier ne doit pas changer l'identité métier.

### Faire connaître OpenSpec au provider Markdown

Rejeté : couplage direct entre adapters et impossibilité d'étendre proprement la composition.

### Déduire l'identité par similarité textuelle

Rejeté en M18 : heuristique non démontrable et ambiguïtés silencieuses.

## Preuve obtenue

ADR acceptée après validation M18 sur le head de code :

```text
7e8caacff567f51354fcb88bd7505a6d135071c0
```

Le gate reproductible `validate-m18.cmd` a prouvé :

- OpenSpec + Structured Markdown dans le même projet ;
- conflits de contenu, ownership et type/identité déterministes et requêtables ;
- optionalité non fatale et provider requis absent explicitement rejeté ;
- Memory == SQLite et close/reopen exact ;
- provenance et priorité persistées ;
- CLI/MCP/HTTP cohérents ;
- OpenAPI 1.7.0 ;
- architecture sans fuite de types provider ;
- **418/418 tests PASS**, dont **170/170 Architecture** ;
- packaging Windows + smokes **PASS** ;
- archive portable `morpheus-0.1.0-windows-x64.zip` de **33,919,431 octets**.

Preuve détaillée : [`../validation/VALIDATION_M18.md`](../validation/VALIDATION_M18.md).