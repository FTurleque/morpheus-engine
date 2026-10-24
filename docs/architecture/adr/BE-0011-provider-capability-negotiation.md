---
id: BE-0011
title: "Sélectionner les providers par capacités et non par type de source uniquement"
domain: backend
rules: false
---

# Sélectionner les providers par capacités et non par type de source uniquement

- Statut : **Acceptée**
- Date : 2026-07-22

## Context

Deux providers capables de lire une même famille de sources ne fournissent pas forcément les mêmes informations ni les
mêmes garanties : l'un lit les spécifications courantes mais pas les archives, l'un expose les tâches mais pas les
critères d'acceptation, l'un produit des deltas incrémentaux quand l'autre ne fait qu'une lecture complète, l'un est
read-only quand l'autre peut écrire ou archiver, l'un comprend une version récente du format et l'autre seulement une
ancienne.

Une sélection sur une règle du type « ce dossier ressemble à OpenSpec » risque de retenir un provider techniquement
compatible mais insuffisant pour le cas d'usage. Le moteur doit répondre à : quel provider peut satisfaire les
capacités nécessaires pour cette source et cette opération précise ? Une compatibilité binaire supporté/non supporté
ne suffit pas.

Forces en présence : ajouter des providers sans modifier les services métier ; produire un diagnostic plutôt qu'un
faux succès incomplet ; ne pas devenir un moteur de planification général ; garder des règles de sélection
déterministes et testables ; tenir compte de la version de source détectée ; ne jamais déduire un droit de mutation
d'une capacité de lecture.

Options étudiées :

| Option | Verdict | Raison |
|---|---|---|
| Sélection uniquement par format | Rejetée | Trop grossière, incapable de représenter la dégradation fonctionnelle |
| Un provider unique codé en dur | Rejetée | Contradictoire avec l'indépendance de format |
| Détection par capacités | Retenue | Correspond aux cas d'usage et permet l'extension |
| Composer plusieurs providers pour une même source | Hors de cet ADR | Introduit identité, fusion et provenance : décidé par ADR-0084 |

## Decision

La sélection repose sur les types `SpecificationProvider`, `SpecificationProviderRegistry` et
`ProviderSelectionPolicy` (`morpheus-application`), `ProviderCapability` et `ProviderCapabilitySet`
(`morpheus-domain`).

Un provider expose un ensemble de capacités **effectives** pour une source donnée. Une capacité déclarée en général
mais inexerçable sur une version ou une source donnée n'en fait pas partie : le résultat du probe ou de la découverte
porte les capacités effectives.

La sélection part de la source détectée, de la version de format, des capacités requises, des contraintes de
configuration et de la politique de fallback :

```text
1. probe candidate providers
2. reject unsupported
3. determine effective capabilities
4. filter providers missing required capabilities
5. rank remaining providers
6. select deterministic winner
7. report degraded optional capabilities
```

**Taxonomie.** Les capacités sont l'énumération `ProviderCapability`. Elle reste stable et contrôlée, extensible par
évolution du contrat, et centrée sur des catégories d'information ou des opérations génériques plutôt que sur chaque
détail d'un provider.

**Requises et préférées.** Un cas d'usage définit `requiredCapabilities` et `preferredCapabilities`. Par exemple,
lire un changement exige `READ_CHANGES` et préfère `READ_DESIGN_DECISIONS`, `READ_ACCEPTANCE_CRITERIA`,
`READ_IMPLEMENTATION_TASKS`. Une capacité préférée manquante laisse la réponse possible mais expose une dégradation ;
une capacité requise manquante fait échouer l'opération explicitement ou demande un autre provider.

**Ranking.** Il reste déterministe et explicable. Il peut considérer la préférence configurée, la fidélité connue, la
version supportée, la performance, local ou distant, read-only ou writer, la capacité incrémentale.

**Préférence locale.** À capacités équivalentes, un provider local l'emporte sur un provider distant, sauf
configuration explicite contraire. Un provider distant n'est jamais retenu sans opt-in, même s'il offre davantage de
fonctionnalités.

