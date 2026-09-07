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

Le résultat transporte la version courante, la version disponible, le channel, l’URI de l’artefact, son SHA-256 annoncé, l’URI d’attestation lorsqu’elle existe, `updateAvailable` et un niveau de confiance explicite :

```text
trustLevel=DISCOVERY_ONLY
```

`DISCOVERY_ONLY` signifie que la référence de provenance a été conservée par le contrat de découverte, mais qu’aucune vérification cryptographique de l’attestation ou de l’identité de l’éditeur n’a été effectuée.

```text
update discovery != automatic update
provenance reference != verified provenance
```

## MCP

La version produit reste accessible via :

```text
get_product_info
```

Le nom historique `check_product_update` est conservé uniquement comme **stub sans I/O** : il ne lit aucun fichier, n’effectue aucune requête réseau et retourne une erreur indiquant que la découverte à URI fournie est CLI-only. Cette asymétrie réduit la surface SSRF/file-read d’un client agentique.

## HTTP

`GET /api/v1/version` expose la version de la distribution HTTP.

La découverte d’update à URI fournie par le client est **volontairement absente** de l’API HTTP locale (`EXPLICITLY_NOT_EXPOSED`). Le but est d’éviter d’ajouter un fetcher d’URI arbitraire à la surface HTTP. Cette asymétrie est contractuelle et documentée ; elle n’est pas une divergence silencieuse.

## Intégrité et confiance

Le SHA-256 annoncé par le manifeste permet de contrôler la forme d’une valeur d’intégrité attendue et de la transporter avec les métadonnées de découverte. Il ne constitue pas une preuve d’identité de l’éditeur :

```text
checksum != provenance
```

Les releases produites par le workflow `MORPHEUS Release` reçoivent une attestation GitHub de provenance liée au workflow et au commit tagué. Le champ `attestationUri` rend cette preuve explicitement référençable par le contrat de découverte distant et elle est désormais conservée dans `UpdateCheckResult`.

MORPHEUS ne vérifie ni ne télécharge cette attestation dans `update-check`. La méthode de validation du manifeste vérifie donc un **contrat de découverte distant**, pas une décision cryptographique de confiance éditeur. Toute installation automatique future devra introduire un niveau de confiance vérifié et vérifier cryptographiquement l’attestation ainsi que son lien avec l’artefact avant d’utiliser celui-ci.
