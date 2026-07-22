# ADR-0011 — Sélectionner les providers par capacités et non par type de source uniquement

- Statut : **Proposée — à valider pendant C0 et M0**
- Date : 22 juillet 2026
- Portée : providers, registry, orchestration d'ingestion

---

## 1. Contexte

Deux providers capables de lire une même famille de sources ne fournissent pas nécessairement les mêmes informations ni les mêmes garanties.

Exemples :

- l'un lit les spécifications courantes mais pas les archives ;
- l'un expose les tâches mais pas les critères d'acceptation ;
- l'un sait produire des deltas incrémentaux ;
- l'autre ne sait faire qu'une lecture complète ;
- l'un est read-only ;
- l'autre peut écrire ou archiver ;
- l'un comprend une version récente du format ;
- l'autre seulement une version ancienne.

Si MORPHEUS sélectionne un provider uniquement sur une règle de type « ce dossier ressemble à OpenSpec », le moteur risque d'utiliser un provider techniquement compatible mais insuffisant pour le cas d'usage demandé.

---

## 2. Problème

Le moteur doit répondre à :

> Quel provider peut satisfaire les capacités nécessaires pour cette source et cette opération précise ?

La notion de compatibilité binaire supporté/non supporté est insuffisante.

---

## 3. Forces en présence

### Extensibilité

De nouveaux providers pourront être ajoutés sans modifier les services métier.

### Dégradation explicite

Un cas d'usage partiellement supporté doit produire un diagnostic, pas un faux succès incomplet.

### Simplicité

Le mécanisme ne doit pas devenir un moteur de planification général.

### Testabilité

Les règles de sélection doivent être déterministes et testables.

### Évolution du format

Les capacités peuvent dépendre de la version de source détectée.

### Séparation lecture/écriture

Les droits de mutation ne doivent jamais être déduits de la capacité de lecture.

---

## 4. Décision proposée

Introduire explicitement :

```text
SpecificationProvider
SpecificationProviderRegistry
ProviderCapability
ProviderCapabilitySet
ProviderSelectionPolicy
```

Un provider expose un ensemble de capacités effectives pour une source donnée.

La sélection est effectuée à partir :

1. de la source détectée ;
2. de la version de format ;
3. des capacités requises ;
4. des contraintes de configuration ;
5. de la politique de fallback.

---

## 5. Capacités initiales candidates

```text
DISCOVER_PROJECT
READ_CURRENT_SPECIFICATIONS
READ_CHANGES
READ_REQUIREMENTS
READ_CONSTRAINTS
READ_SCENARIOS
READ_DESIGN_DECISIONS
READ_ACCEPTANCE_CRITERIA
READ_IMPLEMENTATION_TASKS
READ_HISTORY
READ_ARCHIVES
INCREMENTAL_READ
WATCH_CHANGES
WRITE_CHANGE
WRITE_TASK_STATE
ARCHIVE_CHANGE
```

La taxonomie devra rester stable et contrôlée, mais extensible par évolution du contrat.

---

## 6. Capacités effectives vs théoriques

Un provider peut déclarer une capacité générale mais ne pas pouvoir l'exercer sur une version ou source donnée.

Exemple :

```text
Provider supports READ_ARCHIVES generally
but format version v1 does not expose archives
```

Le résultat du `probe` ou de la découverte doit donc inclure les capacités **effectives**.

---

## 7. Capacités obligatoires et optionnelles

Chaque cas d'usage peut définir :

```text
requiredCapabilities
preferredCapabilities
```

Exemple : UC-03 — lire un changement :

```text
required = READ_CHANGES
preferred = READ_DESIGN_DECISIONS,
            READ_ACCEPTANCE_CRITERIA,
            READ_IMPLEMENTATION_TASKS
```

Si une capacité préférée manque, la réponse reste possible mais expose une dégradation.

Si une capacité obligatoire manque, l'opération échoue explicitement ou demande un autre provider.

---

## 8. Stratégie de sélection

Algorithme conceptuel :

```text
1. probe candidate providers
2. reject unsupported
3. determine effective capabilities
4. filter providers missing required capabilities
5. rank remaining providers
6. select deterministic winner
7. report degraded optional capabilities
```

Le ranking peut considérer :

- préférence configurée ;
- fidélité connue ;
- version supportée ;
- performance ;
- local vs remote ;
- read-only vs writer ;
- capacité incrémentale.

Le ranking final doit rester explicable.

---

## 9. Préférence locale

Conformément au principe local-first, à capacités équivalentes :

```text
local provider > remote provider
```

sauf configuration explicite contraire.

MORPHEUS ne doit pas sélectionner automatiquement un provider cloud simplement parce qu'il offre davantage de fonctionnalités.

