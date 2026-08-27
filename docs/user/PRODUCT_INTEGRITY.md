# MORPHEUS — version produit et découverte de mise à jour

Cette page décrit les surfaces utilisateur M21 liées à l’intégrité produit. Le contrat machine de convergence reste [`../../contracts/public-surfaces.tsv`](../../contracts/public-surfaces.tsv).

## Version

```powershell
morpheus version
morpheus --json version
morpheus product-info
morpheus --json product-info
```

`product-info` expose :

```text
name
version
apiVersion
updateChannel
```

La version d’une distribution publiée provient des métadonnées du build. Le marqueur `development` est un fallback de développement, jamais une preuve de release.

## Découverte explicite d’une version disponible

```powershell
morpheus update-check --manifest C:\path\to\stable.properties
```

ou :

```bash
morpheus update-check --manifest https://releases.example.invalid/morpheus/stable.properties
```

Format d'un manifeste distant :

```properties
version=1.2.1
channel=stable
artifactUri=https://releases.example.invalid/morpheus-1.2.1-windows-x64.zip
sha256=<64 caractères hexadécimaux>
attestationUri=https://github.com/OWNER/REPO/attestations/...
```

Schémas de manifeste supportés :

```text
file:
https:
```

Un manifeste distant en `http:` est refusé avant toute requête réseau. Pour un manifeste `https:`, le contrat est fail-closed :

- `artifactUri` doit lui aussi utiliser `https:` ;
- `attestationUri` est obligatoire ;
- `attestationUri` doit utiliser `https:` ;
- les URI d'artefact utilisant `http:`, `ftp:` ou un autre schéma sont refusées.

Les manifests locaux `file:` restent utilisables sans attestation pour les tests, la qualification packagée et les diagnostics explicites. Cette exception locale ne rend aucun artefact installable : `update-check` reste strictement read-only.

## Ce que `update-check` ne fait jamais

```text
pas de vérification réseau au démarrage
pas de téléchargement de l’artefact
pas d’installation automatique
pas de remplacement du programme
pas d’exécution de l’artefact annoncé
pas de mutation de la base métier
```

Le résultat indique uniquement la version courante, la version disponible, le channel, l’URI de l’artefact, son SHA-256 annoncé et `updateAvailable`.

```text
update discovery != automatic update
```

## MCP

Les mêmes capacités de lecture sont accessibles par :

```text
get_product_info
check_product_update(manifestUri)
```

## HTTP

`GET /api/v1/version` expose la version de la distribution HTTP.

La découverte d’update à URI fournie par le client est **volontairement absente** de l’API HTTP locale (`EXPLICITLY_NOT_EXPOSED`). Le but est d’éviter d’ajouter un fetcher d’URI arbitraire à la surface HTTP. Cette asymétrie est contractuelle et documentée ; elle n’est pas une divergence silencieuse.

## Intégrité et confiance

Le SHA-256 annoncé par le manifeste permet de vérifier l’intégrité d’un artefact par rapport à une valeur attendue. Il ne constitue pas une preuve d’identité de l’éditeur :

```text
checksum != provenance
```

Les releases produites par le workflow `MORPHEUS Release` reçoivent désormais une attestation GitHub de provenance liée au workflow et au commit tagué. Le champ `attestationUri` rend cette preuve explicitement référençable par le contrat de découverte distant.

MORPHEUS ne vérifie ni ne télécharge encore cette attestation dans `update-check`; l'exigence actuelle garantit qu'un futur installateur ne puisse pas être construit silencieusement sur l'ancien contrat « checksum seulement ». Toute installation automatique future devra vérifier l'attestation avant d'utiliser l'artefact.
