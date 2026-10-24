---
id: ARCH-0123
title: "Vues compactes typées et JSON canonique"
domain: architecture
rules: true
files: ["morpheus-application/pom.xml", "morpheus-application/src/main/java/**/*.java", "morpheus-domain/pom.xml", "morpheus-domain/src/main/java/**/*.java"]
---

# Vues compactes typées et JSON canonique

- Statut : **Acceptée**
- Date : 2026-10-24
- Remplace : ADR-0047, ADR-0058

## Context

Les lectures métier (ADR-0137), l'analyse de changement (ADR-0138) et le rapport qualité (ADR-0124) rendent des
objets applicatifs riches. Les scripts, les agents et les transports (CLI, MCP, HTTP) ont besoin d'une représentation
stable : mêmes champs, même ordre, mêmes octets pour une même réponse. MORPHEUS ne prend pas en charge le ranking, la
fusion multi-moteurs ni la compression par budget de jetons, qui relèvent de NEXUS (ADR-0075).

Les adaptateurs lisent et écrivent par ailleurs du JSON qui n'est pas une vue : corps de requête HTTP, enveloppes du
protocole MCP, réponses des pairs MINOS et NEXUS.

Options écartées :

- sérialiser directement les objets du domaine ou des stores : leur forme suit le modèle, pas le contrat exposé, et
  fuit des types techniques ;
- confier les vues à une bibliothèque JSON générique : l'ordre des champs et des clés et le traitement des valeurs
  absentes dépendraient de sa configuration ;
- persister le JSON des vues : la vue est une exposition, la vérité reste dans les stores.

## Decision

1. **Vues compactes typées.** Chaque vue exposée est un record de `morpheus-application`
   (`application.query.compact`, `application.analysis.compact`, `application.quality.compact`), construit par son
   service de projection (`CompactQueryViewService`, `CompactChangeAnalysisViewService`,
   `CompactQualityReportService`). Aucun objet de domaine ni de store n'est sérialisé directement : identités,
   `SourceLocator` et `Instant` (ISO-8601) sont convertis en chaînes, les enums en leur `name()`.
2. **Métadonnées de requête.** Toute vue compacte porte `QueryMetadata(schemaVersion, operation)` : `schemaVersion`
   strictement positif, fixé par une constante `SCHEMA_VERSION` du service de projection, et `operation` non vide
   (`find_requirements`, `trace_requirement`, `get_change_context`, `analyze_change`, `get_quality_report`). Une vue
   adossée à un snapshot porte aussi ses métadonnées (identifiant, projet, état, prédécesseur et révision source
   facultatifs, date de création). `find_requirements` porte le texte de recherche normalisé et la page (`offset`,
   `limit`, `totalMatches`, `hasMore`) ; la vue ne reçoit que `RequirementSearchPage`, qui conserve la
   `RequirementSearchQuery` normalisée ayant produit la page, de sorte qu'une page ne peut pas être présentée avec un
   autre texte de recherche.
3. **Identité et temporalité visibles.** Une exigence compacte garde son identité stable, l'identifiant de
   l'occurrence versionnée, la version de spécification, l'état temporel, la spécification, la clé facultative, le
   titre, l'énoncé et la provenance : la compacité ne confond ni `DomainIdentity` et `EntityVersionId`, ni version de
   spécification et snapshot. Les vues de changement, contrainte, décision et tâche gardent identités, rattachements
   métier, contenu utile et provenance.
4. **Provenance et evidence.** La provenance compacte garde fournisseur, version de fournisseur facultative, source,
   identifiant externe et révision source facultatifs, et `evidenceId`. Une vue n'inclut que les evidence
   effectivement référencées par ce qu'elle expose, dédupliquées et triées par identifiant. Une evidence référencée
   absente de la projection laisse la donnée visible et produit l'avertissement `EVIDENCE_NOT_FOUND` ; l'absence de
   toute projection reste un échec (ADR-0137).
