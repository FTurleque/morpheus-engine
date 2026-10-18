# ADR-0089 — Production integrity and public-surface convergence

Statut : **Acceptée — M21**

Date : 27 juillet 2026

## Contexte

MORPHEUS 1.0.0 est publié. La baseline possède plusieurs adapters publics — CLI, MCP STDIO et HTTP — ainsi qu’une chaîne de packaging Windows/Linux. La croissance incrémentale M0→M20 a volontairement privilégié les vertical slices fonctionnelles. Après 1.0, le risque principal devient la divergence silencieuse : versions différentes selon les transports, documentation dupliquée, CI spécifique à un milestone, qualité non mesurée, métadonnées de release non vérifiables ou découverte de mise à jour confondue avec mutation automatique.

M21 doit renforcer la production sans déplacer la source de vérité métier vers un service distant et sans introduire de dépendance réseau cachée.

## Décision

### 1. CI durable et non liée aux milestones

Le dépôt porte un workflow générique `ci.yml` exécutant le reactor Maven complet sur Windows et Linux avec JDK 21. Les validateurs locaux restent supportés ; le workflow est une preuve reproductible supplémentaire et non un remplacement magique des invariants produit.

### 2. Couverture mesurée et bornée

JaCoCo instrumente les modules Java. M21 introduit des floors aggregate volontairement conservateurs : 25% lignes et 20% branches. Ces floors empêchent une perte d’instrumentation ou une chute massive ; ils ne constituent pas une affirmation de couverture suffisante par domaine.

### 3. Métadonnées produit convergentes

La version produit est résolue par une primitive application-level unique à partir des métadonnées de build, avec fallback de développement explicite. CLI, MCP et HTTP n’entretiennent plus de version codée indépendamment.

### 4. Manifeste public de surfaces

`contracts/public-surfaces.tsv` est la source machine lisible des capabilities publiques partagées et de leur intention READ/WRITE. Une capability peut avoir des formes de transport différentes. Une absence sur un transport doit être déclarée explicitement et ne peut plus être une divergence implicite.

### 5. Documentation single-source-of-truth

La documentation humaine explique le manifeste mais ne redéfinit pas un deuxième contrat normatif. Des tests de cohérence vérifient version, manifeste et références documentaires critiques.

### 6. Supply chain vérifiable

Le build produit un SBOM CycloneDX. Les artefacts release sont accompagnés de SHA-256 et d’un manifeste de provenance contenant au minimum version, commit, tag/source ref et environnement de build. Un checksum n’est jamais présenté comme une signature. Une signature cryptographique n’est exigée que lorsqu’une identité/clé de signature réelle est configurée ; elle ne doit jamais être simulée.

### 7. Découverte de mise à jour explicite et read-only

La découverte d’une version disponible est un service read-only. Elle n’est exécutée que sur demande explicite avec une URI de manifeste fournie par l’appelant. Aucun accès réseau n’est effectué au démarrage. Le service peut lire `file:`, `http:` ou `https:`. Il valide version, channel, URI d’artefact et SHA-256, puis compare la version cible à la version courante. Il ne télécharge, n’installe et ne remplace aucun binaire.

## Invariants

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

## Conséquences

Positives :

- un seul contrat machine identifie les capabilities publiques ;
- la version ne peut plus diverger silencieusement entre CLI/MCP/HTTP ;
- la qualité et la couverture deviennent mesurables ;
- le workflow n’a plus à être recréé à chaque milestone ;
- les releases disposent d’un SBOM et d’une provenance vérifiable ;
- la découverte d’update ne crée aucune mutation implicite.

Coûts :

- temps de CI supérieur à cause de la matrice Windows/Linux et de JaCoCo ;
- maintenance du manifeste de surfaces lors de toute évolution publique ;
- une future politique de signature devra fournir une vraie identité cryptographique et pourra nécessiter une ADR complémentaire.

## Alternatives rejetées

### Un workflow par milestone

Rejeté : produit de la dette temporaire et rend la CI non durable.

### Déduire automatiquement le contrat public depuis la documentation

Rejeté : le texte humain n’est pas un contrat machine fiable.

### Forcer exactement les mêmes commandes/routes/outils

Rejeté : la parité porte sur l’intention et la sémantique, pas sur la forme du transport.

### Vérifier les updates automatiquement au démarrage

Rejeté : introduit une dépendance réseau cachée et rompt le caractère local-first.

### Télécharger/appliquer automatiquement une mise à jour trouvée

Rejeté : `update discovery != automatic update` ; une mutation de programme exige un flux séparé et explicitement autorisé.

## Validation d’acceptation

Les conditions d’acceptation ont été démontrées sur le même head exécutable `239d99657fbf193761767f382489dd637e642fe9` :

- reactor complet Windows + Linux : **14/14 SUCCESS** ;
- tests : **473/473 PASS** sur les gates M21 ;
- architecture : **187/187 PASS** ;
- JaCoCo : **46,2800 % lignes / 41,2734 % branches Windows**, **46,2430 % lignes / 41,2734 % branches Linux** ;
- convergence CLI/MCP/HTTP et cohérence documentaire : **PASS** ;
- CycloneDX JSON/XML + provenance : **PASS** ;
- packaging `jpackage` + portable Windows/Linux : **PASS** ;
- update discovery explicite `file:`/HTTP : **PASS** ;
- aucune vérification réseau implicite au démarrage : couverte par contrat/tests ;
- `postGateExecutableDelta=NONE` sur Windows et Linux.

