# ADR-0065 — JDK HttpServer pour l'API locale M11

- Statut : **Proposée — M11 gate pending**
- Date : 24 juillet 2026
- Dépend de : ADR-0014, ADR-0016, ADR-0027, ADR-0061
- Portée : M11 — transport HTTP local

## Contexte

M11 doit exposer MORPHEUS comme service headless local sans restructurer le produit autour d'un framework web. La baseline est Java 21, la distribution M9/M10 est autonome via `jpackage`, et le projet ne possède aucun besoin M11 de servlet container, DI web, templating, websocket ou stack cloud.

Java 21 fournit le module `jdk.httpserver` avec l'API `com.sun.net.httpserver.HttpServer`, suffisante pour un serveur HTTP embarqué local et testable.

## Décision

Utiliser :

```text
module = jdk.httpserver
server = com.sun.net.httpserver.HttpServer
```

Lancement :

```text
morpheus api --host 127.0.0.1 --port 8765
```

Le bind par défaut reste loopback.

## Contraintes

```text
no Spring
no servlet container
no Netty requirement
no HTTP framework dependency
no public network bind by default
no TLS termination in M11
```

Le router HTTP reste un adapter fin. Les règles métier continuent d'appartenir à `morpheus-application` / `morpheus-domain`.

## Distribution

Le packaging `jpackage` M11 ajoute explicitement :

```text
--add-modules jdk.httpserver
```

afin que le runtime embarqué contienne le module nécessaire.

## Critères d'acceptation

1. serveur start/stop sur loopback ;
2. port éphémère utilisable en test ;
3. `health` accessible via vrai client HTTP ;
4. launcher `morpheus api` fonctionnel ;
5. runtime packagé capable de démarrer l'API ;
6. aucune dépendance framework serveur externe ;
7. architecture domain/application inchangée.
