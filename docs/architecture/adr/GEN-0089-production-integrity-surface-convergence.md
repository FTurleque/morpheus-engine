---
id: GEN-0089
title: "Production integrity and public-surface convergence"
domain: general
rules: false
---

# Production integrity and public-surface convergence

- Statut : **Acceptée**
- Date : 2026-07-27

## Context

MORPHEUS est publié et possède plusieurs adaptateurs publics — CLI, MCP STDIO et HTTP — ainsi qu'une chaîne de
packaging Windows / Linux. Le risque principal d'un produit publié est la divergence silencieuse : versions différentes
selon les transports, documentation dupliquée, CI liée à un jalon, qualité non mesurée, métadonnées de release non
vérifiables, ou découverte de mise à jour confondue avec une mutation automatique.

La production se renforce sans déplacer la source de vérité métier vers un service distant et sans introduire de
dépendance réseau cachée.

Options rejetées :

- **Déduire le contrat public de la documentation** : le texte humain n'est pas un contrat machine fiable.
- **Forcer exactement les mêmes commandes, routes et outils** : la parité porte sur l'intention et la sémantique, pas
  sur la forme du transport.
- **Vérifier les mises à jour au démarrage** : introduit une dépendance réseau cachée et rompt le caractère
  local-first.
- **Télécharger ou appliquer automatiquement une mise à jour trouvée** : une mutation de programme exige un flux séparé
  et explicitement autorisé.
- **Accepter un manifeste de mise à jour en `http:`** : un manifeste lu en clair n'offre aucune garantie d'intégrité de
  transport, alors que la découverte porte un SHA-256 et une référence d'attestation qu'il faut pouvoir croire. Le
  cantonner à la boucle locale n'a pas de besoin : les essais passent par `file:`.
- **Dériver les chemins de commande CLI des `case` des sous-commandes** : ils mêlent commandes, actions et noms
  d'option. **Faire de l'aide la source** : elle peut omettre une commande servie. **Une liste tenue par le seul test** :
  une seconde liste qui peut mentir comme le manifeste.

## Decision

### 1. CI durable et non liée aux jalons

La CI requise, son workflow générique et son rapport aux validateurs locaux relèvent d'ADR-0133.

### 2. Couverture mesurée et bornée

JaCoCo instrumente les modules Java. Les seuils de couverture, leurs deux échelles et leurs plafonds qualifiés relèvent
d'ADR-0104 ; leurs valeurs vivent dans `config/m21-quality-ratchets.properties` et ne se recopient pas ici. Un seuil ne
constitue pas une affirmation de couverture suffisante par domaine.

### 3. Présence des tests : deux mesures

Le ratchet de présence compte des exécutions telles que Surefire les rapporte, en deux comptes — exécutions déclarées
et exécutions générées — bornés chacun par sa propre clé de `config/m21-quality-ratchets.properties`. La convention de
comptage, la comparaison faite par chaque validateur et la gouvernance des deux clés sont celles de
`.claude/rules/testing.md` (« Deux mesures de présence : déclarées et générées »). `validate-m21.*` et `validate-d2.*`
écrivent les deux comptes dans `validation-summary.txt`, et aucune source génératrice n'admet
`allowZeroInvocations = true`.

### 4. Métadonnées produit convergentes

La version produit est résolue par une primitive unique, `ProductMetadata.version()` : la version d'implémentation
empaquetée, sinon la propriété `morpheus.project.version`, sinon le marqueur explicite `development`. CLI, MCP et HTTP
délèguent à `ProductMetadata` et n'entretiennent aucune version codée indépendamment.

### 5. Manifeste public de surfaces

`contracts/public-surfaces.tsv` est la source machine lisible des capacités publiques et de leur intention READ /
WRITE. Une capacité peut avoir des formes de transport différentes. Une absence sur un transport est déclarée par un
sentinelle explicite, jamais implicite.

Chaque colonne est comparée à ce que son transport sert, dans les deux sens :

- **`http`** : à `MorpheusHttpRouteTable` et aux opérations des documents OpenAPI ; toute route servie a sa ligne, hors
  les routes du seul serveur remote et les sondes d'exploitation, exclusions motivées dans le test ;
- **`mcp`** : aux outils que le serveur sert ; chaque outil servi apparaît dans exactement une ligne ;
- **`cli`** : à `CliCommandPaths`, la déclaration unique des chemins de commande. Un chemin est la suite de mots qui
  sélectionne un comportement avant toute option (`version`, `portfolio list`, `policy pack create`).
  `MorpheusMain.run` refuse comme commande inconnue tout chemin non déclaré, avant d'interroger une sous-commande : une
  action ajoutée au seul `switch` d'une sous-commande n'est pas atteignable tant qu'elle n'est pas déclarée. Un chemin
  interrompu avant un chemin déclaré (`portfolio`, `policy pack`) est laissé à sa commande, qui dit l'action qui manque.
  Toute cellule `cli` qui n'est pas un sentinelle nomme, par les mots qui précèdent sa première option, un chemin
  déclaré ; tout chemin déclaré est nommé par exactement une ligne ou par une exclusion écrite avec sa raison. Les
  exclusions sont les points d'entrée du processus (`help`, `paths`, les lanceurs `api` et `mcp`) et les formes dont la
  capacité porte une autre ligne ; une exclusion pour un chemin non déclaré, ou déjà nommé par une ligne, est refusée.
  Un sentinelle ne cache pas une commande servie. L'aide embarquée documente chaque chemin déclaré, hors les formes par
  lesquelles on l'atteint (`help`, `reason help`).

