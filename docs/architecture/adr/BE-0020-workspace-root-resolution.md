---
id: BE-0020
title: "Résoudre la racine de workspace sans dépendance Git obligatoire"
domain: backend
rules: true
files: ["morpheus-application/src/main/java/com/morpheus/application/discovery/**/*.java"]
---

# Résoudre la racine de workspace sans dépendance Git obligatoire

- Statut : **Acceptée**
- Date : 2026-07-22

## Context

MORPHEUS découvre de manière déterministe un workspace local et les sources de spécification qu'il contient, à partir
d'un `Path` sur lequel il exécute les probes des providers. Les cas réels imposent plus qu'un chemin explicite :

- l'utilisateur peut lancer MORPHEUS depuis un sous-répertoire ;
- un dépôt Git peut contenir plusieurs sous-projets ;
- un workspace peut ne pas utiliser Git ;
- le chemin peut être Windows, Unix, relatif ou absolu ;
- un provider peut être présent au chemin explicite même si une racine Git existe plus haut ;
- Git ne doit pas devenir une dépendance obligatoire du cœur MORPHEUS.

Une stratégie naïve « remonter systématiquement jusqu'à la racine Git » casse les monorepos :

```text
repo/
├── .git/
├── service-a/
│   └── openspec/
└── service-b/
```

Pour une invocation sur `repo/service-a`, la racine fonctionnelle pertinente peut être `service-a`, pas
nécessairement `repo`. À l'inverse, une stratégie « ne jamais remonter » échoue lorsqu'un utilisateur lance MORPHEUS
depuis `repo/src/main/java` alors que la source de spécification est à `repo/openspec`.

Forces en présence : préserver le chemin explicitement fourni ; supporter les monorepos et les workspaces sans Git ;
ne pas exiger le binaire `git` ni exécuter de commande externe pendant la discovery ; rester déterministe et
explicable ; garder la logique de format dans les providers ; éviter une exploration récursive arbitraire du
filesystem ; produire un locator stable et portable entre Windows et Unix lorsque possible.

Options considérées :

- **Toujours utiliser la racine Git** : rejetée, simple mais incorrecte pour les monorepos et les sous-projets
  autonomes.
- **Ne jamais utiliser Git** : rejetée, trop fragile lorsqu'une commande est lancée depuis un sous-répertoire
  ordinaire du projet.
- **Exécuter `git rev-parse --show-toplevel`** : rejetée, elle introduit une dépendance au binaire Git et à
  l'exécution de processus externes alors qu'un marqueur structurel suffit.
- **Recherche récursive globale de toutes les sources** : différée, elle augmente fortement le coût, les règles
  d'exclusion et les ambiguïtés.
- **Explicit-first + fallback Git structurel** : retenue, elle protège les monorepos et reste locale, déterministe et
  sans dépendance externe.

L'ordre de résolution retenu :

```text
1. chemin explicite
2. racine Git ancêtre, seulement en fallback
3. échec déterministe
```

Le marqueur `.git` peut être un fichier pour rester compatible avec les worktrees et certaines configurations Git. La
discovery distingue `workspace root`, `provider probe` et `specification source` ; le locator d'un provider explique
pourquoi il a matché, par exemple pour OpenSpec `scheme = file`, `value = openspec/config.yaml`. Le séparateur `/`
évite d'injecter les séparateurs Windows dans les contrats persistables, et la normalisation lexicale sans
`toRealPath()` évite de modifier silencieusement l'identité visible des chemins.

## Decision

Stratégie **explicit-first avec fallback Git structurel**, portée par `WorkspaceRootResolver` et
`ProjectDiscoveryService` (`com.morpheus.application.discovery`). Git n'est pas une dépendance obligatoire du cœur.

1. **Chemin explicite** : `requestedPath` passe par `toAbsolutePath().normalize()`, doit exister et être un
   répertoire, sinon diagnostic `INVALID_SOURCE`. Les providers sont découverts d'abord sur ce chemin.
2. **Fallback Git** : seulement si aucune source n'est reconnue au chemin explicite, recherche vers les parents du
   premier répertoire portant un marqueur `.git`, fichier ou répertoire ; les probes sont réessayés sur une racine
   distincte trouvée. Aucune commande `git` ni processus externe n'est exécuté ; sinon, échec déterministe.
3. **Priorité** : un provider valide au chemin explicite n'est jamais remplacé par un provider trouvé plus haut.
4. **Source reconnue mais invalide ou non supportée** (schéma OpenSpec inconnu, configuration illisible) : les
   diagnostics du provider au chemin explicite sont rendus ; aucun fallback ne la masque.
