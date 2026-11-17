---
id: SEC-0105
title: "Pas de coffre de secrets : MORPHEUS ne stocke aucun secret réutilisable, et le seul qu'il lit vient de l'environnement"
domain: security
rules: false
---

# Pas de coffre de secrets : MORPHEUS ne stocke aucun secret réutilisable, et le seul qu'il lit vient de l'environnement

- Statut : **Acceptée**
- Date : 2026-09-22

## Context

La question « faut-il un coffre (Gestionnaire d'identification Windows, magasin de certificats Windows, trousseau
Linux, identifiants systemd) ? » appelle une réponse écrite : ce que le comportement de MORPHEUS protège, ce qu'il ne
protège pas, et quand le reconsidérer.

Inventaire des secrets que MORPHEUS manipule :

| Secret | Où il vit | Ce que MORPHEUS en garde |
|---|---|---|
| Jeton bearer d'une identité remote | Chez l'utilisateur qui l'a reçu | **Rien.** Rendu une seule fois à la création ou à la rotation, jamais écrit. Seul `SHA-256(jeton)` est persisté dans `remote-auth.txt` |
| Mot de passe du keystore TLS | Environnement du processus serveur (`MORPHEUS_SERVER_TLS_PASSWORD`) | **Rien de durable.** Un `char[]` le temps d'ouvrir le keystore, puis écrasé ; aucun champ ne le retient |
| Clé privée TLS | Keystore PKCS12 fourni par l'opérateur | Rien de durable : le fichier est lu au démarrage depuis un répertoire exigé protégé en écriture (`requireWriteProtectedDirectory`), ses octets sont écrasés dès que le contexte TLS existe. MORPHEUS ne le crée ni ne le recopie |

Ces secrets n'existent qu'en **mode serveur remote**, optionnel (ADR-0094). En usage local, MORPHEUS ne manipule
aucun secret.

Options écartées :

| Alternative | Pourquoi elle est écartée |
|---|---|
| Gestionnaire d'identification Windows (DPAPI) | Pas d'API Java : exige du code natif, donc une dépendance, dans un produit sans framework. Ne protège pas mieux contre la compromission du compte |
| Magasin de certificats Windows (`Windows-MY`) | Le meilleur candidat — plus de mot de passe du tout, clé non exportable. Mais il change un invariant asserté (`KeyStore.getInstance("PKCS12")`), exige d'ajouter `jdk.crypto.mscapi` à l'image jlink, et crée un chemin propre à Windows. Gain réel mais limité aux opérateurs du mode remote |
| Trousseau Linux (Secret Service / libsecret) | Absent sur un serveur sans session graphique — là où tourne le mode remote. Exige D-Bus et une bibliothèque native |
| Identifiants systemd (`LoadCredentialEncrypted=`) | Solution serveur crédible, mais propre à systemd ; demanderait d'accepter un fichier de mot de passe. L'opérateur peut s'en approcher sans changement de MORPHEUS, via un fichier d'environnement protégé |
| Accepter le mot de passe en propriété JVM, en le documentant | Contredit la règle « jamais un argument de ligne de commande » : une propriété `-D` en est un, et la documenter inscrirait dans le contrat une fuite vers les autres comptes |

Raisons que la décision tient :

- l'absence de tout secret réutilisable au repos rend un coffre sans objet : il protégerait des empreintes, qui ne
  permettent ni de s'authentifier ni, en pratique, de retrouver le jeton. Cela tient **parce que les jetons sont
  générés, jamais choisis** : une empreinte SHA-256 non salée d'un secret choisi par un humain serait attaquable par
  dictionnaire, celle d'un secret de 256 bits aléatoires ne l'est pas ;
- un argument MORPHEUS et une propriété `-D` sont tous deux des arguments de ligne de commande, et
  `/proc/<pid>/cmdline` est lisible par tous les comptes d'un Linux par défaut, là où `/proc/<pid>/environ` ne l'est
  que par le propriétaire du processus (et root). Le keystore, le fichier d'identités, les racines de workspace et la
  concurrence acceptent une propriété `morpheus.server.*` parce qu'aucun d'eux n'est un secret ; le mot de passe ne
  s'aligne pas sur eux, et un serveur ne démarre jamais sans le secret attendu ;
