# Plugins de provider MORPHEUS

Statut : **M22 candidate**.

## Modèle de confiance

```text
plugin discovery != plugin activation
provider metadata != executable trust
classloader isolation != security sandbox
```

`discover` lit uniquement les métadonnées de JAR non symboliques. Il n'active jamais du code tiers.

`probe` exécute du code tiers. MORPHEUS exige un SHA-256 de confiance avant toute activation. Le JAR épinglé est copié dans un staging privé, vérifié, puis seule cette copie est chargée.

## CLI

```text
provider-plugins discover --directory <path>
provider-plugins probe --directory <path> --plugin <id> --workspace <path> --sha256 <64-hex>
```

Un probe non épinglé échoue avant tout classloading.

## MCP

Le MCP n'expose pas l'exécution du probe. Seule la découverte de métadonnées reste model-facing :

```text
discover_provider_plugins(directory)
```

Le nom historique `probe_provider_plugin` n'est plus enregistré comme outil MCP.

## HTTP

L'API HTTP locale ordinaire n'expose pas l'exécution d'un provider plugin. La découverte de métadonnées reste disponible en `GET /api/v1/provider-plugins/discover`.

Le probe HTTP est **remote-only** : `POST /api/v1/provider-plugins/probe`, authentification Bearer, rôle `ADMIN`, répertoire de plugins configuré côté serveur, workspace limité par `AllowedWorkspaceRoots` et SHA-256 obligatoire. Le proxy distant conserve la méthode `POST` jusqu'à l'API loopback interne.

## Règle opérationnelle

Ne considérez jamais un JAR découvert comme approuvé. L'activation nécessite une empreinte obtenue depuis une source de confiance.
