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

## Decision

### 1. Pas de coffre

MORPHEUS n'intègre aucun coffre de secrets, sur aucune plateforme.

### 2. Invariant : aucun secret réutilisable au repos

MORPHEUS ne persiste jamais un secret qui permettrait, lu tel quel, de s'authentifier. Les jetons sont persistés sous
forme d'empreinte SHA-256 d'une valeur de 32 octets tirée par `SecureRandom`
(`RemoteIdentityCredentialService.TOKEN_BYTES`) : l'empreinte ne permet ni de s'authentifier ni, en pratique, de
retrouver le jeton. C'est cet invariant qui rend un coffre sans objet : il protégerait des empreintes.

Cette propriété tient **parce que les jetons sont générés, jamais choisis**. Une empreinte SHA-256 non salée d'un
secret choisi par un humain serait attaquable par dictionnaire ; celle d'un secret de 256 bits aléatoires ne l'est
pas.

### 3. Le mot de passe TLS ne vient que de l'environnement

Le mot de passe du keystore TLS est lu dans la variable d'environnement `MORPHEUS_SERVER_TLS_PASSWORD`, et nulle part
ailleurs : jamais d'un argument MORPHEUS (`--tls-password`), jamais d'une propriété JVM (`-D`). Les deux sont des
arguments de ligne de commande, et `/proc/<pid>/cmdline` est lisible par tous les comptes d'un Linux par défaut, là
où `/proc/<pid>/environ` n'est lisible que par le propriétaire du processus (et root).

C'est le seul réglage du mode remote sans repli propriété : le keystore, le fichier d'identités, les racines de
workspace et la concurrence acceptent une propriété `morpheus.server.*`, parce qu'aucun d'eux n'est un secret. Le mot
de passe ne s'aligne pas sur eux.

Un lancement qui ne fournit pas la variable — y compris un lancement qui fournit le mot de passe par `-D` — est refusé
**au démarrage**, avec un message qui nomme `MORPHEUS_SERVER_TLS_PASSWORD` : jamais un serveur qui démarre sans le
secret attendu. `TlsKeystorePassword` ne retient pas la valeur : il la relit au moment d'ouvrir le keystore, en un
`char[]` que l'appelant écrase.

### 4. Modèle de menace

**Protège contre** les **autres comptes** de la machine. `remote-auth.txt` est en `rw-------`, son répertoire en
`rwx------` (POSIX et ACL Windows), tout accès se fait sans suivre les liens symboliques, et le fichier temporaire est
durci avant le déplacement atomique. Le mot de passe TLS vit dans un environnement de processus qu'un autre compte ne
lit pas.

**Ne protège pas contre**, et ne prétend pas le faire :

- la compromission du **compte** qui exécute MORPHEUS : ce compte lit son propre environnement et ses propres
  fichiers. Un coffre ne changerait rien : un code malveillant sous ce compte utilise la clé depuis le coffre aussi
  bien que depuis l'environnement ;
- un **administrateur / root** de la machine ;
- une **sauvegarde** du profil ou un disque non chiffré contenant le keystore ;
- la manière dont l'**opérateur** conserve le mot de passe hors de MORPHEUS (script de démarrage, fichier
  d'environnement) : c'est sa responsabilité, et la documentation opérateur lui recommande un compte de service
  dédié ;
- les copies que la **JVM** détient déjà sous forme de `String` dès la lecture de l'environnement : MORPHEUS n'en
  fabrique pas d'autre, il ne peut pas effacer celle-là.

Non vérifiés : le comportement de `/proc/<pid>/cmdline` sur un système monté avec `hidepid`, et la visibilité de la
ligne de commande d'un autre compte sous Windows. L'argument tient sur un Linux par défaut, ce qui suffit à rendre la
propriété JVM incompatible avec ce modèle de menace.

### 5. Conditions de réouverture

Cette décision est à reconsidérer si l'une de ces conditions devient vraie :

1. MORPHEUS doit **stocker lui-même** un secret réutilisable (clé d'API d'un fournisseur, jeton d'un service tiers,
   secret partagé) — l'invariant du §2 tomberait ;
2. le mode remote est déployé **sur des machines partagées** par plusieurs utilisateurs, ou sous un compte non dédié ;
3. une exigence de **conformité** impose un coffre ou une clé non exportable ;
4. un jeton devient **choisi** plutôt que généré — l'empreinte non salée deviendrait attaquable (§2).

Le candidat privilégié en cas de réouverture est le magasin de certificats Windows (`Windows-MY`) et, sous Linux, un
fichier de mot de passe compatible avec les identifiants systemd.

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

- `morpheus-cli/src/test/java/com/morpheus/cli/RemoteApiLaunchOptionsTest.java` : mot de passe refusé en argument,
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
