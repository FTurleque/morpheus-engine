---
id: ARCH-0135
title: "Le domaine appartient à MORPHEUS, et sa neutralité se prouve sur plusieurs providers"
domain: architecture
rules: true
files: ["morpheus-domain/src/main/java/**/*.java", "**/pom.xml"]
---

# Le domaine appartient à MORPHEUS, et sa neutralité se prouve sur plusieurs providers

- Statut : **Acceptée**
- Date : 2026-10-24
- Remplace : ADR-0001, ADR-0029

## Context

MORPHEUS comprend des spécifications et des intentions qui viennent de plusieurs sources : OpenSpec, Markdown
structuré, formats futurs ou systèmes externes. OpenSpec est le provider de référence (ADR-0002), ce qui crée un
risque classique : concevoir les objets du domaine en reproduisant la structure du premier format disponible. Ce
serait rapide au début, mais MORPHEUS deviendrait progressivement la façade d'un outil particulier et perdrait son
indépendance, sa capacité à intégrer d'autres sources, la stabilité de ses contrats publics et sa valeur propre de
moteur d'intelligence.

La question est donc : comment exploiter profondément un provider de référence sans que ce provider définisse le
langage métier et les frontières du moteur ? Et comment prouver en code, pas seulement affirmer, que deux formats
distincts traversent les mêmes ports applicatifs et produisent le même domaine MORPHEUS ?

Forces en présence :

- **vitesse initiale** : réutiliser les structures d'un provider réduit le mapping et accélère un prototype ;
- **pérennité** : les formats externes évoluent indépendamment de MORPHEUS ;
- **multi-provider** : un futur provider peut exprimer les mêmes intentions avec une structure très différente ;
- **contrats publics** : CLI, MCP, API, NEXUS et JARVIS ont besoin de concepts stables ;
- **testabilité** : le domaine doit pouvoir être testé sans installer le provider réel ;
- **valeur produit** : elle réside dans la normalisation, la traçabilité et l'intelligence des spécifications, pas
  dans la simple lecture d'un format.

Options étudiées :

| Option | Verdict | Raison |
|---|---|---|
| Exposer directement le modèle du premier provider | Rejetée | Prototype rapide, mais couplage irréversible, contrats instables, multi-provider difficile |
| Utiliser uniquement des documents Markdown bruts | Rejetée comme modèle principal | Le texte brut peut rester preuve ou contenu source, mais ne permet pas de requêtes fiables de traçabilité |
| Définir une union de tous les formats connus | Rejetée | Produit un domaine guidé par les fournisseurs au lieu des cas d'usage |
| Domaine MORPHEUS normalisé + métadonnées externes | Retenue | Préserve la sémantique MORPHEUS et conserve ce qu'il faut pour le round-trip ou le diagnostic |

## Decision

### 1. MORPHEUS possède son propre modèle de domaine

Les concepts publics sont définis à partir des cas d'usage MORPHEUS, pas à partir des fichiers ou classes d'un
provider. Le domaine porte notamment :

```text
ProjectSpecification   Specification        Requirement         Scenario
ChangeProposal         Constraint           DesignDecision      AcceptanceCriterion
ImplementationTask     SpecificationVersion Evidence            TraceabilityLink
ExternalReference
```

### 2. Un provider est un adaptateur derrière les ports de l'application

Les providers sont placés derrière les ports `SpecificationProvider` et `SpecificationContentReader` de
`morpheus-application`. Le flux de lecture est obligatoire :

```text
Format externe -> Provider / Adapter -> Ingestion / Normalisation -> Domaine MORPHEUS
```

Un flux d'écriture vers un format externe passe, lui aussi, par un adaptateur explicite.

### 3. Les invariants de la frontière

Ils s'imposent aux providers, à `SpecificationKnowledgeStore`, à la CLI, au MCP, à l'API et aux intégrations
MINOS/NEXUS/JARVIS :

1. Aucun type propre à un provider n'apparaît dans les interfaces du domaine, ni ne traverse un contrat de
   l'application : aucun type de SDK ou de provider ne fuit dans `NormalizedProjectContent` ou `ProviderReadResult`.
