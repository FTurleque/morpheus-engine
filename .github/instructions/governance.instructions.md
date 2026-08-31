---
applyTo: "contracts/**,docs/openapi/**,docs/adr/**,config/**,morpheus-architecture-tests/**"
---

# Gouvernance, contrats & convergence

Détail complet et exemples réels : `.claude/rules/governance.md` (source partagée avec
Claude Code).

## Le manifeste de convergence est la loi

`contracts/public-surfaces.tsv` — une ligne par capacité :
`capability	intent	cli	mcp	http	notes`, `intent` ∈ `READ`|`WRITE`.

**Une case vide est une violation.** L'absence sur un transport doit être déclarée par un
sentinelle explicite : `EXPLICITLY_NOT_EXPOSED`, `EXPLICITLY_LOCAL_ONLY`,
`EXPLICITLY_REMOTE_ONLY`, `EXPLICITLY_OFFLINE_ONLY`. Les tests
`publicManifestAndOpenApiExposeSame*IntentFamilies` comparent le TSV **caractère par
caractère** avec l'OpenAPI.

## TOUJOURS

- Mettre à jour **ensemble** : le code, `contracts/public-surfaces.tsv`, et
  `docs/openapi/morpheus-v1-*.yaml`
- Écrire un ADR dans `docs/adr/` pour toute décision structurelle — compter les fichiers
  réels avec un `glob` avant de citer un total, ne jamais recopier un nombre mémorisé
  (un doublon de numérotation existe déjà dans l'historique du dépôt)
- Livrer le quadruplet complet pour un nouveau milestone (suite ArchUnit + scripts
  dual-platform `.ps1`/`.sh` + `EXECUTION` + `VALIDATION`)

## JAMAIS

- Jamais contourner un gate en éditant le test pour qu'il passe à tort
- Jamais supprimer une règle ArchUnit sans la remplacer par une équivalente ou plus stricte
- Jamais force-pusher sur `main` ou `develop`
- Jamais introduire `docker` dans l'installeur ou l'intégration MCP

## Version produit — source unique

`ProductMetadata` est la **seule** source de vérité — lire `ProductMetadata.version()` /
`.current()`, jamais un littéral. Aucun fichier `src/main/java/` ne doit contenir
`0.1.0-SNAPSHOT` ni `FALLBACK_VERSION`.

## Sémantiques métier non négociables

- Policy tri-state : `UNKNOWN` n'est **jamais** implicitement `BLOCKED`
- Pas de last-write-wins silencieux — CAS obligatoire (`expected revision`) sur les
  écritures de configuration
- Non-destructif : `missing`, `archive`, `deactivate` conservent identité, références,
  révision antérieure
- Reasoning strictement read-only (`const: false` dans l'OpenAPI)
- Un saved view exécute contre la vérité publiée courante — ce n'est pas une vérité
  matérialisée

## Ratchets — jamais de chiffre codé en dur

Ne jamais recopier un seuil de coverage, un nombre de tests ou un nombre d'ADR dans une
réponse à impact décisionnel sans l'avoir revérifié dans la session : source vivante
= `config/m21-quality-ratchets.properties` + `docs/adr/` compté par `glob`. Voir
`.claude/rules/meta.md`.

