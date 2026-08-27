# Production Integrity — M21

M21 transforme les garanties de production de MORPHEUS 1.x en gates durables et reproductibles.

## Autorités

```text
Build/version          pom.xml + ProductMetadata
Public surfaces        contracts/public-surfaces.tsv
Architecture           morpheus-architecture-tests
Release packaging      distribution/** + .github/workflows/release.yml
M21 gate               scripts/validate-m21.*
CI durable             .github/workflows/ci.yml
SCA durable            .github/workflows/security.yml
SAST durable           .github/workflows/codeql.yml
Preuve finale          docs/validation/VALIDATION_M21.md
```

La documentation humaine explique ces contrats ; elle ne doit pas devenir une deuxième source normative divergente.

## Quality gates M21

Baseline active :

```text
Tests              >= 820 PASS
Architecture       >= 258 PASS
Reactor            18/18 SUCCESS
Windows            PASS
Linux              PASS
JaCoCo lines        >= 50.6 % aggregate
JaCoCo branches     >= 43.0 % aggregate
Changed lines       >= 80 %
Changed branches    >= 70 %
```

La qualification exact-head `ec0f1b4d4821d4b6a946a820e257bd4449bfaf58` a mesuré 839 tests, 260 tests d’architecture, 50,6959 % de lignes et 43,1147 % de branches. Les seuils restent volontairement légèrement sous les mesures qualifiées afin de former des ratchets stables. Le changed-code gate complète désormais les lignes par les branches situées sur les lignes exécutables modifiées.

## Reproducible-build hygiene

Le build fixe `project.build.outputTimestamp`, centralise les versions de plugins structurants et écrit les métadonnées de version dans les manifests JAR. Une release reste construite à partir d’un ref/tag exact conformément au contrat M20.

Les métadonnées de release ne sont pas un état métier :

```text
release metadata != runtime business state
```

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
