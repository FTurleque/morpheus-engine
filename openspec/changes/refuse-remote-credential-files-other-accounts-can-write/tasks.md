# Tâches : refuser un fichier d'identifiants remote que d'autres comptes peuvent écrire

## 1. Vérification en lecture seule

- [ ] 1.1 Écrire dans `LocalWritePermissionHardenerTest` les cas de `requireWriteProtectedFile` (POSIX groupe/autres, propriétaire non fiable, ACL non fiable, fichier conforme inchangé) — exigence : `remote-credential-file-trust` › The identity file is read only when no other account can write it ; vérification : ne compile pas avant 1.2
- [ ] 1.2 Ajouter `LocalWritePermissionHardener.requireWriteProtectedFile(Path)` sans écriture de permission (design.md, D1) — vérification : 1.1 vert ; `ApplicationTechnologyBoundaryArchitectureTest` vert sans liste modifiée

## 2. Fichier d'identités

- [ ] 2.1 Écrire `RemoteIdentityFileStoreTest#aGroupWritableIdentityFileIsRefusedUnchanged` et `MorpheusRemoteIdentitySnapshotCacheTest#aReloadOfAGroupWritableFileAuthenticatesNoIdentity` — exigence : `remote-credential-file-trust` › The identity file is read only when no other account can write it ; vérification : rouges avant 2.2
- [ ] 2.2 Appeler la vérification dans `RemoteIdentityFileStore.readLines` avant la lecture, avec un message qui nomme la permission et le remède (D2) — vérification : 2.1 vert, `MorpheusRemoteIdentityFileTest` et `MorpheusRemoteIdentityLifecycleTest` verts

## 3. Keystore TLS

- [ ] 3.1 Écrire `MorpheusRemoteHttpServerStartupPolicyTest#aGroupWritableKeystoreIsRefused` — exigence : `remote-credential-file-trust` › The TLS keystore is read only when no other account can write it ; vérification : rouge avant 3.2
- [ ] 3.2 Appeler la vérification avant la lecture du keystore dans `MorpheusRemoteHttpServerBootstrap` — vérification : 3.1 vert

## 4. Documentation durable

- [ ] 4.1 Écrire l'exigence et le remède dans `SECURITY.md` et `docs/user/TEAM_REMOTE_SERVER.md`, et la rupture dans les notes de version de la version qui livre — vérification : `UserDocumentationSpeaksOfTheInstalledProductTest`, `RepositoryDocumentationCoherenceTest`
- [ ] 4.2 Créer `docs/developer/specifications/remote-credential-file-trust.md` (gabarit de `docs/developer/specifications/README.md`, diagramme de design.md) et l'ajouter à l'index — vérification : `RepositoryDocumentationCoherenceTest`

## 5. Vérification d'ensemble

- [ ] 5.1 Lancer `openspec validate refuse-remote-credential-files-other-accounts-can-write --strict` — vérification : aucun avertissement
- [ ] 5.2 Compléter la matrice de traçabilité de la page durable — vérification : chaque exigence a une preuve
- [ ] 5.3 Lancer `./mvnw test -pl morpheus-application,morpheus-api` sous Linux et sous Windows (cas POSIX et ACL), puis `./mvnw clean verify` — vérification : verts

## Workflow follow-up

- Archiver le changement après la revue, sur demande explicite.
