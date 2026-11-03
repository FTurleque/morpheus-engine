---
id: SEC-0157
title: "Aucun contenu de l'espace de travail n'est lu à travers un lien symbolique ni hors de sa frontière"
domain: security
rules: false
---

# Aucun contenu de l'espace de travail n'est lu à travers un lien symbolique ni hors de sa frontière

- Statut : **Acceptée**
- Date : 2026-11-03

## Context

`morpheus-application` lit l'espace de travail local par deux frontières, toutes deux nommées par ADR-0150 comme
classes à accès fichier :

- `files.SafeWorkspaceFileResolver` sert les lectures des providers (OpenSpec, Markdown, synthétique) et les fichiers
  du serveur remote. Avant de lire, il refuse tout segment symbolique du chemin (`rejectSymbolicComponents`), lit en
  `NOFOLLOW_LINKS`, puis revalide identité et contenu après lecture. Il ne garde aucune réponse en cache ; seule la
  racine physique (`realRoot`) est résolue une fois, à la construction.
- `sync.ScanWorkspaceBoundary` sert le scan d'inventaire (`LocalSourceInventoryScanner`) et le watcher
  (`WorkspacePathBoundary`). Pour que le coût d'un scan croisse avec les entrées et non avec leur profondeur, il
  mémorise les répertoires déjà validés et la racine physique `physicalWorkspace`.

Un chemin rapide qui ne questionnerait que le segment de l'entrée laisserait passer un ancêtre déjà validé
remplacé par un lien résolu **dans** l'espace de travail, ou une racine remplacée par un lien : le refus viendrait
après que le fichier a été ouvert, lu et empreinté, et le parcours continuerait à lire à travers le lien. Le même
espace de travail aurait deux garanties selon le chemin d'entrée. ADR-0087 dit que le scan ne suit pas les liens ;
il ne dit pas **quand** le refus a lieu, et cette décision le fixe (constats AUD-SEC-17 et AUD-SEC-18 de l'audit
du 2026-10-09).

Options considérées :

- **Propriété faible** — « rien hors frontière n'entre dans l'inventaire », le refus pouvant suivre la lecture.
  Écartée : sur un espace de travail partagé ou monté, la lecture et l'empreinte d'un fichier refusé ont déjà eu lieu.
- **Marche complète avant chaque lecture** — quatre requêtes par segment et par entrée. Écartée : la propriété
  n'exige que le test de lien ; la résolution des segments au-dessus d'une entrée ne dit rien de plus avant lecture.
- **Test de lien sur chaque segment avant lecture, résolution du seul segment de l'entrée** — retenue. Elle coûte une
  requête par segment au-dessus de l'entrée, en plus des quatre de son propre segment.

Java NIO n'offre pas d'ouverture relative à un descripteur de répertoire (`openat`) : entre le dernier test et
l'ouverture, un remplacement reste possible. Les deux frontières ferment cette fenêtre par la lecture en
`NOFOLLOW_LINKS` du fichier et par une revalidation complète après lecture, qui refuse l'entrée.

## Decision

Le contrat du produit pour toute lecture de l'espace de travail local est la propriété forte : **avant qu'un contenu
soit lu, chaque segment de son chemin, racine comprise, a été interrogé sur le système de fichiers, et aucun n'est un
lien symbolique ni ne se résout hors de la racine physique**. Elle vaut pour les deux frontières,
`SafeWorkspaceFileResolver` et `ScanWorkspaceBoundary`.

1. Une réponse mémorisée ne remplace jamais le test de lien. Le scan peut se dispenser de **résoudre** à nouveau un
   répertoire validé pendant le même scan ; il teste encore la racine et chaque segment au-dessus de l'entrée, une
   requête chacun, avant de la lire.
2. La racine physique peut être mémorisée — à la construction pour le resolver, à chaque marche complète pour le scan
   — parce que la racine lexicale est testée avant chaque lecture et qu'un changement au-dessus d'elle place toute
   entrée résolue ensuite hors de la valeur mémorisée, ce qui la refuse.
