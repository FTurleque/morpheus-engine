# MORPHEUS

> *« Il y a une différence entre connaître le chemin et arpenter le chemin. »*
> — Morpheus, *The Matrix* (1999)

Le code **parcourt** le chemin. MORPHEUS est ce qui le **connaît**.

**MORPHEUS** est un moteur local d’intelligence des spécifications et de l’intention (*Specification & Intent Intelligence Engine*). Il lit les spécifications d’un projet, en fait une base de connaissance versionnée et interrogeable, et la met à disposition des humains, des scripts et des assistants IA. Il répond à une question que le code seul ne peut pas trancher :

> Qu’est-ce qui doit être construit, pourquoi, selon quelles règles, et comment prouver que le résultat correspond à l’intention ?

## Le problème

Dans un projet, l’intention est éparpillée : des exigences dans des fichiers Markdown, des changements proposés à moitié appliqués, des décisions dans des comptes rendus, des critères d’acceptation dans la tête de quelqu’un. Un développeur — ou un assistant IA — qui ouvre le code voit *ce qui est fait*, jamais *ce qui était voulu*. Il devine, et il devine mal.

MORPHEUS rend cette intention **lisible par une machine**, sans la déformer :

- il distingue ce qui est **en vigueur** de ce qui est seulement **proposé** ou **historique** ;
- il garde la **provenance** de chaque fait : de quel fichier, de quelle source, dans quelle version ;
- quand deux sources se contredisent, il **montre le conflit** au lieu de choisir en silence ;
- quand il ne sait pas, il répond **inconnu** — jamais une supposition présentée comme un fait.

## Comment ça marche

```text
   spécifications du projet                MORPHEUS                      qui interroge
 ┌──────────────────────────┐    ┌───────────────────────────┐    ┌──────────────────────────┐
 │ OpenSpec                 │    │ normalise                 │    │ CLI     humains, scripts │
 │ Markdown structuré       │ ─► │ compose plusieurs sources │ ─► │ MCP     IDE, agents IA   │
 │ plugins de provider      │    │ versionne en snapshots    │    │ HTTP    intégrations     │
 └──────────────────────────┘    │ stocke en local (SQLite)  │    └──────────────────────────┘
                                 └───────────────────────────┘
```

1. **Lire** — un *provider* lit les spécifications là où elles sont (OpenSpec, Markdown structuré, ou votre propre format via le Provider SDK).
2. **Normaliser** — exigences, changements, contraintes, décisions, critères d’acceptation et tâches deviennent des objets à identité stable, indépendants du format d’origine.
3. **Publier** — chaque synchronisation produit un *snapshot* versionné ; on peut toujours dire ce qui était vrai à un moment donné.
4. **Interroger** — recherche, traçabilité, analyse d’impact d’un changement, requêtes, règles de gouvernance, par la surface qui vous convient.

Tout tourne **sur votre machine**. Aucun service en ligne, aucun LLM, aucun Docker n’est requis.

## Ce que vous pouvez en faire

**Comprendre un projet**
- retrouver une exigence, ses scénarios, ses critères d’acceptation et ce qui la justifie ;
- suivre la traçabilité d’une exigence vers les décisions, contraintes et tâches liées ;
- voir ce qu’un changement proposé modifierait avant de l’appliquer.

**Donner du contexte fiable à un assistant IA**
- brancher MORPHEUS comme serveur MCP dans Copilot, Claude ou Codex : l’assistant lit l’intention réelle du projet au lieu de l’inférer du code ;
- obtenir une analyse assistée où **faits**, **inférences** et **suggestions** restent séparés, chaque fait renvoyant à sa preuve.

**Gouverner**
- écrire des règles versionnées (*Policy Packs*), les évaluer à blanc, auditer leurs activations ;
- piloter le cycle de vie d’un changement, avec confirmation explicite à chaque écriture ;
- interroger plusieurs projets à la fois (*portfolio*), enregistrer des vues, exporter en JSON, CSV ou Markdown.

**Travailler en équipe**
- exposer, sur demande seulement, un serveur HTTPS avec authentification, rôles lecture / écriture / administration et audit ;
- sauvegarder la base de façon cohérente et la restaurer hors ligne.

## Installation

Les distributions embarquent leur runtime Java : aucun JDK n’est à installer.

Windows :

