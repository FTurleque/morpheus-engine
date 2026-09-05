# Production Integrity — M21

M21 transforme les garanties de production de MORPHEUS 1.x en gates durables et reproductibles.

## Autorités

```text
Build/version          pom.xml + ProductMetadata
Public surfaces        contracts/public-surfaces.tsv
Architecture           morpheus-architecture-tests
Release packaging      distribution/** + .github/workflows/release.yml
M21 gate               config/m21-quality-ratchets.properties + scripts/validate-m21.*
CI durable             .github/workflows/ci.yml
SCA durable            .github/workflows/security.yml
SAST durable           .github/workflows/codeql.yml
Preuve finale          docs/validation/VALIDATION_M21.md
```

La documentation humaine explique ces contrats ; elle ne doit pas devenir une deuxième source normative divergente.

## Quality gates M21

Baseline active :

```text
Tests              >= 1300 PASS
Architecture       >= 335 PASS
Reactor            18/18 SUCCESS
Windows            PASS
Linux              PASS
JaCoCo lines        >= 54.5 % aggregate
JaCoCo branches     >= 47.7 % aggregate
Changed lines       >= 80 %
Changed branches    >= 70 %
```

La baseline qualifiée qui borne les ratchets actifs est enregistrée dans `CoverageQualityGateTest` : **52,6971 % de lignes** et **45,7250 % de branches**, mesurées exact-head sous Linux après #230. Les seuils de `config/m21-quality-ratchets.properties` restent volontairement légèrement sous cette mesure afin de former des ratchets stables, et `CoverageQualityGateTest` refuse tout ratchet qui la dépasserait. La qualification de la PR #187 sur `75768168ce552d97ede15a5fe4aa3979993ee108` (871 tests, 269 tests d’architecture, 51,3447 % de lignes sous Linux, 51,3530 % sous Windows, 43,9379 % de branches) est conservée comme trace d’audit et ne décrit plus les seuils actifs. Le changed-code gate complète les lignes par les branches situées sur les lignes exécutables modifiées.

Les quatre ratchets durables sont définis une seule fois dans `config/m21-quality-ratchets.properties`. Les validateurs Linux/Windows et `CoverageQualityGateTest` lisent cette configuration au lieu de recopier les valeurs.

## Reproducible-build hygiene

Le build fixe `project.build.outputTimestamp`, centralise les versions de plugins structurants et écrit les métadonnées de version dans les manifests JAR. Maven Enforcer impose Maven `>= 3.9.16` et `< 4.0.0`, la ligne JDK **21 uniquement** (`>= 21` et `< 22`) ainsi que la convergence des dépendances transitives. Une release reste construite à partir d’un ref/tag exact conformément au contrat M20.

Les métadonnées de release ne sont pas un état métier :

```text
release metadata != runtime business state
```

## Frontière HTTP des corps de requête

Toutes les routes HTTP étendues utilisent `HttpRequestBodyReader`, qui délègue à `TimedBoundedInputReader` et applique la même politique que la frontière HTTP principale :

```text
request body max size     65 536 bytes
request body read timeout 15 seconds
```

Les contextes Query/Saved Views/Export, Policy, Policy Management et Reasoning ne doivent pas effectuer de `readNBytes(...)` direct sur `HttpExchange.getRequestBody()`. Cette règle empêche un client local lent ou défaillant de conserver indéfiniment une lecture de body ouverte et est verrouillée par un contrat de repository.

## Supply chain et provenance de release

Le build M21 génère un SBOM CycloneDX JSON/XML et une provenance de build explicite. Les builders de release produisent également des checksums SHA-256.

Le workflow `MORPHEUS Release` ajoute une preuve d'origine authentifiée pour les releases futures :

- déclenchement uniquement sur tag `vX.Y.Z` ;
- tag obligatoirement atteignable depuis `main` ;
- build depuis le SHA exact du tag ;
- attestation GitHub via OIDC avec `actions/attest` pinné par SHA ;
- bundle d'attestation conservé avec les assets ;
- refus d'écraser une GitHub Release déjà publiée.

Politique de confiance :

```text
checksum          -> intégrité par rapport à une valeur attendue
SBOM              -> composition logicielle
build provenance  -> traçabilité du build
GitHub attestation -> preuve d'origine liée au workflow et au commit
```

Un checksum publié à côté d'un artefact ne suffit donc plus comme seule preuve de confiance pour les nouvelles releases produites par ce workflow.

Le workflow `MORPHEUS Security` complète ce contrôle par OWASP Dependency-Check. Le chemin `pull_request` n’injecte aucun secret NVD dans le code de la PR ; la clé NVD est réservée aux événements de confiance. La base trusted est rafraîchie quotidiennement et un cache PR âgé de plus de 72 h est refusé. `MORPHEUS CodeQL` fournit en plus un SAST Java versionné avec actions pinnées par SHA et requêtes `security-extended`.

## Update channel

La version courante et le channel `stable` sont exposés par `ProductMetadata`. La découverte d’une version disponible est strictement read-only :

```text
update discovery != automatic update
```

Elle est déclenchée uniquement par un appel explicite CLI/MCP avec une URI de manifeste. Construction et démarrage du runtime n’effectuent aucune requête réseau pour rechercher une mise à jour.

Un manifeste local `file:` peut rester minimal pour les tests et diagnostics :

```properties
version=1.2.1
channel=stable
artifactUri=https://example.invalid/morpheus-1.2.1.zip
sha256=<64 hex chars>
```

Un manifeste distant `https:` doit en plus déclarer une provenance :

```properties
version=1.2.1
channel=stable
artifactUri=https://downloads.example.invalid/morpheus-1.2.1.zip
sha256=<64 hex chars>
attestationUri=https://github.com/OWNER/REPO/attestations/...
```

Pour une source distante, `artifactUri` et `attestationUri` doivent tous deux être en HTTPS et l'attestation est obligatoire. Les URI d'artefact en HTTP/FTP/autres schémas sont rejetées. Cette règle ne transforme pas encore `update-check` en vérificateur d'attestation : le service ne télécharge, n’installe, ne remplace et n’exécute jamais l’artefact. Elle verrouille toutefois le contrat de sorte qu'un futur auto-updater ne puisse pas être construit silencieusement sur un simple couple URI + checksum sans provenance.

Toute future capacité d'installation devra vérifier cryptographiquement l'attestation avant utilisation de l'artefact et recevoir son propre ADR/contrat de sécurité.

## Public surface convergence

Voir [`../reference/PUBLIC_SURFACES.md`](../reference/PUBLIC_SURFACES.md) et la source normative [`../../contracts/public-surfaces.tsv`](../../contracts/public-surfaces.tsv).

Une capability peut avoir des formes différentes selon CLI/MCP/HTTP. Une différence doit être explicite. Une capacité WRITE reste distincte d’une lecture ou d’une simple décision d’autorisation.

## Qualification

Le gate M21 doit être exécuté sur le head exact. Les changements de sécurité, release, coverage ou update contract doivent rester couverts par des tests/contrats d'architecture afin d'éviter une régression silencieuse des gates.
