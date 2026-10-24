---
id: ARCH-0128
title: "MINOS et NEXUS sont des pairs optionnels, lancés à la demande par MCP STDIO"
domain: architecture
rules: true
files: ["distribution/build-portable.ps1", "distribution/build-portable.sh"]
---

# MINOS et NEXUS sont des pairs optionnels, lancés à la demande par MCP STDIO

- Statut : **Acceptée**
- Date : 2026-10-24
- Remplace : ADR-0069, ADR-0072, ADR-0073, ADR-0076

## Context

MORPHEUS s'enrichit de deux moteurs voisins : MINOS, qui résout des références de code, et NEXUS, qui construit un
contexte technique. Chacun expose un serveur MCP STDIO. Une dépendance binaire à `com.minos.*` ou à `com.nexus.*`
couplerait les dépôts, leurs baselines Java et leurs cycles de livraison ; elle ferait aussi d'un moteur facultatif
une condition de démarrage de MORPHEUS.

Le lanceur Java d'un pair est un choix de sécurité : une commande nue (`java`) est résolue par le système dans le
`PATH` du processus MORPHEUS, où un autre exécutable de ce nom peut être placé en tête.

## Decision

1. **Processus externe, aucun import.** MORPHEUS parle à MINOS et à NEXUS uniquement par MCP STDIO
   inter-processus, au moyen du client MCP Java et du transport borné de `morpheus-mcp-transport`. Aucune classe de
   MORPHEUS n'importe `com.minos..` ni `com.nexus..`, et la distribution n'embarque aucune de leurs classes : elle
   embarque les adaptateurs (`morpheus-integration-minos`, `morpheus-integration-nexus`), jamais les moteurs ni leurs
   JAR. Les deux adaptateurs ne dépendent pas l'un de l'autre.
2. **Commande de lancement.**

   ```text
   MINOS : <lanceur Java> [-Dminos.home=<home>] -cp <copie vérifiée du JAR> com.minos.mcp.MinosMcpServer
   NEXUS : <lanceur Java> [-Dnexus.home=<home>] -jar <copie vérifiée du JAR>
   ```

   Le JAR d'un pair n'est lancé que s'il est épinglé par son SHA-256 et vérifié avant lancement (ADR-0129).
3. **Outils requis.** Après `initialize`, l'adaptateur liste les outils du pair et le déclare incompatible s'il en
   manque un : `minos_index_status` et `minos_find_symbols` pour MINOS (`MinosMcpCodeGateway`), `list_projects`,
   `build_context` et `explain_context` pour NEXUS (`NexusMcpContextGateway`).
