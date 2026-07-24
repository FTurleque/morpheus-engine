# MORPHEUS CLI — M9

La CLI M9 est une interface locale et scriptable au moteur MORPHEUS. Elle utilise SQLite par défaut et réutilise les services applicatifs validés des jalons précédents.

## Démarrage

Archive portable Windows :

```powershell
.\morpheus\morpheus.exe help
```

Archive portable Linux :

```bash
./morpheus/bin/morpheus help
```

JAR autonome pour usage avancé :

```bash
java -jar morpheus-cli-0.1.0-SNAPSHOT-all.jar help
```

## Options globales

```text
--json
--data-dir PATH
--config-dir PATH
--db PATH
```

`--json` envoie le résultat normal sur stdout en JSON déterministe. Les erreurs restent sur stderr et utilisent le même code de sortie qu'en mode humain.

## Workflow minimal

### 1. Enregistrer un workspace OpenSpec

```bash
morpheus projects add --workspace /path/to/project
```

La commande retourne un `projectId` MORPHEUS stable dans la DB locale.

### 2. Synchroniser

```bash
morpheus sync --project <projectId>
```

M9 exécute volontairement un **FULL_REBUILD conservateur** du snapshot publié. Le planificateur incrémental M7 reste présent dans le moteur mais n'est pas présenté comme exécuté tant qu'un exécuteur de sous-graphe incrémental n'est pas disponible.

Une révision source opaque peut être fournie :

```bash
morpheus sync --project <projectId> --revision <revision>
```

### 3. Vérifier la fraîcheur

```bash
morpheus sync-status --project <projectId>
```

### 4. Requêter

```bash
morpheus requirements find --project <projectId> --query "remember me"
morpheus changes list --project <projectId>
morpheus changes get --project <projectId> --change <changeId>
morpheus constraints list --project <projectId> --change <changeId>
morpheus decisions list --project <projectId> --change <changeId>
morpheus tasks list --project <projectId> --change <changeId>
```

Pagination :

```text
--offset N
--limit N   # 1..100
```

### 5. Traçabilité et contexte

```bash
morpheus trace-requirement --project <projectId> --requirement <requirementId> --depth 2
morpheus change-context --project <projectId> --change <changeId> --depth 2
```

### 6. Analyse M8

```bash
morpheus analyze-change --project <projectId> --change <changeId> --depth 2
```

Le contenu proposé est relu depuis le workspace OpenSpec avec les identités persistantes puis confronté au snapshot CURRENT, sans promotion implicite.

### 7. Qualité M6

```bash
morpheus quality --project <projectId>
```

## JSON

Exemples :

```bash
morpheus --json projects list
morpheus --json requirements find --project <projectId> --query session
morpheus --json change-context --project <projectId> --change <changeId>
morpheus --json analyze-change --project <projectId> --change <changeId>
morpheus --json quality --project <projectId>
```

Les commandes qui possèdent déjà une vue compacte M5/M6/M8 réutilisent cette vue ; les autres utilisent des records CLI simples sérialisés par `CanonicalJsonSerializer`.

## Exit codes

| Code | Nom | Signification |
|---:|---|---|
| 0 | `SUCCESS` | commande réussie |
| 2 | `USAGE` | option/identité/argument invalide |
| 3 | `NOT_FOUND` | projet, snapshot ou entité demandée absente |
| 4 | `STATE_ERROR` | état persisté/synchronisation incompatible |
| 5 | `IO_ERROR` | erreur d'I/O classifiée par l'adapter |
| 10 | `INTERNAL_ERROR` | erreur inattendue |

Pour l'automatisation, le code de sortie est contractuel ; le texte humain d'erreur ne doit pas être parsé comme API.

## Data/config

Voir `distribution/README.md` pour les defaults Windows/Linux, les variables `MORPHEUS_*`, les archives autonomes, le runtime embarqué, l'upgrade et l'uninstall.

## Frontière architecturale

La CLI n'est pas un second moteur métier. Elle dépend des services `morpheus-application` et des adapters provider/store. Les règles d'intention, temporalité, trace, qualité, synchronisation et analyse restent hors de `com.morpheus.cli`.