**Provider explicite.** Une configuration peut imposer un provider (`provider = openspec`) ; le moteur vérifie tout de
même ses capacités et sa version, et une incompatibilité ne devient jamais un succès silencieux.

**Écriture.** Une opération d'écriture requiert une capacité explicite (`WRITE_CHANGE`, `WRITE_TASK_STATE`,
`ARCHIVE_CHANGE`). `READ_CHANGES` n'implique jamais `WRITE_CHANGE`. Un provider read-only reste valide et préférable
pour de nombreux usages.

**Diagnostics de négociation.** La sélection nomme son issue par un code : `NO_PROVIDER_FOUND`,
`UNSUPPORTED_SOURCE`, `UNSUPPORTED_FORMAT_VERSION`, `MISSING_REQUIRED_CAPABILITY`,
`OPTIONAL_CAPABILITY_UNAVAILABLE`, `MULTIPLE_PROVIDER_MATCHES`, `EXPLICIT_PROVIDER_INCOMPATIBLE`,
`REMOTE_PROVIDER_REQUIRES_OPT_IN`. Ces diagnostics ont vocation à être visibles par CLI, API et MCP.

**Composition.** Cet ADR choisit un provider par source. La composition de plusieurs providers relève d'ADR-0084.

## Do's and Don'ts

### Do

- Calculer les capacités effectives d'un provider pour la source et la version détectées.
- Déclarer pour chaque cas d'usage ses capacités requises et préférées.
- Exposer une dégradation quand une capacité préférée manque.
- Vérifier capacités et version d'un provider imposé par configuration.
- Préférer un provider local à capacités équivalentes.

### Don't

- Ne pas sélectionner un provider sur la seule ressemblance du format.
- Ne pas déduire une capacité d'écriture d'une capacité de lecture.
- Ne pas retenir un provider distant sans opt-in explicite.
- Ne pas transformer une incompatibilité d'un provider explicite en succès.
- Ne pas laisser le ranking dépendre d'un ordre non déterministe.

## Consequences

### Positives

- Architecture réellement provider-agnostic : nouveaux providers sans modification des services métier.
- Dégradation contrôlée et meilleur support des versions de formats.
- Lecture et écriture séparées ; sélection explicable ; fallback possible.
- Meilleure sécurité local-first.

### Négatives

- Registre plus complexe et taxonomie de capacités à gouverner.
- Tests de compatibilité nécessaires.
- Ambiguïtés possibles lorsque plusieurs providers correspondent.
- Configuration plus riche, et évolution du contrat si une nouvelle capacité générique apparaît.

### Risques

- **Explosion du nombre de capacités** : capacités centrées sur des catégories génériques.
- **Provider qui ment sur ses capacités** : tests de contrat et probes réels sur fixtures.
- **Ranking instable** : politique déterministe et ordre de priorité documenté.
- **Sélection d'un provider distant** : opt-in obligatoire et préférence locale par défaut.

## Compliance and Enforcement

- `morpheus-application/src/test/java/com/morpheus/application/provider/ProviderSelectionPolicyTest.java` : capacités
  requises et préférées, préférence locale, opt-in distant, échec explicite d'une capacité requise manquante,
  provider explicite incompatible, résolution déterministe des providers équivalents.
- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/ProviderAntiLockInTest.java` : plusieurs
  providers implémentent le même port, sans couplage au provider de référence.
- `morpheus-provider-openspec/src/test/java/com/morpheus/provider/openspec/OpenSpecDiscoveryIntegrationTest.java` :
  découverte et capacités du provider OpenSpec sur fixtures.
- Revue : séparation des capacités d'écriture dans les nouveaux cas d'usage.

## References

- [ADR-0084](ARCH-0084-provider-neutral-multi-provider-composition.md) — composition multi-provider.
- [ADR-0008](SEC-0008-read-first-write-capability.md) — providers read-first, écriture optionnelle.
- [ADR-0028](BE-0028-unified-provider-read-contract.md) — contrat de lecture unifié.
- [ADR-0004](SEC-0004-local-first-no-llm-core.md) — cœur local-first.
- [VALIDATION_M0](../../project/validation/VALIDATION_M0.md).
