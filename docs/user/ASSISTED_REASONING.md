# Utiliser l’analyse assistée fondée sur des preuves

M27 permet de demander à MORPHEUS d’enrichir un ensemble de preuves par des **inférences**, des **heuristiques** et des **suggestions** clairement identifiées.

Cette fonction est volontairement prudente : une sortie assistée ne devient jamais automatiquement un fait publié et ne modifie jamais une spécification.

## 1. Ce que MORPHEUS distingue

### Preuves

Vous fournissez des éléments typés :

```text
PUBLISHED_FACT    fait publié dans une source autoritative
SOURCE_EXCERPT    extrait d’une source ou d’un document
POLICY_RESULT     résultat d’une évaluation de politique
EXTERNAL_CONTEXT  contexte fourni par une intégration externe
OBSERVATION       observation non encore publiée comme fait
```

### Résultats assistés

MORPHEUS peut retourner :

```text
INFERENCE    conclusion déduite des preuves
HEURISTIC    appréciation produite par une règle ou méthode approximative
SUGGESTION   action ou vérification proposée
```

Chaque résultat assisté contient :

- un score de confiance entre `0` et `1` ;
- une bande de confiance lisible ;
- les identifiants des preuves utilisées ;
- l’adaptateur qui l’a produit ;
- sa provenance méthodologique.

## 2. Ce que M27 ne fait jamais

```text
une inference ne remplace pas un fait
une suggestion ne déclenche pas une action
une heuristic ne bloque pas un lifecycle
une analyse ne modifie pas une policy
une analyse ne publie rien
```

Toutes les réponses contiennent `mutated=false`.

## 3. Lister les adaptateurs

```powershell
morpheus --json reason adapters
```

L’installation standard inclut :

```text
builtin-evidence-synthesis-v1
```

Cet adaptateur est local, déterministe, sans réseau et sans LLM.

La liste indique ce qui est disponible ; elle n’exécute aucun adaptateur.

## 4. Mode facts-only

Vous pouvez utiliser l’enveloppe M27 sans sélectionner d’adaptateur :

```powershell
morpheus --json reason analyze `
  --question "What remains authoritative?" `
  --evidence "fact-1|PUBLISHED_FACT|history|Published history remains authoritative|source=snapshot"
```

Résultat attendu :

```json
{
  "facts": ["..."],
  "inferences": [],
  "heuristics": [],
  "suggestions": [],
  "executions": [],
  "assisted": false,
  "mutated": false
}
```

Ce mode est utile pour uniformiser une réponse factuelle tout en garantissant qu’aucune analyse assistée n’a été exécutée.

## 5. Analyse assistée explicite

```powershell
morpheus --json reason analyze `
  --question "Can remote mode be enabled safely?" `
  --evidence "fact-1|PUBLISHED_FACT|remote|TLS is required|source=published" `
  --evidence "fact-2|PUBLISHED_FACT|remote|Authentication is required|source=published" `
  --evidence "obs-1|OBSERVATION|deployment|The keystore is not provisioned|source=operator" `
  --adapter builtin-evidence-synthesis-v1 `
  --max-claims 10
```

L’adaptateur n’est exécuté que parce que `--adapter` est présent.

## 6. Format des preuves CLI

```text
id|kind|subject|statement[|key=value,key=value]
```

Exemple :

```text
fact-1|PUBLISHED_FACT|authentication|TLS is mandatory|source=adr-0094,version=1
```

Contraintes :

- `id` unique dans la requête ;
- `kind` parmi les cinq catégories ;
- sujet et texte non vides ;
- provenance recommandée ;
- pas de caractère `|` dans les champs CLI.

Pour des textes riches ou contenant `|`, utilisez l’API HTTP ou MCP.

## 7. Comprendre la confiance

```text
VERY_LOW   0.00 à moins de 0.20
LOW        0.20 à moins de 0.40
MEDIUM     0.40 à moins de 0.65
HIGH       0.65 à moins de 0.85
VERY_HIGH  0.85 à 1.00
```

Un score élevé ne transforme pas la claim en fait. Il signifie uniquement que l’adaptateur exprime un niveau de confiance élevé selon sa propre méthode.

Avant d’utiliser une claim :

1. vérifiez sa catégorie ;
2. consultez `evidenceIds` ;
3. relisez les preuves correspondantes ;
4. vérifiez `adapterId` et la provenance ;
5. confirmez manuellement toute action ou publication ultérieure.

## 8. Échecs d’adaptateurs

Une panne d’adaptateur apparaît dans `executions` :

```json
{
  "adapterId": "example-adapter",
  "status": "FAILED",
  "acceptedClaims": 0,
  "message": "..."
}
```

Les faits fournis restent présents. MORPHEUS n’accepte pas une partie des claims d’un adaptateur en panne.

Un identifiant d’adaptateur inconnu provoque une erreur explicite ; MORPHEUS ne choisit pas automatiquement un autre moteur.

## 9. API HTTP

### Lister

```http
GET /api/v1/reasoning/adapters
```

### Analyser

```http
POST /api/v1/reasoning/analyze
Content-Type: application/json
```

```json
{
  "question": "Can remote mode be enabled safely?",
  "evidence": [
    {
      "id": "fact-1",
      "kind": "PUBLISHED_FACT",
      "subject": "remote",
      "statement": "TLS is required",
      "provenance": {"source": "ADR-0094"}
    },
    {
      "id": "fact-2",
      "kind": "PUBLISHED_FACT",
      "subject": "remote",
      "statement": "Authentication is required",
      "provenance": {"source": "ADR-0094"}
    }
  ],
  "adapterIds": ["builtin-evidence-synthesis-v1"],
  "parameters": {},
  "maxClaims": 10
}
```

En mode serveur remote, ces deux routes exigent au minimum le rôle READ. L’analyse reste une opération read-only même si elle utilise HTTP POST.

## 10. MCP

Outils :

```text
list_reasoning_adapters
reason_with_evidence
```

`reason_with_evidence` accepte la même structure conceptuelle que l’API HTTP. Une liste `adapterIds` vide conserve le mode facts-only.

## 11. Budgets

```text
preuves                 256 maximum
adaptateurs sélectionnés  8 maximum
claims                  256 maximum
citations par claim      32 maximum
question               8192 caractères
statement             16384 caractères
body HTTP             65536 octets
```

MORPHEUS rejette un dépassement au lieu de tronquer silencieusement une analyse.

## 12. Bonnes pratiques

- utilisez `PUBLISHED_FACT` uniquement pour une vérité réellement publiée ;
- classez une donnée incertaine comme `OBSERVATION` ;
- fournissez une provenance utile ;
- sélectionnez l’adaptateur explicitement ;
- traitez les suggestions comme des propositions ;
- ne copiez jamais automatiquement une inference dans une spécification ;
- conservez les preuves avec le résultat lors d’une revue ;
- vérifiez les claims à faible confiance ou reposant sur peu de preuves.

## 13. Limites de M27

M27 ne contient pas :

- de connexion native à un service LLM ;
- de clé API ;
- de mémoire conversationnelle persistée ;
- de promotion automatique ;
- de commande d’application ;
- de nouvelle table SQLite ;
- de changement de lifecycle.

Voir aussi le contrat OpenAPI [`../openapi/morpheus-v1-reasoning-m27.yaml`](../openapi/morpheus-v1-reasoning-m27.yaml).
