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

Les tests `publicManifestAndOpenApiExposeSame*IntentFamilies` comparent le TSV **caractère par caractère**
avec l'OpenAPI. Modifier une signature sans mettre à jour les deux casse le gate.

## TOUJOURS

- Mettre à jour **ensemble** : le code, `contracts/public-surfaces.tsv`, et `docs/openapi/morpheus-v1-*.yaml`
- Écrire un ADR dans `docs/adr/` pour toute décision structurelle (96 ADRs existants — chercher avant de décider)
- Livrer le quadruplet complet pour un nouveau milestone (suite ArchUnit + scripts dual-platform + EXECUTION + VALIDATION)
- Fournir les scripts de validation **en `.ps1` ET `.sh`** — la parité Windows/Linux est assertée

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

`scripts/validate-m21.*` et `scripts/validate-d2.*` assertent les compteurs `711` et `253`
ainsi que la version `1.2.1`. Ces nombres sont des **ratchets** — ils ne descendent pas.
