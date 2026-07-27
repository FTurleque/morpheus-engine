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

Format du manifeste :

```properties
version=1.0.1
channel=stable
artifactUri=https://releases.example.invalid/morpheus-1.0.1-windows-x64.zip
sha256=<64 caractères hexadécimaux>
```

Schémas de manifeste supportés :

```text
file:
http:
https:
```

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

Le SHA-256 annoncé par le manifeste sert à comparer l’intégrité d’un artefact avec une valeur attendue. Il ne constitue pas une preuve cryptographique d’identité de l’éditeur :

```text
checksum != signature
```

MORPHEUS ne simule aucune signature en l’absence d’une identité et d’une clé de signature réelles.
