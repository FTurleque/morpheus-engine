# Evidence-backed Assisted Reasoning — guide développeur

M27 ajoute une capacité d’analyse assistée **read-only**, provider-neutral et optionnelle. Le code vit principalement dans :

```text
morpheus-application/src/main/java/com/morpheus/application/reasoning
morpheus-cli/src/main/java/com/morpheus/cli/MorpheusReasoningCli.java
morpheus-mcp/src/main/java/com/morpheus/mcp/MorpheusReasoningMcpTools.java
morpheus-api/src/main/java/com/morpheus/api/MorpheusReasoning*.java
```

## 1. Frontière fondamentale

```text
published evidence                    assisted claims
------------------                    ---------------
PUBLISHED_FACT                        INFERENCE
SOURCE_EXCERPT                        HEURISTIC
POLICY_RESULT                         SUGGESTION
EXTERNAL_CONTEXT
OBSERVATION
```

Un fait n’est jamais une claim. Une claim ne peut donc pas être sérialisée dans la collection `facts` par simple changement de score ou de provenance.

Le résultat impose :

```java
mutated == false
facts.equals(evidence.stream()
        .filter(item -> item.kind() == PUBLISHED_FACT)
        .toList())
```

## 2. Contrats principaux

### `ReasoningContracts.Evidence`

```java
Evidence(
    String id,
    EvidenceKind kind,
    String subject,
    String statement,
    Map<String, String> provenance)
```

L’identité est locale à l’enveloppe de requête, mais doit être unique. `provenance` doit identifier l’origine utile au consommateur : snapshot, provider, document, version, règle, integration, etc.

### `ReasoningContracts.Claim`

```java
Claim(
    String id,
    ClaimKind kind,
    String statement,
    Confidence confidence,
    List<String> evidenceIds,
    String adapterId,
    Map<String, String> provenance)
```

Invariants :

- au moins une citation ;
- toutes les citations existent dans la requête ;
- `adapterId` correspond à l’adaptateur exécuté ;
- identité unique dans le résultat ;
- score fini dans `[0,1]` ;
- bande dérivée du score ;
- nature de claim cohérente avec sa collection de sortie.

### `ReasoningContracts.Request`

Une requête sans `adapterIds` est valide et produit un résultat facts-only. Ce comportement doit rester le chemin nominal minimal.

### `ReasoningContracts.Result`

Les collections sont séparées :

```text
evidence
facts
inferences
heuristics
suggestions
executions
```

Ne jamais fournir un champ générique `answers` ou `items` qui obligerait les consommateurs à deviner la nature sémantique de chaque valeur.

## 3. Service d’orchestration

`ReasoningService` :

1. valide l’unicité de l’évidence ;
2. calcule les faits à partir de `PUBLISHED_FACT` ;
3. résout uniquement les adaptateurs explicitement demandés ;
4. exécute chaque adaptateur indépendamment ;
5. valide toute la sortie de l’adaptateur ;
6. accepte ou rejette atomiquement ses claims ;
7. conserve toujours les preuves et les faits ;
8. répartit les claims par nature ;
9. construit un résultat non mutant.

Une sortie partiellement valide d’un adaptateur n’est pas partiellement intégrée.

## 4. Écrire un adaptateur

Implémenter :

```java
public interface ReasoningAdapter {
    String id();
    String description();
    ReasoningContracts.AdapterResult reason(
        ReasoningContracts.AdapterRequest request);
}
```

Règles :

- identifiant stable et versionné si la sémantique change ;
- aucune mutation de store ou lifecycle ;
- aucune claim sans preuve ;
- respecter `request.maxClaims()` ;
- retourner son propre `adapterId` dans chaque claim ;
- déclarer la méthode/modèle/version dans la provenance ;
- ne jamais présenter le score comme un fait ;
- encapsuler les erreurs provider-specific dans une exception non secrète ;
- ne jamais inclure token, clé API, prompt secret ou données sensibles dans les metadata.

Exemple d’identité :

```text
acme-requirement-risk-v2
```

Une modification incompatible du raisonnement doit changer l’identité ou exposer une version explicite dans la provenance.

## 5. Découverte