5. **Workspace sans Git** : valide, le chemin explicite normalisé est sa racine.
6. **Inventaire** : les providers restent propriétaires de leurs signatures de format ; un provider supporté expose un
   locator qui explique son match ; les locators fichiers relatifs utilisent `/`. La discovery ne parse ni exigence, ni
   changement, ni aucun concept métier OpenSpec.
7. **Liens symboliques** : normalisation lexicale ; pas de canonicalisation par `toRealPath()` sans cas démontré.
8. **Résultat** : expose `requestedPath`, le `workspaceRoot` résolu et l'origine de la résolution. Un locator n'est
   jamais une `DomainIdentity`.

## Do's and Don'ts

### Do

- Normaliser le chemin demandé par `toAbsolutePath().normalize()` et refuser un chemin qui n'est pas un répertoire par
  `INVALID_SOURCE`.
- Évaluer le chemin explicite avant toute racine Git ancêtre.
- Accepter un marqueur `.git` fichier comme répertoire.
- Rendre les diagnostics du provider au chemin explicite quand une source y est reconnue mais invalide.
- Exposer `requestedPath`, la racine résolue et l'origine de la résolution.
- Écrire les locators fichiers relatifs avec `/` comme séparateur.

### Don't

- Ne pas exécuter le binaire `git` ni aucun processus externe pendant la discovery.
- Ne pas remonter vers une racine Git quand une source est reconnue au chemin explicite.
- Ne pas parser d'exigence ni de changement pendant la discovery.
- Ne pas utiliser un locator comme `DomainIdentity`.
- Ne pas canonicaliser les chemins par `toRealPath()` sans cas démontré.

## Consequences

### Positives

- Comportement prévisible en monorepo.
- Fonctionnement sans Git installé et support naturel des workspaces non-Git.
- Aucun processus externe.
- Diagnostics non masqués et résultat de discovery explicable.
- Locators compatibles avec la provenance.

### Négatives

- Une racine Git détectée par marqueur n'offre pas toutes les garanties de `git rev-parse`.
- Les sous-modules et worktrees complexes peuvent nécessiter des raffinements.
- Une source située dans un répertoire frère n'est pas découverte automatiquement.
- La recherche récursive multi-source n'est pas couverte.

### Risques

- **Marqueur `.git` atypique** : mitigé en acceptant fichier ou répertoire, les deux cas étant testés.
- **Monorepo avec source au sous-projet et à la racine** : mitigé, le chemin explicite gagne dès qu'une source y est
  reconnue.
- **Source invalide au sous-projet et valide à la racine** : mitigé, une signature reconnue arrête le fallback et ses
  diagnostics sont rendus.
- **Liens symboliques et jonctions** : la normalisation lexicale peut diverger du chemin physique ; accepté tant
  qu'aucun cas démontré n'impose autre chose.

## Compliance and Enforcement

- Règle Archgate `BE-0020-workspace-root-resolution.rules.ts` : `workspace-discovery-starts-no-process`.
- `morpheus-application/src/test/java/com/morpheus/application/discovery/WorkspaceRootResolverTest.java` : chemin
  explicite en premier, marqueur `.git` fichier accepté, workspace non-Git.
- `morpheus-application/src/test/java/com/morpheus/application/discovery/ProjectDiscoveryServiceTest.java` : source
  explicite prioritaire, fallback Git, source reconnue invalide non masquée, workspace non-Git, diagnostic
  structuré d'un chemin invalide, déterminisme sur exécutions répétées.
- `morpheus-provider-openspec/src/test/java/com/morpheus/provider/openspec/OpenSpecDiscoveryIntegrationTest.java` :
  fallback depuis un sous-répertoire et OpenSpec invalide au chemin explicite non masqué par la racine Git.
- `morpheus-application/src/test/java/com/morpheus/application/provider/ProviderSelectionPolicyTest.java` : sélection
  du provider parmi les probes.
- Revue : absence d'exécution de `git` et de canonicalisation physique dans la discovery.

## References

- [ADR-0135](ARCH-0135-domaine-propre-et-neutralite-multi-provider.md) — domaine propre à MORPHEUS.
- [ADR-0169](ARCH-0169-openspec-provider-de-reference-et-schemas-qui-derivent-de-spec-driven.md) — OpenSpec, provider de référence.
- [ADR-0011](BE-0011-provider-capability-negotiation.md) — négociation des capacités des providers.
- [ADR-0133](GEN-0133-toolchain-java-21-et-maven-wrapper.md) — fondation de build Maven.
- [VALIDATION_M1](../../project/validation/VALIDATION_M1.md) — preuve du jalon M1.
