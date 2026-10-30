# Installer MORPHEUS 1.2.1

MORPHEUS 1.2.1 est distribué avec son propre runtime Java. **L’utilisateur final n’a besoin ni de Git, ni de Maven, ni d’un JDK.**

## Windows — installation recommandée

Artefacts publiés :

```text
MORPHEUS-1.2.1-windows-x64-setup.exe
MORPHEUS-1.2.1-windows-x64-setup.exe.sha256
```

Le setup est **per-user** et ne demande pas d’élévation administrative.

Installation par défaut :

```text
%LOCALAPPDATA%\Programs\MORPHEUS
```

Le setup peut ajouter MORPHEUS au `PATH` utilisateur et connecter explicitement le MCP natif à certains clients. Toutes ces options sont opt-in.

Clients MCP proposés :

```text
GitHub Copilot — JetBrains / IntelliJ
GitHub Copilot CLI
Claude Code
Claude Desktop
OpenAI Codex
```

Après installation :

```powershell
& "$env:LOCALAPPDATA\Programs\MORPHEUS\morpheus.exe" --version
& "$env:LOCALAPPDATA\Programs\MORPHEUS\morpheus.exe" --json product-info
& "$env:LOCALAPPDATA\Programs\MORPHEUS\morpheus.exe" paths
```

Version attendue : `1.2.1`.

## Données persistantes Windows

```text
Programme
%LOCALAPPDATA%\Programs\MORPHEUS

État persistant
%LOCALAPPDATA%\MORPHEUS\data
%LOCALAPPDATA%\MORPHEUS\config
%LOCALAPPDATA%\MORPHEUS\logs
%LOCALAPPDATA%\MORPHEUS\backups
```

Base SQLite par défaut :

```text
%LOCALAPPDATA%\MORPHEUS\data\morpheus.db
```

Mettre à jour ou désinstaller MORPHEUS ne supprime pas le knowledge store par défaut.

## Mise à jour Windows

**Avant de mettre à jour depuis 1.2.0 ou 1.1.0, lire [UPGRADE_1_2_1.md](UPGRADE_1_2_1.md)** : 1.2.1 fait avancer le schéma SQLite à sa première ouverture de la base, et le seul retour possible passe par une sauvegarde prise avant.

Lancer ensuite le setup 1.2.1 avec le même utilisateur Windows. Le setup conserve le même AppId et remplace les fichiers programme sans supprimer `data`, `config`, `logs` ou `backups`.

## Désinstallation Windows

Utiliser **Applications installées** ou le désinstalleur du répertoire programme.

L’uninstall retire les fichiers programme et les intégrations qu’il gère encore. Pour les clients MCP :

- une entrée étrangère `morpheus` n’est jamais supprimée ;
- une entrée modifiée manuellement après installation est conservée ;
- la suppression est pilotée par le registre de propriété MORPHEUS.

L’état persistant sous `%LOCALAPPDATA%\MORPHEUS` est conservé par défaut.

## Windows — archive portable

Artefacts :

```text
morpheus-1.2.1-windows-x64.zip
morpheus-1.2.1-windows-x64.zip.sha256
morpheus-1.2.1-windows-x64-release-manifest.json
```

Extraire puis lancer :

```powershell
.\morpheus\morpheus.exe --version
```

Le gestionnaire MCP est inclus sous `integration\configure-mcp-clients.ps1`.

## Linux x64

Artefacts :

```text
morpheus-1.2.1-linux-x64.tar.gz
morpheus-1.2.1-linux-x64.tar.gz.sha256
morpheus-1.2.1-linux-x64-release-manifest.json
```

Vérifier puis extraire :

```bash
sha256sum -c morpheus-1.2.1-linux-x64.tar.gz.sha256
tar -xzf morpheus-1.2.1-linux-x64.tar.gz
./morpheus/bin/morpheus --version
```

Layout par défaut :

```text
${XDG_DATA_HOME:-$HOME/.local/share}/morpheus
${XDG_CONFIG_HOME:-$HOME/.config}/morpheus
${XDG_STATE_HOME:-$HOME/.local/state}/morpheus/logs
${XDG_STATE_HOME:-$HOME/.local/state}/morpheus/backups
```

## Overrides de chemins

CLI :

```text
--data-dir PATH
--config-dir PATH
--db PATH
```

Environnement :

```text
MORPHEUS_DATA_DIR
MORPHEUS_CONFIG_DIR
MORPHEUS_LOGS_DIR
MORPHEUS_BACKUPS_DIR
MORPHEUS_DB
```

## Journal opérationnel

Par défaut MORPHEUS n'écrit aucun journal opérationnel. La variable d'environnement `MORPHEUS_OPERATIONAL_LOGS`
l'active :