Le registre standard inclut `builtin-evidence-synthesis-v1` et interroge `ServiceLoader<ReasoningAdapter>`.

La découverte est fault-isolated :

- provider invalide : ignoré ;
- constructeur provider en panne : ignoré ;
- description provider en panne : ignorée dans le catalogue ;
- identifiant dupliqué : premier provider conservé ;
- aucun provider externe : fonctionnement normal.

La découverte ne déclenche jamais `reason()`.

Pour enregistrer un provider Java :

```text
META-INF/services/com.morpheus.application.reasoning.ReasoningAdapter
```

Le fichier contient le nom qualifié de l’implémentation.

## 6. Adaptateur builtin

`EvidenceSynthesisReasoningAdapter` est une référence déterministe :

- pas de réseau ;
- pas de LLM ;
- pas de store ;
- couverture des faits publiée comme heuristic ;
- inference multi-faits seulement à partir de plusieurs `PUBLISHED_FACT` ;
- suggestion de revue lorsque l’enveloppe contient des observations non publiées.

Il montre le contrat, pas une vérité métier universelle.

## 7. Surfaces

### CLI

```powershell
morpheus reason --help
morpheus --json reason adapters
morpheus --json reason analyze `
  --question "Can remote mode be enabled safely?" `
  --evidence "fact-1|PUBLISHED_FACT|remote|TLS is required|source=published" `
  --evidence "fact-2|PUBLISHED_FACT|remote|Authentication is required|source=published" `
  --adapter builtin-evidence-synthesis-v1
```

Le séparateur CLI est :

```text
id|kind|subject|statement[|key=value,key=value]
```

Pour des textes contenant `|`, préférer MCP ou HTTP.

### MCP

```text
list_reasoning_adapters
reason_with_evidence
```

Le schéma MCP est strict (`additionalProperties=false`) et expose tous les budgets.

### HTTP

```text
GET  /api/v1/reasoning/adapters
POST /api/v1/reasoning/analyze
```

Le body HTTP est limité à 65 536 octets. La façade remote classe l’analyse en READ et le test TLS/RBAC exécute réellement cette route avec une identité READ.

## 8. Persistance et transactions

Aucune classe M27 ne dépend d’un store concret ou d’un port de persistance. Aucun résultat n’est sauvegardé automatiquement.

Un appelant peut conserver la réponse comme artefact externe, mais cela ne lui confère aucune autorité de spécification. Toute future promotion doit réutiliser les mécanismes explicites d’écriture, de confirmation, de CAS et d’audit.

## 9. Sécurité

Un adaptateur externe est du code Java exécuté dans le process ; `ServiceLoader` n’est pas une sandbox. Les règles de confiance des plugins restent applicables.

Pour un adaptateur réseau :

- activation explicitement configurée ;
- timeouts bornés ;
- taille de réponse bornée ;
- allowlist d’endpoint si applicable ;
- secrets hors arguments, logs, metadata et sorties ;
- erreurs nettoyées ;
- absence de retry infini ;
- aucune activation automatique au démarrage.

M27 n’inclut volontairement aucun client LLM ni gestionnaire de clé API.

## 10. Tests attendus

Chaque adaptateur doit couvrir :

- requête vide ;
- evidence facts-only ;
- evidence mixte ;
- budget minimal ;
- citation valide ;
- aucune citation inconnue ;
- score aux bornes `0` et `1` ;
- panne contrôlée ;
- absence de mutation ;
- déterminisme si revendiqué.

Les tests M27 de plateforme couvrent en plus la convergence des surfaces, les schémas stricts, l’isolation des providers, les entrées HTTP nulles, le RBAC remote et les frontières ArchUnit/source.

## 11. Qualification

```powershell
.\validate-m27.cmd 1.0.0
```

```bash
bash ./scripts/validate-m27.sh 1.0.0
```

Minimums :

```text
602 tests
238 architecture tests
42% line coverage
35% branch coverage
```

Voir [`../roadmap/M27_EXECUTION.md`](../roadmap/M27_EXECUTION.md) et [`../adr/0095-evidence-backed-assisted-reasoning.md`](../adr/0095-evidence-backed-assisted-reasoning.md).
