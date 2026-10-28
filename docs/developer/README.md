# Guide développeur MORPHEUS

Cette documentation distingue deux états :

```text
release publiée         MORPHEUS 1.2.1 / tag v1.2.1
release commit           2a8b1ede9cdeb107244b04969726305afc672189
release précédente       MORPHEUS 1.2.0 / tag v1.2.0 / 3ad9ebf030b58df97482e21e272c24feae6b9d86
version du reactor       1.2.1
branche d'intégration    develop
```

`v1.2.1` a été publiée le 16 octobre 2026 et qualifiée par les deux scripts de relecture publique ([`../project/validation/VALIDATION_RELEASE_1.2.1.md`](../project/validation/VALIDATION_RELEASE_1.2.1.md)). Les tags `v1.2.0` et `v1.2.1` sont immuables : le code qui avance sur `develop` ne doit pas être republié sous `1.2.1`, la prochaine version se déclare par un bump explicite.

## Prérequis

```text
Java   >= 21
Maven  via Maven Wrapper 3.10.0 + SHA-256 vérifié
Git
Windows PowerShell pour le gate Windows
Linux/WSL pour le gate Linux
```

Le reactor compile avec `release=21`.

## Import IntelliJ IDEA

Charger le `pom.xml` racine comme projet Maven. Ne pas créer les sous-modules manuellement : ils sont définis par le reactor.

## Modules

Le reactor déclare les modules ci-dessous dans le bloc `<modules>` du `pom.xml` racine, dans son ordre. Le nombre se compte dans le POM, il ne s'écrit pas ici :

```text
morpheus-domain
morpheus-application
morpheus-provider-sdk
morpheus-provider-testkit
morpheus-provider-reference
morpheus-provider-openspec
morpheus-provider-markdown
morpheus-provider-synthetic
morpheus-store-memory
morpheus-store-sqlite
morpheus-mcp-transport
morpheus-integration-minos
morpheus-integration-nexus
morpheus-mcp
morpheus-api
morpheus-cli
morpheus-architecture-tests
morpheus-coverage-report
```

Architecture générale :

```text
adapters / sdk -> application -> domain
```

Le domaine et l’application ne dépendent ni des transports ni des clients MCP.

## Baseline technique active

```text
product                    1.2.1
Java                       21
Jackson                    3.2.4
sqlite-jdbc                3.53.4.0
MCP SDK                    2.1.0
OWASP Dependency-Check     13.0.0
JaCoCo aggregate line ratchet     >= 91.2%
JaCoCo aggregate branch ratchet   >= 77.3%
JaCoCo per-module line ratchet    >= 71.2%
JaCoCo per-module branch ratchet  >= 62.5%
Surefire written floor     >= 2600
Surefire generated floor   >= 1574
Architecture floor         >= 813
Changed-line gate          80%
Changed-branch gate        70%
dependency analyze         failOnWarning=true
```

Le SCA réseau n’est pas attaché au `clean verify` développeur ordinaire. Il est exécuté par `MORPHEUS Security` sur `main` et `develop`, avec refresh trusted quotidien et TTL PR de 72 h. Le profil local `d2-security` reste disponible pour une qualification spécialisée.

## Gate Maven canonique

Windows :

```powershell
.\mvnw.cmd clean verify
```

Linux :

```bash
./mvnw clean verify
```

`clean test` n’est pas la qualification finale : les tests d’architecture consomment les JARs et rapports produits jusqu’à `verify`.

## Gate durable M21

Windows :

```powershell
.\scripts\validate.cmd m21 -Version 1.2.1
```

Linux :

```bash
bash ./scripts/validate-m21.sh 1.2.1
```

M21 exige le même SHA exact sur Windows et Ubuntu/Linux, vérifie les ratchets de tests/couverture, le SBOM, la provenance, le portable et la convergence de version CLI/API/update.

La CI canonique exécute M21 sur les pull requests ainsi que sur les pushes `main` et `develop`. Les PR Java de production doivent en plus conserver `>= 80%` de changed-line coverage et `>= 70%` de changed-branch coverage.

## Audit de code : SpotBugs et PIT

Deux profils Maven opt-in, absents de `clean verify` et de la CI : `audit-spotbugs` (analyse statique, contrôle
bloquant) et `audit-mutation` (tests de mutation, un module à la fois). Configuration, commandes, rapports et lecture des
résultats : [Audit de code](CODE_AUDIT.md). Référence détaillée de SpotBugs (versions, périmètre, commandes,
dépannage, sources officielles) : [SpotBugs](SPOTBUGS.md).

## Gate D2 spécialisé

Windows :

```powershell
.\scripts\validate.cmd d2 -Version 1.2.1 -BaseRef origin/develop
```

Linux / WSL :

```bash
MORPHEUS_D2_BASE_REF=origin/develop bash ./scripts/validate-d2.sh 1.2.1
```

D2 conserve sa règle historique de périmètre local : il refuse une PR qui modifie `.github/workflows/**`. Il ne doit donc pas être utilisé comme preuve principale d'une PR dont l'objet est précisément de faire évoluer la CI ; M21 est le gate durable de ce cas.

## Sécurité JSON