---

## 10. Provider explicite

La configuration peut imposer un provider :

```text
provider = openspec
```

Dans ce cas, le moteur vérifie tout de même les capacités et la version.

Une configuration explicite ne doit pas convertir une incompatibilité en succès silencieux.

---

## 11. Multi-provider

Cette ADR n'adopte pas encore une stratégie de composition de plusieurs providers pour une même source.

Deux modes futurs restent possibles :

### Mode A — provider unique

Un provider est choisi comme autorité pour la source.

### Mode B — composition

Plusieurs providers apportent des capacités complémentaires.

Exemple :

```text
Provider A -> specs + changes
Provider B -> history
```

La composition introduit des problèmes d'identité, fusion et provenance. Elle est donc différée tant qu'un besoin réel ne la justifie pas.

---

## 12. Écriture

Une opération d'écriture doit requérir une capacité explicite :

```text
WRITE_CHANGE
WRITE_TASK_STATE
ARCHIVE_CHANGE
```

`READ_CHANGES` n'implique jamais `WRITE_CHANGE`.

Les providers read-only restent valides et préférables pour de nombreux usages.

---

## 13. Diagnostics de négociation

Exemples :

```text
NO_PROVIDER_FOUND
UNSUPPORTED_SOURCE
UNSUPPORTED_FORMAT_VERSION
MISSING_REQUIRED_CAPABILITY
OPTIONAL_CAPABILITY_UNAVAILABLE
MULTIPLE_PROVIDER_MATCHES
EXPLICIT_PROVIDER_INCOMPATIBLE
REMOTE_PROVIDER_REQUIRES_OPT_IN
```

Ces diagnostics doivent être visibles via CLI/API/MCP à terme.

---

## 14. Conséquences positives

- architecture réellement provider-agnostic ;
- nouveaux providers ajoutables sans modifier les services métier ;
- dégradation contrôlée ;
- meilleur support des versions de formats ;
- lecture/écriture séparées ;
- sélection explicable ;
- possibilité future de fallback ;
- meilleure sécurité local-first.

---

## 15. Conséquences négatives

- registre plus complexe ;
- taxonomie de capacités à gouverner ;
- besoin de tests de compatibilité ;
- ambiguïtés possibles lorsque plusieurs providers correspondent ;
- configuration plus riche ;
- évolution du contrat si une nouvelle capacité générique apparaît.

---

## 16. Alternatives étudiées

### A. Sélection uniquement par format

**Rejetée.**

Trop grossière et incapable de représenter la dégradation fonctionnelle.

### B. Un provider unique codé en dur

**Rejetée comme architecture MORPHEUS.**

Contradictoire avec l'indépendance de format.

### C. Détection par capacités

**Retenue.**

Elle correspond directement aux cas d'usage et permet l'extension.

### D. Composer plusieurs providers dès M0

**Différée.**

Complexité prématurée tant qu'un besoin réel n'est pas démontré.

---

## 17. Risques et mitigations

### Risque — explosion du nombre de capacités

Mitigation : capacités centrées sur des catégories d'information ou opérations génériques, pas sur chaque détail provider.

### Risque — providers qui mentent sur leurs capacités

Mitigation : tests de contrat et probes réels sur fixtures.

### Risque — ranking instable

Mitigation : politique déterministe et ordre de priorité documenté.

### Risque — sélection d'un provider distant

Mitigation : opt-in obligatoire pour tout accès externe et préférence locale par défaut.

---

## 18. Validation M0

Le provider OpenSpec de référence doit être capable de produire un `ProviderCapabilitySet` vérifiable sur :

- D1 ;
- D3 ;
- D4 ;
- D6.

Les tests doivent couvrir :

```text
one matching provider
no matching provider
multiple matches
missing required capability
missing optional capability
unsupported version
explicit incompatible provider
read-only provider
```

---

## 19. Critères d'acceptation

Cette ADR peut passer à **Acceptée** lorsque :

1. la taxonomie MVP des capacités est stabilisée ;
2. les capacités nécessaires aux UC MVP sont mappées ;
3. le registry sélectionne de manière déterministe le provider sur les fixtures ;
4. les capacités manquantes produisent des diagnostics explicites ;
5. les écritures nécessitent des capacités séparées ;
6. les providers distants ne sont jamais activés sans opt-in ;
7. au moins un fake provider ou second provider de test valide l'absence de couplage au provider de référence.

---

## 20. Impact sur les autres décisions

Cette ADR influence :

- M1 — découverte et providers ;
- contrat `SpecificationProvider` ;
- stratégie OpenSpec ;
- ingestion incrémentale ;
- sécurité ;
- CLI/MCP/API ;
- possibilité future de composition multi-provider.