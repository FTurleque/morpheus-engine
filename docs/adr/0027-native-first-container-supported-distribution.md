# ADR-0027 — Distribution native-first et container-supported

- Statut : **Acceptée avec contraintes — stratégie de distribution**
- Date : 22 juillet 2026
- Dépend de : ADR-0004, ADR-0014, ADR-0016, ADR-0017, ADR-0018
- Portée : packaging, installation, Docker, CLI, MCP, API

## 1. Contexte

ADR-0014 a volontairement différé la topologie de déploiement afin d'éviter qu'un choix de spike devienne une architecture de production par inertie.

MORPHEUS a maintenant une direction produit suffisamment stable pour fixer une stratégie de distribution sans encore choisir tous les outils de packaging.

Les contraintes structurantes sont :

```text
local-first
Windows prioritaire mais Linux supportable
aucun cloud obligatoire
CLI locale réaliste
MCP et API futurs
SQLite embarqué
MORPHEUS autonome de MINOS/NEXUS/JARVIS
```

## 2. Décision

La stratégie officielle est :

```text
Native-first
Container-supported
```

Cela signifie :

1. l'expérience développeur locale et la CLI ne dépendent pas de Docker ;
2. une distribution native/portable reste le mode principal pour l'usage local ;
3. Docker devient un mode officiel pour les usages headless, MCP réseau, API, CI/CD et déploiements de services ;
4. le même cœur `domain/application/store/providers` est utilisé dans tous les modes ;
5. aucune logique métier ne dépend du mode de packaging.

## 3. Topologie cible

```text
                    MORPHEUS CORE
                         │
             ┌───────────┼───────────┐
             │           │           │
             ▼           ▼           ▼
          CLI adapter  MCP adapter  API adapter
             │           │           │
         native-first  native ou   native ou
                       container    container
```

## 4. Distribution locale

La cible M9 doit fournir au minimum :

```text
morpheus <command>
```

sans imposer à l'utilisateur :

```text
Docker Desktop
installation manuelle d'un JDK
plusieurs services externes
```

Le packaging Java candidat est :

```text
jlink + jpackage
```

mais ce choix reste **à prouver pendant M9**.

Cibles envisagées :

```text
Windows : MSI/EXE ou distribution portable
Linux   : archive/package adapté
Advanced: JAR exécutable/documenté
```

## 5. Docker

Docker est une distribution secondaire officielle, particulièrement adaptée à :

```text
MCP réseau
API HTTP
headless service
CI/CD
serveur partagé
stack MORPHEUS + MINOS + NEXUS + JARVIS
```

Le conteneur doit rester optionnel.

Une image Docker ne doit pas devenir une dépendance du domaine ou du build local de développement.

## 6. Filesystem et SQLite

Pour un déploiement conteneurisé :

```text
workspace source -> bind mount, idéalement read-only
MORPHEUS data    -> volume persistant
SQLite           -> volume MORPHEUS
```

Exemple conceptuel :

```text
host workspace  --ro--> /workspace
named volume    -----> /data
SQLite                 /data/morpheus.db
```

La politique exacte des répertoires/configuration sera stabilisée avec la CLI et le runtime.

## 7. MCP

M10 doit distinguer au minimum :

```text
MCP local/stdio  -> distribution native privilégiée
MCP réseau       -> native ou Docker
```

Le transport concret sera choisi selon les standards et besoins disponibles au moment de M10.

## 8. API

À partir de M11, une image Docker officielle devient une cible de distribution de premier ordre pour le serveur API.

La stack HTTP reste différée jusqu'à M11 conformément à ADR-0014.

## 9. Docker Compose / écosystème

Une composition multi-service pourra être proposée ultérieurement :

```text
morpheus
minos
nexus
jarvis
```

mais chaque moteur doit rester installable et exécutable indépendamment.

`docker compose` n'est donc pas une dépendance du MVP MORPHEUS.

## 10. Invariants

```text
native CLI != Docker required
container packaging != business architecture
same core in native and container modes
workspace may be mounted read-only
persistent data is externalized from container filesystem
no cloud dependency
MORPHEUS remains standalone
```

## 11. Validation future

Cette ADR accepte la **stratégie**, pas encore les outils concrets de packaging.

### M9 doit prouver

- installation locale Windows réaliste ;
- runtime Java embarqué ou stratégie équivalente ;
- commande `morpheus` accessible sans JDK manuel ;
- upgrade/uninstall documentés ;
- répertoires de config/data définis ;
- distribution Linux raisonnable.

### M10 doit prouver

- MCP local simple ;
- Docker seulement lorsque le transport/usage le justifie.

### M11 doit prouver

- image Docker reproductible ;
- configuration externe ;
- volume de données ;
- workspace read-only lorsque possible ;
- healthcheck ;
- arrêt propre ;
- persistance SQLite correcte après recréation du conteneur.

## 12. Conséquence

MORPHEUS ne sera ni `Docker-only` ni `Docker-first`.

La cible officielle est :

> **Native-first pour l'expérience développeur ; container-supported pour les usages service/headless.**