Les routes HTTP conservent :

```text
MAX_REQUEST_BODY_BYTES = 65536
FAIL_ON_UNKNOWN_PROPERTIES
FAIL_ON_TRAILING_TOKENS
```

Jackson 3.2.4 est utilisé sans default typing. Les tests de régression couvrent notamment la profondeur JSON, les tailles de requête et les frontières workspace/provider.

## SCA

Profil local :

```text
d2-security
```

Commande :

```text
org.owasp:dependency-check-maven:13.0.0:aggregate
```

Politique : CVSS >= 7.0 fait échouer la qualification ; test scope exclu ; erreur de scan bloquante ; rapports sous `target/d2-security`.

Le workflow `.github/workflows/security.yml` exécute le même contrôle sur :

```text
PR -> main, develop
push -> main, develop
schedule -> quotidien 04:17 UTC
workflow_dispatch
```

`.github/dependabot.yml` ouvre en parallèle les mises à jour Maven et GitHub Actions vers `develop`. Les alertes de vulnérabilité Dependabot et Secret Scanning ont été vérifiées directement dans les réglages GitHub administrateur ; #154 est clôturée.

## Packaging et release

Les builders actifs produisent `1.2.1` par défaut :

```powershell
.\distribution\build-portable.ps1 -Version 1.2.1
.\distribution\build-installer.ps1 -Version 1.2.1
```

```bash
bash ./distribution/build-portable.sh 1.2.1
```

Les builders de release refusent de publier si le workspace n'est pas propre ou si le tag attendu `v1.2.1` ne pointe pas exactement sur HEAD.

Le workflow `MORPHEUS Release` ajoute une chaîne de publication attestée : tag `vX.Y.Z` atteignable depuis `main`, builds Linux + Windows, attestation GitHub/OIDC pour les artefacts, puis création unique de la GitHub Release sans écrasement d'assets existants.

Les distributions embarquent leur runtime Java, le MCP STDIO, l’API, les providers et les adapters MINOS/NEXUS optionnels, mais jamais les implémentations MINOS/NEXUS/JARVIS.

## Gouvernance mono-développeur

Le ruleset GitHub **Protect main & develop** protège désormais les deux branches :

```text
PR obligatoire
checks exact-head Linux + Windows requis
Dependency-Check requis
CodeQL / code scanning requis
conversations résolues
strict required checks
suppression / non-fast-forward interdits
0 approbation obligatoire
aucun bypass
```

Le choix de `0` approbation obligatoire reste cohérent avec le contexte mono-mainteneur. #166 et #154 sont clôturées ; le Quality Gate SonarCloud et les réglages de sécurité administrateur ont été vérifiés directement sur leurs plateformes.

## Index de l'espace développeur

Démarrer :

- [Reprise à froid](COLD_START_RECOVERY.md) — par où recommencer après une longue interruption : la commande unique qui qualifie le dépôt, les secrets externes et ce qui casse sans eux, comment lire une preuve, et quels gates rouges ne sont pas des régressions
- [Build et tests](BUILD_AND_TEST.md)
- [Audit de code](CODE_AUDIT.md) · [SpotBugs](SPOTBUGS.md)
- [Version produit](PRODUCT_VERSION.md)
- [Production integrity](PRODUCTION_INTEGRITY.md)

Architecture et décisions (dans `docs/architecture/`) :

- [Architecture générale](ARCHITECTURE.md)
- [Dossier d'architecture arc42](../architecture/README.md) — [vue d'ensemble](../architecture/overview.md), [scénarios qualité](../architecture/quality/scenarios.md), [registre des risques](../architecture/risks/register.md)
- [Registre des ADR](../architecture/adr/README.md) — [gabarit](../architecture/adr/gabarit/template.md)
- [Archgate](ARCHGATE.md) — lecture du registre d'ADR et règles TypeScript rattachées, hors CI

Plateformes et surfaces :

- [Provider SDK](PROVIDER_SDK.md) · [Provider OpenSpec — règles de lecture](OPENSPEC_PROVIDER.md)
- [Portfolio Intelligence](PORTFOLIO_INTELLIGENCE.md)
- [Query Platform](QUERY_PLATFORM.md)
- [Policy Platform](POLICY_PLATFORM.md)
- [Remote Server Platform](REMOTE_SERVER_PLATFORM.md)
- [Assisted Reasoning](ASSISTED_REASONING.md)
- [API](API.md)
- [MCP](MCP.md)
- [Intégrations](INTEGRATIONS.md)
- [Operability](OPERABILITY.md)

Contrats machine — [`reference/`](reference/README.md) :

- [Surfaces publiques](reference/PUBLIC_SURFACES.md) et [`contracts/public-surfaces.tsv`](../../contracts/public-surfaces.tsv)
- [OpenAPI](reference/openapi/)
- [Contrats de ports](reference/contracts/) — provider et knowledge store
- [Modèle de domaine](reference/domain/MODEL.md) · [cycle de vie des changements](reference/domain/CHANGE_LIFECYCLE.md)

Les plans, preuves de validation et audits ne sont pas ici : voir le [suivi du projet](../project/README.md). La documentation d'usage est dans [`../user/`](../user/README.md).
