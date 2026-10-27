# MORPHEUS 1.2.2 — Notes de version

Statut : **EN PRÉPARATION**

Cette page recense les changements qu'un utilisateur de 1.2.1 doit connaître avant de migrer.

## Ruptures

### CLI : une option mal formée est refusée de la même façon par toutes les commandes

Toutes les commandes de la CLI lisent désormais leurs options de la même manière. Un appel bien formé ne change pas ;
seul le message d'un appel mal formé change, et parfois son code de sortie. Le format de la ligne d'erreur
(`MORPHEUS error [<code>]: <message>`, ou les préfixes propres à `server`, `provider-plugins`, `reason`, `version`,
`product-info` et `update-check`) et les codes de sortie des appels bien formés sont inchangés.

Les refus sont, pour toutes les commandes :

| Saisie | Refus (code de sortie 2) |
|---|---|
| une option que la commande ne connaît pas | `unknown option: --<option>` |
| un mot qui n'est pas une option | `unexpected positional argument: <mot>` |
| une option à une valeur, ou un drapeau, donné deux fois | `duplicate option: --<option>` |
| une option sans valeur, ou suivie d'une autre option | `--<option> requires a value` |
| une valeur vide ou faite d'espaces | `--<option> requires a non-blank value; omit the option to leave it unset` |

Une option inconnue est refusée avant que les autres options ne soient lues : `changes list --bogus x` répond
`unknown option: --bogus` et non plus `--project is required`.

Ce qui change, commande par commande :

- `paths` refuse une option ou un mot en trop (code de sortie 2) ; elle les ignorait et répondait 0.
- `projects`, `sync`, `sync-status`, `requirements`, `trace-requirement`, `changes`, `constraints list`, `decisions`,
  `tasks`, `change-context`, `analyze-change`, `quality` : une option inconnue est nommée seule
  (`unknown option: --bogus` au lieu de `unknown options: [bogus]`), un mot en trop aussi
  (`unexpected positional argument: x` au lieu de `unexpected positional arguments: [x]`), et `sync --force --force`
  répond `duplicate option: --force` au lieu de `duplicate flag: --force`. Un nombre entouré d'espaces
  (`--limit " 5 "`) est accepté, comme par les autres commandes.
- `policy`, `portfolio`, `query`, `views`, `export` : un mot en trop répond `unexpected positional argument: x` au lieu de `unknown token: x`, et une action
  inconnue est refusée avant que ses options ne soient lues.
- `acceptance-criteria`, `constraints evaluate`, `composition`, `lifecycle`,
  `change-orchestration` : un mot en trop répond `unexpected positional argument: x` au lieu de `unknown option: x`.
- `composition` : une action inconnue est refusée avant que ses options ne soient lues.