3. Après lecture, la revalidation reste une marche complète, répondue par le système de fichiers.
4. Un refus du scan à la frontière, avant ou après lecture, porte le code `WORKSPACE_BOUNDARY_ESCAPED`, levé par
   `WorkspaceBoundaryEscapeException`, et arrête le scan. Il n'est jamais rapporté `SOURCE_UNREADABLE`.
5. Ce qu'un scan mémorise ne lui survit pas ; le watcher interroge le système de fichiers à chaque appel.

## Do's and Don'ts

### Do

- Tester la racine et chaque segment d'un chemin pour un lien avant d'en lire le contenu, à chaque lecture.
- Lire un fichier de l'espace de travail en `NOFOLLOW_LINKS` et le revalider en entier après lecture.
- Lever `WorkspaceBoundaryEscapeException` pour tout refus de `ScanWorkspaceBoundary`, et le rapporter
  `WORKSPACE_BOUNDARY_ESCAPED`.
- Prouver toute optimisation de ces frontières par un test qui remplace un segment déjà validé par un lien pointant
  dans l'espace de travail, et constate le refus avant lecture.

### Don't

- Ne pas répondre au test de lien d'un segment depuis un cache, même pendant un seul scan.
- Ne pas lever une `IOException` nue depuis `ScanWorkspaceBoundary` : son refus deviendrait `SOURCE_UNREADABLE`.
- Ne pas continuer un scan après un refus de frontière.
- Ne pas présenter la revalidation après lecture comme le contrôle de frontière : elle refuse l'entrée, elle n'empêche
  pas la lecture.

## Consequences

### Positives

- Une seule garantie pour l'espace de travail, quel que soit le chemin d'entrée : provider, scan ou watcher.
- Une évasion de frontière se lit sous son nom dans le résultat de scan et dans sa vue publique.

### Négatives

- Avant lecture, le coût du scan par entrée croît d'une requête par segment au-dessus d'elle ; la résolution reste
  une par répertoire. La revalidation après lecture coûte déjà quatre requêtes par segment.

### Risques

- La fenêtre entre le dernier test et l'ouverture subsiste faute d'`openat` en Java : elle est fermée après coup par
  la revalidation, qui refuse l'entrée, et non avant lecture.
- Les deux tests qui remplacent un ancêtre pendant la lecture d'un fichier ne tournent que hors Windows, qui garde
  ouvert un répertoire en cours de parcours ; les tests du chemin rapide, eux, tournent sur les deux plateformes.

## Compliance and Enforcement

- `morpheus-application/src/test/java/com/morpheus/application/sync/ScanWorkspaceBoundaryTest.java` : ancêtre validé
  remplacé par un lien dans l'espace de travail et racine remplacée par un lien, refusés avant la lecture suivante ;
  aucun autre fichier lu après le remplacement, refus `WORKSPACE_BOUNDARY_ESCAPED` qui arrête le scan ; coût avant
  lecture d'une requête de test de lien par segment.
- `morpheus-application/src/test/java/com/morpheus/application/files/SafeWorkspaceFileResolverTest.java` : segments
  symboliques refusés avant lecture, revalidation après lecture.
- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/WorkspaceBoundaryRefusalArchitectureTest.java` :
  `ScanWorkspaceBoundary` ne construit aucune `IOException` nue, et le scan rapporte une évasion sous
  `WORKSPACE_BOUNDARY_ESCAPED`.
- Revue : une optimisation de l'une des deux frontières garde le test de lien de chaque segment avant lecture.

## References

- [ADR-0087](SEC-0087-local-first-operability-and-secure-diagnostic-defaults.md) — le scan local ne suit pas les liens.
- [ADR-0150](ARCH-0150-ce-que-le-coeur-applicatif-peut-toucher.md) — les classes de l'application qui touchent le
  disque.
- [ADR-0028](BE-0028-unified-provider-read-contract.md) — contrat de lecture des providers.
- Spécification OpenSpec `workspace-boundary-scan-cost`.