Le côté manifeste se vérifie en lisant le texte, un `.tsv` ne se lisant pas autrement ; le côté CLI se vérifie par
exécution : chaque chemin déclaré est lancé sans option contre un store vide et n'est pas refusé comme inconnu, un
chemin non déclaré l'est.

### 6. Documentation single-source-of-truth

La documentation humaine explique le manifeste mais ne redéfinit pas un second contrat normatif. Des tests de
cohérence vérifient version, manifeste et références documentaires critiques.

### 7. Chaîne d'approvisionnement vérifiable

Le build produit un SBOM CycloneDX. Les artefacts de release sont accompagnés de leur SHA-256 et d'un manifeste de
provenance contenant au minimum version, commit, référence source et environnement de build. Un checksum n'est jamais
présenté comme une signature. Une signature cryptographique n'est exigée que lorsqu'une identité de signature réelle
est configurée ; elle n'est jamais simulée.

La CI elle-même est une chaîne d'approvisionnement :

- toute action GitHub non locale est épinglée par un SHA de commit de 40 caractères, jamais par un tag mutable ;
- l'analyse OWASP Dependency-Check échoue à partir du score `failBuildOnCVSS` fixé dans le POM racine (7.0) ;
- CodeQL est versionné dans `.github/workflows/codeql.yml` et exécute les requêtes Java `security-extended` ;
- la clé `NVD_API_KEY` n'est lue que par variable d'environnement (`-DnvdApiKeyEnvironmentVariable=NVD_API_KEY`) et
  réservée aux événements de confiance ; le chemin `pull_request` n'utilise aucun secret du dépôt.

### 8. Découverte de mise à jour explicite et read-only

La découverte d'une version disponible est un service read-only, exécuté uniquement sur demande explicite avec une
URI de manifeste fournie par l'appelant. Aucun accès réseau n'est effectué au démarrage. Le service valide version,
channel, URI d'artefact et SHA-256, puis compare la version cible à la version courante. Il ne télécharge, n'installe
et ne remplace aucun binaire.

- Le service lit un manifeste `file:` ou `https:`. Un manifeste `http:` est refusé avant toute requête réseau
  (« insecure update manifest scheme »), tout autre schéma aussi (« unsupported update manifest scheme »).
- Un manifeste `https:` est accepté sous un contrat fail-closed : `artifactUri` en `https:`, `attestationUri` présent et
  en `https:` (`UpdateManifest.requireRemoteDiscoveryContract`).
- Un manifeste `file:` reste utilisable sans attestation, pour les tests, la qualification packagée et les diagnostics
  explicites. Il ne rend aucun artefact installable, et l'URI d'artefact qu'il annonce n'est jamais en `http:` ni dans
  un autre schéma que `file:` ou `https:`.

### Invariants

```text
surface parity != same transport shape
read surface != write capability
release metadata != runtime business state
update discovery != automatic update
security metadata != hidden network dependency
checksum != signature
CI evidence != business source of truth
local-first remains default
```

## Do's and Don'ts

### Do

- Lire tout seuil de couverture ou de présence dans `config/m21-quality-ratchets.properties`.
- Comparer le compte déclaré à `testsMinimum` et le compte généré à `generatedTestsMinimum`, chacun nommé dans son
  refus.
- Passer le flux de chaque `@TestFactory` par `GeneratedCases.nonEmpty`.
- Déléguer toute version exposée à `ProductMetadata`.
- Déclarer dans le même changement la ligne du manifeste, l'opération OpenAPI ou le chemin `CliCommandPaths` d'une
  surface qui bouge.
- Écrire un sentinelle explicite pour toute absence sur un transport.
- Accompagner chaque artefact de release de son SHA-256, du SBOM et d'un manifeste de provenance.
- Épingler toute action GitHub par un SHA de 40 caractères.
- Réserver `NVD_API_KEY` aux événements de confiance et la passer par variable d'environnement.
- Exiger un artefact et une attestation en `https:` pour un manifeste de mise à jour distant.

### Don't

- Ne pas recopier un seuil de couverture ou un minimum de tests hors de sa source.
- Ne pas comparer un compte de présence à l'autre clé ni à leur somme.
- Ne pas coder une version produit dans un adaptateur.
- Ne pas laisser une case vide dans le manifeste, ni un sentinelle devant une surface servie.
- Ne pas présenter un checksum comme une signature, ni simuler une signature.
- Ne pas utiliser de tag mutable `uses: <action>@v…`.
- Ne pas passer `-DnvdApiKey=${NVD_API_KEY}` en ligne de commande.
- Ne pas accéder au réseau au démarrage pour découvrir une mise à jour.
- Ne pas lire un manifeste de mise à jour en `http:`.
- Ne pas télécharger, installer ni remplacer un binaire depuis la découverte de mise à jour.