```text
(absente, vide)            aucun journal
off, none                  aucun journal
json, jsonl, structured    un événement JSON par ligne, sur la sortie d'erreur
file                       un événement JSON par ligne, dans le répertoire de logs
```

Avec `file`, MORPHEUS écrit dans `morpheus-operational.jsonl`, sous le répertoire de logs que la commande
`morpheus paths` affiche et que `MORPHEUS_LOGS_DIR` déplace. Le fichier ne dépasse pas 4 Mio : au-delà, il est renommé
en `morpheus-operational.jsonl.1` et un nouveau commence ; quatre anciens fichiers sont gardés, le plus vieux est
supprimé. Le répertoire et les fichiers ne sont lisibles que par le compte qui lance MORPHEUS. Plusieurs processus
MORPHEUS du même compte peuvent écrire dans le même journal.

Si le répertoire de logs ne peut pas être créé, n'est pas réservé à ce compte ou n'accepte pas l'écriture, MORPHEUS ne
démarre pas : il affiche une ligne qui commence par `MORPHEUS error: OPERATIONAL_LOG_FILE_UNUSABLE` et sort avec le
code 5. Il n'écrit jamais le journal ailleurs à la place. Désigner un autre répertoire avec `MORPHEUS_LOGS_DIR`, ou
choisir un autre mode.

Quelques messages techniques du transport MCP et de la base restent écrits sur la sortie d'erreur dans tous les
modes ; ils ne figurent pas dans le fichier.

La casse et les espaces autour de la valeur ne comptent pas. Toute autre valeur — `true`, `1`, une faute de frappe —
est refusée : MORPHEUS ne démarre pas, affiche sur la sortie d'erreur une ligne qui commence par
`MORPHEUS usage error: OPERATIONAL_LOG_MODE_UNKNOWN` avec les valeurs acceptées, et sort avec le code 2. Une valeur
inconnue n'est jamais remplacée par « aucun journal ».

Quand le journal est actif, il contient aussi les avertissements et les erreurs des bibliothèques que MORPHEUS
embarque, sous le code `THIRD_PARTY_LOG` : une liaison avec MINOS ou NEXUS qui échoue y laisse ce que la bibliothèque
de protocole en a dit. Quand il ne l'est pas, ces bibliothèques n'écrivent rien.

## MCP natif

Le serveur local est :

```text
morpheus mcp --stdio
```

Docker n’est pas requis. Guide détaillé : [MCP_CLIENTS.md](MCP_CLIENTS.md).

## Intégrations MINOS / NEXUS

MINOS et NEXUS ne sont pas embarqués. Les adapters clients sont présents mais restent optionnels. Sans configuration, les commandes de statut retournent `DISABLED`.

Voir [INTEGRATIONS.md](INTEGRATIONS.md).

## Vérification SHA-256

Windows :

```powershell
Get-FileHash .\MORPHEUS-1.2.1-windows-x64-setup.exe -Algorithm SHA256
Get-Content .\MORPHEUS-1.2.1-windows-x64-setup.exe.sha256
```

Linux :

```bash
sha256sum -c morpheus-1.2.1-linux-x64.tar.gz.sha256
```

## Signature de l'éditeur (Windows)

L'installeur et le lanceur de MORPHEUS 1.2.1 **ne sont pas signés** par un certificat d'éditeur. Au lancement de
l'installeur, Windows le présente comme provenant d'un **éditeur inconnu**, et SmartScreen peut demander une
confirmation supplémentaire (« Informations complémentaires », puis « Exécuter quand même »). Ce n'est pas le signe d'un
fichier altéré : c'est ce que Windows affiche pour tout exécutable non signé.

Pour une release non signée, la vérification disponible est la somme SHA-256 publiée avec chaque fichier (section
suivante), complétée par l'attestation de provenance décrite dans [PRODUCT_INTEGRITY.md](PRODUCT_INTEGRITY.md).

Aucune signature d'éditeur n'est prévue pour les releases de MORPHEUS. Si une release venait à être signée, ses
notes de version le diraient, et les versions antérieures resteraient telles qu'elles ont été publiées. L'état d'un
fichier se lit dans l'onglet « Signatures numériques » de ses propriétés (clic droit, Propriétés), qui n'existe que pour
un fichier signé, ou avec PowerShell :

```powershell
Get-AuthenticodeSignature .\MORPHEUS-<version>-windows-x64-setup.exe | Format-List Status, SignerCertificate
```

Même signé, un installeur nouvellement publié peut encore déclencher l'avertissement de réputation de SmartScreen : la
réputation d'un éditeur s'acquiert dans la durée, quel que soit le type de certificat.

La preuve de publication des douze assets de 1.2.1 (provenance, sommes de contrôle, inventaire) est conservée dans [`../project/validation/VALIDATION_RELEASE_1.2.1.md`](../project/validation/VALIDATION_RELEASE_1.2.1.md).
