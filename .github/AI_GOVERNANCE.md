# Gouvernance IA — Morpheus Engine

Ce document cartographie **tout le paramétrage IA du dépôt** : où vit chaque artefact, ce
qui le charge, et où vit la vérité. Il ne duplique aucune règle métier — pour le contenu
réel, suivre les renvois.

## Principe fondamental

**Une seule surface IA : `.claude/`.** Les règles détaillées (exemples réels, invariants
complets) vivent dans `.claude/rules/*.md` et nulle part ailleurs. Une règle se modifie
à un seul endroit.

**Aucun chiffre périssable n'est recopié tel quel** dans un artefact IA — seuils de
coverage, nombre de tests, nombre d'ADR, version produit. Chaque artefact renvoie vers sa
source vivante (`config/m21-quality-ratchets.properties`, `pom.xml`, `docs/adr/` compté
par `glob`, les tests sous `morpheus-architecture-tests/`). Voir `.claude/rules/meta.md`.

## Le dépôt n'a plus de configuration Copilot — ne pas la reconstruire

Jusqu'au 15/09/2026, `.github/` portait une configuration Copilot complète
(`copilot-instructions.md`, `instructions/*.instructions.md`, `prompts/*.prompt.md`,
`skills/morpheus-engine/SKILL.md`, `hooks/rtk-rewrite.json`) présentée comme le pendant de
`.claude/`. Elle a été **retirée**, pour une raison mesurée et non de préférence :

- les six `instructions/*.instructions.md` doublaient `.claude/rules/*.md`, et **aucun test ne
  vérifiait que les deux copies disaient la même chose** — chaque changement de règle se
  payait deux fois, à la main, sans filet ;
- la dérive s'était déjà produite dans les deux sens : les prompts Copilot avaient été
  purgés de leurs chiffres codés en dur alors que les commandes `.claude/commands/` citaient
  encore un ratchet de couverture périmé ; à l'inverse `copilot-instructions.md` portait des
  consignes absentes de `.claude/rules/`, dont deux contredisaient les règles Claude.

Avant la suppression, les consignes normatives propres à la surface Copilot ont été
reportées dans `.claude/` (secrets, justification des fichiers de gouvernance en PR, style
de test, formulations sans chiffre des commandes). Celles qui contredisaient `.claude/rules/`
n'ont pas été reportées : un commentaire de code qui cite un ticket, et l'interdiction
d'amender un ADR fusionné — ADR-0103 porte un amendement daté.

**Ne pas réintroduire** de miroir `.github/instructions/`, `.github/prompts/`, ni de
`copilot-instructions.md`. Si un second outil IA doit un jour lire les règles, il lit
`.claude/rules/`, ou un test vérifie la convergence des deux copies — jamais une copie
maintenue à la main.

L'intégration MCP de MORPHEUS avec Copilot (M28 : `integration/configure-mcp-clients.ps1`,
installeur, `docs/user/MCP_CLIENTS.md`) est une **fonctionnalité produit**, sans rapport avec
ce paramétrage : elle reste entière.

## Matrice des artefacts

| Type | Emplacement | Chargé par | Versionné | Rôle |
|---|---|---|---|---|
| Règles | `.claude/CLAUDE.md` + `.claude/rules/*.md` | Claude Code, automatiquement | ✅ | Source détaillée unique |
| Skills | `.claude/skills/<nom>/SKILL.md` | Claude Code, à la demande quand la description correspond à la tâche | ✅ | Procédures (« comment faire »), là où une règle dit « ce qui est interdit » |
| Agents | `.claude/agents/*.md` | Invocables comme sub-agents (`architect`, `bug-investigator`, `contract-guardian`, `security-reviewer`) | ✅ | Revue spécialisée avec procédure et format de réponse stricts |
| Commandes | `.claude/commands/*.md` | Invocables (`/governance`, `/security-audit`, `/test-gate`, `/validate`, `/milestone`, `/health`, `/bug-fix`) | ✅ | Workflows pas-à-pas |
| Hooks | `.claude/hooks/pre-bash.ps1`, `.claude/hooks/post-edit.ps1` | `PreToolUse`/`PostToolUse`, déclarés dans `.claude/settings.json` | ✅ | Garde-fous fail-open (avertir/bloquer sans jamais bloquer tous les outils par bug interne) |
| Permissions | `.claude/settings.json` | Claude Code | ✅ | Allow/deny partagés (force-push protégé, `rm -rf` non borné) |
| Permissions locales | `.claude/settings.local.json` | Claude Code, machine du mainteneur | ❌ | Allow additionnel (commandes `rtk`) ; toute permission utile à tous va dans `settings.json` |
| Filtres RTK | `.rtk/filters.toml` | `rtk`, une fois la confiance accordée localement (`rtk trust`) | ✅ | Filtre de sortie des validateurs, avec ses propres tests inline |

