# MORPHEUS 1.2.2 — Notes de version

Statut : **EN PRÉPARATION**

Cette page recense les changements qu'un utilisateur de 1.2.1 doit connaître avant de migrer.

## Ruptures

### CLI : une option mal formée est refusée de la même façon par toutes les commandes

Toutes les commandes de la CLI lisent leurs options de la même manière. Un appel bien formé ne change pas ; seul le
message d'un appel mal formé change, et parfois son code de sortie. Le format de la ligne d'erreur
(`MORPHEUS error [<code>]: <message>`, ou les préfixes propres à `server`, `provider-plugins`, `reason`, `version`,
`product-info` et `update-check`) et les codes de sortie des appels bien formés sont inchangés.

Les refus sont, pour toutes les commandes :

| Saisie | Refus (code de sortie 2) |
|---|---|
| un mot qui n'est pas une option | `unexpected positional argument: <mot>` |
| une option que la commande ne connaît pas | `unknown option: --<option>` |
| une option à une valeur, ou un drapeau, donné deux fois | `duplicate option: --<option>` |
| une option sans valeur, ou suivie d'une autre option | `--<option> requires a value` |
| une valeur vide ou faite d'espaces | `--<option> requires a non-blank value; omit the option to leave it unset` |

Une option inconnue est refusée avant que les autres options ne soient lues : `changes list --bogus x` répond
`unknown option: --bogus`, pas `--project is required`. Une action ou une sous-commande inconnue est refusée avant que
ses options ne soient lues.

Ce qui change, commande par commande :

- `paths` refuse une option ou un mot en trop, code de sortie 2 ; la 1.2.1 les ignorait et répondait 0.
- `projects`, `sync`, `sync-status`, `requirements`, `trace-requirement`, `changes`, `constraints list`, `decisions`,
  `tasks`, `change-context`, `analyze-change`, `quality`, `external-references` : une option inconnue est nommée seule
  (`unknown option: --bogus`, et non `unknown options: [bogus]`), un mot en trop aussi
  (`unexpected positional argument: x`, et non `unexpected positional arguments: [x]`), et `sync --force --force`
  répond `duplicate option: --force` (et non `duplicate flag: --force`). Un nombre entouré d'espaces
  (`--limit " 5 "`) est accepté.
- `policy`, `portfolio`, `query`, `views`, `export` : un mot en trop répond `unexpected positional argument: x`, et non
  `unknown token: x`.
- `acceptance-criteria`, `constraints evaluate`, `composition`, `lifecycle`, `change-orchestration`,
  `augmented-context` : un mot en trop répond `unexpected positional argument: x`, et non `unknown option: x`.
- `augmented-context` : `--explain` donné deux fois répond `duplicate option: --explain` ; la 1.2.1 ignorait le second.
- `change-orchestration` : une option de l'autre action (`--from` pour `state`, `--lifecycle` pour `transition-check`)
  est refusée avant l'ouverture de la base, code de sortie 2, même quand le projet n'existe pas.
- `version`, `product-info`, `update-check` : une option inconnue répond `unknown option: --x`, et non
  `unknown option for <commande>: --x` ; un mot en trop, `unexpected positional argument: x`.
- `provider-plugins` : une option inconnue répond `unknown option: --x`, et non `unknown provider-plugins option: --x` ;
  un mot en trop, `unexpected positional argument: x`.
- `reason` : une option inconnue répond `unknown option: --x`, et non `unknown reason option: --x` (ni
  `--x requires a value` quand elle venait en dernier) ; un mot en trop, `unexpected positional argument: x`, et non
  `x requires a value`.
- `server` : une option inconnue répond `unknown option: --x`, et non `unknown server option: --x` ; un mot en trop,
  `unexpected positional argument: x`, et non `unexpected server argument: x`.

Ce qui reste propre à une commande : `server` lit aussi les options de disposition sous la forme `--data-dir=CHEMIN` ;
`augmented-context` et `change-orchestration` refusent une option de l'autre sujet ou de l'autre action par un message
qui le dit (`--change is not valid for augmented-context requirement`) ; `export query` refuse `--offset` et `--limit`
en disant qu'un export n'est jamais paginé ; `help` ne lit pas les mots qui le suivent.

### Serveur distant : toute requête relayée a une échéance

Le serveur distant attend la réponse du traitement interne 60 secondes pour une route READ et 300 secondes pour une
route WRITE ou ADMIN, probe de plugin compris. En 1.2.1, une écriture ou une opération ADMIN n'avait aucune échéance :
une réponse qui ne venait jamais retenait sa place sur la voie privilégiée jusqu'au redémarrage du serveur.

Une écriture expirée reçoit `504 UPSTREAM_WRITE_TIMEOUT`, distinct du `504 UPSTREAM_TIMEOUT` d'une lecture expirée.
Son résultat est **inconnu** : l'opération peut avoir abouti, ou aboutir encore. Un client relit l'état concerné avant
de la rejouer ; il ne la rejoue jamais aveuglément comme une lecture. Voir le guide du serveur d'équipe, section
concurrence.