Preuve : [`../../project/validation/VALIDATION_M21.md`](../../project/validation/VALIDATION_M21.md).

## Portée du manifeste (clarification post-audit 1.2.1)

Un audit de finalisation 1.2.1 a relevé que `contracts/public-surfaces.tsv` ne couvre que les
capabilities introduites à partir de M21 ; les surfaces antérieures (M0–M20 : specifications,
requirements, changes, constraints, decisions, tasks, sync, augmented-context, jarvis-
orchestration, external-reference, controlled-lifecycle) sont exposées de façon symétrique sur
CLI/MCP/HTTP mais n'ont pas de ligne dans le manifeste — elles restent documentées par
`docs/developer/reference/openapi/morpheus-v1.yaml` et le code des trois adapters plutôt que par le TSV.

Ceci est une décision de portée assumée, pas un oubli à corriger a posteriori : le manifeste a
été introduit après ces surfaces précisément pour discipliner la croissance *future*, et
`ProductionIntegrityContractTest` ne prétend pas à une couverture exhaustive rétroactive
(`entries.size() >= 6`, pas un compte exact). Backfiller ces lignes resterait possible si un
besoin de convergence machine-lisible apparaissait pour ces surfaces historiques, mais n'est pas
requis par cette ADR.


## Amendement du 18 octobre 2026 — la présence des tests a deux mesures

Le ratchet de présence compte des exécutions telles que Surefire les rapporte. Un total unique répond pourtant à deux
questions : des tests ont-ils été supprimés, et la matrice d'options de la CLI a-t-elle changé ? Treize fabriques de
tests (`@TestFactory`), dans trois fichiers de `morpheus-cli`, produisent plus d'un tiers des exécutions : retirer une
option d'une commande retire plus d'exécutions que la marge du ratchet n'en contient et casse le build comme si des tests
avaient été supprimés, et en ajouter une relève le compte comme si des tests avaient été écrits (constat AUD-TST-08 de
l'audit du 9 octobre 2026).

### Décision

1. **Deux comptes, définis par l'origine d'une exécution.** Une exécution *déclarée* est l'unique exécution d'une
   méthode de test déclarée. Une exécution *générée* vient d'une fabrique ou de toute source qui produit plusieurs
   exécutions par méthode (`@ParameterizedTest`, `@RepeatedTest`, `@TestTemplate`). Surefire nomme chaque invocation d'une
   telle source `méthode()[N]` et une méthode déclarée par son nom nu (mesuré sur les rapports de Windows et de Linux, et
   sur un projet minimal pour les trois autres annotations) : les validateurs lisent ce nom, si bien qu'un test
   paramétré introduit plus tard est compté comme généré sans qu'on touche aux validateurs. Les deux comptes ont pour
   somme le total Surefire.
2. **Deux clés dans `config/m21-quality-ratchets.properties`.** `testsMinimum` garde son nom et borne les exécutions
   déclarées ; `generatedTestsMinimum` borne les exécutions générées. `validate-m21.*` et `validate-d2.*` comparent
   chaque compte à sa propre clé, nomment la clé dans le refus, n'en comparent aucun à l'autre clé ni à leur somme, et
   écrivent les deux comptes dans `validation-summary.txt`. `validate-m22.*` à `validate-m27.*` comparent le compte des
   exécutions déclarées à `testsMinimum`. `architectureTestsMinimum` borne les exécutions déclarées du module
   d'architecture : une fabrique qui y serait ajoutée est comptée sous la clé des exécutions générées.
3. **Le plancher des exécutions déclarées ne fait que monter ; celui des exécutions générées suit la matrice
   d'options.** Le lot qui retire une option ou une commande abaisse `generatedTestsMinimum` et le dit. Aucun test ne
   distingue une réduction justifiée d'une réduction injustifiée : la règle est de gouvernance (`rules/testing.md`) ;
   ce que garantit le build est que la réduction est refusée sous son propre nom.
4. **Une fabrique qui ne génère rien échoue.** Chaque `@TestFactory` passe son flux par `GeneratedCases.nonEmpty`, qui
   échoue en nommant la fabrique ; `TestFactoryAssertsItsSourceTest` refuse une fabrique qui ne l'asserte pas (assertion
   textuelle sur `src/test/java` : ArchUnit ne voit pas ces sources, ADR-0103). JUnit refuse déjà un test paramétré, répété
   ou à gabarit sans invocation ; la même règle refuse `allowZeroInvocations = true`.
5. **Le rebasage n'abaisse rien.** À la décision, `testsMinimum` est ramené du total qu'il bornait au seul compte des
   exécutions déclarées, et la somme des deux clés n'est pas inférieure au minimum unique qu'elles remplacent : ce qui
   était exigé l'est encore. Le nombre écrit à côté de `testsMinimum` baisse parce qu'il mesure moins.

### Ce que cet amendement ne décide pas

Les valeurs : elles vivent dans le fichier de ratchets et ne se recopient pas ici (`rules/meta.md`). Il ne réduit ni les
fabriques ni leur matrice, et ne pose aucun plancher sur le nombre d'annotations des sources, qui n'est pas ce qui
s'exécute.
