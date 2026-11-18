# Proposition : construire le classpath du worker du probe depuis le seul classpath de l'hôte (AUD-TRV-09)

## Why

Constat `AUD-TRV-09` (Faible), `docs/project/audits/2026-11-17/findings.md`, revérifié le 17/11/2026 sur `1a539c22`.
`ProviderPluginProbeProcess.command` (`morpheus-provider-sdk/src/main/java/com/morpheus/sdk/provider/ProviderPluginProbeProcess.java:253-256`)
lit d'abord la propriété `surefire.test.class.path`, puis `java.class.path` si elle est absente, pour composer le `-cp`
de la JVM enfant qui exécute du code tiers. C'est la seule lecture d'une propriété Surefire hors de `src/test` (recherche
sur le dépôt : dix-sept autres occurrences, toutes dans des sources de test).

Le produit connaît ainsi son harnais de test, ce que `.claude/rules/code-style.md` refuse (pas de shim). Côté sécurité,
quiconque pose `-Dsurefire.test.class.path` au lanceur (par exemple par `JAVA_TOOL_OPTIONS`) choisit les classes que
charge le seul chemin qui exécute du code tiers ; l'exploitation suppose déjà le contrôle de la JVM, d'où la sévérité
Faible.

## What Changes

- Le worker du probe est lancé avec le classpath de la JVM hôte (`java.class.path`) et rien d'autre ; une valeur absente
  ou vide reste un échec nommé (« current JVM classpath is unavailable »).
- Les tests du SDK qui lancent un vrai probe sous Surefire passent leur classpath par une injection de test
  (constructeur package-private de `ProviderPluginProbeProcess`), au lieu d'une lecture dans le code principal.
- Une règle de build refuse toute lecture d'une propriété `surefire.` dans une source principale du réacteur.
- Aucune surface publique, aucun format persisté ne change.

## Capabilities

### New Capabilities

### Modified Capabilities
- `provider-plugin-pinned-entry` : le worker du probe est lancé avec le classpath de l'hôte seulement, et aucune source
  principale ne lit une propriété de l'outillage de test.

## Périmètre et exclusions

**Inclus :** `ProviderPluginProbeProcess` et ses tests (`morpheus-provider-sdk`), une méthode de test d'architecture,
la page durable de la capacité.

**Exclus :** les dix-sept lectures de `surefire.test.class.path` dans des sources de test, légitimes ; la séparation SPI /
hôte (`AUD-ARC-30`), qui peut déplacer la classe sans changer ce comportement.

## Acteurs et surfaces concernés

Opérateur qui lance un probe (CLI `provider-plugins probe`, HTTP remote `POST /api/v1/provider-plugins/probe`) : aucun
changement observable en fonctionnement normal ; CI (verdict de build).

## Nature des informations

| Affirmation | Nature | Source ou preuve |
|---|---|---|
| Le code principal lit `surefire.test.class.path` avant `java.class.path` | existant vérifié | `ProviderPluginProbeProcess.java:253-256` |
| C'est la seule lecture hors `src/test` | existant vérifié | recherche de `surefire.test.class.path` sur le dépôt, le 17/11/2026 |
| Une injection de classpath existe déjà pour les tests | écart | les constructeurs package-private n'injectent que le délai et le recorder (`:59`, `:63`) ; design.md, Écarts |
| Aucun ADR n'est requis | décision | `.claude/rules/adr.md` : correctif local, retour arrière sans coût |

## Impact

`morpheus-provider-sdk` (classe et tests de probe : `ProviderPluginProcessRoundTripTest`,
`ProviderPluginProcessIsolationTest`, `ProviderPluginProbeCleanupTraceTest`), `morpheus-architecture-tests` (nouvelle
méthode, justification de PR). Indépendant des autres constats du sprint ; si `AUD-ARC-30` est appliqué d'abord, la
classe a changé de paquet.

## Questions ouvertes

Aucune.
