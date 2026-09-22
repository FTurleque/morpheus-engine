# ADR-0105 — Pas de coffre de secrets : MORPHEUS ne stocke aucun secret réutilisable, et le seul qu'il lit vient de l'environnement

- Statut : **Acceptée — post-audit 1.2.1**
- Date : 22 septembre 2026
- Dépend de : ADR-0094 (mode serveur remote optionnel), ADR-0087 (diagnostics sûrs par défaut),
  ADR-0100 (l'audit d'identités est une preuve, pas une autorité), ADR-0103 (une règle n'est acceptée qu'après
  avoir été cassée)
- Portée : `MorpheusRemoteIdentityFile`, `RemoteIdentityCredentialService`, `RemoteIdentityFileStore`,
  `TlsKeystorePassword`, `RemoteApiLaunchOptions`, `MorpheusRemoteHttpServerBootstrap`,
  `LocalWritePermissionHardener`, `SensitiveValueRedactor`

## Contexte

La question « faut-il un coffre (Gestionnaire d'identification Windows, magasin de certificats Windows,
trousseau Linux, identifiants systemd) ? » est revenue lors de l'audit de publication 1.2.1 (constat SEC-1).
Elle n'avait jamais été tranchée par écrit : le comportement actuel était défendable, mais rien ne disait ce
qu'il protège, ce qu'il ne protège pas, ni quand le reconsidérer.

Inventaire des secrets que MORPHEUS manipule :

| Secret | Où il vit | Ce que MORPHEUS en garde |
|---|---|---|
| Jeton bearer d'une identité remote | Chez l'utilisateur qui l'a reçu | **Rien.** Rendu une seule fois à la création ou à la rotation, jamais écrit. Seul `SHA-256(jeton)` est persisté dans `remote-auth.txt` |
| Mot de passe du keystore TLS | Environnement du processus serveur (`MORPHEUS_SERVER_TLS_PASSWORD`) | **Rien de durable.** Un `char[]` le temps d'ouvrir le keystore, puis écrasé ; aucun champ ne le retient |
| Clé privée TLS | Keystore PKCS12 fourni par l'opérateur | Rien de durable : le fichier est lu au démarrage depuis un répertoire exigé protégé en écriture (`requireWriteProtectedDirectory`), ses octets sont écrasés dès que le contexte TLS existe. MORPHEUS ne le crée ni ne le recopie |

Ces secrets n'existent qu'en **mode serveur remote**, qui est optionnel (ADR-0094). En usage local, MORPHEUS
ne manipule aucun secret.

### Ce que l'écriture de cette décision a révélé

En relisant le chemin du mot de passe TLS, un repli non documenté est apparu : `RemoteApiLaunchOptions`
acceptait aussi le mot de passe depuis la propriété JVM `morpheus.server.tls.password`, c'est-à-dire depuis
`-Dmorpheus.server.tls.password=…` **sur la ligne de commande**. Le message de refus l'appelait « protected
property » ; rien ne la protégeait. La garde textuelle existante interdisait le flag `--tls-password` — la bonne
intention — mais laissait passer `-D`, qui est aussi un argument de ligne de commande. Aucun test n'exerçait ce
chemin et aucune documentation opérateur ne le mentionnait. Il a été supprimé dans le même changement que cet
ADR : sans cette suppression, le §4 ci-dessous aurait été faux sous Linux.

## Décision

### 1. Pas de coffre

MORPHEUS n'intègre aucun coffre de secrets, sur aucune plateforme.

### 2. Invariant : aucun secret réutilisable au repos

MORPHEUS ne persiste jamais un secret qui permettrait, lu tel quel, de s'authentifier. Les jetons sont
persistés sous forme d'empreinte SHA-256 d'une valeur de 32 octets tirée par `SecureRandom` : l'empreinte
ne permet ni de s'authentifier ni, en pratique, de retrouver le jeton. C'est cet invariant qui rend un coffre
sans objet : il protégerait des empreintes.

Cette propriété tient **parce que les jetons sont générés, jamais choisis**. Une empreinte SHA-256 non salée
d'un secret choisi par un humain serait attaquable par dictionnaire ; celle d'un secret de 256 bits aléatoires
ne l'est pas.

### 3. Le mot de passe TLS ne vient que de l'environnement

Jamais d'un argument MORPHEUS (`--tls-password`), jamais d'une propriété JVM (`-D`) : les deux sont des
arguments de ligne de commande, et `/proc/<pid>/cmdline` est lisible par tous les comptes d'un Linux par
défaut, là où `/proc/<pid>/environ` n'est lisible que par le propriétaire du processus (et root).

C'est le seul réglage du mode remote sans repli propriété : le keystore, le fichier d'identités, les racines de
workspace et la concurrence acceptent toujours une propriété `morpheus.server.*`, parce qu'aucun d'eux n'est un
secret. Ne pas aligner le mot de passe sur eux.

Un lancement qui fournirait le mot de passe par `-D` est refusé **au démarrage**, avec un message qui nomme
`MORPHEUS_SERVER_TLS_PASSWORD` — jamais un serveur qui démarre sans le secret attendu.

