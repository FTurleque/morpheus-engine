# Règles — Outillage de session (RTK, PowerShell, sorties longues)

Ces règles portent sur la manière d'exécuter les commandes du dépôt, pas sur ce que le code
doit contenir. Elles existent parce que deux défauts réels ont coûté cher : un garde-fou muet
pendant toute une révision, et des validateurs dont le verdict se noie sous des milliers de
lignes Maven.

## RTK — proxy de sortie

`rtk` filtre la sortie des commandes avant qu'elle n'atteigne le contexte. Il est **configuré
hors du dépôt**, dans le profil Claude Code du mainteneur, qui réécrit les commandes Bash à la
volée. Le dépôt n'ajoute pas de hook : il apporte uniquement des **filtres projet**.

### Ce qui est déjà couvert sans rien faire

`./mvnw` (le wrapper est bien résolu, ce n'est pas le `mvn` du PATH), `git`, `gh`, `grep`,
`cat`, `ls`, `find`. Aucune action à prendre : écrire la commande normalement.

### Ce que le dépôt ajoute — `.rtk/filters.toml`

Un filtre `morpheus-validators` garde les verdicts, les écarts et les échecs des validateurs
`scripts/validate-*` et `scripts/verify-*`, et coupe la sortie Maven qu'ils produisent en
interne. C'est une **allowlist** : un motif d'échec absent de la liste est un échec invisible,
donc le filtre porte ses propres tests inline, au même titre qu'un gate porte les siens.

```bash
rtk verify --filter morpheus-validators   # rejoue les tests inline du filtre
rtk trust -y                              # accorde la confiance, machine par machine
```

**Un filtre modifié n'est plus de confiance, donc plus chargé du tout.** Après toute édition de
`.rtk/filters.toml` : rejouer `rtk verify`, puis `rtk trust -y`.

### Ce qui n'est pas couvert

La réécriture ne s'applique qu'aux formes que `rtk` sait reconnaître : `powershell -File
scripts/validate-*.ps1` l'est, `bash scripts/validate-*.sh` ne l'est pas. Pour ces formes, ne
pas chercher à filtrer la sortie : les validateurs écrivent déjà leur relevé dans
`validation-output/<cible>/validation-summary.txt` et leurs échecs dans `failure-summary.txt`.
**Lire le relevé plutôt que la sortie.**

### Ne pas contourner

`rtk proxy <cmd>` exécute sans filtrer. C'est un outil de diagnostic quand une sortie semble
tronquée à tort — pas un mode par défaut. Si un filtre coupe une information utile, c'est le
filtre qu'il faut corriger et re-tester.

## PowerShell — ASCII obligatoire dans les scripts

`.claude/settings.json` lance les hooks avec `powershell`, c'est-à-dire **Windows PowerShell
5.1**, qui décode un fichier sans BOM dans la page de codes ANSI et non en UTF-8. Un tiret cadratin
UTF-8 y devient une paire d'octets dont l'un correspond à un guillemet typographique, que
PowerShell traite comme un **délimiteur de chaîne** : le script entier cesse de parser.

C'est arrivé. Les deux hooks du dépôt ont passé une révision complète à ne rien faire, en
renvoyant une erreur d'analyse à chaque appel d'outil au lieu d'exécuter le moindre garde-fou.
Le fail-open protégeait de tout sauf de ça.

- Tout `.ps1` du dépôt reste **ASCII pur** — c'est déjà la convention de `scripts/`.
- `post-edit.ps1` signale un `.ps1` qui acquiert un octet non-ASCII sans BOM.
- Après avoir édité un hook, le tester avant de conclure :

```bash
echo '{"command":"git status"}' | powershell -NoProfile -File .claude/hooks/pre-bash.ps1
```

Une erreur d'analyse rend le garde-fou muet **sans rien casser de visible** : rien dans le
déroulement d'une session ne le signale.

## Codes de sortie des hooks

| Code | Effet |
|---|---|
| `0` | autorise ; `stdout` est consultatif et n'atteint que la transcription |
| `2` | **bloque** ; `stderr` est renvoyé au modèle, qui peut choisir une autre commande |
| autre | erreur non bloquante — la commande passe quand même |

Un blocage écrit en `exit 1` ne bloque rien. Un avertissement destiné au modèle qui sort sur
`stdout` en `PostToolUse` ne lui parvient pas : utiliser le `additionalContext` JSON.

## Lancer les validateurs

Toujours passer la version courante, lue dans `ProductMetadata` : chaque validateur a une
valeur par défaut datée de son milestone et échoue sur la version du lanceur sans cet argument.
Le hook `pre-bash` avertit quand l'argument manque.