## Ce que les tests vérifient

`RepositoryDocumentationCoherenceTest` scanne `.claude/CLAUDE.md`, ce document,
`.claude/commands/`, `.claude/agents/`, `.claude/rules/` et `.claude/skills/` à la recherche
de chiffres périssables (version de schéma SQLite, totaux d'ADR ou de modules). La liste de ces
surfaces **refuse** une surface absente au lieu de scanner moins : une surface se retire en
la retirant de la liste déclarée, dans le même changement. Les skills vivent un niveau plus
bas que les autres répertoires (`<nom>/SKILL.md`), donc la collecte descend d'un cran — une
skill posée hors de ce schéma ne serait pas scannée, ce qu'un test refuse explicitement.
`D2RepositoryHardeningArchitectureTest` vérifie que la commande CVE documentée par
`.claude/commands/security-audit.md`, `.claude/agents/security-reviewer.md` et
`.claude/rules/build.md` est bien celle qui lance le scan.

## RTK

La **réécriture** des commandes shell par `rtk` reste configurée **hors du dépôt**, dans le
profil Claude Code de l'utilisateur ; `.claude/settings.local.json` porte les autorisations
correspondantes sur la machine du mainteneur. Le dépôt ne porte toujours aucun hook RTK et
n'en dépend pas : sans `rtk` installé, chaque commande s'exécute telle quelle.

Le dépôt porte en revanche un **filtre projet**, `.rtk/filters.toml`, parce que la
connaissance qu'il encode est celle du dépôt et non celle d'une machine : quelles lignes d'un
validateur MORPHEUS sont un verdict et lesquelles sont du bruit Maven. Un filtre qui coupe une
ligne d'échec est pire que pas de filtre, donc il porte ses propres tests inline
(`rtk verify --filter morpheus-validators`), au même titre qu'un gate porte les siens. La
confiance (`rtk trust`) est accordée **par machine** et n'est pas versionnée ; un filtre
modifié la perd, donc n'est plus chargé du tout tant qu'elle n'est pas réaccordée.

`.claude/rules/tooling.md` dit ce qui est couvert, ce qui ne l'est pas, et pourquoi le relevé
écrit par un validateur dans `validation-output/` reste la lecture la moins chère.

Les règles `deny` de `.claude/settings.json` existent en double, avec et sans le préfixe
`rtk` : une commande réécrite ne doit pas échapper à une interdiction écrite pour sa forme
d'origine.

## Procédure de changement

Toute modification d'un artefact de gouvernance IA (`.claude/rules/`, `.claude/skills/`,
`.claude/agents/`, `.claude/commands/`, `.claude/hooks/`, `.claude/settings.json`,
`.rtk/filters.toml`, ce document) doit :

1. Éviter tout chiffre périssable — renvoyer vers la source vivante
2. Être mentionnée dans la description de la PR (ce sont des fichiers de gouvernance, pas
   de simples fichiers de config)
3. Si elle touche un `.ps1` : rester ASCII et être **exécutée une fois** avant de conclure —
   une erreur d'analyse rend un hook muet sans rien casser de visible (`.claude/rules/tooling.md`)
4. Si elle touche `.rtk/filters.toml` : rejouer les tests inline du filtre, puis réaccorder
   la confiance locale
