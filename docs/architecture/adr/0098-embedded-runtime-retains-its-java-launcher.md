# ADR-0098 — Le runtime embarqué conserve son launcher Java

- Statut : **Acceptée — post-audit 1.2.1**
- Date : 3 septembre 2026
- Dépend de : ADR-0061, ADR-0027
- Portée : distribution portable, installateur Windows, probe isolé de plugin provider

## Contexte

ADR-0061 fixe l'app-image `jpackage` comme distribution de référence et décrit son contenu :
launcher natif, JAR applicatif, runtime Java embarqué.

M22 a ensuite introduit le probe isolé de plugin provider. Exécuter du code tiers dans le
processus MORPHEUS est refusé : `ProviderPluginProbeProcess` démarre une JVM enfant qui charge le
plugin, applique un timeout borné, un environnement minimisé et termine son propre sous-arbre de
processus. Cette JVM enfant est résolue **exclusivement** depuis le runtime MORPHEUS :

```java
Path.of(System.getProperty("java.home"), "bin", windows ? "java.exe" : "java")
```

Aucune recherche via `PATH` n'est effectuée, délibérément : un `java` substituable sur le `PATH`
rendrait le pin SHA-256 du plugin sans valeur, puisque l'interpréteur lui-même deviendrait le
vecteur.

Or `jpackage`, quand aucun `--runtime-image` n'est fourni, appelle `jlink` avec les options par
défaut suivantes :

```text
--strip-native-commands --strip-debug --no-man-pages --no-header-files
```

`--strip-native-commands` supprime `bin/java` du runtime embarqué. Conséquence observée sur la
distribution 1.2.1 : dans l'app-image, `provider-plugins discover` fonctionne et
`provider-plugins probe` échoue systématiquement en fail-closed avec
`PLUGIN_ACTIVATION_OR_PROBE_FAILED` / *"Java executable is unavailable for isolated provider plugin
probe"*. La distribution de référence ne pouvait donc pas exécuter une capacité que le contrat M22
déclare supportée.

## Décision

L'app-image portable est construite avec les options `jlink` par défaut de `jpackage` **moins**
`--strip-native-commands` :

```text
--jlink-options "--strip-debug --no-man-pages --no-header-files"
```

Le runtime embarqué conserve ainsi `runtime/bin/java(.exe)`, exactement le chemin que
`java.home` désigne pour le launcher packagé.

Les deux scripts officiels appliquent la même option et **prouvent** la présence du launcher enfant
avant de poursuivre : `distribution/build-portable.ps1` et `distribution/build-portable.sh`.
L'installateur Windows réutilise l'app-image validée (`jpackage --app-image`), il hérite donc du
même runtime sans invocation `jlink` propre.

## Alternatives écartées

| Alternative | Raison du rejet |
|---|---|
| Fallback `PATH` pour la JVM enfant | Rend le pin SHA-256 du plugin sans valeur : l'interpréteur devient substituable. Contredit `rules/security.md` (activation fail-closed sur pin de confiance). |
| Déclarer le probe non supporté dans la distribution | Le contrat M22 et `contracts/public-surfaces.tsv` exposent `provider.plugins.probe` ; le retirer réduirait une surface publique pour une raison de packaging. |
| Asserter l'échec fail-closed dans le gate M22 | Fige un défaut de packaging en contrat, et laisse la capacité inutilisable pour l'utilisateur final. |

## Conséquences

- Le runtime embarqué grossit d'environ 50 Ko : les autres optimisations `jlink`
  (`--strip-debug`, `--no-man-pages`, `--no-header-files`) restent actives.
- Le gate M22 portable exécute réellement `discover` → activation isolée → `probe` depuis le
  launcher packagé et exige `status = SUPPORTED`.
- Aucune propriété de sécurité de `ProviderPluginProbeProcess` n'est modifiée : résolution depuis
  `java.home` uniquement, environnement minimisé, staging privé, refus des symlinks, pin SHA-256,
  timeout borné, terminaison du sous-arbre. L'absence réelle de JVM enfant reste un échec explicite.