5. **Trace compacte.** Le graphe expose des nœuds (type, identité) et des liens (identifiant, source, relation,
   cible, origine, résolution, identifiants d'evidence triés), sans nouvelle traversée ni arête ajoutée ; la troncature
   du sous-graphe est rendue par `truncated` et `truncationReason` (ADR-0108). Une référence externe compacte garde
   l'identité portée par le lien, même quand la `ExternalReference` n'existe pas (`BROKEN_REFERENCE`). Dans l'analyse
   de changement, chaque étape de chemin (`PathStepView`) porte le sens de traversée (`from`, `into`) et le lien tel
   que persisté, indispensable pour une traversée entrante.
6. **Avertissements structurés.** Un avertissement est un objet (code, sévérité, message, détails), jamais du texte
   libre. Les vues de requête utilisent `CompactWarningCode` et n'admettent que la sévérité `WARNING`
   (`WarningView`) ; l'analyse de changement a son propre DTO (`AnalysisWarningView`), qui admet `INFO` et `WARNING`
   et une exigence facultative. Les avertissements ne dérivent que de faits observables (changement absent, cible
   `AFFECTS` sans occurrence `CURRENT`, disponibilité d'une référence externe, evidence absente), sont dédupliqués et
   ordonnés ; une référence externe `REFERENCE_RESOLVED` n'en produit aucun.
7. **`CanonicalJsonSerializer` est le seul sérialiseur des vues.** Il accepte record, `String`, `char`, `URI`,
   booléen, nombre fini, enum, `Optional`, collection, tableau, `Map` à clés `String` et `null`, et refuse tout autre
   type, une clé non `String`, `NaN` et l'infini. Contrat : champs d'un record dans l'ordre de déclaration, clés de map
   en ordre lexicographique, collections dans l'ordre que fixe le DTO, `Optional` vide rendu `null`, enum par
   `name()`, aucune mise en forme, échappement strict des guillemets, barres obliques inverses, caractères de
   contrôle et demi-codets de substitution. Même DTO, même chaîne ; même chaîne, mêmes octets UTF-8. La sérialisation
   est bornée : `toUtf8` applique un plafond d'octets UTF-8 (`CanonicalJsonSerializer.DEFAULT_MAX_UTF8_BYTES` par
   défaut) pendant le rendu. Aucune projection ne génère d'horodatage ni d'empreinte d'exécution ; aucun JSON de vue
   n'est persisté.
8. **Réflexion bornée du sérialiseur.** `CanonicalJsonSerializer` lit les composants d'un record par réflexion
   (`Class.getRecordComponents()` et l'accesseur de chaque `RecordComponent`) et les éléments d'un tableau par
   `java.lang.reflect.Array`. Il n'instancie aucun type, ne charge aucune classe par son nom et n'interprète jamais un
   objet métier.
9. **Jackson 3 pour le JSON des adaptateurs.** Le JSON qui n'est pas une vue est lu et écrit par Jackson 3
   (`tools.jackson`, version gérée par le `pom.xml` racine), dans les adaptateurs seulement : `morpheus-api`, les
   passerelles `morpheus-integration-minos` et `morpheus-integration-nexus`, et, par `mcp-json-jackson3`,
   `morpheus-mcp` et `morpheus-mcp-transport`. `morpheus-domain` et `morpheus-application` ne dépendent pas de
   Jackson. Un corps de requête HTTP est décodé par le seul `JsonMapper` de `morpheus-api`,
   `MorpheusHttpRequestDecoder`, avec `FAIL_ON_UNKNOWN_PROPERTIES` et `FAIL_ON_TRAILING_TOKENS`. Aucune source de
   production n'appelle `activateDefaultTyping(` ni `enableDefaultTyping(`.

## Do's and Don'ts

### Do

- Exposer une réponse par un record compact typé construit par un service de projection.
- Porter `QueryMetadata(schemaVersion, operation)` sur toute vue compacte.
- Sérialiser toute vue par `CanonicalJsonSerializer`, avec un plafond d'octets pour une charge de transport.
- Trier toute collection d'une vue par identité avant de la sérialiser.
- Rendre une donnée incomplète par un avertissement structuré plutôt que par son retrait.
- Décoder un corps de requête HTTP par `MorpheusHttpRequestDecoder`.

### Don't

- Ne jamais sérialiser un objet de domaine ou de store directement.
- Ne jamais introduire Jackson ni une autre bibliothèque JSON dans `morpheus-domain` ou `morpheus-application`.
- Ne jamais construire un autre `JsonMapper` dans `morpheus-api`.
- Ne jamais appeler `activateDefaultTyping(` ou `enableDefaultTyping(`.
- Ne jamais générer d'horodatage ou d'empreinte pendant une projection.
- Ne jamais persister le JSON d'une vue.
- Ne jamais classer, fusionner ou compresser une réponse par budget de jetons.

## Consequences

### Positives

- Une même réponse donne les mêmes octets sur tous les transports et après réouverture de la base : les sorties se
  comparent, se mettent en cache et se testent à l'octet.
- Le contrat exposé est découplé du modèle : un champ interne ne fuit pas.
- La couche application reste sans dépendance JSON tierce.

### Négatives

- Chaque nouvelle surface exige ses DTO et sa projection, en plus du résultat riche.
- Deux familles de JSON coexistent (vues canoniques, JSON d'adaptateur Jackson), avec deux jeux de règles.

### Risques

- Un DTO qui contiendrait une `Map` ou une collection non triée en amont garderait l'ordre de sa source pour une
  collection : seul l'ordre des clés de map est imposé par le sérialiseur.
- Les passerelles MINOS et NEXUS construisent leur `JsonMapper` avec `FAIL_ON_UNKNOWN_PROPERTIES` désactivé pour lire
  la réponse d'un pair configuré : la règle stricte de la décision 9 vaut pour les corps HTTP reçus, pas pour elles.

## Compliance and Enforcement

- Règle Archgate `ARCH-0123-vues-compactes-et-json-canonique.rules.ts` : `inner-layers-have-no-jackson`.
- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/CanonicalJsonSerializerTest.java` : ordre,
  tri des clés, valeurs nulles, échappement, refus explicites.
- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/CompactQueryViewContractTest.java` et
  `RequirementQueryMetadataRetentionTest.java` (même répertoire) : métadonnées, page et requête retenue, identité et
  temporalité, provenance et evidence, avertissements, JSON identique à l'octet, parité mémoire/SQLite.
- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/ChangeAnalysisContractTest.java` : vue
  `analyze_change`, chemins `from`/`into` avec lien persisté, JSON et UTF-8 identiques après réouverture SQLite.
- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/AggregateQualityReportContractTest.java` :
  vue `get_quality_report` et JSON déterministe.
- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/TraceabilityTruncationContractTest.java` :
  troncature rendue dans les vues de trace.
- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/d2/D2RepositoryHardeningArchitectureTest.java`
  (`untrustedJsonRemainsStrictAndDefaultTypingIsNotActivated`) : features strictes du décodeur, présence de
  `JacksonSecurityRegressionTest`, et absence de `activateDefaultTyping(` / `enableDefaultTyping(` dans toute source
  de production ; `dependencyAndQualityBaselineIsPinned` épingle la version de Jackson du `pom.xml`.
- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/HttpRoutesTransportBoundaryArchitectureTest.java`
  (`everyApiJsonMapperIsStrictAboutUnknownPropertiesAndTrailingTokens`) : tout `JsonMapper.builder()` de
  `morpheus-api` active les deux features.
- `morpheus-api/src/test/java/com/morpheus/api/JacksonSecurityRegressionTest.java` : régression de désérialisation.
- Revue : l'absence de Jackson dans `morpheus-domain` et `morpheus-application` (seul `application.reasoning` est
  tenu par `m27/ReasoningPlatformArchitectureTest`), la présence de `QueryMetadata` sur toute nouvelle vue et le
  cantonnement de la réflexion au sérialiseur ne sont vérifiés par aucun test transverse.

## References

- [ADR-0004](SEC-0004-local-first-no-llm-core.md)
- [ADR-0138](ARCH-0138-analyse-de-changement-derivee-et-bornee.md)
- [ADR-0138](ARCH-0138-analyse-de-changement-derivee-et-bornee.md)
- [ADR-0062](ARCH-0062-official-java-mcp-sdk-and-native-stdio.md)
- [ADR-0075](ARCH-0075-morpheus-intent-vs-nexus-technical-context.md)
- [ADR-0108](BE-0108-a-response-says-what-it-could-not-observe.md)
- [ADR-0137](ARCH-0137-lectures-et-recherche-sur-le-snapshot-publie.md)
- [ADR-0124](ARCH-0124-qualite-findings-et-faits-tri-etat.md)
- Preuves de jalon : [VALIDATION_M5](../../project/validation/VALIDATION_M5.md),
  [VALIDATION_M8](../../project/validation/VALIDATION_M8.md)
