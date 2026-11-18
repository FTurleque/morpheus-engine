# Tâches : construire le classpath du worker du probe depuis le seul classpath de l'hôte

## 1. Reproduction

- [ ] 1.1 Écrire la méthode de test d'architecture qui refuse `"surefire.` dans les sources principales (D2) —
  exigence : `provider-plugin-pinned-entry` › Aucune source principale NE DOIT lire une propriété de l'outillage de
  test ; vérification : rouge sur l'arbre courant, nomme `ProviderPluginProbeProcess.java:253` ; justification de PR
  (test d'architecture ajouté)
- [ ] 1.2 Écrire dans `morpheus-provider-sdk` le test de la commande construite (classpath du fournisseur de production,
  propriété `surefire.test.class.path` ignorée) et celui du classpath vide — exigence : `provider-plugin-pinned-entry` ›
  Le worker du probe DOIT être lancé avec le classpath de la JVM hôte seulement ; vérification : le premier est rouge
  avant 2.1 ; le second asserte le message `current JVM classpath is unavailable` et l'absence de processus

## 2. Correctif

- [ ] 2.1 Ajouter le fournisseur de classpath au constructeur package-private de `ProviderPluginProbeProcess`, la
  production sur `java.class.path`, et retirer la lecture de `surefire.test.class.path` (D1) — vérification : tests de
  1.1 et 1.2 verts
- [ ] 2.2 Faire passer à `ProviderPluginProcessRoundTripTest`, `ProviderPluginProcessIsolationTest` et
  `ProviderPluginProbeCleanupTraceTest` un fournisseur qui lit `surefire.test.class.path` — vérification :
  `./mvnw -pl morpheus-provider-sdk -am verify` vert, seuil propre du module
  (`config/m21-per-module-coverage-floors.properties`) tenu

## 3. Documentation durable

- [ ] 3.1 Ajouter les deux exigences, leur matrice et le diagramme de séquence à
  `docs/developer/specifications/provider-plugin-pinned-entry.md` — vérification : `RepositoryDocumentationCoherenceTest`
  vert

## 4. Vérification d'ensemble

- [ ] 4.1 Lancer `openspec validate build-the-probe-worker-classpath-from-the-launcher-classpath-only --strict` —
  vérification : aucun avertissement
- [ ] 4.2 Compléter la matrice exigence → scénario → D1/D2 → tâche → test — vérification : chaque scénario a sa preuve
- [ ] 4.3 Lancer `./mvnw clean verify` puis `scripts\validate.cmd m22 -Version <version de ProductMetadata>` (probe du
  launcher packagé) — vérification : verts, statut `SUPPORTED`

## Workflow follow-up

- Archiver le changement après la revue, sur demande explicite.
