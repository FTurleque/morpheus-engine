# Version produit MORPHEUS

La version runtime de MORPHEUS possède une source de vérité unique : `com.morpheus.application.product.ProductMetadata`.

## Baselines

```text
v1.2.0 / 3ad9ebf030b58df97482e21e272c24feae6b9d86  release publiée le 30 juillet 2026
1.2.1                                                  baseline corrective active en développement
```

Le tag publié `v1.2.0` et ses preuves de release restent historiques et ne sont ni déplacés ni réécrits. Le code actif postérieur à cette release utilise `1.2.1` afin qu'un arbre logiciel différent ne soit jamais reconstruit ou promu sous l'identité déjà publiée `1.2.0`.

`1.2.1` devient une release publiée uniquement après création du tag/release correspondant et qualification de ses artefacts ; le simple fait que le reactor Maven porte cette version ne constitue pas une publication.

## Résolution

`ProductMetadata.version()` applique cet ordre déterministe :

1. `Implementation-Version` du package lorsqu'il existe dans l'artefact packagé ;
2. propriété JVM `morpheus.project.version`, alimentée par Maven pendant les tests et les exécutions de build ;
3. marqueur explicite `development` lorsqu'aucune métadonnée de build n'est disponible.

Aucune surface runtime ne doit définir son propre fallback semantic-version. En particulier, un build 1.2.x ne doit jamais se présenter silencieusement comme une ancienne version telle que `0.1.0-SNAPSHOT`.

## Surfaces

Les surfaces suivantes délèguent à `ProductMetadata` :

- CLI `version` / `--version` ;
- CLI `product-info` ;
- MCP `get_product_info` ;
- HTTP `/version` ;
- HTTP health pour `apiVersion` ;
- vérification de mise à jour via `UpdateDiscoveryService`.

La distribution fournit `Implementation-Version` à partir de `${project.version}`. Le comportement IDE/tests sans manifeste reste volontairement explicite grâce à `morpheus.project.version` ou au marqueur `development`.

## Anti-drift

`ProductVersionSourceOfTruthTest` vérifie que les sources runtime actives ne réintroduisent ni `FALLBACK_VERSION` ni l'ancien fallback `0.1.0-SNAPSHOT`, et que CLI/MCP/HTTP restent raccordés à `ProductMetadata`.

`ProductReleaseContractTest` vérifie en plus que les 17 POMs du reactor actif utilisent `1.2.1` et qu'aucun POM actif ne conserve `1.2.0` comme version MORPHEUS.

Les scripts M21, D2 et les builders portables/release actifs utilisent également `1.2.1` par défaut. Les validateurs et documents de preuve historiques conservent volontairement la version réellement qualifiée à leur époque.

Toute évolution de la version produit doit modifier la version Maven canonique et les mécanismes de packaging/release associés ; elle ne doit pas ajouter de version courante codée en dur dans un adaptateur.
