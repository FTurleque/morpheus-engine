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

Les tests `publicManifestAndOpenApiExposeSame*IntentFamilies` comparent le TSV **caractère par caractère**
avec l'OpenAPI. Modifier une signature sans mettre à jour les deux casse le gate.

## TOUJOURS

- Mettre à jour **ensemble** : le code, `contracts/public-surfaces.tsv`, et `docs/openapi/morpheus-v1-*.yaml`
- Écrire un ADR dans `docs/adr/` pour toute décision structurelle (compter `docs/adr/0*.md` avec un `glob` — ne jamais recopier un total, cf. `rules/meta.md` ; le `README.md` du répertoire n'est pas un ADR)
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

**Ne pas se fier aux nombres codés en dur ici** — source de vérité vivante :
`config/m21-quality-ratchets.properties`. `scripts/validate-m21.*` et `scripts/validate-d2.*`
lisent ce fichier et assertent le nombre de tests, le nombre de tests d'architecture,
la couverture ligne/branche et la version courante (`1.2.1`). Valeurs constatées le
04/09/2026 : `1300 / 335 / 54,5% / 47,7%`. Ces nombres sont des
**ratchets** — ils ne descendent pas, mais ils **montent** au fil des milestones, donc
toute valeur recopiée ici (y compris dans une version antérieure de cette page) peut être
périmée. Relire le fichier `.properties` avant de citer un chiffre. Voir `rules/meta.md`.
