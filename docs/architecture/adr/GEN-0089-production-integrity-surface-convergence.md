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

Principes que la décision tient :

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

La CI requise et son workflow générique relèvent d'ADR-0133. JaCoCo instrumente les modules Java ; un seuil de
couverture n'affirme pas une couverture suffisante par domaine. La convention de comptage de la présence des tests, la
comparaison faite par chaque validateur et la gouvernance des deux clés sont celles de `.claude/rules/testing.md`
(« Deux mesures de présence : déclarées et générées »).

Un chemin de commande CLI est la suite de mots qui sélectionne un comportement avant toute option (`version`,
`portfolio list`, `policy pack create`). Parce que `MorpheusMain.run` refuse un chemin non déclaré avant d'interroger
une sous-commande, une action ajoutée au seul `switch` d'une sous-commande n'est pas atteignable tant qu'elle n'est pas
déclarée. Le côté manifeste se vérifie en lisant le texte, un `.tsv` ne se lisant pas autrement ; le côté CLI se
vérifie par exécution, chaque chemin déclaré lancé sans option contre un store vide. Un manifeste de mise à jour `file:`
sert aux tests, à la qualification packagée et aux diagnostics explicites.

Détail des règles de convergence et de livraison que la décision résume :

- `ProductMetadata.version()` rend la version d'implémentation empaquetée, sinon la propriété
  `morpheus.project.version`, sinon le marqueur explicite `development`.
- Colonne `http` : les exclusions motivées sont les routes du seul serveur remote et les sondes d'exploitation.
  Colonne `mcp` : chaque outil servi apparaît dans exactement une ligne.
- Colonne `cli` : une cellule qui n'est pas un sentinelle nomme un chemin déclaré par les mots qui précèdent sa
  première option. Les exclusions sont les points d'entrée du processus (`help`, `paths`, les lanceurs `api` et `mcp`)
  et les formes dont la capacité porte une autre ligne ; une exclusion pour un chemin non déclaré, ou déjà nommé par
  une ligne, est refusée. L'aide documente chaque chemin déclaré, hors les formes par lesquelles on l'atteint (`help`,
  `reason help`). Un chemin interrompu avant un chemin déclaré va à sa commande, qui dit l'action qui manque.
- Les tests de cohérence documentaire vérifient version, manifeste et références documentaires critiques.
- Le manifeste de provenance contient au minimum version, commit, référence source et environnement de build ; une
  signature cryptographique n'est exigée que lorsqu'une identité de signature réelle est configurée. Le score
  `failBuildOnCVSS` du POM racine vaut 7.0.
- La découverte de mise à jour valide version, channel, URI d'artefact et SHA-256, puis compare la version cible à la
  version courante. L'URI d'artefact d'un manifeste `file:` n'est jamais en `http:` ni dans un autre schéma que
  `file:` ou `https:`.

## Decision

1. **Tests** : seuils selon ADR-0167, valeurs dans `config/m21-quality-ratchets.properties`, jamais recopiées ;
   exécutions déclarées et générées, chacune sous sa clé, écrites par `validate-m21.*` et `validate-d2.*` dans
   `validation-summary.txt` ; pas d'`allowZeroInvocations = true`.
2. **Version** : `ProductMetadata.version()` seule ; CLI, MCP et HTTP y délèguent.
3. **Manifeste** : `contracts/public-surfaces.tsv`, seul contrat machine des capacités (intention READ / WRITE),
   que la documentation explique sans redéfinir ; une absence est un sentinelle, qui ne cache rien de servi. Chaque
   colonne est comparée dans les deux sens à son transport (`http` : `MorpheusHttpRouteTable` et OpenAPI ; `mcp` :
   outils servis ; `cli` : `CliCommandPaths`) ; une surface servie a une ligne exactement, ou une exclusion motivée.
4. **CLI** : `MorpheusMain.run` refuse un chemin non déclaré, laisse à sa commande un chemin interrompu (`portfolio`,
   `policy pack`) ; une exclusion vise un chemin déclaré non nommé ; l'aide documente chaque chemin déclaré.
5. **Approvisionnement** : SBOM CycloneDX ; release avec SHA-256 et provenance ; checksum jamais dit signature,
   signature non simulée. Actions par SHA de 40 caractères ; OWASP au `failBuildOnCVSS` du POM ; CodeQL
   `security-extended` (`.github/workflows/codeql.yml`) ; `NVD_API_KEY` par
   `-DnvdApiKeyEnvironmentVariable=NVD_API_KEY`, événements de confiance seuls, aucun secret sur `pull_request`.
6. **Mise à jour** : read-only, sur demande, URI fournie, jamais au démarrage ; rien téléchargé, installé, remplacé. Refus avant réseau de `http:` (« insecure update manifest scheme ») et de tout schéma hors `file:` /
   `https:` (« unsupported update manifest scheme »). `https:` exige `artifactUri` et `attestationUri` en `https:`
   (`UpdateManifest.requireRemoteDiscoveryContract`) ; `file:` admis sans attestation ni artefact installable.

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

- [ADR-0167](GEN-0167-deux-echelles-de-couverture-un-seuil-par-module-tenu-dans-une-bande-et-le-credit-emprunte.md) — deux échelles de couverture, une population.
- [ADR-0133](GEN-0133-toolchain-java-21-et-maven-wrapper.md) — toolchain de build et CI requise.
- [ADR-0130](BE-0130-contrat-des-options-de-la-cli.md) — contrat des options de la CLI.
- [ADR-0088](GEN-0088-product-release-installation-and-persistent-data-separation.md) — release, installation et
  données persistantes.
- [ADR-0004](SEC-0004-local-first-no-llm-core.md) — local-first.
- [VALIDATION_M21](../../project/validation/VALIDATION_M21.md) — preuve du jalon M21.
- `contracts/public-surfaces.tsv` — manifeste de convergence.
- `config/m21-quality-ratchets.properties` — ratchets de couverture et de présence.
- [Intégrité du produit](../../user/PRODUCT_INTEGRITY.md) — découverte de mise à jour côté utilisateur.
