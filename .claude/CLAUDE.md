# Morpheus Engine

## Règles à respecter (chargées automatiquement)

@.claude/rules/meta.md
@.claude/rules/architecture.md
@.claude/rules/testing.md
@.claude/rules/security.md
@.claude/rules/governance.md
@.claude/rules/build.md
@.claude/rules/code-style.md

---

## Identité

`io.github.fturleque:morpheus-engine` **1.2.1** — Specification & Intent Intelligence Engine
Java 21 · Maven 3.9.16 · 17 modules (constaté le 31/08/2026 dans `pom.xml` — recompter avant de citer) · local-first · **sans framework** · **sans Docker**

## Principe directeur

**Les règles de ce projet sont exécutables.** Une suite de tests d'architecture dans
`morpheus-architecture-tests` (compter avec un `glob`, pas de mémoire — le total évolue à
chaque milestone) assertent la structure, les contrats, la sécurité et jusqu'au contenu
textuel de fichiers précis.
`docs/adr/` documente le *pourquoi* (98 fichiers constatés au 31/08/2026 ; le doublon de
numéro `0095-*` alors présent a été corrigé le 01/09/2026 — renumérotation en `0097-*` —
mais revérifier quand même avant de citer ce total ou d'assigner un nouveau numéro, le
compte évolue à chaque ADR ajouté).

> Avant de décider quoi que ce soit de structurel : **lire le test ArchUnit concerné**, puis l'ADR.
> Ne jamais deviner une règle — elle est écrite quelque part et vérifiable.
> Ne jamais deviner un **chiffre** non plus (coverage, nb de tests, nb d'ADR) — voir `rules/meta.md`.

## Garde-fous (hooks) — principe fail-open

`.claude/hooks/pre-bash.ps1` et `post-edit.ps1` avertissent ou bloquent des opérations
risquées (force-push protégé, `rm -rf` non borné, édition de couches critiques). Ils sont
conçus pour **fail-open sur un bug interne** : toute exception non prévue dans le hook
laisse passer la commande plutôt que de bloquer 100% des outils par accident — un hook qui
bloque tout est pire que l'absence de hook. Si un hook semble bloquer une commande anodine,
c'est un bug du hook à corriger, pas une règle à contourner.

`.claude/settings.local.json` reste **versionné** (choix assumé) : il complète
`.claude/settings.json` avec des permissions additionnelles (ex. `Bash(rtk git *)`) plutôt
que de les dupliquer — ne pas fusionner les deux fichiers.

## Paramétrage IA — pendant Copilot

Ce dépôt paramètre l'IA sur deux surfaces qui doivent rester convergentes :
`.claude/` (ce fichier) et `.github/` (Copilot). La cartographie complète — instructions
ciblées par chemin, prompts, skill projet, correspondance commande ↔ prompt — vit dans
`.github/AI_GOVERNANCE.md`. Le hook RTK est configuré des deux côtés :
`.claude/settings.local.json` (`Bash(rtk git *)`) et `.github/hooks/rtk-rewrite.json`
(`PreToolUse` → `rtk hook copilot`, portable au niveau repo, indépendant du profil
utilisateur `~/.copilot/`).

## Architecture : ports & adapters

`application` définit les **ports** et ne connaît **aucun** adaptateur.
Les adaptateurs dépendent vers l'intérieur. Les adaptateurs sont **frères** — ils ne s'appellent pas entre eux.

```
                  ┌──────────────────────────────┐
                  │      morpheus-domain         │  ← modèle pur, 22 packages
                  └──────────────▲───────────────┘
                  ┌──────────────┴───────────────┐
                  │   morpheus-application       │  ← ports + use cases, 29 packages
                  └──▲────▲────▲────▲────▲────▲──┘
      ┌──────────────┘    │    │    │    │    └──────────────┐
┌─────┴─────┐ ┌───────────┴┐ ┌─┴────┴─┐ ┌┴──────────┐ ┌──────┴──────┐
│ provider-*│ │  store-*   │ │api│mcp │ │integration│ │provider-sdk │
└───────────┘ └────────────┘ └───▲────┘ └───────────┘ └─────────────┘
                                 │
                          ┌──────┴──────┐
                          │ morpheus-cli│  ← câblage explicite uniquement
                          └─────────────┘
```

## Commandes

```bash
./mvnw clean verify                                       # reactor complet
./mvnw test -pl morpheus-architecture-tests               # tous les gates
./mvnw test -pl morpheus-architecture-tests -Dtest=*M28*  # dernier gate milestone livré
./mvnw dependency:analyze                                 # 0 warning exigé
```

Validation milestone : `scripts/validate-m<N>.ps1` (Windows) · `scripts/validate-m<N>.sh` (Linux)
Dispatcher : `scripts/validate.cmd` → `scripts/validate.ps1`

## Milestones

Dernier milestone livré : **M28** (intégration client MCP native, livré dans 1.2.0). Aucun
milestone n'est actuellement en cours — la baseline **1.2.1** est une passe corrective et de
durcissement (audit, sécurité, dette de gouvernance), suivie par l'issue #185 jusqu'à sa
release réelle, pas un nouveau milestone.
Gates actifs : **M19** (perf) · **M20** (release) · **M21** (coverage + intégrité) · **M22** (plugins)
· **M23** (portfolio) · **M24** (query DSL) · **M25** (policy) · **M26** (remote) · **M27** (reasoning) · **M28** (MCP clients) · **D2** (hardening repo)

## Points d'entrée clés

| Fichier | Rôle |
|---|---|
| `morpheus-cli/.../MorpheusMain.java` | Câblage complet de l'application |
| `morpheus-api/.../MorpheusHttpServer.java` | Serveur local (loopback obligatoire) |
| `morpheus-api/.../MorpheusRemoteHttpServer.java` | Serveur remote (TLS + RBAC) |
| `morpheus-application/.../product/ProductMetadata.java` | Source unique de la version |
| `contracts/public-surfaces.tsv` | Manifeste de convergence CLI/MCP/HTTP |
| `morpheus-architecture-tests/.../LayerDependencyTest.java` | Règles de couches |