2. Aucun chemin de fichier spécifique à un provider ne devient une identité métier universelle sans justification.
3. Les statuts propres à un provider sont mappés vers des concepts MORPHEUS ou conservés comme métadonnées externes.
4. Les identifiants externes peuvent être conservés mais ne constituent pas automatiquement l'identité MORPHEUS.
   L'identité externe est scopée par provider : une même clé externe dans deux providers ne constitue pas la même
   identité MORPHEUS.

   ```text
   (openspec, requirement, X) != (synthetic-json, requirement, X)
   ```

5. Un test du domaine s'exécute sans OpenSpec ni autre provider installé.
6. Un nouveau provider s'ajoute sans modifier le domaine ni les cas d'usage publics existants, sauf extension
   légitime du domaine.
7. Un consommateur de port ne branche pas sur l'identifiant du provider : la même fonction consomme chaque provider,
   et le même vocabulaire `ReadCategory` s'applique à tous.
8. Un provider ne déclare que les capacités qu'il normalise réellement.

```text
provider format != MORPHEUS domain
OpenSpec type -X-> application contract
Synthetic JSON type -X-> application contract
consumer does not branch on provider id
the same ReadCategory vocabulary applies to both providers
external identity is provider-scoped
```

Aucune décision ultérieure ne contourne cette frontière sans nouvel ADR explicite.

### 4. Un second provider, de vérification, prouve la neutralité

Le module `morpheus-provider-synthetic` est **verification-only** : il démontre l'architecture extensible, il n'est
pas un provider produit supporté publiquement. Il implémente les mêmes ports que le provider OpenSpec :

```text
OpenSpec source ──> OpenSpec adapter ────┐
                                         │
                                         ├──> SpecificationContentReader
                                         │        ↓
Synthetic JSON ─> Synthetic adapter ─────┘    ProviderReadResult
                                                  ↓
                                         NormalizedProjectContent
```

Il lit `morpheus-spec.json` ; la fixture `experiments/m0/fixtures/synthetic-basic/morpheus-spec.json` est l'oracle du
second format, et porte des métadonnées explicites (spécification, exigence, scénario, changement) afin que le reader
n'invente ni titre de spécification ni action de scénario. Ses capacités sont celles que déclare
`SyntheticSpecificationProvider`. Son parser JSON reste encapsulé dans `com.morpheus.provider.synthetic` et n'ajoute
aucune dépendance externe. Il n'est qu'une dépendance de portée `test` des autres modules.

Le provider synthétique doit permettre de vérifier que :

1. il est détecté via `SpecificationProvider` ;
2. son reader implémente `SpecificationContentReader` ;
3. il produit les concepts MORPHEUS (`Specification`, `Requirement`, `Scenario`, `ChangeProposal`, `Evidence`,
   `Provenance`) ;
4. OpenSpec et le format synthétique sont consommés par la même fonction sans branche provider-specific ;
5. les deux résultats exposent le même ensemble de `ReadCategory` demandé ;
6. une même external key produit des identités distinctes entre OpenSpec et le format synthétique ;
7. `com.morpheus.domain..` et `com.morpheus.application..` restent indépendants de `com.morpheus.provider..` ;
8. aucun type de SDK ou de provider ne fuit dans `NormalizedProjectContent` ou `ProviderReadResult` ;
9. l'accueil du second provider n'exige aucune modification du domaine.

Hors périmètre : la publication du provider synthétique comme fonctionnalité utilisateur, la compatibilité
ascendante de son schéma JSON, l'écriture du format synthétique, et un enrichissement fonctionnel au niveau
d'OpenSpec.

## Do's and Don'ts

### Do

- Définir un nouveau concept public à partir d'un cas d'usage MORPHEUS, dans `morpheus-domain`.
- Faire passer toute lecture d'un format externe par un adaptateur qui implémente `SpecificationProvider` et
  `SpecificationContentReader`.
- Mapper un statut provider vers un concept MORPHEUS, ou le conserver comme métadonnée externe.
- Conserver `Evidence`, `ExternalReference` et des métadonnées provider contrôlées pour ne pas perdre d'information.
- Écrire un consommateur de port qui fonctionne avec chaque provider, sans tester l'identifiant du provider.
- Scoper chaque identité externe par provider.
- Déclarer dans un provider les seules capacités qu'il normalise réellement.
- Garder `morpheus-provider-synthetic` en dépendance de portée `test`.

### Don't

