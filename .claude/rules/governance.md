# Règles — Gouvernance, contrats & convergence

## Le manifeste de convergence est la loi

`contracts/public-surfaces.tsv` — une ligne par capacité, format TSV :

```
capability	intent	cli	mcp	http	notes
```

- `intent` ∈ `READ` | `WRITE`
- Les colonnes `cli` / `mcp` / `http` portent soit une surface réelle, soit un **sentinelle explicite** :

| Sentinelle | Sens |
|---|---|
| `EXPLICITLY_NOT_EXPOSED` | Délibérément absent de ce transport |
| `EXPLICITLY_LOCAL_ONLY` | Local uniquement, jamais remote |
| `EXPLICITLY_REMOTE_ONLY` | Remote uniquement, jamais local |
| `EXPLICITLY_OFFLINE_ONLY` | Hors-ligne uniquement (ex. `server.restore`) |

**Une case vide est une violation.** L'absence doit être *déclarée*, jamais implicite.

### Exemple réel (`contracts/public-surfaces.tsv`)

```
capability	intent	cli	mcp	http	notes
provider.plugins.probe	WRITE	provider-plugins probe	EXPLICITLY_NOT_EXPOSED	POST /api/v1/provider-plugins/probe	Executable third-party code is not model-facing; ...
```

Ici, `mcp` porte `EXPLICITLY_NOT_EXPOSED` plutôt qu'une case vide : le probe exécute du
code tiers, donc il est délibérément absent du transport MCP (jamais model-facing), et la
colonne `notes` justifie pourquoi. C'est le patron à reproduire pour toute nouvelle
capacité qui n'expose pas les trois transports.

Ce que les gates comparent réellement — deux mécanismes, à ne pas confondre :