## Consequences

### Positives

- Un seul contrat machine identifie les capacités publiques, comparé à ce que chaque transport sert.
- La version ne peut pas diverger silencieusement entre CLI, MCP et HTTP.
- La qualité et la couverture sont mesurables.
- Les releases disposent d'un SBOM et d'une provenance vérifiable.
- La découverte de mise à jour ne crée aucune mutation implicite.
- Retirer une option de la CLI se lit sous la clé des exécutions générées, pas comme une suppression de tests.

### Négatives

- Temps de CI supérieur, à cause de la matrice Windows / Linux et de JaCoCo.
- Maintenance du manifeste lors de toute évolution publique.
- Une politique de signature exigera une identité cryptographique réelle et pourra nécessiter un ADR complémentaire.
- Une source de mise à jour servie seulement en `http:` est inutilisable.

### Risques

- **Garde CLI limité aux chemins** : une option renommée, une intention ou une note fausse, une commande qui échoue
  pour une autre raison que « inconnue » lui échappent ; les options sont tenues par l'aide et son test d'analyse.
  Un nom de commande n'est pas une capacité : la ligne du manifeste reste une décision d'écriture.
- **Baisse injustifiée de `generatedTestsMinimum`** : le build ne la distingue pas d'une baisse justifiée ; mitigé par
  la règle de gouvernance qui exige de la dire dans le lot.

## Compliance and Enforcement

- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/m21/ProductionIntegrityContractTest.java` :
  version à source unique, manifeste explicite et adossé aux adaptateurs, absence de point d'entrée de découverte de
  mise à jour au démarrage, documentation qui renvoie au contrat machine.
- `morpheus-architecture-tests/src/test/java/com/morpheus/api/PublicHttpRouteConvergenceTest.java`,
  `morpheus-architecture-tests/src/test/java/com/morpheus/mcp/PublicSurfaceManifestCoversEveryServedToolTest.java`,
  `morpheus-architecture-tests/src/test/java/com/morpheus/cli/PublicSurfaceManifestCoversEveryServedCliCommandTest.java` :
  convergence des colonnes `http`, `mcp` et `cli`.
- `morpheus-cli/src/test/java/com/morpheus/cli/CliCommandPathsTest.java` : chaque chemin déclaré est routé, un chemin
  non déclaré est refusé, l'aide documente chaque chemin.
- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/TestFactoryAssertsItsSourceTest.java` :
  fabriques assertées non vides, aucun `allowZeroInvocations = true`.
- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/m21/CoverageScaleSeparationTest.java` et
  `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/d2/D2RepositoryHardeningArchitectureTest.java` :
  lecture des clés de ratchet par les validateurs, versions et `failBuildOnCVSS` épinglés dans le POM, CodeQL
  `security-extended`, chemin `pull_request` sans mise à jour de confiance.
- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/d2/RepositoryTextHygieneContractTest.java`
  (`everyWorkflowActionIsPinnedByACommitSha`) : épinglage des actions par SHA.
- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/d2/DependencyCheckWorkflowContractTest.java` :
  usage de `NVD_API_KEY` par variable d'environnement.
- `morpheus-application/src/test/java/com/morpheus/application/product/ProductIntegrityTest.java` : `http:` et schémas
  non supportés refusés avant tout accès, artefact `http:` refusé même en local, contrat distant `https:` avec
  attestation, manifeste local sans attestation.
- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/m21/UpdateTrustBoundaryContractTest.java` : la
  découverte n'affirme pas une vérification d'éditeur.
- `scripts/validate-m21.ps1`, `scripts/validate-m21.sh`, `scripts/validate-d2.ps1`, `scripts/validate-d2.sh` : comptes
  déclaré et généré comparés chacun à sa clé.
- Revue : refus de `http:` plutôt que cantonnement à la boucle locale ; absence de signature simulée.

## References

- [ADR-0104](GEN-0104-two-coverage-scales-share-one-population.md) — deux échelles de couverture, une population.
- [ADR-0133](GEN-0133-toolchain-java-21-et-maven-wrapper.md) — toolchain de build et CI requise.
- [ADR-0130](BE-0130-contrat-des-options-de-la-cli.md) — contrat des options de la CLI.
- [ADR-0088](GEN-0088-product-release-installation-and-persistent-data-separation.md) — release, installation et
  données persistantes.
- [ADR-0004](SEC-0004-local-first-no-llm-core.md) — local-first.
- [VALIDATION_M21](../../project/validation/VALIDATION_M21.md) — preuve du jalon M21.
- `contracts/public-surfaces.tsv` — manifeste de convergence.
- `config/m21-quality-ratchets.properties` — ratchets de couverture et de présence.
- [Intégrité du produit](../../user/PRODUCT_INTEGRITY.md) — découverte de mise à jour côté utilisateur.