- Ne pas exposer un type, un nom d'état ou un chemin de fichier d'un provider dans une interface du domaine, ni
  laisser un type propre à un format traverser un contrat de l'application.
- Ne pas faire dépendre `com.morpheus.domain..` ni `com.morpheus.application..` d'un paquet `provider..` ; ni le
  domaine d'un paquet `store..`, `cli..`, `mcp..`, `api..` ou `integration..`.
- Ne pas utiliser un identifiant externe comme identité MORPHEUS.
- Ne pas brancher un consommateur sur l'identifiant d'un provider.
- Ne pas modifier un cas d'usage public existant pour accueillir un nouveau provider, hors extension légitime du
  domaine.
- Ne pas exposer le provider synthétique comme fonctionnalité utilisateur.
- Ne pas ajouter de dépendance externe au parser JSON synthétique.

## Consequences

### Positives

- Découplage fort et stabilité des contrats publics.
- Domaine testable sans provider réel.
- Plusieurs providers possibles, chacun évoluant indépendamment.
- Meilleure séparation des responsabilités, et un modèle qui peut s'enrichir au-delà des limites du format source.
- La neutralité vis-à-vis des providers est prouvée en code, pas seulement affirmée.
- Le provider synthétique sert de fixture déterministe aux tests des autres modules.

### Négatives

- Coût de mapping et duplication apparente de certaines structures.
- Règles de normalisation à définir, et gestion plus complexe des concepts non représentables.
- Tests de conformité nécessaires par provider.
- Un module de vérification est maintenu avec le reste du réacteur.

Ces coûts sont acceptés parce qu'ils protègent une frontière structurante.

### Risques

- **Abstraction trop générique** : le domaine perdrait des informations importantes. Réponse : conserver `Evidence`,
  `ExternalReference` et des métadonnées provider contrôlées.
- **Sur-conception** : le modèle deviendrait théorique. Réponse : valider chaque concept avec au moins un cas réel.
- **Fuite indirecte de concepts provider** : sans type technique, des noms ou des états peuvent encore reproduire un
  provider. Réponse : revue d'architecture et test avec un second provider.
- Le schéma JSON synthétique n'a aucune garantie de compatibilité : un usage hors des tests serait sans contrat.

## Compliance and Enforcement

- Règle Archgate `ARCH-0135-domaine-propre-et-neutralite-multi-provider.rules.ts` : `domain-names-no-provider`,
  `synthetic-provider-is-test-scoped`.
- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/LayerDependencyTest.java`
  (`domainMustNotDependOnAdapters`, `applicationMustNotDependOnAdapters`) : le domaine et l'application ne dépendent
  d'aucun adaptateur.
- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/ProviderAntiLockInTest.java` : les providers
  OpenSpec, synthétique et Markdown structuré implémentent le même port de lecture, un consommateur neutre lit les trois
  formats, et des clés externes identiques restent scopées par provider.
- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/AdapterSiblingArchitectureTest.java` : les
  providers intégrés, synthétique compris, ne dépendent pas du SDK de plugins.
- `morpheus-provider-synthetic/src/test/java/com/morpheus/provider/synthetic/SyntheticSpecificationProviderTest.java`
  et `SyntheticSpecificationContentReaderTest.java`.
- Revue : absence de nom ou d'état provider dans les interfaces du domaine (invariants 2 et 3), et non-modification
  des cas d'usage publics à l'ajout d'un provider (invariant 6).

## References

- [ADR-0002](ARCH-0002-openspec-reference-provider.md) — OpenSpec premier provider, sans verrouillage.
- [ADR-0003](DATA-0003-specification-knowledge-store.md) — persistance derrière `SpecificationKnowledgeStore`.
- [ADR-0140](DATA-0140-identite-de-domaine.md) — identité de domaine, scopée par provider, distincte de la version, du
  localisateur et de la référence externe.
- [ADR-0011](BE-0011-provider-capability-negotiation.md) — négociation des capacités d'un provider.
- [ADR-0028](BE-0028-unified-provider-read-contract.md) — contrat de lecture unifié des providers.
- [VALIDATION_C0](../../project/validation/VALIDATION_C0.md), [VALIDATION_M0](../../project/validation/VALIDATION_M0.md)
  et [VALIDATION_M2](../../project/validation/VALIDATION_M2.md).