- **Route par route, dans les deux sens** : `PublicHttpRouteConvergenceTest` (paquet `com.morpheus.api` des tests
  d'architecture) compare méthode + chemin entre la table `MorpheusHttpRouteTable`, la colonne `http` du manifeste
  et les opérations de **tous** les `docs/openapi/*.yaml`, paramètres de chemin comparés par position (`{id}`,
  `{savedViewId}` et `{viewId}` sont le même segment). Une route servie absente de l'OpenAPI, une opération OpenAPI ou
  une ligne du manifeste qu'aucun serveur ne sert, une route servie sans ligne au manifeste font échouer le build. Il
  démarre aussi un vrai serveur local pour prouver qu'il route chaque entrée de la table, et qu'un `405` y porte un
  `Allow` égal aux méthodes de la route. Exclusions motivées dans le
  test : les routes servies par le seul serveur remote (`REMOTE_ONLY`) et les sondes d'exploitation sans ligne au
  manifeste (`NOT_IN_MANIFEST` : racine, `health`, `readiness`, `metrics`). La colonne `mcp` a son équivalent,
  `PublicSurfaceManifestCoversEveryServedToolTest`. Ni l'un ni l'autre ne vérifie la colonne `cli`.
- **Lignes épinglées** : les tests `publicManifestAndOpenApiExposeSame*IntentFamilies` (M24, M25, M27) cherchent quelques
  lignes **exactes** du TSV et quelques clés de chemin de l'OpenAPI, choisies à la main. Ils épinglent une famille de
  capacités ; ils ne comparent pas les deux fichiers.

`MorpheusHttpRouteTable` est la seule liste des routes et de leurs méthodes : l'en-tête `Allow` local en est calculé,
et `MorpheusRemoteRoutePolicy` refuse de se charger si ses rôles ne couvrent pas exactement ses routes et méthodes.
Une route ajoutée au code sans sa ligne de manifeste et son opération OpenAPI casse le gate de convergence ; une route
servie qui manque à `MorpheusHttpRouteTable` n'est vue par aucun des deux (elle est refusée en remote, 404).

## TOUJOURS

- Mettre à jour **ensemble** : le code, `contracts/public-surfaces.tsv`, et `docs/openapi/morpheus-v1-*.yaml`
- Écrire un ADR dans `docs/adr/` pour toute décision structurelle (compter `docs/adr/0*.md` avec un `glob` — ne jamais recopier un total, cf. `rules/meta.md` ; le `README.md` du répertoire n'est pas un ADR)
- Livrer le quadruplet complet pour un nouveau milestone (suite ArchUnit + scripts dual-platform + EXECUTION + VALIDATION)
- Fournir les scripts de validation **en `.ps1` ET `.sh`** — la parité Windows/Linux est assertée
- Justifier dans la description de la PR toute modification de `contracts/public-surfaces.tsv`,
  `config/*ratchets*.properties`, `docs/openapi/*.yaml` ou d'un test sous `morpheus-architecture-tests/` —
  ce sont des fichiers de gouvernance, pas de simples fichiers de configuration

## JAMAIS

- Jamais contourner un gate en éditant le test pour qu'il passe à tort
- Jamais supprimer une règle ArchUnit sans la remplacer par équivalente ou plus stricte
- Jamais force-pusher sur `main` ou `develop`
- Jamais introduire `docker` dans l'installeur ou l'intégration MCP (M28 l'interdit textuellement)

## Version produit — source unique

`ProductMetadata` est la **seule** source de vérité (actuellement `1.2.1`).

- Toute surface publique délègue : `ProductMetadata.version()` / `ProductMetadata.current()`
  → dans `MorpheusApiService`, `MorpheusProductCli`, `MorpheusProductMcpTools`
- **Aucun** fichier `src/main/java/` ne doit contenir `0.1.0-SNAPSHOT` ni `FALLBACK_VERSION`
- La version apparaît aussi dans `scripts/validate-m21.*` et `scripts/validate-d2.*` — la bumper implique de les mettre à jour

## Contrats OpenAPI — bornes obligatoires

Toute spec `docs/openapi/*.yaml` doit porter :
- `additionalProperties: false` sur les schémas d'entrée
- Des bornes explicites : `maximum`, `maxItems`, `maxLength`
  (M24 : `maximum: 500`, `maxLength: 16384` · M25 : `maxItems: 128` · M26 : `maximum: 512`, `maximum: 15` · M27 : `maxItems: 256`)
- **Jamais** de vocabulaire d'échappement : `sql query`, `sql passthrough`, `script source`, `apply mutation` sont interdits

## Sémantiques métier non négociables

- **Policy tri-state** : `UNKNOWN` n'est **jamais** implicitement `BLOCKED` (ADR-0078, ADR-0093)
- **Pas de last-write-wins silencieux** : les conflits de composition restent explicites
- **CAS obligatoire** sur les écritures de configuration (`expected revision`) — les writers périmés échouent explicitement
- **Non-destructif** : `missing`, `archive`, `deactivate` conservent identité, références et révision antérieure
- **Reasoning strictement read-only** (M27) : aucune mutation, `const: false` dans l'OpenAPI
- **Lifecycle** : `WRITE_CHANGE` + confirmation + CAS restent obligatoires
- Un **saved view n'est pas une vérité matérialisée** — il exécute contre la vérité publiée courante

## Ratchets de présence qualifiée

**Ne pas se fier aux nombres codés en dur ici** — source de vérité vivante :
`config/m21-quality-ratchets.properties`. `scripts/validate-m21.*` et `scripts/validate-d2.*`
lisent ce fichier et assertent le nombre de tests, le nombre de tests d'architecture,
la couverture ligne/branche et la version courante (`1.2.1`). La couverture y est déclarée
**deux fois**, une paire de clés par échelle de mesure (`aggregate*` et `perModule*`) — citer
un seuil sans nommer son échelle n'a pas de sens. Valeurs constatées le 09/10/2026 :
`3820 / 585`, couverture `90,0% / 75,7%` agrégée et `69,1% / 61,2%` par module
(les six relevées ce jour-là, dans les deux plafonds requalifiés le même jour sur `8e3fda5a`).
Ces nombres sont des
**ratchets** — ils ne descendent pas, mais ils **montent** au fil des milestones, donc
toute valeur recopiée ici (y compris dans une version antérieure de cette page) peut être
périmée. Relire le fichier `.properties` avant de citer un chiffre. Voir `rules/meta.md`.