4. **Lancement à la demande.** Aucun pair n'est démarré au démarrage de MORPHEUS. Un pair est lancé pour l'opération
   qui le demande (résolution d'une référence externe, statut de l'intégration, construction d'un contexte), puis
   fermé avec son processus.
5. **Indisponibilité non fatale.** Le statut d'une intégration est `DISABLED` quand aucun JAR n'est configuré (aucun
   resolver MINOS n'est alors enregistré), `INVALID` quand la configuration est refusée, `UNAVAILABLE` quand le pair ne
   démarre pas, ne répond pas à temps ou est incompatible, `AVAILABLE` sinon. Aucun de ces états n'empêche MORPHEUS de
   démarrer ni de servir ses autres surfaces.
6. **Configuration.** Chaque pair se règle par propriété Java ou variable d'environnement, la propriété l'emportant sur
   la variable, la variable sur le défaut : `morpheus.<pair>.jar` / `MORPHEUS_<PAIR>_JAR`,
   `morpheus.<pair>.jar.sha256` / `MORPHEUS_<PAIR>_JAR_SHA256`, `morpheus.<pair>.java` / `MORPHEUS_<PAIR>_JAVA`,
   `morpheus.<pair>.home` / `MORPHEUS_<PAIR>_HOME`, `morpheus.<pair>.timeoutSeconds` /
   `MORPHEUS_<PAIR>_TIMEOUT_SECONDS`, avec `<pair>` valant `minos` ou `nexus`. Les noms sont les constantes de
   `MinosIntegrationSettings` et `NexusIntegrationSettings`.
7. **Timeout borné.** Le timeout de requête vaut `DEFAULT_TIMEOUT_SECONDS` par défaut et doit être compris entre 1 et
   `MAX_TIMEOUT_SECONDS` secondes ; une valeur hors bornes rend la configuration invalide. Une opération entière est
   bornée par `PeerOperationDeadline` (ADR-0106).
8. **Lanceur Java en chemin absolu.** Par défaut, le lanceur est celui du runtime qui exécute MORPHEUS,
   `<java.home>/bin/java` ; si ce runtime n'en porte aucun et qu'un pair est configuré, l'intégration est invalide.
   Une valeur configurée doit être le chemin absolu d'un fichier exécutable ; un nom nu ou un chemin relatif rend
   l'intégration invalide et rien n'est lancé. Sous Windows, le suffixe `.exe` peut être omis. Un lien symbolique est
   résolu, pas refusé : le fichier réel est celui qui est lancé et celui que le statut local affiche. L'invariant est
   dans le constructeur compact de `MinosIntegrationSettings` et de `NexusIntegrationSettings`, qui le portent
   chacun.
9. **Environnement réduit.** Le pair ne reçoit pas l'environnement de MORPHEUS : `BoundedStdioClientTransport` le
   réduit à une allowlist de lancement (dont `PATH`), complétée des seules variables que MORPHEUS a explicitement
   configurées pour ce pair. Les variables sensibles, comme `MORPHEUS_SERVER_TLS_PASSWORD`, ne sont jamais
   transmises.
10. **Surfaces.** Les surfaces MINOS et NEXUS sont des lectures : capacités `external-reference.list`,
    `external-reference.resolve`, `integration.minos.status`, `augmented-context.requirement`,
    `augmented-context.change` et `integration.nexus.status` de `contracts/public-surfaces.tsv`, qui dit leur
    exposition sur chaque transport. Toutes les surfaces d'une capacité délèguent au même use case applicatif ; aucune
    ne réimplémente la résolution ni la construction du contexte, et aucune ne fait muter MINOS ou NEXUS.

## Do's and Don'ts

### Do

- Parler à MINOS et à NEXUS par MCP STDIO, dans un processus séparé.
- Vérifier la présence des outils requis avant le premier appel d'outil.
- Rendre `DISABLED`, `INVALID` ou `UNAVAILABLE` plutôt que faire échouer le démarrage ou une autre surface.
- Lancer un pair avec un lanceur Java en chemin absolu, `<java.home>/bin/java` par défaut.
- Lire la propriété Java avant la variable d'environnement, et la variable avant le défaut.
- Passer à la passerelle les seules variables d'environnement configurées pour le pair.

### Don't

- Ne pas importer `com.minos..` ou `com.nexus..`, ni embarquer leurs classes dans la distribution.
- Ne pas démarrer un pair au démarrage de MORPHEUS.
- Ne pas lancer un pair par une commande nue ou un chemin relatif que le `PATH` résoudrait.
- Ne pas transmettre implicitement tout l'environnement de MORPHEUS au pair.
- Ne pas exposer une surface MINOS ou NEXUS qui fasse muter le pair.
- Ne pas faire dépendre `morpheus-integration-minos` de `morpheus-integration-nexus`, ni l'inverse.

## Consequences

### Positives

- MORPHEUS démarre, se package et passe ses smokes sans MINOS ni NEXUS configurés.
- Les baselines Java et les cycles de livraison des moteurs restent indépendants.
- Un exécutable placé en tête du `PATH` ne peut pas être lancé comme pair.

### Négatives

- Chaque opération paie le démarrage d'un processus Java.
- Le runtime embarqué de la distribution est un Java réduit de la baseline de MORPHEUS : un pair compilé pour une
  version plus récente n'y démarre pas, et l'opérateur doit nommer un JDK de cette version.

### Risques

- Un pair configuré reste du code de confiance exécuté sous le compte MORPHEUS (ADR-0129) ; la terminaison de ses
  descendants est best-effort.
- `PATH` reste transmis au pair, qui peut en avoir besoin.

## Compliance and Enforcement

- Règle Archgate `ARCH-0128-pairs-minos-et-nexus-par-mcp-stdio.rules.ts` : `portable-image-refuses-peer-engines`.
- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/LayerDependencyTest.java`
  (`minosIntegrationMustNotDependOnOuterMorpheusAdaptersOrMinosImplementation`,
  `nexusIntegrationMustNotDependOnOuterMorpheusAdaptersOrNexusImplementation`) : aucune dépendance aux moteurs.
- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/AdapterSiblingArchitectureTest.java` : les deux
  adaptateurs ne dépendent pas l'un de l'autre.
- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/PeerLauncherArchitectureTest.java` : aucune des
  deux intégrations n'accepte un lanceur que le `PATH` résoudrait.
- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/ExternalCodeTrustBoundaryArchitectureTest.java`
  (`noMcpLaunchPathTakesThePeerEnvironmentFromTheSdkParameters`) et
  `morpheus-mcp-transport/src/test/java/com/morpheus/integration/mcp/BoundedStdioClientTransportTest.java`
  (`aPeerLaunchedWithoutExplicitEnvironmentReceivesOnlyTheLaunchAllowlist`) : environnement réduit à l'allowlist.
- `MinosMcpTransportIntegrationTest`, `MinosIntegrationSettingsTest`, `MinosPeerLauncherResolutionTest`,
  `MinosMcpOperationDeadlineTest` (`morpheus-integration-minos/src/test/java/com/morpheus/integration/minos/`) et leurs
  équivalents NEXUS (`morpheus-integration-nexus/src/test/java/com/morpheus/integration/nexus/`) : vrai échange STDIO,
  outils requis, configuration, lanceur, délais.
- `distribution/build-portable.ps1` et `distribution/build-portable.sh` : l'image refuse toute classe `com/minos/*` ou
  `com/nexus/*` et vérifie les classes des adaptateurs ; le smoke packagé tourne sans MINOS ni NEXUS configurés.
- Revue : lancement à la demande et absence de mutation par les surfaces.

## References

- [ADR-0007](ARCH-0007-cross-engine-integration.md) — intégration entre moteurs.
- [ADR-0062](ARCH-0062-official-java-mcp-sdk-and-native-stdio.md) — SDK MCP Java et STDIO.
- [ADR-0026](ARCH-0026-optional-external-reference-resolution.md) — résolution facultative des références externes.
- [ADR-0070](BE-0070-exact-minos-symbol-reference-and-revision.md) — référence exacte d'un symbole MINOS.
- [ADR-0074](BE-0074-explicit-nexus-project-mapping.md) — correspondance explicite des projets NEXUS.
- [ADR-0075](ARCH-0075-morpheus-intent-vs-nexus-technical-context.md) — intention MORPHEUS et contexte NEXUS.
- [ADR-0106](BE-0106-inbound-bound-is-defensive-outbound-bound-is-a-defect.md) — bornes des opérations vers un pair.
- [ADR-0129](SEC-0129-plugins-provider-code-de-confiance-hors-processus.md) — code externe de confiance, pin SHA-256.
- [ADR-0063](ARCH-0063-read-only-mcp-tool-contract.md) et [ADR-0066](ARCH-0066-versioned-http-api-contract.md) —
  contrats MCP et HTTP.
- [VALIDATION_M12](../../project/validation/VALIDATION_M12.md),
  [VALIDATION_M13](../../project/validation/VALIDATION_M13.md).