### 4. Modèle de menace

**Protège contre** : les **autres comptes** de la machine. `remote-auth.txt` est en `rw-------`, son répertoire
en `rwx------` (POSIX et ACL Windows), tout accès se fait sans suivre les liens symboliques, et le fichier
temporaire est durci avant le déplacement atomique. Le mot de passe TLS vit dans un environnement de processus
qu'un autre compte ne lit pas.

**Ne protège pas contre** — et ne prétend pas le faire :

- la compromission du **compte** qui exécute MORPHEUS : ce compte lit son propre environnement et ses propres
  fichiers. Un coffre ne changerait rien : un code malveillant sous ce compte utilise la clé depuis le coffre
  aussi bien que depuis l'environnement ;
- un **administrateur / root** de la machine ;
- une **sauvegarde** du profil ou un disque non chiffré contenant le keystore ;
- la manière dont l'**opérateur** conserve le mot de passe hors de MORPHEUS (script de démarrage, fichier
  d'environnement) : c'est sa responsabilité, et la documentation opérateur lui recommande un compte de service
  dédié ;
- les copies que la **JVM** détient déjà sous forme de `String` dès la lecture de l'environnement : MORPHEUS
  n'en fabrique pas d'autre, il ne peut pas effacer celle-là.

Non vérifié lors de la décision : le comportement de `/proc/<pid>/cmdline` sur un système monté avec `hidepid`,
et la visibilité de la ligne de commande d'un autre compte sous Windows. L'argument tient sur un Linux par
défaut, ce qui suffit à rendre la propriété incompatible avec ce modèle de menace.

## Alternatives écartées

| Alternative | Pourquoi elle est écartée |
|---|---|
| Gestionnaire d'identification Windows (DPAPI) | Pas d'API Java : exige du code natif, donc une dépendance, dans un produit sans framework. Ne protège pas mieux contre la compromission du compte |
| Magasin de certificats Windows (`Windows-MY`) | Le meilleur candidat — plus de mot de passe du tout, clé non exportable. Mais il change un invariant asserté (`KeyStore.getInstance("PKCS12")`), exige d'ajouter `jdk.crypto.mscapi` à l'image jlink, et crée un chemin propre à Windows. Gain réel mais limité aux opérateurs du mode remote |
| Trousseau Linux (Secret Service / libsecret) | Absent sur un serveur sans session graphique — là où tourne le mode remote. Exige D-Bus et une bibliothèque native |
| Identifiants systemd (`LoadCredentialEncrypted=`) | Solution serveur crédible, mais propre à systemd ; demanderait d'accepter un fichier de mot de passe. L'opérateur peut déjà s'en approcher sans changement de MORPHEUS, via un fichier d'environnement protégé |
| Garder le repli propriété JVM, documenté | Il contredisait la règle (« jamais un flag CLI »), la documentation opérateur (« jamais un argument de ligne de commande ») et l'intention de la garde existante ; le documenter aurait inscrit une fuite vers les autres comptes dans le contrat |

## Conditions de réouverture

Cet ADR est à reconsidérer si l'une de ces conditions devient vraie :

1. MORPHEUS doit **stocker lui-même** un secret réutilisable (clé d'API d'un fournisseur, jeton d'un service
   tiers, secret partagé) — l'invariant du §2 tomberait ;
2. le mode remote est déployé **sur des machines partagées** par plusieurs utilisateurs, ou sous un compte non
   dédié ;
3. une exigence de **conformité** impose un coffre ou une clé non exportable ;
4. un jeton devient **choisi** plutôt que généré — l'empreinte non salée deviendrait attaquable (§2).

Le candidat privilégié en cas de réouverture est le magasin de certificats Windows (`Windows-MY`) et, sous
Linux, un fichier de mot de passe compatible avec les identifiants systemd.

## Preuves exécutables

- `RemoteApiLaunchOptionsTest` : mot de passe refusé en argument, refusé en propriété JVM
  (`aTlsPasswordGivenOnlyAsAJvmPropertyIsRefused`, message nommant la variable), non retenu, non rendu. Rouge sur
  le code antérieur ; rouge à nouveau quand le repli propriété est réintroduit (constaté le 22/09/2026)
- `RemoteHttpServerBootstrapArchitectureTest` : `--tls-password` et `morpheus.server.tls.password` interdits dans
  `RemoteApiLaunchOptions`, « protected property » interdit dans les deux messages de refus, `char[]` écrasé dans
  le lanceur et dans le bootstrap. La réintroduction du repli le fait échouer sur l'assertion de la propriété
  (constaté le 22/09/2026)
- `ProviderPluginTrustBoundaryContractTest`, `ExternalCodeTrustBoundaryArchitectureTest` :
  `MORPHEUS_SERVER_TLS_PASSWORD` jamais hérité par un processus externe
- `RemoteIdentityCredentialServiceTest`, `RemoteIdentityFileStoreTest`, `MorpheusRemoteIdentityFileTest` et les
  suites voisines de `morpheus-api` : empreinte seule, comparaison `MessageDigest.isEqual`, `toString()` redacté,
  fichier durci avant publication
