# Production Integrity — M21

M21 transforme les garanties de production de MORPHEUS 1.x en gates durables et reproductibles.

## Autorités

```text
Build/version          pom.xml + ProductMetadata
Public surfaces        contracts/public-surfaces.tsv
Architecture           morpheus-architecture-tests
Release packaging      distribution/**
M21 gate               scripts/validate-m21.*
CI durable             .github/workflows/ci.yml
Preuve finale          docs/validation/VALIDATION_M21.md
```

La documentation humaine explique ces contrats ; elle ne doit pas devenir une deuxième source normative divergente.

## Quality gates M21

Baseline minimale :

```text
Tests              >= 454 PASS
Architecture       >= 182 PASS
Reactor            14/14 SUCCESS
Windows            PASS
Linux              PASS
JaCoCo lines        >= 25 % aggregate
JaCoCo branches     >= 20 % aggregate
```

Les seuils 25 % / 20 % sont des floors de non-régression instrumentale introduits en M21. Ils ne signifient pas qu’une couverture de 25 % est un objectif de qualité final. La tendance doit être conservée et les seuils pourront être relevés avec des mesures réelles.

## Reproducible-build hygiene

Le build fixe `project.build.outputTimestamp`, centralise les versions de plugins structurants et écrit les métadonnées de version dans les manifests JAR. Une release reste construite à partir d’un ref/tag exact conformément au contrat M20.

Les métadonnées de release ne sont pas un état métier :

```text
release metadata != runtime business state
```

## Supply chain

Le build M21 génère un SBOM CycloneDX JSON/XML et une provenance de build explicite. Les artefacts de release conservent des checksums SHA-256.

Politique de confiance :

```text
checksum != signature
SBOM != signature
provenance metadata != cryptographic identity
```

Un SHA-256 permet de vérifier l’intégrité d’un fichier par rapport à une valeur attendue ; il ne prouve pas à lui seul l’identité de l’émetteur. MORPHEUS ne simule donc jamais une signature cryptographique. Une signature ou attestation cryptographique ne peut devenir obligatoire qu’avec une identité et une clé réelles, gérées par un flux de release explicite.

## Update channel

La version courante et le channel `stable` sont exposés par `ProductMetadata`. La découverte d’une version disponible est strictement read-only :

```text
update discovery != automatic update
```

Elle est déclenchée uniquement par un appel explicite CLI/MCP avec une URI de manifeste. Les schémas supportés sont `file:`, `http:` et `https:`. Construction et démarrage du runtime n’effectuent aucune requête réseau pour rechercher une mise à jour.

Le manifeste contient au minimum :

```properties
version=1.0.1
channel=stable
artifactUri=https://example.invalid/morpheus-1.0.1.zip
sha256=<64 hex chars>
```

La découverte valide les métadonnées et compare les versions. Elle ne télécharge, n’installe, ne remplace et n’exécute jamais l’artefact.

## Public surface convergence

Voir [`../reference/PUBLIC_SURFACES.md`](../reference/PUBLIC_SURFACES.md) et la source normative [`../../contracts/public-surfaces.tsv`](../../contracts/public-surfaces.tsv).

Une capability peut avoir des formes différentes selon CLI/MCP/HTTP. Une différence doit être explicite. Une capacité WRITE reste distincte d’une lecture ou d’une simple décision d’autorisation.

## Qualification

Le gate M21 doit être exécuté sur le head exact. Après le gate final, seuls des changements de preuve/documentation explicitement contrôlés sont admis ; le delta exécutable post-gate doit rester `NONE` avant passage de la PR en Ready.
