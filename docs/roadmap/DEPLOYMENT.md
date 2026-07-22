# MORPHEUS — Roadmap de distribution et déploiement

Statut : **stratégie acceptée, implémentation différée aux jalons concernés**

Décision de référence : [`../adr/0027-native-first-container-supported-distribution.md`](../adr/0027-native-first-container-supported-distribution.md)

---

# 1. Résumé

```text
Développeur local     -> native-first
CLI                    -> native-first
MCP stdio              -> native-first
MCP réseau             -> native ou Docker
API                    -> Docker support officiel
CI/CD headless         -> Docker ou archive portable
Écosystème multi-moteur-> Docker Compose possible, jamais obligatoire
```

Principe :

> **Même cœur MORPHEUS, plusieurs adapters et plusieurs formats de distribution.**

---

# 2. Cibles de distribution

| Cible | Usage principal | Statut cible |
|---|---|---|
| Installation native Windows | développeur / CLI | **principal** |
| Distribution portable | CI / utilisateurs avancés | officiel |
| JAR exécutable | debug / intégration avancée | supporté |
| Image Docker | headless / MCP réseau / API | officiel |
| Docker Compose | écosystème multi-services | futur / optionnel |

---

# 3. M9 — CLI et distribution locale

## M9-S1 — CLI stabilisée

Commandes métier prévues dans `ROADMAP.md`.

Porte : la CLI appelle uniquement les services applicatifs, sans logique métier propre au packaging.

## M9-S2 — Layout runtime

À définir :

```text
config directory
data directory
logs directory
SQLite location
workspace registry location
cache/temp policy
```

Contraintes : Windows et Linux ; chemins configurables ; aucune donnée importante dans un répertoire temporaire.

## M9-S3 — Distribution portable

Cible : archive utilisable sans installation système lourde.

À prouver :

```text
unpack
run morpheus
configure
sync
query
```

## M9-S4 — Runtime Java embarqué

Candidats :

```text
jlink
jpackage
```

Objectif : l'utilisateur final ne doit pas avoir à installer/configurer manuellement Java pour utiliser la distribution standard.

## M9-S5 — Installateur Windows

Cible candidate : MSI ou EXE généré par le mécanisme de packaging retenu.

À tester :

```text
install
PATH / launcher
upgrade
uninstall
data preservation
```

## M9-S6 — Distribution Linux

Objectif : distribution raisonnable, sans exiger l'identité parfaite avec l'installateur Windows.

---

# 4. M10 — MCP

## M10-S1 — MCP local

Mode privilégié :

```text
morpheus mcp --stdio
```

Usage : IDE/agents locaux.

Aucun Docker obligatoire.

## M10-S2 — MCP réseau

Seulement si le transport et les consommateurs le justifient.

Distribution :

```text
native
ou
Docker
```

La stack réseau ne doit pas contaminer le domaine/application.

---

# 5. M11 — API et image Docker officielle

## M11-S1 — Serveur API

Framework choisi à ce jalon, pas avant.

## M11-S2 — Image Docker

Exigences :

```text
reproductible
versionnée
non-root si possible
configuration externe
healthcheck
arrêt propre
logs exploitables
```

## M11-S3 — Volumes

Topologie cible :

```text
workspace -> /workspace (read-only lorsque possible)
data      -> /data
SQLite    -> /data/morpheus.db
```

Test obligatoire : recréer le conteneur sans perdre la connaissance persistée.

## M11-S4 — Configuration

Priorités candidates :

```text
CLI args
environment variables
config file
```

Les secrets éventuels ne doivent pas être intégrés à l'image.

---

# 6. Composition écosystème — future

Cible possible :

```text
services:
  morpheus
  minos
  nexus
  jarvis
```

Objectifs :

- environnement d'intégration reproductible ;
- réseau local des moteurs ;
- volumes séparés ;
- démarrage indépendant ;
- aucun moteur requis pour lancer MORPHEUS seul.

Ce compose ne constitue pas une architecture monolithique : il ne fait qu'assembler des services autonomes.

---

# 7. CI/CD

Docker pourra être utilisé pour :

```text
integration tests
API tests
release smoke tests
packaging validation
headless execution
```

Mais le gate de développement actuel reste :

```text
Windows : .\mvnw.cmd clean test
Unix    : ./mvnw clean test
```

Une image Docker ne devient pas le moyen obligatoire de compiler/tester MORPHEUS.

---

# 8. Critères transverses

Toute distribution doit préserver :

```text
local-first
offline core
SQLite persistence
provider isolation
same MORPHEUS domain/application semantics
no mandatory cloud
no mandatory MINOS/NEXUS/JARVIS
```

---

# 9. Ce qui n'est pas décidé aujourd'hui

Les éléments suivants nécessitent encore une preuve à leur jalon :

```text
jpackage exact configuration
Windows MSI vs EXE
Linux package format
base Docker image
HTTP framework
MCP network transport
ports
Docker Compose production topology
release registry / GHCR publication
update mechanism
```

Leur absence de décision n'empêche pas le développement du cœur.
