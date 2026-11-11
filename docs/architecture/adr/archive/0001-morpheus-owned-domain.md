# ADR-0001 — Le domaine MORPHEUS est indépendant des formats et providers

> **Archivée.** Cette décision est consolidée dans [ADR-0135](../ARCH-0135-domaine-propre-et-neutralite-multi-provider.md). Ce texte n'est plus en vigueur et n'est pas tenu à jour.

- Statut : **Remplacée par ADR-0135**
- Date : 2026-07-22

## Context

MORPHEUS comprend des spécifications et des intentions qui viennent de plusieurs sources : OpenSpec, Markdown
structuré, formats futurs ou systèmes externes. OpenSpec est le premier provider réel, ce qui crée un risque
classique : concevoir les objets du domaine en reproduisant la structure du premier format disponible. Ce serait
rapide au début, mais MORPHEUS deviendrait progressivement la façade d'un outil particulier et perdrait son
indépendance, sa capacité à intégrer d'autres sources, la stabilité de ses contrats publics et sa valeur propre de
moteur d'intelligence.

La question est donc : comment exploiter profondément un provider de référence sans que ce provider définisse le
langage métier et les frontières du moteur ?

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

MORPHEUS possède son propre modèle de domaine. Les concepts publics sont définis à partir des cas d'usage MORPHEUS,
pas à partir des fichiers ou classes d'un provider. Le domaine porte notamment :

```text
ProjectSpecification   Specification        Requirement         Scenario
ChangeProposal         Constraint           DesignDecision      AcceptanceCriterion
ImplementationTask     SpecificationVersion Evidence            TraceabilityLink
ExternalReference
```

Les providers sont placés derrière une abstraction, le port `SpecificationProvider` de `morpheus-application`. Le flux
de lecture est obligatoire :

```text
Format externe -> Provider / Adapter -> Ingestion / Normalisation -> Domaine MORPHEUS
```

Un flux d'écriture vers un format externe passe, lui aussi, par un adaptateur explicite.

Les contraintes du statut sont les six invariants suivants ; ils s'imposent au provider OpenSpec, à
`SpecificationKnowledgeStore`, à la CLI, au MCP, à l'API et aux intégrations MINOS/NEXUS/JARVIS :

1. Aucun type propre à un provider n'apparaît dans les interfaces du domaine.
2. Aucun chemin de fichier spécifique à un provider ne devient une identité métier universelle sans justification.
3. Les statuts propres à un provider sont mappés vers des concepts MORPHEUS ou conservés comme métadonnées externes.
4. Les identifiants externes peuvent être conservés mais ne constituent pas automatiquement l'identité MORPHEUS.
5. Un test du domaine s'exécute sans OpenSpec ni autre provider installé.
6. Un nouveau provider s'ajoute sans modifier les cas d'usage publics existants, sauf extension légitime du domaine.

Aucune décision ultérieure ne contourne cette frontière sans nouvel ADR explicite.

## Do's and Don'ts

### Do

- Définir un nouveau concept public à partir d'un cas d'usage MORPHEUS, dans `morpheus-domain`.
- Mapper un statut provider vers un concept MORPHEUS, ou le conserver comme métadonnée externe.
- Conserver `Evidence`, `ExternalReference` et des métadonnées provider contrôlées pour ne pas perdre d'information.
- Faire passer toute lecture d'un format externe par un adaptateur qui implémente un port de `morpheus-application`.

### Don't

- Ne pas exposer un type, un nom d'état ou un chemin de fichier d'un provider dans une interface du domaine.
- Ne pas faire dépendre `com.morpheus.domain..` d'un paquet `provider..`, `store..`, `cli..`, `mcp..`, `api..` ou
  `integration..`.
- Ne pas utiliser un identifiant externe comme identité MORPHEUS.
- Ne pas modifier un cas d'usage public existant pour accueillir un nouveau provider, hors extension légitime du
  domaine.

## Consequences

### Positives

- Découplage fort et stabilité des contrats publics.
- Domaine testable sans provider réel.
- Plusieurs providers possibles, chacun évoluant indépendamment.
- Meilleure séparation des responsabilités, et un modèle qui peut s'enrichir au-delà des limites du format source.

### Négatives

- Coût de mapping et duplication apparente de certaines structures.
- Règles de normalisation à définir, et gestion plus complexe des concepts non représentables.
- Tests de conformité nécessaires par provider.

Ces coûts sont acceptés parce qu'ils protègent une frontière structurante.

### Risques

- **Abstraction trop générique** : le domaine perdrait des informations importantes. Réponse : conserver `Evidence`,
  `ExternalReference` et des métadonnées provider contrôlées.
- **Sur-conception** : le modèle deviendrait théorique. Réponse : valider chaque concept avec au moins un cas réel.
- **Fuite indirecte de concepts provider** : sans type technique, des noms ou des états peuvent encore reproduire un
  provider. Réponse : revue d'architecture et test avec un second provider.

## Compliance and Enforcement

- Règle Archgate `ARCH-0001-morpheus-owned-domain.rules.ts` : `domain-names-no-provider`.
- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/LayerDependencyTest.java`
  (`domainMustNotDependOnAdapters`, `applicationMustNotDependOnAdapters`) : le domaine et l'application ne dépendent
  d'aucun adaptateur.
- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/ProviderAntiLockInTest.java` : les providers
  OpenSpec, synthétique et Markdown structuré implémentent le même port de lecture, un consommateur neutre lit les trois
  formats, et des clés externes identiques restent scopées par provider.
- Revue : absence de nom ou d'état provider dans les interfaces du domaine (invariants 2 et 3), et non-modification
  des cas d'usage publics à l'ajout d'un provider (invariant 6).

## References

- [ADR-0002](../ARCH-0002-openspec-reference-provider.md) — OpenSpec premier provider, sans verrouillage.
- [ADR-0003](../DATA-0003-specification-knowledge-store.md) — persistance derrière `SpecificationKnowledgeStore`.
- [ADR-0009](0009-stable-domain-identity.md) — identité, version, localisateur et référence externe distincts.
- [ADR-0029](0029-second-provider-anti-lockin-proof.md) — preuve anti-verrouillage par un second provider.
- [VALIDATION_C0](../../../project/validation/VALIDATION_C0.md) et
  [VALIDATION_M0](../../../project/validation/VALIDATION_M0.md).
