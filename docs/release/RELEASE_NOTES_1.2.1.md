# MORPHEUS 1.2.1 — Notes de version

Statut : **EN PRÉPARATION — NON PUBLIÉE**

`1.2.1` est la baseline corrective de développement. Elle ne devient une release publiée qu'après création du tag et
qualification de ses artefacts (suivi #185, critère de clôture dans
[`../validation/RELEASE_QUALIFICATION.md`](../validation/RELEASE_QUALIFICATION.md) ; version courante dans
[`../developer/PRODUCT_VERSION.md`](../developer/PRODUCT_VERSION.md)).
Cette page recense, au fil des corrections, les changements qu'un utilisateur de 1.2.0 doit connaître avant de migrer.
Elle ne revendique aucune publication.

## Ruptures

### Structured Markdown : un bloc `morpheus task` déclare son achèvement

Jusqu'à 1.2.0, un bloc `task` sans champ `completed` était publié comme **non achevé** : l'absence valait `false`, et
ce `false` était stocké puis servi par la CLI, le HTTP, le MCP et le contexte augmenté (`[pending]`) comme une
observation.

À partir de 1.2.1, `completed` est **obligatoire**, comme `key`, `change` et `title`, et vaut `true` ou `false`. Un bloc
qui l'omet est refusé à la lecture :

```text
Structured Markdown normalization failed: missing 'completed' in task block at line <N>
```

Le diagnostic est bloquant (`INVALID_SOURCE`, sévérité `ERROR`) et le provider Structured Markdown ne contribue alors
aucun contenu : dans un workspace où il est seul, la synchronisation échoue et n'active aucun nouveau snapshot ; en
composition, c'est le comportement de tout échec de lecture de ce provider.

**Migration.** Ajouter `completed=false` (ou `completed=true`) à chaque bloc `task` de `morpheus/specification.md` qui ne
le déclare pas. Aucune migration de base n'est nécessaire : les tâches déjà publiées ne changent pas tant que le projet
n'est pas resynchronisé.

Décision : [ADR-0108, amendement du 24 septembre 2026 (PRV-4)](../adr/0108-a-response-says-what-it-could-not-observe.md).
Format du bloc : [`../user/QUICKSTART.md`](../user/QUICKSTART.md).

### Contexte augmenté : plus de chemin serveur dans le statut NEXUS (HTTP et MCP)

Jusqu'à 1.2.0, les deux routes `augmented-context` (rôle READ en remote) et les deux outils MCP `get_augmented_*_context`
publiaient le statut NEXUS tel quel dans `technicalContext.status.details` : `javaCommand`, `jar` et `home`, trois chemins
absolus du serveur, que la route `GET /api/v1/integrations/nexus/status` retirait pourtant déjà.

À partir de 1.2.1, une seule projection s'applique à ces quatre surfaces et à la route de statut : chaque emplacement est
rapporté comme configuré (`jarPathConfigured`, `homeDirectoryConfigured`, `javaCommandConfigured` : `"true"`/`"false"`) et non
nommé ; un message d'échec qui nomme un chemin est remplacé. `projectId`, `projectName` et `estimatedTokens` sont conservés. Les clés
NEXUS `jar`/`home` sont alignées sur les clés MINOS `jarPath`/`homeDirectory` (avant, la route de statut NEXUS les supprimait sans les
remplacer). La CLI garde les réglages complets.

**Migration.** Un client qui lisait `details.jar`, `details.home` ou `details.javaCommand` dans une réponse HTTP ou MCP doit lire
`jarPathConfigured`, `homeDirectoryConfigured` ou `javaCommandConfigured` ; la valeur du chemin reste disponible via la CLI.

Décision : [ADR-0094, amendement du 25 septembre 2026 (NEX-1)](../adr/0094-optional-team-remote-server-mode.md).

### CLI `portfolio` : une option inconnue est refusée

Jusqu'à 1.2.0, `morpheus portfolio <action>` acceptait en silence toute option `--clé valeur` qu'elle ne lisait pas.
Une faute de frappe sur un filtre facultatif changeait le sens de la commande sans l'annoncer : `portfolio references
--portfolio P --projet X` rendait **toutes** les références du portefeuille, code 0. Sur une action d'écriture, `add-project
… --workspac /src` persistait une appartenance sans workspace.

À partir de 1.2.1, chaque action refuse une option qu'elle ne lit pas, comme les autres adaptateurs du CLI :

```text
MORPHEUS error [2]: unknown option: --projet
```

**Migration.** Un script qui passait une option ignorée reçoit maintenant le code `2` : corriger ou retirer l'option.
Les options reconnues par action sont exactement celles que l'action lit (`ACTION_OPTIONS` dans `MorpheusPortfolioCli`).

Décision : garde `CliOptionParsingRefusesUnknownOptionsTest` (chaque méthode qui appelle `SimpleOptions.parse` appelle aussi `rejectUnknown`) ; un `case` sans entrée dans `ACTION_OPTIONS` est refusé comme action inconnue.

### CLI `policy evaluate` et `policy dry-run` : un refus atteint le code de sortie

Jusqu'à 1.2.0, ces commandes rendaient `0` quelle que soit la décision écrite dans le JSON, `BLOCK` et `UNKNOWN` compris.
Un pipeline qui appliquait l'idiome documenté `if ($LASTEXITCODE -ne 0) { throw }` laissait donc passer un refus, et un
`UNKNOWN` (règle non évaluable) était converti en succès au niveau du processus.

À partir de 1.2.1, la décision effective est reportée sur le code de sortie :

| Décision | Code |
|---|---:|
| `PASS`, `WARN` | `0` |
| `BLOCK`, `UNKNOWN` | `4` (`STATE_ERROR`) |

Le JSON est toujours imprimé, y compris avec le code `4`. Les autres actions de `policy` (configuration, listes, audit) ne
changent pas : elles rendent `0` quand elles réussissent.

**Migration.** Une CI qui appelait `policy evaluate` ou `policy dry-run` sans regarder le JSON échoue maintenant sur `BLOCK` et
`UNKNOWN` : c'est le but. Pour ne pas échouer, lire `decision` dans le JSON plutôt que le code de sortie. Les validateurs
`scripts/validate-m25.*` attendent désormais explicitement le code `4` sur leurs deux appels.

Décision : [ADR-0108](../adr/0108-a-response-says-what-it-could-not-observe.md).