```powershell
MORPHEUS-1.2.0-windows-x64-setup.exe
morpheus --version
```

Linux :

```bash
tar -xzf morpheus-1.2.0-linux-x64.tar.gz
./morpheus/bin/morpheus --version
```

Guide complet : [`docs/user/INSTALLATION.md`](docs/user/INSTALLATION.md).

## Premier projet

```bash
morpheus projects add --workspace /path/to/project
morpheus projects list
morpheus sync --project <projectId>
morpheus requirements find --project <projectId> --query "session"
```

La suite pas à pas : [`docs/user/QUICKSTART.md`](docs/user/QUICKSTART.md).

## Brancher un assistant IA

```text
morpheus mcp --stdio
```

Clients documentés : GitHub Copilot (JetBrains / IntelliJ et CLI), Claude Code, Claude Desktop, OpenAI Codex. Sous Windows, un gestionnaire optionnel écrit la configuration pour vous, sauvegarde l’existant et ne touche à aucun autre serveur déjà déclaré.

Guide : [`docs/user/MCP_CLIENTS.md`](docs/user/MCP_CLIENTS.md).

## Trois façons d’y accéder

| Surface | Pour qui | Transport |
|---|---|---|
| **CLI** | humains, scripts, administration locale | processus local |
| **MCP** | IDE, agents, orchestrateurs | STDIO |
| **HTTP** | intégrations | API locale `/api/v1` sur la boucle locale ; façade HTTPS d’équipe sur demande |

Les trois reposent sur les mêmes services. Ce qu’une surface n’expose pas est déclaré, jamais oublié : la restauration d’une sauvegarde, par exemple, ne se fait que hors ligne. Le détail est tenu dans [`contracts/public-surfaces.tsv`](contracts/public-surfaces.tsv).

## Ce que MORPHEUS ne confond jamais

Un outil qui parle d’intention n’a de valeur que si l’on peut se fier à ce qu’il affirme. MORPHEUS tient donc quelques distinctions pour acquises, partout :

- **Proposé n’est pas en vigueur.** Un changement proposé n’apparaît jamais dans l’état courant tant qu’il n’a pas été explicitement appliqué.
- **Inconnu n’est ni un échec ni un blocage.** Une information manquante est signalée comme telle ; elle n’est jamais convertie en verdict.
- **Un conflit n’est pas résolu en silence.** Quand deux sources divergent, les deux restent visibles, avec leur provenance.
- **Lire n’est pas écrire.** Une requête, un export, une évaluation à blanc ou une analyse assistée ne modifient rien.
- **Une suggestion n’est pas un fait.** L’analyse assistée sépare ce qui est établi, ce qui est déduit et ce qui est proposé.
- **Local d’abord.** Le mode distant est un choix explicite ; s’authentifier ne donne pas tous les droits.

La liste complète de ces garanties est dans le [guide utilisateur](docs/user/README.md#garanties-structurantes).

## Documentation

Point d’entrée : [`docs/README.md`](docs/README.md).

- **utiliser MORPHEUS** — [`docs/user/`](docs/user/README.md) : [installation](docs/user/INSTALLATION.md), [démarrage rapide](docs/user/QUICKSTART.md), [référence CLI](docs/user/CLI.md), [notes de version](docs/user/release-notes/) ;
- **le développer ou l’étendre** — [`docs/developer/`](docs/developer/README.md) : [build et tests](docs/developer/BUILD_AND_TEST.md), [Provider SDK](docs/developer/PROVIDER_SDK.md), [contrats machine](docs/developer/reference/README.md) ;
- **comprendre son architecture** — [`docs/architecture/`](docs/architecture/README.md) et le [registre des décisions](docs/architecture/adr/README.md) ;
- **suivre le projet** — [`docs/project/`](docs/project/README.md).

## État du projet

Version stable publiée : **1.2.0**. Version en développement : **1.2.1**.

L’état détaillé — release, baseline technique, modules, gates de qualité et chaîne d’approvisionnement — est dans [`CONTRIBUTING.md`](CONTRIBUTING.md) ; la politique de sécurité dans [`SECURITY.md`](SECURITY.md).

## Licence

MORPHEUS est un logiciel propriétaire dont le code source est consultable ; ce n’est pas un logiciel open source. Voir [`LICENSE`](LICENSE).