- le compte qui exécute MORPHEUS lit son propre environnement et ses propres fichiers : un coffre ne changerait rien,
  un code malveillant sous ce compte utilise la clé depuis le coffre aussi bien que depuis l'environnement. La
  conservation du mot de passe hors de MORPHEUS (script de démarrage, fichier d'environnement) est la responsabilité
  de l'opérateur, à qui la documentation recommande un compte de service dédié. La JVM détient une copie `String` du
  mot de passe dès la lecture de l'environnement : MORPHEUS n'en fabrique pas d'autre et ne peut pas effacer
  celle-là.

Non vérifiés : le comportement de `/proc/<pid>/cmdline` sur un système monté avec `hidepid`, et la visibilité de la
ligne de commande d'un autre compte sous Windows. L'argument tient sur un Linux par défaut, ce qui suffit à rendre la
propriété JVM incompatible avec ce modèle de menace.

## Decision

1. **Pas de coffre** de secrets, sur aucune plateforme.
2. **Aucun secret réutilisable au repos** : MORPHEUS ne persiste jamais un secret qui, lu tel quel, authentifie. Un
   jeton est généré, jamais choisi : 32 octets tirés par `SecureRandom` (`RemoteIdentityCredentialService.TOKEN_BYTES`),
   persistés en empreinte SHA-256 seulement.
3. **Mot de passe TLS par l'environnement seul** : lu dans `MORPHEUS_SERVER_TLS_PASSWORD`, jamais d'un argument
   (`--tls-password`) ni d'une propriété JVM (`-D`) ; c'est le seul réglage du mode remote sans repli propriété
   `morpheus.server.*`. Un lancement sans la variable, mot de passe en `-D` compris, est refusé au démarrage par
   un message qui nomme `MORPHEUS_SERVER_TLS_PASSWORD`. `TlsKeystorePassword` ne retient pas la valeur : il la
   relit à l'ouverture du keystore en un `char[]` que l'appelant écrase.
4. **Modèle de menace** : protège contre les autres comptes de la machine — `remote-auth.txt` en `rw-------`, son
   répertoire en `rwx------` (POSIX et ACL Windows), aucun lien symbolique suivi, fichier temporaire durci avant le
   déplacement atomique, mot de passe dans un environnement qu'un autre compte ne lit pas. Ne protège pas, et ne le
   prétend pas, contre la compromission du compte qui exécute MORPHEUS, un administrateur (root), une sauvegarde ou
   un disque non chiffré, la conservation du mot de passe par l'opérateur, ni les copies `String` que la JVM détient.
5. **Réouverture** si MORPHEUS doit stocker lui-même un secret réutilisable (clé d'API, jeton tiers, secret
   partagé), si le mode remote est déployé sur des machines partagées ou sous un compte non dédié, si une conformité
   impose un coffre ou une clé non exportable, ou si un jeton devient choisi. Candidats privilégiés : `Windows-MY`
   sous Windows, un fichier de mot de passe compatible avec les identifiants systemd sous Linux.

## Do's and Don'ts

### Do

- Persister un jeton remote uniquement sous forme d'empreinte SHA-256, et le comparer par `MessageDigest.isEqual`.
- Générer chaque jeton par `SecureRandom` sur 32 octets ; ne jamais le laisser choisir.
- Lire le mot de passe TLS dans `MORPHEUS_SERVER_TLS_PASSWORD` seulement, et écraser le `char[]` dès que le contexte
  TLS existe.
- Refuser au démarrage un mode remote sans cette variable, avec un message qui la nomme.
- Garder `remote-auth.txt` en `rw-------` et son répertoire en `rwx------`, sans suivre les liens symboliques.

### Don't

- N'intégrer aucun coffre de secrets.
- Ne jamais accepter le mot de passe TLS en flag (`--tls-password`) ni en propriété JVM
  (`morpheus.server.tls.password`).
- Ne jamais retenir le mot de passe TLS dans un champ ni le rendre dans un `toString()`.
- Ne jamais écrire un jeton en clair, dans un fichier ou un journal.
- Ne jamais transmettre `MORPHEUS_SERVER_TLS_PASSWORD` à un processus externe.

## Consequences

### Positives

- Aucun secret réutilisable au repos : une fuite du fichier d'identités ne donne aucun accès.
- Pas de dépendance native ni de chemin propre à une plateforme.
- Le mot de passe TLS reste hors de la ligne de commande, invisible des autres comptes d'un Linux par défaut.

### Négatives

- L'opérateur porte la conservation du mot de passe TLS hors de MORPHEUS.
- Pas de clé non exportable : la clé privée vit dans un keystore PKCS12 fourni par l'opérateur.

### Risques

- La compromission du compte qui exécute MORPHEUS, un administrateur, une sauvegarde non chiffrée : hors du modèle de
  menace, explicitement.
- Un jeton qui deviendrait choisi rendrait l'empreinte non salée attaquable : condition de réouverture 4.

## Compliance and Enforcement

- `morpheus-cli/src/test/java/com/morpheus/cli/launch/RemoteApiLaunchOptionsTest.java` : mot de passe refusé en argument,
  refusé en propriété JVM (`aTlsPasswordGivenOnlyAsAJvmPropertyIsRefused`, message nommant la variable), non retenu,
  non rendu (`parsedOptionsNeitherRetainNorRenderTheTlsPassword`).
- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/RemoteHttpServerBootstrapArchitectureTest.java` :
  `--tls-password` et `morpheus.server.tls.password` interdits dans `RemoteApiLaunchOptions`, « protected property »
  interdit dans les messages de refus, `char[]` écrasé dans le lanceur et dans le bootstrap.
- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/m22/ProviderPluginTrustBoundaryContractTest.java`
  et `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/ExternalCodeTrustBoundaryArchitectureTest.java` :
  `MORPHEUS_SERVER_TLS_PASSWORD` jamais hérité par un processus externe.
- `morpheus-api/src/test/java/com/morpheus/api/remote/RemoteIdentityCredentialServiceTest.java`,
  `RemoteIdentityFileStoreTest.java`, `MorpheusRemoteIdentityFileTest.java` : empreinte seule, comparaison
  `MessageDigest.isEqual`, `toString()` masqué, fichier durci avant publication.
- Revue : l'absence de tout coffre et de tout autre secret persisté.

## References

- [ADR-0087](SEC-0087-local-first-operability-and-secure-diagnostic-defaults.md) — diagnostics sûrs par défaut.
- [ADR-0094](SEC-0094-optional-team-remote-server-mode.md) — mode serveur remote optionnel.
- [ADR-0100](SEC-0100-identity-audit-is-evidence-not-authority.md) — l'audit d'identités est une preuve.
- `SECURITY.md`
